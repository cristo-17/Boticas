package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dto.PresentacionResponse;
import com.botica.backend.dto.ProductoResponse;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import com.botica.backend.util.EstadosNegocio;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Reglas de negocio de productos — sin SQL acá (Regla 2). */
@Service
public class ProductoService {

    private static final int LIMITE_BUSQUEDA = 20;
    private static final int LIMITE_MAS_VENDIDOS = 6;

    private final ProductoDao productoDao;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;
    private final ConfigNegocioProperties config;

    public ProductoService(ProductoDao productoDao, ContextoOperacion contexto, FechaNegocio fechaNegocio, ConfigNegocioProperties config) {
        this.productoDao = productoDao;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
        this.config = config;
    }

    public List<ProductoResponse> buscar(String query) {
        List<Producto> productos = productoDao.buscar(contexto.boticaId(), query, LIMITE_BUSQUEDA);
        return construirRespuestas(productos);
    }

    public ProductoResponse buscarPorCodigoBarras(String codigoBarras) {
        Producto producto = productoDao.buscarPorCodigoBarras(contexto.boticaId(), codigoBarras)
                .orElseThrow(ProductoNoEncontradoException::new);
        return construirRespuestas(List.of(producto)).get(0);
    }

    public List<ProductoResponse> masVendidos() {
        List<Producto> productos = productoDao.masVendidos(contexto.boticaId(), LIMITE_MAS_VENDIDOS);
        return construirRespuestas(productos);
    }

    private List<ProductoResponse> construirRespuestas(List<Producto> productos) {
        List<Long> ids = productos.stream().map(Producto::getId).toList();
        Map<Long, LocalDate> fechasProximas = productoDao.fechaVencimientoMasProximaPorProductos(ids);
        Map<Long, List<PresentacionProducto>> presentacionesPorProducto = productoDao.listarPresentacionesPorProductos(ids);
        LocalDate hoy = fechaNegocio.hoy();

        return productos.stream().map(producto -> {
            LocalDate fechaVencimiento = fechasProximas.get(producto.getId());
            String estadoVencimiento = EstadosNegocio.estadoVencimiento(
                    fechaVencimiento, hoy, config.getVencimientoCriticoDias(), config.getVencimientoAdvertenciaDias());
            List<PresentacionResponse> presentaciones = presentacionesPorProducto
                    .getOrDefault(producto.getId(), List.of())
                    .stream()
                    .map(p -> PresentacionResponse.desde(p, producto.getUnidadNombre()))
                    .toList();
            return ProductoResponse.desde(producto, estadoVencimiento, fechaVencimiento, presentaciones);
        }).toList();
    }
}
