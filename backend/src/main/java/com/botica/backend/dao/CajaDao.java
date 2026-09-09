package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenCierreResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.MovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * No sabe qué es HTTP (Regla 2): devuelve modelos y DTOs de datos, nunca
 * ResponseEntity. Todo método recibe boticaId explícito y filtra por él
 * desde la primera consulta (D1) — nunca confía en que el llamador ya
 * filtró.
 */
public interface CajaDao {

    Optional<CajaDiaria> buscarAbiertaDelUsuario(Long boticaId, Long usuarioId);

    boolean existeCajaAbierta(Long boticaId, Long usuarioId, String turno, LocalDate fecha);

    CajaDiaria insertar(CajaDiaria caja);

    Optional<CajaDiaria> buscarPorId(Long boticaId, Long id);

    void actualizarCierre(CajaDiaria caja);

    BigDecimal sumarMovimientosEfectivo(Long boticaId, Long cajaId);

    ResumenCierreResponse calcularResumen(Long boticaId, Long cajaId);

    PaginaResponse<MovimientoCaja> listarMovimientosPaginado(Long boticaId, Long cajaId, int pagina, int tamano, String orden);
}
