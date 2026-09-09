package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.exception.StockInsuficienteException;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.util.FechaNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba real de contención (Tarea 11 Bloque B): dos hilos, dos
 * transacciones/conexiones independientes, registrando una venta cada
 * uno sobre EL MISMO lote al mismo tiempo, con stock que solo alcanza
 * para uno. Sin @Transactional en la clase — cada llamada a
 * ventaService.registrar() abre y confirma su propia transacción real,
 * igual que dos peticiones HTTP concurrentes en dos hilos de Tomcat.
 * Esto es lo que SELECT ... FOR UPDATE tiene que impedir: que ambos
 * hilos lean "hay stock" antes de que cualquiera de los dos escriba.
 */
@SpringBootTest
class VentaConcurrenciaTest {

    @Autowired
    private VentaService ventaService;
    @Autowired
    private CajaDao cajaDao;
    @Autowired
    private ContextoOperacion contexto;
    @Autowired
    private FechaNegocio fechaNegocio;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long loteId;
    private Long productoId;
    private Long presentacionId;

    @BeforeEach
    void prepararLoteConStockLimitadoYCajaAbierta() {
        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();

        // Cualquier producto con una presentación "Unidad" (factor_conversion = 1) sirve.
        productoId = jdbcTemplate.queryForObject(
                "SELECT producto_id FROM presentaciones WHERE botica_id = ? AND factor_conversion = 1 LIMIT 1",
                Long.class, boticaId);
        presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE botica_id = ? AND producto_id = ? AND factor_conversion = 1",
                Long.class, boticaId, productoId);
        loteId = jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE botica_id = ? AND producto_id = ? ORDER BY id LIMIT 1",
                Long.class, boticaId, productoId);

        // Stock controlado: exactamente 10 en el único lote que va a quedar con stock.
        jdbcTemplate.update("UPDATE lotes SET stock = 0 WHERE botica_id = ? AND producto_id = ? AND id != ?",
                boticaId, productoId, loteId);
        jdbcTemplate.update("UPDATE lotes SET stock = 10 WHERE id = ?", loteId);

        if (cajaDao.buscarAbiertaDelUsuario(boticaId, usuarioId).isEmpty()) {
            cajaDao.insertar(CajaDiaria.builder()
                    .boticaId(boticaId)
                    .usuarioId(usuarioId)
                    .fecha(fechaNegocio.hoy())
                    .turno(contexto.turno())
                    .montoApertura(new BigDecimal("100.00"))
                    .horaApertura(fechaNegocio.ahora())
                    .build());
        }
    }

    @Test
    void dosVentasConcurrentesSobreElMismoLote_soloUnaTieneExito() throws Exception {
        NuevaVentaRequest pedido1 = new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(productoId, presentacionId, 8, "BUSQUEDA")), "efectivo");
        NuevaVentaRequest pedido2 = new NuevaVentaRequest(UUID.randomUUID(),
                List.of(new ItemVentaRequest(productoId, presentacionId, 8, "BUSQUEDA")), "efectivo");

        CountDownLatch arrancar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Object> tarea1 = () -> {
            arrancar.await();
            return ventaService.registrar(pedido1);
        };
        Callable<Object> tarea2 = () -> {
            arrancar.await();
            return ventaService.registrar(pedido2);
        };

        Future<Object> f1 = executor.submit(tarea1);
        Future<Object> f2 = executor.submit(tarea2);
        arrancar.countDown();

        Object r1 = obtenerResultado(f1);
        Object r2 = obtenerResultado(f2);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        List<Object> resultados = List.of(r1, r2);
        long exitos = resultados.stream().filter(r -> r instanceof VentaResponse).count();
        long fallosPorStock = resultados.stream().filter(r -> r instanceof StockInsuficienteException).count();

        assertThat(exitos).as("exactamente una de las dos ventas concurrentes debe tener éxito").isEqualTo(1);
        assertThat(fallosPorStock).as("la otra debe fallar por stock insuficiente, no por otra causa").isEqualTo(1);

        Integer stockFinal = jdbcTemplate.queryForObject("SELECT stock FROM lotes WHERE id = ?", Integer.class, loteId);
        assertThat(stockFinal).as("el stock final debe reflejar UNA sola venta (10 - 8), nunca -6 ni 2 dos veces").isEqualTo(2);
    }

    /** Desenvuelve el resultado de la tarea: la excepción de negocio (esperada para el hilo que pierde la carrera) o la venta creada. */
    private Object obtenerResultado(Future<Object> future) throws InterruptedException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("La venta concurrente no terminó a tiempo", e);
        }
    }
}
