package com.botica.backend.dao;

import com.botica.backend.dto.PaginaResponse;
import com.botica.backend.dto.ResumenCierreResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.MovimientoCaja;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * No sabe qué es HTTP (Regla 2): devuelve modelos y DTOs de datos, nunca
 * ResponseEntity. Todo método recibe boticaId explícito y filtra por él
 * desde la primera consulta (D1) — nunca confía en que el llamador ya
 * filtró.
 */
public interface CajaDao {

    /**
     * La caja ABIERTA del usuario. Desde V8 hay como mucho una (índice
     * único parcial por usuario); si por datos viejos hubiera más, devuelve
     * la más antigua, que es la que hay que cerrar primero (DIF-12).
     */
    Optional<CajaDiaria> buscarAbiertaDelUsuario(Long boticaId, Long usuarioId);

    CajaDiaria insertar(CajaDiaria caja);

    Optional<CajaDiaria> buscarPorId(Long boticaId, Long id);

    /** D-E2-1: cajas ABIERTAS de la botica, de cualquier usuario -- para que el ADMINISTRADOR elija cuál cerrar. Orden fijo: la más antigua primero. */
    PaginaResponse<CajaDiaria> listarAbiertasPaginado(Long boticaId, int pagina, int tamano);

    void actualizarCierre(CajaDiaria caja);

    BigDecimal sumarMovimientosEfectivo(Long boticaId, Long cajaId);

    ResumenCierreResponse calcularResumen(Long boticaId, Long cajaId);

    PaginaResponse<MovimientoCaja> listarMovimientosPaginado(Long boticaId, Long cajaId, int pagina, int tamano, String orden);

    /** Primer escritor real de movimientos_caja (Tarea 11 Bloque B) — apertura/cierre no generan movimiento propio, solo ventas/egresos/mermas. */
    void insertarMovimiento(MovimientoCaja movimiento);
}
