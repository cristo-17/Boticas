package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.ReporteDao;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.ReporteResultadosResponse;
import com.botica.backend.dto.TotalesResultadoResponse;
import com.botica.backend.exception.FiltroInvalidoException;
import com.botica.backend.exception.RangoFechasInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Servicio del reporte de resultados (D-E2-3, Tarea 18).
 *
 * <ul>
 *   <li>Solo {@code ADMINISTRADOR} (restringido en {@code SecurityConfig}).</li>
 *   <li>Rango válido: {@code desde <= hasta} y máximo 1 año ({@code hasta <= desde.plusYears(1)}).</li>
 *   <li>Agrupación: {@code dia} (por defecto) o {@code mes}, por día calendario de Lima.</li>
 *   <li>Totales calculados en el servidor (R19: el frontend no suma dinero).</li>
 *   <li>CSV en UTF-8 con BOM ({@code \uFEFF}) para apertura directa en Excel con tildes.</li>
 * </ul>
 */
@Service
public class ReporteService {

    public static final String NOTA_REPORTE_INTERNO =
            "Reporte interno, sin valor tributario. Montos con IGV incluido.";

    public static final String UTF8_BOM = "\uFEFF";

    private static final Set<String> AGRUPACIONES_VALIDAS = Set.of("dia", "mes");

    private final ReporteDao reporteDao;
    private final ContextoOperacion contexto;

    public ReporteService(ReporteDao reporteDao, ContextoOperacion contexto) {
        this.reporteDao = reporteDao;
        this.contexto = contexto;
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

    @Transactional(readOnly = true)
    public String generarCsvResultados(
            LocalDate desde, LocalDate hasta, String agrupar) {
        return generarCsvResultados(contexto.boticaId(), desde, hasta, agrupar);
    }

    @Transactional(readOnly = true)
    public String generarCsvResultados(
            Long boticaId, LocalDate desde, LocalDate hasta, String agrupar) {
        ReporteResultadosResponse reporte = obtenerResultados(boticaId, desde, hasta, agrupar);
        StringBuilder sb = new StringBuilder();
        sb.append(UTF8_BOM);
        sb.append("Período,Ventas (S/),Costo de ventas (S/),Margen bruto (S/),Mermas a costo (S/),Egresos de caja (S/),Resultado neto (S/)\r\n");
        for (FilaResultadoResponse f : reporte.filas()) {
            sb.append(f.periodo()).append(',')
              .append(fmt(f.ventas())).append(',')
              .append(fmt(f.costoVentas())).append(',')
              .append(fmt(f.margenBruto())).append(',')
              .append(fmt(f.mermasCosto())).append(',')
              .append(fmt(f.egresos())).append(',')
              .append(fmt(f.resultado())).append("\r\n");
        }
        TotalesResultadoResponse t = reporte.totales();
        sb.append("TOTAL").append(',')
          .append(fmt(t.ventas())).append(',')
          .append(fmt(t.costoVentas())).append(',')
          .append(fmt(t.margenBruto())).append(',')
          .append(fmt(t.mermasCosto())).append(',')
          .append(fmt(t.egresos())).append(',')
          .append(fmt(t.resultado())).append("\r\n");
        return sb.toString();
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

    private static String fmt(BigDecimal valor) {
        if (valor == null) {
            return "0.00";
        }
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
