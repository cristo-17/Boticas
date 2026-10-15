package com.botica.backend.dao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T16 H6: V6 corrige V5 (ya aplicada en Neon) sin editarla. Comprueba en el
 * catálogo de Postgres que lo que agrega V6 quedó puesto: CHECK que rechazan
 * valores fuera del enum, FKs con ON DELETE RESTRICT y COMMENT ON TABLE.
 * V7 (regla 9): fecha_creacion pasó a creado_en y existe creado_por (NULL =
 * generada por el sistema). "Sobre datos" no se prueba acá: la suite hace
 * clean() ([BE-009]).
 * @Transactional: cada test deja botica_test como estaba.
 */
@SpringBootTest
@Transactional
class NotificacionesMigracionesTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NotificacionDao notificacionDao;

    @Test
    void tipoFueraDelEnum_loRechazaLaBase() {
        assertThatThrownBy(() -> insertar("OTRO_TIPO", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rolDestinatarioFueraDelEnum_loRechazaLaBase() {
        assertThatThrownBy(() -> insertar("STOCK_CRITICO", "CAJERO")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lasCombinacionesQueEscribeElCodigo_siEntran() {
        assertThat(insertar("STOCK_CRITICO", null)).isEqualTo(1);
        assertThat(insertar("DESCUADRE_GRAVE", "ADMINISTRADOR")).isEqualTo(1);
    }

    @Test
    void fks_boticaYUsuarioRestrict_creadoPorSetNull() {
        // confdeltype 'r' = RESTRICT ('a' = NO ACTION, el default implícito de V5); 'n' = SET NULL (V7, como en V1).
        assertThat(jdbcTemplate.queryForList(
                "SELECT conname::text || ':' || confdeltype::text FROM pg_constraint " +
                        "WHERE conrelid = 'notificaciones'::regclass AND contype = 'f' ORDER BY conname", String.class))
                .containsExactly("notificaciones_botica_id_fkey:r", "notificaciones_creado_por_fkey:n", "notificaciones_usuario_id_fkey:r");
    }

    @Test
    void laTablaTieneComentario() {
        assertThat(jdbcTemplate.queryForObject("SELECT obj_description('notificaciones'::regclass, 'pg_class')", String.class))
                .isNotBlank();
    }

    private int insertar(String tipo, String rol) {
        Long boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas ORDER BY id LIMIT 1", Long.class);
        return jdbcTemplate.update(
                "INSERT INTO notificaciones (botica_id, rol_destinatario, tipo, titulo, mensaje) VALUES (?, ?, ?, 'QA V6', 'QA V6')",
                boticaId, rol, tipo);
    }

    // --- V7 ---

    @Test
    void creadoEn_reemplazaAFechaCreacion_yCreadoPorAdmiteNull() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT column_name || ':' || is_nullable FROM information_schema.columns " +
                        "WHERE table_name = 'notificaciones' AND column_name IN ('creado_en', 'creado_por', 'fecha_creacion') ORDER BY column_name",
                String.class))
                .containsExactly("creado_en:NO", "creado_por:YES");
    }

    @Test
    void creadoPor_documentaQueNullEsElSistema() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT col_description('notificaciones'::regclass, " +
                        "(SELECT attnum FROM pg_attribute WHERE attrelid = 'notificaciones'::regclass AND attname = 'creado_por'))",
                String.class))
                .isEqualTo("NULL = generada por el sistema.");
    }

    @Test
    void elDao_leeCreadoEnDeLaBase() {
        Long boticaId = jdbcTemplate.queryForObject("SELECT id FROM boticas ORDER BY id LIMIT 1", Long.class);
        insertar("STOCK_CRITICO", null);

        assertThat(notificacionDao.listarRecientes(boticaId, "ADMINISTRADOR", 1))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getCreadoEn()).isNotNull();
                    assertThat(n.getCreadoPor()).isNull();
                });
    }
}
