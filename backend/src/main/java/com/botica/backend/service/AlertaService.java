package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.AlertaDao;
import com.botica.backend.dto.AlertaResponse;
import com.botica.backend.dto.ResumenDashboardResponse;
import com.botica.backend.util.Dinero;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AlertaService {

    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final AlertaDao alertaDao;
    private final ContextoOperacion contexto;
    private final ConfigNegocioProperties config;

    public AlertaService(AlertaDao alertaDao, ContextoOperacion contexto, ConfigNegocioProperties config) {
        this.alertaDao = alertaDao;
        this.contexto = contexto;
        this.config = config;
    }

    public List<AlertaResponse> listarAlertas() {
        Long boticaId = contexto.boticaId();
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        LocalDate hoyMasCritico = hoy.plusDays(config.getVencimientoCriticoDias());

        List<AlertaResponse> alertas = new ArrayList<>();

        // 1. Lotes vencidos (Urgente)
        var vencidos = alertaDao.listarLotesVencidos(boticaId, hoy);
        for (var lote : vencidos) {
            BigDecimal totalPerdida = lote.precioUnitario() != null
                    ? Dinero.redondear(lote.precioUnitario().multiply(BigDecimal.valueOf(lote.stock())))
                    : BigDecimal.ZERO;
            alertas.add(new AlertaResponse(
                    "vencido-" + lote.id(),
                    "urgente",
                    lote.productoNombre() + " · lote " + lote.codigo() + " vencido",
                    lote.stock() + " unidades vencieron el " + lote.fechaVencimiento() + ". Retíralas del anaquel y regístralas como merma.",
                    "S/ " + totalPerdida,
                    "Registrar merma",
                    "/merma",
                    Map.of("loteId", String.valueOf(lote.id()))
            ));
        }

        // 2. Lotes por vencer (Atención)
        var porVencer = alertaDao.listarLotesPorVencer(boticaId, hoy, hoyMasCritico);
        for (var lote : porVencer) {
            long dias = ChronoUnit.DAYS.between(hoy, lote.fechaVencimiento());
            BigDecimal totalValor = lote.precioUnitario() != null
                    ? Dinero.redondear(lote.precioUnitario().multiply(BigDecimal.valueOf(lote.stock())))
                    : BigDecimal.ZERO;
            alertas.add(new AlertaResponse(
                    "por-vencer-" + lote.id(),
                    "atencion",
                    lote.productoNombre() + " vence en " + dias + (dias == 1 ? " día" : " días"),
                    lote.stock() + " unidades en lote " + lote.codigo() + ". Aplica descuento de rotación o devuelve al proveedor.",
                    "S/ " + totalValor,
                    "Ver lote",
                    "/inventario",
                    Map.of("vencimiento", "critico")
            ));
        }

        // 3. Productos en stock crítico (Atención)
        var criticos = alertaDao.listarProductosStockCritico(boticaId, config.getUmbralStockBajo());
        for (var p : criticos) {
            alertas.add(new AlertaResponse(
                    "stock-critico-" + p.id(),
                    "atencion",
                    p.nombre() + " en stock crítico (" + p.stockTotal() + " un.)",
                    "Por debajo del mínimo de seguridad (" + config.getUmbralStockBajo() + " unidades). Coordina reposición.",
                    null,
                    "Ver inventario",
                    "/inventario",
                    Map.of("soloStockBajo", "true")
            ));
        }

        // 4. Alerta de caja si no está abierta
        var cajaOpt = alertaDao.obtenerCajaHoy(boticaId, hoy);
        if (cajaOpt.isEmpty() || !cajaOpt.get().abierta()) {
            alertas.add(new AlertaResponse(
                    "caja-no-abierta",
                    "atencion",
                    "Caja sin abrir en el turno de hoy",
                    "No se registra una caja abierta activa en la botica. Abre caja para registrar cobros en mostrador.",
                    null,
                    "Abrir caja",
                    "/caja",
                    Map.of("tab", "apertura")
            ));
        }

        return alertas;
    }

    public ResumenDashboardResponse obtenerResumen() {
        Long boticaId = contexto.boticaId();
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        LocalDate hoyMasCritico = hoy.plusDays(config.getVencimientoCriticoDias());

        var ventasHoy = alertaDao.obtenerVentasHoy(boticaId, hoy);
        var porVencer = alertaDao.listarLotesPorVencer(boticaId, hoy, hoyMasCritico);
        var vencidos = alertaDao.listarLotesVencidos(boticaId, hoy);
        var criticos = alertaDao.listarProductosStockCritico(boticaId, config.getUmbralStockBajo());
        var cajaOpt = alertaDao.obtenerCajaHoy(boticaId, hoy);

        int totalPorVencerOVencidos = porVencer.size() + vencidos.size();
        String porVencerNota = vencidos.isEmpty()
                ? porVencer.size() + " vencen en los próximos " + config.getVencimientoCriticoDias() + " días"
                : vencidos.size() + " lote(s) vencido(s) que requieren retiro";

        String stockCriticoNota = criticos.isEmpty()
                ? "Inventario en niveles óptimos"
                : criticos.size() + " con unidades por debajo de " + config.getUmbralStockBajo();

        boolean cajaAbierta = cajaOpt.isPresent() && cajaOpt.get().abierta();
        String cajaEstado = cajaAbierta ? "Abierta" : "Cerrada";
        String cajaNota = cajaAbierta
                ? "Por " + cajaOpt.get().usuarioNombre() + " · aperturada con S/ " + Dinero.redondear(cajaOpt.get().montoApertura())
                : "Sin caja abierta actualmente para cobros";

        return new ResumenDashboardResponse(
                "S/ " + Dinero.redondear(ventasHoy.total()),
                ventasHoy.conteo() + " boleta(s) registradas hoy",
                totalPorVencerOVencidos,
                porVencerNota,
                criticos.size(),
                stockCriticoNota,
                cajaEstado,
                cajaNota
        );
    }
}
