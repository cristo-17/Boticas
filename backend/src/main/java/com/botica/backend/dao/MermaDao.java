package com.botica.backend.dao;

import com.botica.backend.dto.MermaResponse;
import com.botica.backend.model.Merma;

import java.time.LocalDate;
import java.util.List;

public interface MermaDao {

    Merma insertar(Merma merma);

    List<MermaResponse> listarDelDia(Long boticaId, LocalDate hoy);

    /** Inserta fila en movimientos_stock con tipo='MERMA'. */
    void insertarMovimientoStock(Long boticaId, Long loteId, int cantidad, Long mermaId, Long creadoPor);
}
