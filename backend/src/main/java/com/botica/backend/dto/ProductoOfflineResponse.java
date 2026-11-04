package com.botica.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Tarea 22: la misma forma que ProductoResponse (mostrador) más los lotes
 * vendibles, para que Punto de Venta venda sin conexión con la misma
 * pantalla y avise si el carrito supera el stock aproximado.
 */
public record ProductoOfflineResponse(
        Long id,
        String nombre,
        String laboratorio,
        String categoria,
        String codigoBarras,
        String estadoVencimiento,
        LocalDate fechaVencimiento,
        List<PresentacionResponse> presentaciones,
        boolean activo,
        boolean requiereReceta,
        List<LoteVendibleResponse> lotesVendibles
) {
    public static ProductoOfflineResponse desde(ProductoResponse p, List<LoteVendibleResponse> lotes) {
        return new ProductoOfflineResponse(p.id(), p.nombre(), p.laboratorio(), p.categoria(), p.codigoBarras(),
                p.estadoVencimiento(), p.fechaVencimiento(), p.presentaciones(), p.activo(), p.requiereReceta(), lotes);
    }
}
