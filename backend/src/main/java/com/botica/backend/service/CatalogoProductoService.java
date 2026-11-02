package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.PresentacionCatalogoResponse;
import com.botica.backend.dto.PresentacionRequest;
import com.botica.backend.dto.ProductoCatalogoResponse;
import com.botica.backend.dto.ProductoRequest;
import com.botica.backend.exception.CodigoBarrasDuplicadoException;
import com.botica.backend.exception.FactorNoEditableException;
import com.botica.backend.exception.PresentacionDuplicadaException;
import com.botica.backend.exception.PresentacionInvalidaException;
import com.botica.backend.exception.PresentacionUnidadException;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo del administrador (Tarea 17, Bloque A). El rol lo exige
 * SecurityConfig; aquí viven las reglas de dominio:
 * <ul>
 *   <li>Todo producto tiene exactamente una presentación de factor 1, y es
 *       siempre la misma fila: nunca se desactiva ni se reemplaza por otra.
 *       Merma, alertas y lotes la buscan con un JOIN por factor_conversion = 1;
 *       dos filas de factor 1 (aunque una esté inactiva) duplicarían filas ahí.</li>
 *   <li>Nunca se borra nada: un producto se desactiva; una presentación que no
 *       viene en la edición se desactiva (venta_detalle la referencia).</li>
 *   <li>El factor de una presentación existente no se edita; el precio sí, y
 *       solo afecta ventas futuras (venta_detalle lo tiene congelado).</li>
 * </ul>
 */
@Service
public class CatalogoProductoService {

    private final ProductoDao productoDao;
    private final ContextoOperacion contexto;

    public CatalogoProductoService(ProductoDao productoDao, ContextoOperacion contexto) {
        this.productoDao = productoDao;
        this.contexto = contexto;
    }

    public PaginaResponse<ProductoCatalogoResponse> listar(String buscar, int pagina, int tamano, String orden) {
        PaginaResponse<Producto> productos = productoDao.listarCatalogo(contexto.boticaId(), buscar, pagina, tamano, orden);
        List<Long> ids = productos.contenido().stream().map(Producto::getId).toList();
        Map<Long, List<PresentacionProducto>> presentaciones = productoDao.listarPresentacionesPorProductos(ids);
        List<ProductoCatalogoResponse> contenido = productos.contenido().stream()
                .map(p -> aRespuesta(p, presentaciones.getOrDefault(p.getId(), List.of())))
                .toList();
        return new PaginaResponse<>(contenido, productos.pagina(), productos.tamano(), productos.totalElementos(), productos.totalPaginas());
    }

    @Transactional
    public ProductoCatalogoResponse crear(ProductoRequest request) {
        Long boticaId = contexto.boticaId();
        if (request.presentaciones().stream().anyMatch(p -> p.id() != null)) {
            // Un producto nuevo no tiene presentaciones existentes que referenciar.
            throw new PresentacionInvalidaException();
        }
        validarConjunto(request.presentaciones());

        Producto producto = aModelo(request, boticaId);
        producto.setCreadoPor(contexto.usuarioId());
        Long productoId;
        try {
            productoId = productoDao.insertar(producto);
        } catch (DuplicateKeyException e) {
            throw new CodigoBarrasDuplicadoException(producto.getCodigoBarras());
        }
        for (PresentacionRequest p : request.presentaciones()) {
            productoDao.insertarPresentacion(nuevaPresentacion(p, boticaId, productoId));
        }
        return obtener(boticaId, productoId);
    }

