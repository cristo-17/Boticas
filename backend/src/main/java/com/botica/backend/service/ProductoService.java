package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dto.CatalogoOfflineResponse;
import com.botica.backend.dto.LoteVendibleResponse;
import com.botica.backend.dto.PresentacionResponse;
import com.botica.backend.dto.ProductoOfflineResponse;
import com.botica.backend.dto.ProductoResponse;
import com.botica.backend.exception.ProductoInactivoException;
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
    private static final int LIMITE_CATALOGO_OFFLINE = 5000;
    /**
     * DIF-06: ventana de "más vendidos", hoy incluido. El Punto de Venta la rotula
     * "Más vendidos (últimos 30 días)" (punto-venta.html): si cambia, cambia también el rótulo.
     */
    private static final int DIAS_MAS_VENDIDOS = 30;

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

    /** incluirInactivos: solo Merma lo pide (Tarea 17); Punto de Venta y el alta de lote ven únicamente activos. */
    public List<ProductoResponse> buscar(String query, boolean incluirInactivos) {
        List<Producto> productos = productoDao.buscar(contexto.boticaId(), query, incluirInactivos, LIMITE_BUSQUEDA);
        return construirRespuestas(productos);
    }

    public ProductoResponse buscarPorCodigoBarras(String codigoBarras) {
        Producto producto = productoDao.buscarPorCodigoBarras(contexto.boticaId(), codigoBarras)
                .orElseThrow(ProductoNoEncontradoException::new);
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            // El escáner es de Punto de Venta: un desactivado no se vende (Tarea 17).
            throw new ProductoInactivoException(producto.getNombre());
        }
        return construirRespuestas(List.of(producto)).get(0);
    }

    public List<ProductoResponse> masVendidos() {
        List<Producto> productos = productoDao.masVendidos(contexto.boticaId(), LIMITE_MAS_VENDIDOS, fechaNegocio.hoy().minusDays(DIAS_MAS_VENDIDOS - 1));
        return construirRespuestas(productos);
    }

    /**
     * Tarea 22: GET /api/productos/catalogo-offline. Todos los productos activos de la botica
     * (sin el límite de 20 de la búsqueda), con sus lotes vendibles. LIMITE_CATALOGO_OFFLINE es una
     * guardia: una botica independiente tiene cientos de productos, no miles.
     */
    public CatalogoOfflineResponse catalogoOffline() {
        Long boticaId = contexto.boticaId();
        List<Producto> productos = productoDao.buscar(boticaId, "", false, LIMITE_CATALOGO_OFFLINE);
        List<ProductoResponse> respuestas = construirRespuestas(productos);
        Map<Long, List<LoteVendibleResponse>> lotes = productoDao.lotesVendiblesPorProductos(
                boticaId, productos.stream().map(Producto::getId).toList(), fechaNegocio.hoy());
        List<ProductoOfflineResponse> conLotes = respuestas.stream()
                .map(p -> ProductoOfflineResponse.desde(p, lotes.getOrDefault(p.id(), List.of())))
                .toList();
        return new CatalogoOfflineResponse(boticaId, fechaNegocio.ahora(), conLotes);
    }

    private List<ProductoResponse> construirRespuestas(List<Producto> productos) {
        List<Long> ids = productos.stream().map(Producto::getId).toList();
        LocalDate hoy = fechaNegocio.hoy();
        Map<Long, LocalDate> fechasProximas = productoDao.fechaVencimientoMasProximaPorProductos(ids, hoy);
        Map<Long, List<PresentacionProducto>> presentacionesPorProducto = productoDao.listarPresentacionesPorProductos(ids);

        return productos.stream().map(producto -> {
            LocalDate fechaVencimiento = fechasProximas.get(producto.getId());
            String estadoVencimiento = EstadosNegocio.estadoVencimiento(
                    fechaVencimiento, hoy, config.getVencimientoCriticoDias(), config.getVencimientoAdvertenciaDias());
            List<PresentacionResponse> presentaciones = presentacionesPorProducto
                    .getOrDefault(producto.getId(), List.of())
                    .stream()
                    // Solo las vendibles: una presentación quitada al editar no se ofrece en ninguna lista.
                    .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                    .map(p -> PresentacionResponse.desde(p, producto.getUnidadNombre()))
                    .toList();
            return ProductoResponse.desde(producto, estadoVencimiento, fechaVencimiento, presentaciones);
        }).toList();
    }
}
