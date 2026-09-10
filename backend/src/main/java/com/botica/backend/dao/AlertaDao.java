package com.botica.backend.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * No sabe qué es HTTP (Regla 2). Todo KPI se calcula con SQL agregado
 * (COUNT/SUM/GROUP BY) -- ningún método trae filas de más para filtrar
 * o contar del lado de Java.
 */
public interface AlertaDao {

    BigDecimal totalVentas(Long boticaId, LocalDate fecha);

    long contarVentas(Long boticaId, LocalDate fecha);

    /** Productos con al menos un lote con stock > 0 que vence entre (hoy, hoy+diasMax]. */
    long contarProductosPorVencer(Long boticaId, LocalDate hoy, int diasMax);

    /** Productos con al menos un lote con 0 < stock <= umbral. */
    long contarProductosStockCritico(Long boticaId, int umbralStockBajo);

    /** Productos con al menos un lote con stock = 0. */
    long contarProductosStockAgotado(Long boticaId);

    /** Nombres de producto con stock <= umbral (crítico o agotado), para el cuerpo de la alerta agregada. */
    List<String> nombresProductosStockBajo(Long boticaId, int umbralStockBajo, int limite);

    /**
     * Lotes VENCIDO o CRITICO con stock > 0, uno por fila.
     */
    List<LoteAlerta> lotesVencidosOCriticos(Long boticaId, LocalDate hoy, int criticoDias);

    /** Cajas ABIERTAS cuya fecha operativa es anterior a hoy. */
    List<CajaAbierta> cajasSinCerrar(Long boticaId, LocalDate hoy);

    record LoteAlerta(Long loteId, String productoNombre, String codigo, String ubicacion, int stock,
                      LocalDate fechaVencimiento, BigDecimal precioUnitario, String estadoVencimiento) {
    }

    record CajaAbierta(Long cajaId, String turno, String usuarioNombre, OffsetDateTime horaApertura) {
    }
}
