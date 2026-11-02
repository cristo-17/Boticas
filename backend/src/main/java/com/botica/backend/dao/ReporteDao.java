package com.botica.backend.dao;

import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.dto.TotalesResultadoResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReporteDao {
    List<FilaResultadoResponse> consultarFilasResultados(Long boticaId, LocalDate desde, LocalDate hasta, String agrupar);

    TotalesResultadoResponse consultarTotalesResultados(Long boticaId, LocalDate desde, LocalDate hasta);
}