    @Transactional
    public ProductoCatalogoResponse actualizar(Long productoId, ProductoRequest request) {
        Long boticaId = contexto.boticaId();
        productoDao.obtenerPorId(boticaId, productoId).orElseThrow(ProductoNoEncontradoException::new);
        validarConjunto(request.presentaciones());

        Map<Long, PresentacionProducto> existentes = productoDao.listarPresentacionesPorProductos(List.of(productoId))
                .getOrDefault(productoId, List.of()).stream()
                .collect(Collectors.toMap(PresentacionProducto::getId, Function.identity()));

        Set<Long> idsEnviados = new HashSet<>();
        for (PresentacionRequest p : request.presentaciones()) {
            if (p.id() == null) {
                continue;
            }
            PresentacionProducto actual = existentes.get(p.id());
            if (actual == null || !idsEnviados.add(p.id())) {
                // De otro producto, de otra botica, inexistente o repetida en la misma petición.
                throw new PresentacionInvalidaException();
            }
            if (!actual.getFactorConversion().equals(p.factorConversion())) {
                throw new FactorNoEditableException(actual.getEtiqueta());
            }
        }

        // La fila de factor 1 que ya existe es la única que puede haber: la petición la trae por su id.
        Optional<PresentacionProducto> unidadActual = existentes.values().stream()
                .filter(p -> p.getFactorConversion() == 1).findFirst();
        PresentacionRequest unidadEnviada = request.presentaciones().stream()
                .filter(p -> p.factorConversion() == 1).findFirst().orElseThrow();
        if (unidadActual.isPresent() && !unidadActual.get().getId().equals(unidadEnviada.id())) {
            throw new PresentacionUnidadException(
                    "La presentación de factor 1 (" + unidadActual.get().getEtiqueta() + ") no se quita ni se reemplaza: edita su precio");
        }

        Producto producto = aModelo(request, boticaId);
        producto.setId(productoId);
        try {
            productoDao.actualizar(producto);
        } catch (DuplicateKeyException e) {
            throw new CodigoBarrasDuplicadoException(producto.getCodigoBarras());
        }

        Map<Long, PresentacionRequest> enviadasPorId = request.presentaciones().stream()
                .filter(p -> p.id() != null)
                .collect(Collectors.toMap(PresentacionRequest::id, Function.identity()));
        for (PresentacionProducto actual : existentes.values()) {
            PresentacionRequest enviada = enviadasPorId.get(actual.getId());
            if (enviada != null) {
                actual.setEtiqueta(enviada.etiqueta().trim());
                actual.setPrecio(enviada.precio());
                actual.setActivo(true); // reenviar una desactivada la reactiva
                productoDao.actualizarPresentacion(actual);
            } else if (Boolean.TRUE.equals(actual.getActivo())) {
                actual.setActivo(false); // quitada: se desactiva, nunca se borra
                productoDao.actualizarPresentacion(actual);
            }
        }
        for (PresentacionRequest p : request.presentaciones()) {
            if (p.id() == null) {
                productoDao.insertarPresentacion(nuevaPresentacion(p, boticaId, productoId));
            }
        }
        return obtener(boticaId, productoId);
    }

    @Transactional
    public ProductoCatalogoResponse cambiarEstado(Long productoId, boolean activo) {
        Long boticaId = contexto.boticaId();
        productoDao.obtenerPorId(boticaId, productoId).orElseThrow(ProductoNoEncontradoException::new);
        productoDao.cambiarEstado(boticaId, productoId, activo);
        return obtener(boticaId, productoId);
    }

    /** Reglas sobre el conjunto que queda activo: exactamente un factor 1 y etiquetas sin repetir. */
    private void validarConjunto(List<PresentacionRequest> presentaciones) {
        long unidades = presentaciones.stream().filter(p -> p.factorConversion() == 1).count();
        if (unidades != 1) {
            throw new PresentacionUnidadException(
                    "El producto necesita exactamente una presentación de factor 1 (la unidad); llegaron " + unidades);
        }
        Set<String> etiquetas = new HashSet<>();
        for (PresentacionRequest p : presentaciones) {
            if (!etiquetas.add(p.etiqueta().trim().toLowerCase(Locale.ROOT))) {
                throw new PresentacionDuplicadaException(p.etiqueta().trim());
            }
        }
    }

    private ProductoCatalogoResponse obtener(Long boticaId, Long productoId) {
        Producto producto = productoDao.obtenerPorId(boticaId, productoId).orElseThrow(ProductoNoEncontradoException::new);
        List<PresentacionProducto> presentaciones = productoDao.listarPresentacionesPorProductos(List.of(productoId))
                .getOrDefault(productoId, List.of());
        return aRespuesta(producto, presentaciones);
    }

    private static ProductoCatalogoResponse aRespuesta(Producto producto, List<PresentacionProducto> presentaciones) {
        return ProductoCatalogoResponse.desde(producto, presentaciones.stream().map(PresentacionCatalogoResponse::desde).toList());
    }

    private static Producto aModelo(ProductoRequest request, Long boticaId) {
        String laboratorio = request.laboratorio() == null || request.laboratorio().isBlank() ? null : request.laboratorio().trim();
        return Producto.builder()
                .boticaId(boticaId)
                .nombre(request.nombre().trim())
                .laboratorio(laboratorio)
                .categoria(request.categoria().trim())
                .codigoBarras(request.codigoBarras().trim())
                .unidadNombre(request.unidadNombre().trim())
                .build();
    }

    private static PresentacionProducto nuevaPresentacion(PresentacionRequest p, Long boticaId, Long productoId) {
        return PresentacionProducto.builder()
                .boticaId(boticaId)
                .productoId(productoId)
                .etiqueta(p.etiqueta().trim())
                .factorConversion(p.factorConversion())
                .precio(p.precio())
                .build();
    }
}
