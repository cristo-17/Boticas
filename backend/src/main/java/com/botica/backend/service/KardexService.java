package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.KardexDao;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.FilaKardexResponse;
import com.botica.backend.dto.KardexResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenKardexResponse;
import com.botica.backend.exception.ProductoNoEncontradoException;
import com.botica.backend.exception.RangoFechasInvalidoException;
import com.botica.backend.model.Producto;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kardex por producto (Tarea 20, D-E2-5). Solo ADMINISTRADOR (SecurityConfig).
 *
 * <ul>
 *   <li>desde/hasta son días de Lima, ambos opcionales; desde &lt;= hasta.</li>
 *   <li>Con desde, la primera fila es el saldo anterior y el acumulado sigue desde ahí.</li>
 *   <li>Un producto desactivado conserva su Kardex (Tarea 17).</li>
 *   <li>Paginado (Anexo D); la exportación .xlsx lleva todas las filas.</li>
 * </ul>
 */
@Service
public class KardexService {

    private final KardexDao kardexDao;
    private final ProductoDao productoDao;
    private final ReporteDao reporteDao;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;

    public KardexService(KardexDao kardexDao, ProductoDao productoDao, ReporteDao reporteDao,
                         ContextoOperacion contexto, FechaNegocio fechaNegocio) {
        this.kardexDao = kardexDao;
        this.productoDao = productoDao;
        this.reporteDao = reporteDao;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
    }

    @Transactional(readOnly = true)
    public KardexResponse consultar(Long productoId, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        Long boticaId = contexto.boticaId();
        Producto producto = validar(boticaId, productoId, desde, hasta);
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);
        OffsetDateTime inicio = inicio(desde);
        OffsetDateTime fin = fin(hasta);

        ResumenKardexResponse resumen = kardexDao.resumir(boticaId, productoId, inicio, fin);
        List<FilaKardexResponse> filas = kardexDao.listarFilas(
                boticaId, productoId, inicio, fin, tamanoSeguro, (long) paginaSegura * tamanoSeguro);
        long total = resumen.movimientos() + (desde != null ? 1 : 0); // + la fila SALDO_ANTERIOR
        return respuesta(producto, desde, hasta, resumen, PaginaResponse.de(filas, paginaSegura, tamanoSeguro, total));
    }

    /** Todas las filas del rango, sin paginar, en .xlsx (mismo formato que el reporte de resultados). */
    @Transactional(readOnly = true)
    public byte[] generarExcel(Long productoId, LocalDate desde, LocalDate hasta) {
        Long boticaId = contexto.boticaId();
        Producto producto = validar(boticaId, productoId, desde, hasta);
        OffsetDateTime inicio = inicio(desde);
        OffsetDateTime fin = fin(hasta);
        ResumenKardexResponse resumen = kardexDao.resumir(boticaId, productoId, inicio, fin);
        List<FilaKardexResponse> filas = kardexDao.listarFilas(boticaId, productoId, inicio, fin, null, 0);
        KardexResponse kardex = respuesta(producto, desde, hasta, resumen,
                PaginaResponse.de(filas, 0, Math.max(filas.size(), 1), filas.size()));
        return KardexExcel.generar(reporteDao.consultarNombreBotica(boticaId), kardex, fechaNegocio.ahora());
    }

    /** kardex_<productoId>[_<desde>][_<hasta>].xlsx — el mismo que arma el frontend. */
    public static String nombreArchivoExcel(Long productoId, LocalDate desde, LocalDate hasta) {
        return "kardex_" + productoId
                + (desde != null ? "_" + desde : "")
                + (hasta != null ? "_" + hasta : "")
                + ".xlsx";
    }

    private Producto validar(Long boticaId, Long productoId, LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new RangoFechasInvalidoException("La fecha desde no puede ser mayor que hasta");
        }
        // Otra botica = inexistente (404), igual que en el resto de la API.
        return productoDao.obtenerPorId(boticaId, productoId).orElseThrow(ProductoNoEncontradoException::new);
    }

    private static OffsetDateTime inicio(LocalDate desde) {
        return desde == null ? null : desde.atStartOfDay(FechaNegocio.ZONA_LIMA).toOffsetDateTime();
    }

    /** Exclusivo: inicio del día siguiente a "hasta", en Lima. */
    private static OffsetDateTime fin(LocalDate hasta) {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay(FechaNegocio.ZONA_LIMA).toOffsetDateTime();
    }

    private static KardexResponse respuesta(Producto producto, LocalDate desde, LocalDate hasta,
                                            ResumenKardexResponse resumen, PaginaResponse<FilaKardexResponse> pagina) {
        return new KardexResponse(producto.getId(), producto.getNombre(), producto.getUnidadNombre(),
                producto.isRequiereReceta(), Boolean.TRUE.equals(producto.getActivo()), desde, hasta, resumen, pagina);
    }
}
