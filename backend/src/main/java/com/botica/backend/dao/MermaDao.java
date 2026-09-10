package com.botica.backend.dao;

import com.botica.backend.dto.MermaResponse;
import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.Merma;
import com.botica.backend.model.MovimientoStock;

import java.time.LocalDate;
import java.util.Optional;

public interface MermaDao {

    /**
     * Lote + nombre de producto + precio de la presentación "Unidad", en
     * una sola fila y bloqueado con SELECT ... FOR UPDATE — el mismo
     * patrón de LoteDao.bloquearLotesFefo, pero para UN lote concreto
     * elegido por el usuario (no FEFO: acá el usuario ya sabe qué lote
     * se echó a perder). Debe llamarse dentro de una transacción.
     */
    Optional<LoteParaMerma> bloquearLoteConPrecio(Long boticaId, Long loteId);

    Merma insertar(Merma merma);

    void insertarMovimientoStock(MovimientoStock movimiento);

    PaginaResponse<MermaResponse> listarPaginado(Long boticaId, LocalDate fecha, int pagina, int tamano, String orden);

    /** Fila mínima que MermaService necesita para validar y calcular, sin exponer costoUnitario (D2). */
    record LoteParaMerma(Long id, Long productoId, String productoNombre, String codigo, int stock, java.math.BigDecimal precioUnitario) {
    }
}
