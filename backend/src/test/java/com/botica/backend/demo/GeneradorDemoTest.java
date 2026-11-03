package com.botica.backend.demo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tarea D: el generador, en memoria (sin base). Como las fechas son relativas al
 * día en que corre la migración, el escenario tiene que salir completo cualquiera
 * sea "hoy" (el generador lanza si no). Se prueba con 35 días seguidos (todos los
 * días de la semana, cambio de mes y de año) y que sea determinista.
 */
class GeneradorDemoTest {

    private static final String HASH = "$2a$10$Ip6WW/zd1eGkCM0rZv2lcOgYZYYOTTiqQMfAGRVKY6obCuMTwdXri";

    private static GeneradorDemo.Parametros parametros(LocalDate hoy) {
        return new GeneradorDemo.Parametros(hoy, new BigDecimal("0.18"), 15, new BigDecimal("10.00"), HASH);
    }

    @Test
    void elEscenarioSaleCompletoCualquieraSeaHoy() {
        LocalDate inicio = LocalDate.of(2026, 12, 10);
        for (int i = 0; i < 35; i++) {
            LocalDate hoy = inicio.plusDays(i);
            assertThatCode(() -> GeneradorDemo.generar(parametros(hoy))).as("hoy = %s", hoy).doesNotThrowAnyException();
        }
    }

    /**
     * Misma familia que los invariantes 1 y 4, en memoria y para 35 fechas: ningún
     * movimiento antes del alta de su lote, saldo nunca negativo en orden (fecha,
     * inserción), y cada salida de venta usa el lote que FEFO elegía con los lotes
     * que existían en ese instante.
     */
    @Test
    void movimientosYFefoConsistentesCualquieraSeaHoy() {
        LocalDate inicio = LocalDate.of(2026, 12, 10);
        for (int i = 0; i < 35; i++) {
            LocalDate hoy = inicio.plusDays(i);
            DatosDemo d = GeneradorDemo.generar(parametros(hoy));
            java.util.Map<DatosDemo.Lote, Integer> saldo = new java.util.HashMap<>();
            java.util.List<DatosDemo.MovStock> movs = new java.util.ArrayList<>(d.movimientosStock);
            movs.sort(java.util.Comparator.comparing((DatosDemo.MovStock m) -> m.creadoEn));
            java.util.Map<DatosDemo.Venta, java.util.List<DatosDemo.MovStock>> porVenta = new java.util.LinkedHashMap<>();
            for (DatosDemo.MovStock m : movs) {
                assertThat(m.creadoEn).as("hoy %s, movimiento antes del alta del lote", hoy).isAfterOrEqualTo(m.lote.creadoEn);
                int s = saldo.merge(m.lote, m.cantidad, Integer::sum);
                assertThat(s).as("hoy %s, saldo negativo", hoy).isNotNegative();
                if (m.venta != null) {
                    porVenta.computeIfAbsent(m.venta, v -> new java.util.ArrayList<>()).add(m);
                }
                if (m.venta != null && porVenta.get(m.venta).size() == 1) {
                    // primera salida de esta venta: ningún lote anterior en FEFO y vigente puede tener stock antes de ella
                    LocalDate dia = m.creadoEn.atZoneSameInstant(GeneradorDemo.LIMA).toLocalDate();
                    for (DatosDemo.Lote otro : m.lote.producto.lotes) {
                        boolean anterior = otro.vencimiento.isBefore(m.lote.vencimiento)
                                || (otro.vencimiento.equals(m.lote.vencimiento) && otro.orden < m.lote.orden);
                        if (otro != m.lote && anterior && !otro.vencimiento.isBefore(dia) && !otro.creadoEn.isAfter(m.creadoEn)) {
                            assertThat(saldo.getOrDefault(otro, 0)).as("hoy %s, FEFO en venta de %s", hoy, m.lote.producto.nombre).isZero();
                        }
                    }
                }
            }
        }
    }

    /** Decisión del usuario (2026-11-03): entre el 12 y el 15 % de las ventas de cada botica llevan receta. */
    @Test
    void ventasConRecetaEntre12y15PorCientoPorBotica() {
        LocalDate inicio = LocalDate.of(2026, 12, 10);
        for (int i = 0; i < 35; i++) {
            LocalDate hoy = inicio.plusDays(i);
            DatosDemo d = GeneradorDemo.generar(parametros(hoy));
            for (DatosDemo.Botica b : d.boticas) {
                long ventas = d.ventas.stream().filter(v -> v.caja.botica == b).count();
                long conReceta = d.recetas.stream().filter(r -> r.venta.caja.botica == b).count();
                double pct = 100.0 * conReceta / ventas;
                assertThat(pct).as("hoy %s, %s: %d de %d", hoy, b.nombre, conReceta, ventas).isBetween(12.0, 15.0);
            }
        }
    }

    @Test
    void esDeterminista() {
        LocalDate hoy = LocalDate.of(2026, 11, 3);
        DatosDemo a = GeneradorDemo.generar(parametros(hoy));
        DatosDemo b = GeneradorDemo.generar(parametros(hoy));
        assertThat(a.totalFilas()).isEqualTo(b.totalFilas());
        BigDecimal totalA = a.ventas.stream().map(v -> v.total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalB = b.ventas.stream().map(v -> v.total).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalA).isEqualByComparingTo(totalB);
        assertThat(a.ventas.get(a.ventas.size() - 1).clave).isEqualTo(b.ventas.get(b.ventas.size() - 1).clave);
    }

    @Test
    void volumenYCodigosDeBarras() {
        DatosDemo d = GeneradorDemo.generar(parametros(LocalDate.of(2026, 11, 3)));
        assertThat(d.boticas).hasSize(3);
        assertThat(d.usuarios).hasSize(13); // 1 admin + 3 técnicos + 1 desactivado (24 h); 1 + 2 + 1 en las otras dos
        d.boticas.forEach(b -> assertThat(d.productos.stream().filter(p -> p.botica == b).count()).isBetween(40L, 60L));
        assertThat(d.productos).allSatisfy(p -> assertThat(Ean13.esValido(p.codigoBarras)).as(p.codigoBarras).isTrue());
        assertThat(d.productos).anyMatch(p -> p.requiereReceta).anyMatch(p -> !p.activo);
        assertThat(d.cajas).filteredOn(c -> c.abierta).hasSize(1);
        System.out.printf("Datos demo: %d filas, %d ventas, %d líneas, %d movimientos de stock, %d notificaciones%n",
                d.totalFilas(), d.ventas.size(), d.detalles.size(), d.movimientosStock.size(), d.notificaciones.size());
    }

    @Test
    void ean13ConDigitoVerificador() {
        assertThat(Ean13.digitoVerificador("400638133393")).isEqualTo(1);
        // El seed de dev usa 7751234000118, que NO es válido (el dígito correcto es 5).
        assertThat(Ean13.esValido("7751234000118")).isFalse();
        assertThat(Ean13.esValido("4006381333931")).isTrue();
        assertThat(Ean13.esValido("4006381333932")).isFalse();
    }
}
