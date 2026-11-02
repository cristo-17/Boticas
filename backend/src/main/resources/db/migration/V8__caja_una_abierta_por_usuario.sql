-- ============================================================
-- V8 — una sola caja abierta por usuario (Tarea 0B, DIF-12)
-- ============================================================
-- La regla de DECISIONES 2026-09-08 es "un mismo usuario no puede tener dos
-- cajas abiertas". V1 la implementó como una abierta por usuario/turno/día:
-- se podía tener abierta la de ayer y la de hoy, o dos turnos a la vez, y la
-- venta usaba "la primera" que devolvía la consulta.
--
-- Corre sobre una tabla CON filas. Falla si algún usuario ya tiene dos cajas
-- ABIERTAS; por eso, antes del deploy, se verifica en producción que no haya
-- ninguno (consulta en docs/ESTADO.md) y se prueba en la rama de Neon
-- "prueba-migracion". No toca datos: solo cambia el índice.
-- Un usuario pertenece a una sola botica, así que usuario_id basta (no hace
-- falta botica_id en el índice).

DROP INDEX idx_caja_abierta_unica;

CREATE UNIQUE INDEX idx_caja_abierta_por_usuario ON caja_diaria(usuario_id)
    WHERE estado = 'ABIERTA';

COMMENT ON TABLE caja_diaria IS 'Una sola caja ABIERTA por usuario, de cualquier día o turno (V8, idx_caja_abierta_por_usuario); cerradas, las que sean. monto_esperado/diferencia/semaforo_descuadre quedan NULL mientras está ABIERTA: conteo ciego (hueco 2) — el servidor no los calcula ni los expone hasta POST /api/caja/cerrar. estado (ABIERTA/CERRADA) es la representación en BD; el contrato JSON lo expone como el booleano "abierta". fecha es DATE de negocio (la fecha operativa de Lima, Regla 5) -- no participa de la discusión TIMESTAMPTZ.';
