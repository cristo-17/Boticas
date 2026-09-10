package com.botica.backend.service;

import com.botica.backend.config.ConfigNegocioProperties;
import com.botica.backend.config.ContextoOperacion;
import com.botica.backend.dao.CajaDao;
import com.botica.backend.dao.LoteDao;
import com.botica.backend.dao.ProductoDao;
import com.botica.backend.dao.VentaDao;
import com.botica.backend.dto.ItemVentaRequest;
import com.botica.backend.dto.NuevaVentaRequest;
import com.botica.backend.dto.VentaResponse;
import com.botica.backend.model.CajaDiaria;
import com.botica.backend.model.Lote;
import com.botica.backend.model.PresentacionProducto;
import com.botica.backend.model.Producto;
import com.botica.backend.model.Venta;
import com.botica.backend.model.VentaDetalle;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tarea 13, sección B (Anexo C, docs/prompts/PROMPT-AGENTE-BACKEND-QA.md)
 * — aparte de VentaTransaccionTest a propósito: esto no prueba reglas de
 * negocio (FEFO, caja abierta, etc.), prueba el requisito de "cero
 * descuadres" en sí mismo. Cada test arma sus propios mocks frescos
 * (no los @Mock de clase) para poder generar N ventas independientes
 * en el mismo método sin que una contamine el estado de otra.
 */
class VentaDineroTest {

    private static final Long BOTICA_ID = 1L;
    private static final Long USUARIO_ID = 1L;
    private static final AtomicLong SECUENCIA_ID = new AtomicLong(1);

