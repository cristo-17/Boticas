package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.AlertaDao;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenDashboardResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.Dinero;
import com.botica.backend.util.FechaNegocio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reglas de negocio de alertas (Tarea 11 Bloque C) — sin SQL acá
 * (Regla 2). Los KPIs de {@link #resumen()} son cada uno un COUNT/SUM
 * real del DAO (regla explícita de Alertas: nunca traer todo a Java y
 * filtrar ahí). La lista de {@link #listar} sí compone texto en Java
 * (títulos/cuerpos en español) a partir de filas YA agregadas por SQL
 * — la agregación pasó por Postgres, la redacción es responsabilidad
 * del Service, no del DAO.
 */
@Service
public class AlertaService {

    // fecha_vencimiento es DATE (sin hora ni zona, Regla 5) -- se formatea directo, sin withZone.
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(FechaNegocio.ZONA_LIMA);

    private final AlertaDao alertaDao;
    private final CajaDao cajaDao;
    private final ContextoOperacion contexto;
    private final ConfigNegocioProperties config;
    private final FechaNegocio fechaNegocio;

    public AlertaService(AlertaDao alertaDao, CajaDao cajaDao, ContextoOperacion contexto,
                          ConfigNegocioProperties config, FechaNegocio fechaNegocio) {
        this.alertaDao = alertaDao;
        this.cajaDao = cajaDao;
        this.contexto = contexto;
        this.config = config;
        this.fechaNegocio = fechaNegocio;
    }

    public ResumenDashboardResponse resumen() {
        Long boticaId = contexto.boticaId();
        LocalDate hoy = fechaNegocio.hoy();
        LocalDate ayer = hoy.minusDays(1);

        BigDecimal ventasHoy = Dinero.redondear(alertaDao.totalVentas(boticaId, hoy));
        BigDecimal ventasAyer = alertaDao.totalVentas(boticaId, ayer);
        long boletasHoy = alertaDao.contarVentas(boticaId, hoy);
        long productosPorVencer = alertaDao.contarProductosPorVencer(boticaId, hoy, config.getVencimientoAdvertenciaDias());
        long productosPorVencerCriticos = alertaDao.contarProductosPorVencer(boticaId, hoy, config.getVencimientoCriticoDias());
        long stockCritico = alertaDao.contarProductosStockCritico(boticaId, config.getUmbralStockBajo());
        long stockAgotado = alertaDao.contarProductosStockAgotado(boticaId);

        Optional<CajaDiaria> cajaPropia = cajaDao.buscarAbiertaDelUsuario(boticaId, contexto.usuarioId());
        String cajaEstado = cajaPropia.isPresent() ? CajaDiaria.ABIERTA : CajaDiaria.CERRADA;
        OffsetDateTime cajaHoraApertura = cajaPropia.map(CajaDiaria::getHoraApertura).orElse(null);

        return new ResumenDashboardResponse(ventasHoy, calcularVariacionPct(ventasAyer, ventasHoy), boletasHoy,
                productosPorVencer, productosPorVencerCriticos, stockCritico, stockAgotado, cajaEstado, cajaHoraApertura);
    }

    public PaginaResponse<AlertaResponse> listar(int pagina, int tamano) {
        Long boticaId = contexto.boticaId();
        LocalDate hoy = fechaNegocio.hoy();

        List<Ordenable> todas = new ArrayList<>();
        for (AlertaDao.LoteAlerta lote : alertaDao.lotesVencidosOCriticos(boticaId, hoy, config.getVencimientoCriticoDias())) {
            todas.add(ordenableDeLote(lote, hoy));
        }

        long stockCritico = alertaDao.contarProductosStockCritico(boticaId, config.getUmbralStockBajo());
        long stockAgotado = alertaDao.contarProductosStockAgotado(boticaId);
        long stockBajoTotal = stockCritico + stockAgotado;
        if (stockBajoTotal > 0) {
            todas.add(ordenableDeStockBajo(boticaId, stockBajoTotal));
        }

        for (AlertaDao.CajaAbierta caja : alertaDao.cajasSinCerrar(boticaId, hoy)) {
            todas.add(ordenableDeCaja(caja));
        }

        todas.sort(Comparator.comparingInt((Ordenable o) -> o.rango)
                .thenComparing((Ordenable o) -> o.monto, Comparator.reverseOrder()));

        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.min(Math.max(tamano, 1), 100);
        int desde = Math.min(paginaSegura * tamanoSeguro, todas.size());
        int hasta = Math.min(desde + tamanoSeguro, todas.size());
        List<AlertaResponse> pagina2 = todas.subList(desde, hasta).stream().map(o -> o.response).toList();
        return PaginaResponse.de(pagina2, paginaSegura, tamanoSeguro, todas.size());
    }

    private Ordenable ordenableDeLote(AlertaDao.LoteAlerta lote, LocalDate hoy) {
        boolean vencido = "VENCIDO".equals(lote.estadoVencimiento());
        long dias = ChronoUnit.DAYS.between(hoy, lote.fechaVencimiento());
        String ubicacionTexto = (lote.ubicacion() != null && !lote.ubicacion().isBlank()) ? " en " + lote.ubicacion() : "";
        String titulo = vencido
                ? lote.productoNombre() + " · lote " + lote.codigo() + " vencido"
                : lote.productoNombre() + " vence en " + dias + " días";
        String cuerpo = vencido
                ? lote.stock() + " unidades vencieron el " + FORMATO_FECHA.format(lote.fechaVencimiento()) + ubicacionTexto + ". Retíralas y regístralas como merma."
                : lote.stock() + " unidades" + ubicacionTexto + ". Aplica descuento de rotación o devuelve al proveedor esta semana.";
        BigDecimal precio = lote.precioUnitario() == null ? BigDecimal.ZERO : lote.precioUnitario();
        BigDecimal monto = Dinero.redondear(precio.multiply(BigDecimal.valueOf(lote.stock())));

        AlertaResponse response = new AlertaResponse(
                "venc-" + lote.loteId(), "urgente", titulo, cuerpo, formatearSoles(monto),
                vencido ? "Retirar lote" : "Ver lote", "/inventario",
                Map.of("vencimiento", vencido ? "vencido" : "critico"));
        return new Ordenable(0, monto, response);
    }

    private Ordenable ordenableDeStockBajo(Long boticaId, long total) {
        List<String> nombres = alertaDao.nombresProductosStockBajo(boticaId, config.getUmbralStockBajo(), 2);
        long masCount = total - nombres.size();
        String listado = String.join(", ", nombres);
        String cuerpo = (listado.isEmpty() ? "" : listado + (masCount > 0 ? " y " + masCount + " más " : " "))
                + "por debajo del mínimo del turno.";
        AlertaResponse response = new AlertaResponse(
                "stock-critico", "atencion", total + " productos en stock crítico", cuerpo, null,
                "Ver inventario", "/inventario", Map.of("soloStockBajo", "true"));
        return new Ordenable(1, BigDecimal.ZERO, response);
    }

    private Ordenable ordenableDeCaja(AlertaDao.CajaAbierta caja) {
        String horaTexto = caja.horaApertura() != null ? FORMATO_HORA.format(caja.horaApertura()) : "--:--";
        String titulo = "Caja sin cerrar del turno " + caja.turno().toLowerCase(Locale.ROOT);
        // Sin monto (conteo ciego, hueco 2): el esperado de una caja abierta no se expone en ninguna pantalla antes del cierre, tampoco acá.
        String cuerpo = caja.usuarioNombre() + " abrió a las " + horaTexto + " y no registró el cierre. Cuadra cuanto antes.";
        AlertaResponse response = new AlertaResponse(
                "caja-" + caja.cajaId(), "atencion", titulo, cuerpo, null,
                "Cerrar caja", "/caja", Map.of("tab", "cierre"));
        return new Ordenable(1, BigDecimal.ZERO, response);
    }

    private String formatearSoles(BigDecimal monto) {
        return "S/ " + Dinero.redondear(monto).toPlainString();
    }

    private int calcularVariacionPct(BigDecimal ayer, BigDecimal hoy) {
        if (ayer.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return hoy.subtract(ayer)
                .divide(ayer, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** Envoltorio interno para ordenar por nivel (0=urgente) y monto desc antes de formatear -- nunca se expone. */
    private record Ordenable(int rango, BigDecimal monto, AlertaResponse response) {
    }
}
