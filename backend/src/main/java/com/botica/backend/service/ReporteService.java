package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.EgresoPorMotivoResponse;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import com.botica.backend.exception.FiltroInvalidoException;
import com.botica.backend.exception.RangoFechasInvalidoException;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Servicio del reporte de resultados (D-E2-3, Tarea 18).
 *
 * <ul>
 *   <li>Solo {@code ADMINISTRADOR} (restringido en {@code SecurityConfig}).</li>
 *   <li>Rango válido: {@code desde <= hasta} y máximo 1 año ({@code hasta <= desde.plusYears(1)}).</li>
 *   <li>Agrupación: {@code dia} (por defecto), {@code semana} (lunes a domingo) o {@code mes},
 *       por día calendario de Lima.</li>
 *   <li>Totales calculados en el servidor (R19: el frontend no suma dinero).</li>
 *   <li>Exportación .xlsx (Bloque C, {@link ReporteExcel}): valores del servidor, no fórmulas.</li>
 * </ul>
 */
@Service
public class ReporteService {

    public static final String NOTA_REPORTE_INTERNO =
            "Reporte interno, sin valor tributario. Montos con IGV incluido.";

    private static final Set<String> AGRUPACIONES_VALIDAS = Set.of("dia", "semana", "mes");

    private final ReporteDao reporteDao;
    private final ContextoOperacion contexto;
    private final FechaNegocio fechaNegocio;

    public ReporteService(ReporteDao reporteDao, ContextoOperacion contexto, FechaNegocio fechaNegocio) {
        this.reporteDao = reporteDao;
        this.contexto = contexto;
        this.fechaNegocio = fechaNegocio;
    }

    @Transactional(readOnly = true)
    public ReporteResultadosResponse obtenerResultados(
            LocalDate desde, LocalDate hasta, String agrupar) {
        return obtenerResultados(contexto.boticaId(), desde, hasta, agrupar);
    }

    @Transactional(readOnly = true)
    public ReporteResultadosResponse obtenerResultados(
            Long boticaId, LocalDate desde, LocalDate hasta, String agrupar) {
        String agrupacionNormalizada = validarParametros(desde, hasta, agrupar);
        List<FilaResultadoResponse> filas = reporteDao.consultarFilasResultados(
                boticaId, desde, hasta, agrupacionNormalizada);
        TotalesResultadoResponse totales = reporteDao.consultarTotalesResultados(
                boticaId, desde, hasta);
        return new ReporteResultadosResponse(
                desde,
                hasta,
                agrupacionNormalizada,
                NOTA_REPORTE_INTERNO,
                filas,
                totales
        );
    }

    /** Archivo .xlsx del reporte (Bloque C): reemplaza al CSV, cuyo separador dependía del Excel regional. */
    @Transactional(readOnly = true)
    public byte[] generarExcelResultados(LocalDate desde, LocalDate hasta, String agrupar) {
        return generarExcelResultados(contexto.boticaId(), desde, hasta, agrupar);
    }

    @Transactional(readOnly = true)
    public byte[] generarExcelResultados(Long boticaId, LocalDate desde, LocalDate hasta, String agrupar) {
        ReporteResultadosResponse reporte = obtenerResultados(boticaId, desde, hasta, agrupar);
        List<EgresoPorMotivoResponse> egresos = reporteDao.consultarEgresosPorMotivo(boticaId, desde, hasta);
        String nombreBotica = reporteDao.consultarNombreBotica(boticaId);
        return ReporteExcel.generar(nombreBotica, reporte, egresos, fechaNegocio.ahora());
    }

    /** Nombre de descarga: {@code reporte-resultados_<desde>_<hasta>.xlsx}. */
    public static String nombreArchivoExcel(LocalDate desde, LocalDate hasta) {
        return "reporte-resultados_" + desde + "_" + hasta + ".xlsx";
    }

    private String validarParametros(LocalDate desde, LocalDate hasta, String agrupar) {
        if (desde == null || hasta == null) {
            throw new RangoFechasInvalidoException("Las fechas desde y hasta son obligatorias");
        }
        if (desde.isAfter(hasta)) {
            throw new RangoFechasInvalidoException("La fecha desde no puede ser mayor que hasta");
        }
        if (hasta.isAfter(desde.plusYears(1))) {
            throw new RangoFechasInvalidoException("El rango máximo permitido es de 1 año");
        }
        String norm = (agrupar == null || agrupar.isBlank()) ? "dia" : agrupar.trim().toLowerCase();
        if (!AGRUPACIONES_VALIDAS.contains(norm)) {
            throw new FiltroInvalidoException("agrupar", agrupar);
        }
        return norm;
    }
}