    @Test
    void cincuentaVentasGeneradas_sumaDeLosTotalesDeLineaEsSiempreExactaAlTotalDeCabecera() {
        Random random = new Random(20260908L); // semilla fija: reproducible, no depende de la suerte de la corrida
        for (int i = 0; i < 50; i++) {
            int cantidadItems = 1 + random.nextInt(4); // 1 a 4 líneas por venta
            List<Escenario.Item> items = new ArrayList<>();
            for (int j = 0; j < cantidadItems; j++) {
                int cantidad = 1 + random.nextInt(200);
                // precio entre 0.10 y 500.00, siempre construido desde String (Anexo C, regla 5).
                BigDecimal precio = BigDecimal.valueOf(0.10 + random.nextDouble() * 499.90)
                        .setScale(2, RoundingMode.HALF_UP);
                int factor = List.of(1, 10, 30, 50, 100).get(random.nextInt(5));
                items.add(new Escenario.Item(cantidad, precio, factor));
            }

            Resultado resultado = ejecutar(items);

            BigDecimal sumaLineas = resultado.detalles.stream()
                    .map(VentaDetalle::getTotalLinea)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sumaLineas)
                    .as("venta #%d generada: la suma de venta_detalle.total_linea debe ser EXACTA a ventas.total", i)
                    .isEqualByComparingTo(resultado.respuesta.total());
            // Anexo C regla 7: el IGV se extrae, nunca se suma -- base + igv debe reconstruir el total exacto.
            assertThat(resultado.respuesta.subtotal().add(resultado.respuesta.igv()))
                    .as("venta #%d: subtotal + igv debe reconstruir el total exacto, sin céntimo perdido", i)
                    .isEqualByComparingTo(resultado.respuesta.total());
        }
    }

    @Test
    void precioQueRompeElPuntoFlotante_0_1_mas_0_2_daExactamente_0_3_noCorrupcionDeDouble() {
        // El caso clásico: 0.1 + 0.2 en double da 0.30000000000000004. Con BigDecimal.valueOf de un
        // double literal (no un String) ese error SE ARRASTRARÍA -- por eso Dinero/Anexo C exige
        // construir desde String. Acá se arma como dos líneas de 1 unidad, S/0.10 y S/0.20.
        Resultado resultado = ejecutar(List.of(
                new Escenario.Item(1, new BigDecimal("0.10"), 1),
                new Escenario.Item(1, new BigDecimal("0.20"), 1)));

        BigDecimal sumaLineas = resultado.detalles.stream()
                .map(VentaDetalle::getTotalLinea).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumaLineas).isEqualByComparingTo("0.30");
        assertThat(resultado.respuesta.total()).isEqualByComparingTo("0.30");
    }

    @Test
    void diezUnidadesA_0_33_daExactamente_3_30_sinArrastreDeRedondeo() {
        Resultado resultado = ejecutar(List.of(new Escenario.Item(10, new BigDecimal("0.33"), 1)));

        assertThat(resultado.respuesta.total()).isEqualByComparingTo("3.30");
        assertThat(resultado.detalles.get(0).getTotalLinea()).isEqualByComparingTo("3.30");
    }

    @Test
    void cajaDe30UnidadesA25Soles_seRedondeaElTotalDeLinea_noElPrecioUnitarioPrimero() {
        // Anexo C regla 6: 25.00 / 30 = 0.8333... -- si se redondeara el unitario a 0.83 PRIMERO y
        // se multiplicara x30, darían 24.90 (se pierden 10 céntimos). El cálculo real es
        // cantidad x precio_presentación (25.00), redondeado DESPUÉS -- acá "cantidad" son
        // presentaciones (cajas), no unidades base, así que 1 caja x S/25.00 = S/25.00 exacto,
        // sin pasar nunca por una división intermedia.
        Resultado resultado = ejecutar(List.of(new Escenario.Item(1, new BigDecimal("25.00"), 30)));

        assertThat(resultado.respuesta.total()).isEqualByComparingTo("25.00");
        assertThat(resultado.detalles.get(0).getTotalLinea()).isEqualByComparingTo("25.00");
        // precio_unitario es un dato INFORMATIVO redondeado a 2 decimales (0.83, no 0.8333...) --
        // no participa del cálculo de total_linea, que ya se cerró en 25.00 arriba directo desde
        // el precio de la presentación. Multiplicar 0.83 x 30 da 24.90, no 25.00: es un redondeo
        // de visualización aceptado (VentaTransaccion, Tarea 11 Bloque B), no un descuadre real --
        // total_linea es el número autoritativo, nunca se recalcula desde precio_unitario.
        assertThat(resultado.detalles.get(0).getPrecioUnitario()).isEqualByComparingTo("0.83");
    }

    @RepeatedTest(5)
    void igvExtraido_baseMasIgvSiempreReconstruyeElTotalExacto_paraVariosTotales() {
        Random random = new Random();
        BigDecimal precio = BigDecimal.valueOf(1 + random.nextInt(2000) / 100.0).setScale(2, RoundingMode.HALF_UP);
        Resultado resultado = ejecutar(List.of(new Escenario.Item(1, precio, 1)));

        // Anexo C regla 7: la base se EXTRAE del total (total / 1.18), nunca se suma el IGV
        // encima -- si estuviera sumado, subtotal + igv daría más que total, no exacto.
        assertThat(resultado.respuesta.subtotal().add(resultado.respuesta.igv()))
                .isEqualByComparingTo(resultado.respuesta.total());
        // La base es MENOR al total (el IGV está adentro, no encima) -- descarta la implementación
        // opuesta (sumar 18% al precio) con un número, no solo con la igualdad de arriba.
        assertThat(resultado.respuesta.subtotal()).isLessThan(resultado.respuesta.total());
    }

    @Test
    void ningunBigDecimalSeComparaConEquals_grepSobreTodoElCodigoFuenteDeProduccion() throws Exception {
        // Anexo C regla 4: new BigDecimal("2.50").equals(new BigDecimal("2.5")) da FALSE -- la
        // escala es parte de la igualdad. Un ".equals(" sobre una variable de dinero es un bug
        // esperando pasar inadvertido. Este test grepea el código de PRODUCCIÓN (nunca de test:
        // acá mismo hay BigDecimal por todos lados, correctamente comparados con
        // isEqualByComparingTo de AssertJ, que no es .equals()).
        java.nio.file.Path raiz = java.nio.file.Path.of("src/main/java");
        List<String> sospechosos = new ArrayList<>();
        try (var paths = java.nio.file.Files.walk(raiz)) {
            for (java.nio.file.Path archivo : (Iterable<java.nio.file.Path>) paths.filter(p -> p.toString().endsWith(".java"))::iterator) {
                List<String> lineas = java.nio.file.Files.readAllLines(archivo);
                for (int i = 0; i < lineas.size(); i++) {
                    String linea = lineas.get(i);
                    // Patrón deliberadamente amplio: cualquier ".equals(" sobre algo que huela a
                    // BigDecimal en la misma línea (variable "precio"/"monto"/"total"/"costo"/"valor"
                    // o el propio BigDecimal). Un falso positivo ocasional se revisa a mano; un falso
                    // negativo (bug real que pasa el grep) es el riesgo que este test no puede tomar.
                    if (linea.matches(".*\\b(precio|monto|total|costo|valor|subtotal|igv|diferencia)\\w*\\s*\\.equals\\(.*")) {
                        sospechosos.add(archivo + ":" + (i + 1) + ": " + linea.trim());
                    }
                }
            }
        }
        assertThat(sospechosos)
                .as("Ninguna comparación de dinero debe usar .equals() -- usar compareTo()/isEqualByComparingTo (Anexo C regla 4). Líneas sospechosas: %s", sospechosos)
                .isEmpty();
    }

    // --- Infraestructura mínima para generar ventas independientes sin @Mock de clase ---

    private record Resultado(VentaResponse respuesta, List<VentaDetalle> detalles) {
    }

    private static final class Escenario {
        record Item(int cantidad, BigDecimal precioPresentacion, int factorConversion) {
        }
    }

    private Resultado ejecutar(List<Escenario.Item> items) {
        VentaDao ventaDao = mock(VentaDao.class);
        LoteDao loteDao = mock(LoteDao.class);
        ProductoDao productoDao = mock(ProductoDao.class);
        CajaDao cajaDao = mock(CajaDao.class);
        ContextoOperacion contexto = mock(ContextoOperacion.class);
        ConfigNegocioProperties config = new ConfigNegocioProperties(); // igv = 0.18

        when(contexto.boticaId()).thenReturn(BOTICA_ID);
        when(contexto.usuarioId()).thenReturn(USUARIO_ID);
        when(ventaDao.buscarPorClaveIdempotencia(any(), any())).thenReturn(java.util.Optional.empty());
        when(cajaDao.buscarAbiertaDelUsuario(BOTICA_ID, USUARIO_ID))
                .thenReturn(java.util.Optional.of(CajaDiaria.builder().id(1L).boticaId(BOTICA_ID).build()));

        List<ItemVentaRequest> pedidoItems = new ArrayList<>();
        for (int idx = 0; idx < items.size(); idx++) {
            Escenario.Item item = items.get(idx);
            long productoId = SECUENCIA_ID.getAndIncrement();
            long presentacionId = SECUENCIA_ID.getAndIncrement();
            long loteId = SECUENCIA_ID.getAndIncrement();

            when(productoDao.obtenerPorId(BOTICA_ID, productoId))
                    .thenReturn(java.util.Optional.of(Producto.builder().id(productoId).boticaId(BOTICA_ID).nombre("Producto " + productoId).build()));
            when(productoDao.obtenerPresentacion(BOTICA_ID, productoId, presentacionId))
                    .thenReturn(java.util.Optional.of(PresentacionProducto.builder()
                            .id(presentacionId).productoId(productoId).etiqueta("Presentación")
                            .factorConversion(item.factorConversion()).precio(item.precioPresentacion()).build()));
            // Stock amplio a propósito: este test mide dinero, no FEFO -- un solo lote sin partir la línea.
            when(loteDao.bloquearLotesFefo(BOTICA_ID, productoId)).thenReturn(List.of(
                    Lote.builder().id(loteId).productoId(productoId)
                            .stock(item.cantidad() * item.factorConversion() + 1000)
                            .costoUnitario(new BigDecimal("0.01")).fechaVencimiento(LocalDate.now().plusYears(1)).build()));

            pedidoItems.add(new ItemVentaRequest(productoId, presentacionId, item.cantidad(), "BUSQUEDA"));
        }

        when(ventaDao.insertarCabecera(any())).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            v.setId(SECUENCIA_ID.getAndIncrement());
            return v;
        });
        List<VentaDetalle> detallesCapturados = new ArrayList<>();
        ArgumentCaptor<VentaDetalle> captor = ArgumentCaptor.forClass(VentaDetalle.class);
        when(ventaDao.insertarDetalle(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        VentaTransaccion transaccion = new VentaTransaccion(ventaDao, loteDao, productoDao, cajaDao, contexto, config);
        NuevaVentaRequest request = new NuevaVentaRequest(UUID.randomUUID(), pedidoItems, "efectivo");
        VentaResponse respuesta = transaccion.ejecutar(request);
        detallesCapturados.addAll(captor.getAllValues());

        return new Resultado(respuesta, detallesCapturados);
    }
}
