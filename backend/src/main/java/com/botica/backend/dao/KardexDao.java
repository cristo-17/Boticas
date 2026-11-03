package com.botica.backend.dao;

import com.botica.backend.dto.FilaKardexResponse;
import com.botica.backend.dto.ResumenKardexResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kardex por producto (Tarea 20, D-E2-5): se calcula desde movimientos_stock
 * al consultar, sin tabla propia. inicio/fin son instantes ya resueltos en
 * hora de Lima (inicio del día "desde" y del día siguiente a "hasta"); null =
 * sin límite por ese lado.
 */
public interface KardexDao {

    /**
     * Filas en orden (fecha, id) con el saldo acumulado calculado en SQL. Con
     * inicio, la primera fila es SALDO_ANTERIOR. limite null = todas (Excel).
     */
    List<FilaKardexResponse> listarFilas(Long boticaId, Long productoId, OffsetDateTime inicio, OffsetDateTime fin,
                                         Integer limite, long offset);

    /** Saldo inicial, entradas, salidas, saldo final, stock actual de los lotes y movimientos en el rango. */
    ResumenKardexResponse resumir(Long boticaId, Long productoId, OffsetDateTime inicio, OffsetDateTime fin);
}
