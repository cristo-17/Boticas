package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.model.Lote;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoteDao {

    PaginaResponse<com.botica.backend.dto.LoteResponse> listarPaginado(
            Long boticaId,
            String categoria,
            String vencimiento,
            boolean soloStockBajo,
            Long productoId,
            int pagina,
            int tamano,
            String orden,
            LocalDate hoy,
            int criticoDias,
            int advertenciaDias,
            int umbralStockBajo
    );

    Lote insertar(Lote lote);

    Optional<Lote> buscarPorId(Long boticaId, Long id);

    /** Misma forma que listarPaginado (JOIN + estados calculados), para que un lote recién creado se vea igual que en el listado. */
    Optional<com.botica.backend.dto.LoteResponse> buscarResponsePorId(
            Long boticaId, Long id, LocalDate hoy, int criticoDias, int advertenciaDias, int umbralStockBajo);

    boolean existeProducto(Long boticaId, Long productoId);

    /**
     * Lotes de un producto con stock > 0, orden FEFO, bloqueados con
     * SELECT ... FOR UPDATE — para que dos ventas simultáneas no
     * consuman el mismo stock (Tarea 11 Bloque B). Debe llamarse
     * dentro de una transacción; el lock se libera al terminarla.
     */
    List<Lote> bloquearLotesFefo(Long boticaId, Long productoId);

    void descontarStock(Long loteId, int cantidad);
}
