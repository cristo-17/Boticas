package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductoDao {

    /**
     * Búsqueda de mostrador (límite duro). incluirInactivos=false es la de
     * Punto de Venta y del alta de lote; Merma pasa true porque un producto
     * desactivado todavía tiene stock físico que retirar (Tarea 17).
     */
    List<Producto> buscar(Long boticaId, String query, boolean incluirInactivos, int limite);

    Optional<Producto> buscarPorCodigoBarras(Long boticaId, String codigoBarras);

    /** Ranking por unidades vendidas desde `desde` (fecha de Lima, inclusive); sin ventas en el periodo, orden alfabético (DIF-06). Solo productos activos: es una lista de Punto de Venta. */
    List<Producto> masVendidos(Long boticaId, int limite, LocalDate desde);

    /**
     * Fecha de vencimiento del primer lote VENDIBLE (stock > 0 y no vencido,
     * la misma regla que FEFO), por producto — vacío si el producto no tiene
     * ninguno (DIF-13, Tarea 0B; revisa D6).
     */
    Map<Long, LocalDate> fechaVencimientoMasProximaPorProductos(List<Long> productoIds, LocalDate hoy);

    /** Todas, activas e inactivas: el que llama decide (Punto de Venta filtra, el catálogo del administrador no). */
    Map<Long, List<PresentacionProducto>> listarPresentacionesPorProductos(List<Long> productoIds);

    /** Catálogo del administrador (Tarea 17): activos e inactivos, paginado, orden validado contra lista blanca. */
    PaginaResponse<Producto> listarCatalogo(Long boticaId, String buscar, int pagina, int tamano, String orden);

    /** Devuelve el id. DuplicateKeyException si el código de barras ya existe en la botica. */
    Long insertar(Producto producto);

    /** Nombre, laboratorio, categoría, código de barras y unidad. DuplicateKeyException si el código choca. */
    void actualizar(Producto producto);

    void cambiarEstado(Long boticaId, Long productoId, boolean activo);

    Long insertarPresentacion(PresentacionProducto presentacion);

    /** Etiqueta, precio y activo. El factor nunca se edita (FACTOR_NO_EDITABLE). */
    void actualizarPresentacion(PresentacionProducto presentacion);

    /** Lookup interno (Regla 11) — nunca expuesto como GET /api/productos/{id}. Lo usa VentaService para el nombre congelado en venta_detalle. */
    Optional<Producto> obtenerPorId(Long boticaId, Long productoId);

    /** Valida que la presentación exista y pertenezca a ese producto y a esa botica — 400 PRESENTACION_INVALIDA si no. */
    Optional<PresentacionProducto> obtenerPresentacion(Long boticaId, Long productoId, Long presentacionId);
}
