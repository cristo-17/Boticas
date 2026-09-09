package com.botica.backend.util;

import java.time.LocalDate;

/**
 * Los 4 estados de vencimiento y los 3 de stock (nota 4,
 * docs/DECISIONES.md) en un solo lugar — Producto, Lote y Alertas
 * (Tarea 11 Bloque C) comparten exactamente la misma regla, con los
 * mismos umbrales configurables (GET /api/config). Nunca se recalcula
 * en el cliente: son reglas de negocio, no una resta que caduca.
 */
public final class EstadosNegocio {

    public static final String VENCIDO = "VENCIDO";
    public static final String CRITICO = "CRITICO";
    public static final String ADVERTENCIA = "ADVERTENCIA";
    public static final String OK = "OK";

    public static final String STOCK_AGOTADO = "AGOTADO";
    public static final String STOCK_CRITICO = "CRITICO";
    public static final String STOCK_OK = "OK";

    private EstadosNegocio() {
    }

    public static String estadoVencimiento(LocalDate fechaVencimiento, LocalDate hoy, int criticoDias, int advertenciaDias) {
        if (fechaVencimiento == null) {
            return null;
        }
        if (fechaVencimiento.isBefore(hoy)) {
            return VENCIDO;
        }
        long dias = java.time.temporal.ChronoUnit.DAYS.between(hoy, fechaVencimiento);
        if (dias <= criticoDias) {
            return CRITICO;
        }
        if (dias <= advertenciaDias) {
            return ADVERTENCIA;
        }
        return OK;
    }

    public static String stockEstado(int stock, int umbralStockBajo) {
        if (stock == 0) {
            return STOCK_AGOTADO;
        }
        if (stock <= umbralStockBajo) {
            return STOCK_CRITICO;
        }
        return STOCK_OK;
    }
}
