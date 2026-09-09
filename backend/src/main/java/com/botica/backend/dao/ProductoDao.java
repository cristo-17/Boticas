package com.botica.backend.dao;

import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductoDao {

    List<Producto> buscar(Long boticaId, String query, int limite);

    Optional<Producto> buscarPorCodigoBarras(Long boticaId, String codigoBarras);

    List<Producto> masVendidos(Long boticaId, int limite);

    /** Fecha de vencimiento más próxima ENTRE LOS LOTES CON STOCK > 0, por producto — vacío si el producto no tiene ningún lote con stock. */
    Map<Long, LocalDate> fechaVencimientoMasProximaPorProductos(List<Long> productoIds);

    Map<Long, List<PresentacionProducto>> listarPresentacionesPorProductos(List<Long> productoIds);

    /** Lookup interno (Regla 11) — nunca expuesto como GET /api/productos/{id}. Lo usa VentaService para el nombre congelado en venta_detalle. */
    Optional<Producto> obtenerPorId(Long boticaId, Long productoId);

    /** Valida que la presentación exista y pertenezca a ese producto y a esa botica — 400 PRESENTACION_INVALIDA si no. */
    Optional<PresentacionProducto> obtenerPresentacion(Long boticaId, Long productoId, Long presentacionId);
}
