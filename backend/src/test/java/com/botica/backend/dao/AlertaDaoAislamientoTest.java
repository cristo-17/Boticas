package com.botica.backend.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 13, sección G (aislamiento multi-botica pendiente), aplicada a
 * Alerta -- mismo patrón que LoteDaoAislamientoTest/CajaDaoAislamientoTest,
 * pero acá cada método de AlertaDao es una agregación (COUNT/lista), no
 * un lookup por id: la pregunta no es "¿se filtra por id ajeno?" sino
 * "¿un dato nuevo de Vida Sana se filtra hacia el conteo/lista de San
 * Lucas?". hoy es fijo y explícito (nunca CURRENT_DATE), igual que en
 * LoteDaoSemaforoTest. @Transactional: rollback automático.
 */
@SpringBootTest
@Transactional
class AlertaDaoAislamientoTest {

    @Autowired
    private AlertaDao alertaDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final LocalDate HOY = LocalDate.of(2026, 1, 1);

    private Long boticaSanLucasId;
    private Long boticaVidaSanaId;

    @BeforeEach
    void cargarIdsDelSeed() {
        boticaSanLucasId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica San Lucas'", Long.class);
        boticaVidaSanaId = jdbcTemplate.queryForObject(
                "SELECT id FROM boticas WHERE nombre = 'Botica Vida Sana'", Long.class);

        assertThat(boticaSanLucasId).as("seed debe existir").isNotNull();
        assertThat(boticaVidaSanaId).isNotNull();
    }

    @Test
    void contarProductosStockAgotado_unProductoAgotadoDeVidaSana_noSumaAlConteoDeSanLucas() {
        long antesSanLucas = alertaDao.contarProductosStockAgotado(boticaSanLucasId);
        long antesVidaSana = alertaDao.contarProductosStockAgotado(boticaVidaSanaId);

        insertarProductoConLote(boticaVidaSanaId, HOY.plusYears(1), 0); // stock 0 = AGOTADO

        assertThat(alertaDao.contarProductosStockAgotado(boticaSanLucasId))
                .as("un producto agotado de Vida Sana no debe sumar al conteo de San Lucas")
                .isEqualTo(antesSanLucas);
        assertThat(alertaDao.contarProductosStockAgotado(boticaVidaSanaId)).isEqualTo(antesVidaSana + 1);
    }

    @Test
    void lotesVencidosOCriticos_unLoteCriticoDeVidaSana_noApareceEnLaListaDeSanLucas() {
        long productoId = insertarProductoConLote(boticaVidaSanaId, HOY, 10); // vence HOY = CRITICO, con stock

        List<AlertaDao.LoteAlerta> comoSanLucas = alertaDao.lotesVencidosOCriticos(boticaSanLucasId, HOY, 30);
        assertThat(comoSanLucas)
                .as("un lote crítico de Vida Sana no debe aparecer en la lista de San Lucas")
                .noneMatch(l -> l.productoNombre().equals("Producto aislamiento QA"));

        List<AlertaDao.LoteAlerta> comoVidaSana = alertaDao.lotesVencidosOCriticos(boticaVidaSanaId, HOY, 30);
        assertThat(comoVidaSana).anyMatch(l -> l.productoNombre().equals("Producto aislamiento QA"));
    }

    @Test
    void cajasSinCerrar_unaCajaAbiertaDeAyerDeVidaSana_noApareceParaSanLucas() {
        int antesSanLucas = alertaDao.cajasSinCerrar(boticaSanLucasId, HOY).size();
        int antesVidaSana = alertaDao.cajasSinCerrar(boticaVidaSanaId, HOY).size();

        // ABIERTA por default (V1__esquema.sql), fecha = ayer respecto a HOY -- justo el caso "sin cerrar".
        jdbcTemplate.update(
                "INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura) " +
                        "VALUES (?, (SELECT id FROM usuarios WHERE botica_id = ? LIMIT 1), ?, 'Mañana', 100.00)",
                boticaVidaSanaId, boticaVidaSanaId, HOY.minusDays(1));

        List<AlertaDao.CajaAbierta> comoSanLucas = alertaDao.cajasSinCerrar(boticaSanLucasId, HOY);
        List<AlertaDao.CajaAbierta> comoVidaSana = alertaDao.cajasSinCerrar(boticaVidaSanaId, HOY);

        assertThat(comoSanLucas)
                .as("la caja sin cerrar recién insertada es de Vida Sana -- San Lucas no debe verla")
                .hasSize(antesSanLucas);
        assertThat(comoVidaSana).hasSize(antesVidaSana + 1);
    }

    /** Producto + un solo lote, aislado del resto del seed. Devuelve el productoId. */
    private long insertarProductoConLote(Long boticaId, LocalDate fechaVencimiento, int stock) {
        // VARCHAR(20): "QA-" + hasta 12 dígitos entra sobrado, sin colisionar entre llamadas del mismo test.
        String codigoBarras = "QA-" + (System.nanoTime() % 1_000_000_000_000L);
        jdbcTemplate.update(
                "INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre) VALUES (?, ?, ?, ?, ?)",
                boticaId, "Producto aislamiento QA", "Otros", codigoBarras, "unidad");
        Long productoId = jdbcTemplate.queryForObject(
                "SELECT id FROM productos WHERE botica_id = ? AND codigo_barras = ?", Long.class, boticaId, codigoBarras);

        String codigoLote = "QA-ALERTA-L-" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                boticaId, productoId, codigoLote, fechaVencimiento, stock, new BigDecimal("1.00"));

        return productoId;
    }
}
