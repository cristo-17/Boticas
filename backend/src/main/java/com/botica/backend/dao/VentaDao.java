package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.MovimientoStock;
import com.botica.backend.model.Receta;
import com.botica.backend.model.Venta;
import com.botica.backend.model.VentaDetalle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VentaDao {

    Optional<Venta> buscarPorClaveIdempotencia(Long boticaId, UUID clave);

    Venta insertarCabecera(Venta venta);

    VentaDetalle insertarDetalle(VentaDetalle detalle);

    void insertarMovimientoStock(MovimientoStock movimiento);

    /** Tarea 20: una por venta (UNIQUE venta_id). */
    void insertarReceta(Receta receta);

    Optional<Receta> buscarReceta(Long boticaId, Long ventaId);

    List<VentaDetalle> listarDetallePorVenta(Long boticaId, Long ventaId);

    Optional<Venta> buscarPorId(Long boticaId, Long id);

    /** cajaId null = todas las ventas de la botica (ADMINISTRADOR); con valor, solo las de esa caja (TECNICO, D-E2-2). */
    PaginaResponse<Venta> listarPaginado(Long boticaId, Long cajaId, int pagina, int tamano, String orden);
}
