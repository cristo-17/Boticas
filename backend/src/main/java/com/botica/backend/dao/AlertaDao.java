package com.botica.backend.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AlertaDao {

    record LoteAlertaRow(Long id, String codigo, LocalDate fechaVencimiento, int stock, BigDecimal precioUnitario, String productoNombre) {}

    record ProductoStockCriticoRow(Long id, String nombre, int stockTotal) {}

    record VentasHoyRow(BigDecimal total, int conteo) {}

    record CajaEstadoRow(boolean abierta, String usuarioNombre, LocalTime horaApertura, BigDecimal montoApertura) {}

    List<LoteAlertaRow> listarLotesVencidos(Long boticaId, LocalDate hoy);

    List<LoteAlertaRow> listarLotesPorVencer(Long boticaId, LocalDate hoy, LocalDate hoyMasCritico);

    List<ProductoStockCriticoRow> listarProductosStockCritico(Long boticaId, int umbral);

    VentasHoyRow obtenerVentasHoy(Long boticaId, LocalDate hoy);

    Optional<CajaEstadoRow> obtenerCajaHoy(Long boticaId, LocalDate hoy);
}
