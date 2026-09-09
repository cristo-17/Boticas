package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.MovimientoStock;
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

    List<VentaDetalle> listarDetallePorVenta(Long boticaId, Long ventaId);

    Optional<Venta> buscarPorId(Long boticaId, Long id);

    PaginaResponse<Venta> listarPaginado(Long boticaId, int pagina, int tamano, String orden);
}
