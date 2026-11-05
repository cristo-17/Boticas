package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.DigemidDao;
import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.FilaDigemidResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ReporteDigemidResponse;
import com.botica.backend.dto.TotalDigemidProductoResponse;
import com.botica.backend.dto.TotalesDigemidResponse;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reporte interno de dispensación con receta (RF-54). Solo ADMINISTRADOR
 * (SecurityConfig, /api/reportes/**). Mismas reglas de rango que Resultados
 * ({@link ReporteService#validarRango}); días de Lima. Se calcula al consultar,
 * como el Kardex. Paginado (Anexo D); el .xlsx lleva todas las líneas.
 */
@Service
public class DigemidService {

    public static final String NOTA =
            "Reporte interno de dispensación con receta. No reemplaza los formatos oficiales de DIGEMID.";

    private final DigemidDao digemidDao;
    private final ReporteDao reporteDao;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;

    public DigemidService(DigemidDao digemidDao, ReporteDao reporteDao, ContextoOperacion contexto,
                          FechaNegocio fechaNegocio) {
        this.digemidDao = digemidDao;
        this.reporteDao = reporteDao;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
    }

    @Transactional(readOnly = true)
    public ReporteDigemidResponse consultar(LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        return consultar(contexto.boticaId(), desde, hasta, pagina, tamano);
    }

    @Transactional(readOnly = true)
    public ReporteDigemidResponse consultar(Long boticaId, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        ReporteService.validarRango(desde, hasta);
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);
        OffsetDateTime inicio = inicio(desde);
        OffsetDateTime fin = fin(hasta);
        TotalesDigemidResponse totales = digemidDao.totales(boticaId, inicio, fin);
        List<FilaDigemidResponse> lineas = digemidDao.listarLineas(
                boticaId, inicio, fin, tamanoSeguro, (long) paginaSegura * tamanoSeguro);
        return new ReporteDigemidResponse(desde, hasta, NOTA,
                PaginaResponse.de(lineas, paginaSegura, tamanoSeguro, totales.lineas()),
                digemidDao.totalesPorProducto(boticaId, inicio, fin), totales);
    }

    @Transactional(readOnly = true)
    public byte[] generarExcel(LocalDate desde, LocalDate hasta) {
        return generarExcel(contexto.boticaId(), desde, hasta);
    }

    @Transactional(readOnly = true)
    public byte[] generarExcel(Long boticaId, LocalDate desde, LocalDate hasta) {
        ReporteService.validarRango(desde, hasta);
        OffsetDateTime inicio = inicio(desde);
        OffsetDateTime fin = fin(hasta);
        List<FilaDigemidResponse> lineas = digemidDao.listarLineas(boticaId, inicio, fin, null, 0);
        List<TotalDigemidProductoResponse> porProducto = digemidDao.totalesPorProducto(boticaId, inicio, fin);
        ReporteDigemidResponse reporte = new ReporteDigemidResponse(desde, hasta, NOTA,
                PaginaResponse.de(lineas, 0, Math.max(lineas.size(), 1), lineas.size()),
                porProducto, digemidDao.totales(boticaId, inicio, fin));
        return DigemidExcel.generar(reporteDao.consultarNombreBotica(boticaId), reporte, fechaNegocio.ahora());
    }

    /** reporte-digemid_<desde>_<hasta>.xlsx — el mismo que arma el frontend. */
    public static String nombreArchivoExcel(LocalDate desde, LocalDate hasta) {
        return "reporte-digemid_" + desde + "_" + hasta + ".xlsx";
    }

    private static OffsetDateTime inicio(LocalDate desde) {
        return desde.atStartOfDay(FechaNegocio.ZONA_LIMA).toOffsetDateTime();
    }

    /** Exclusivo: inicio del día siguiente a "hasta", en Lima. */
    private static OffsetDateTime fin(LocalDate hasta) {
        return hasta.plusDays(1).atStartOfDay(FechaNegocio.ZONA_LIMA).toOffsetDateTime();
    }
}
