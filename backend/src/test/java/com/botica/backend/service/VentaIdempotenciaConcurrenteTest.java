package com.botica.backend.service;

import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
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
 * El caso real: la red se corta, el cliente reintenta con la MISMA
 * claveIdempotencia mientras la primera petición todavía se está
 * procesando. Llama al Service directo (sin HTTP de por medio) para
 * maximizar la chance de solape real entre los dos hilos -- si esto
 * pasa, un curl secuencial por loopback también puede pasarlo, solo
 * que con menos frecuencia.
 */
@SpringBootTest
class VentaIdempotenciaConcurrenteTest {

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

    private Long productoId;
    private Long presentacionId;

    @BeforeEach
    void prepararProductoYCajaAbierta() {
        Long boticaId = contexto.boticaId();
        Long usuarioId = contexto.usuarioId();

        productoId = jdbcTemplate.queryForObject(
                "SELECT producto_id FROM presentaciones WHERE botica_id = ? AND factor_conversion = 1 LIMIT 1",
                Long.class, boticaId);
        presentacionId = jdbcTemplate.queryForObject(
                "SELECT id FROM presentaciones WHERE botica_id = ? AND producto_id = ? AND factor_conversion = 1",
                Long.class, boticaId, productoId);
        // Stock generoso -- este test no busca probar FEFO/insuficiencia, solo la idempotencia.
        jdbcTemplate.update("UPDATE lotes SET stock = 1000 WHERE botica_id = ? AND producto_id = ?", boticaId, productoId);

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
    void dosLlamadasConcurrentes_mismaClaveIdempotencia_devuelvenLaMismaVenta_nuncaUn500() throws Exception {
        UUID claveCompartida = UUID.randomUUID();
        NuevaVentaRequest pedido1 = new NuevaVentaRequest(claveCompartida,
                List.of(new ItemVentaRequest(productoId, presentacionId, 2, "BUSQUEDA")), "efectivo");
        NuevaVentaRequest pedido2 = new NuevaVentaRequest(claveCompartida,
                List.of(new ItemVentaRequest(productoId, presentacionId, 2, "BUSQUEDA")), "efectivo");

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

        Object r1 = resolver(f1);
        Object r2 = resolver(f2);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Ninguno de los dos debe ser una excepción inesperada (500 por violación de constraint).
        assertThat(r1).as("resultado 1 no debe ser una excepción").isInstanceOf(VentaResponse.class);
        assertThat(r2).as("resultado 2 no debe ser una excepción").isInstanceOf(VentaResponse.class);

        VentaResponse v1 = (VentaResponse) r1;
        VentaResponse v2 = (VentaResponse) r2;
        assertThat(v1.id()).as("las dos llamadas deben apuntar a LA MISMA venta").isEqualTo(v2.id());

        Integer totalConEsaClave = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ventas WHERE clave_idempotencia = ?", Integer.class, claveCompartida);
        assertThat(totalConEsaClave).as("debe existir exactamente una venta con esa clave, nunca dos").isEqualTo(1);
    }

    private Object resolver(Future<Object> future) throws InterruptedException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("La venta concurrente no terminó a tiempo", e);
        }
    }
}
