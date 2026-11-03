package com.botica.backend.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea D: reglas que comparten los Services y la migración de datos demo
 * (V12). Fija los mismos resultados que tenían dentro de VentaTransaccion y
 * CajaService antes de separarlas.
 */
class ReglasPurasTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void extraerIgv_divideEntre1MasTasa_yElIgvEsElResto() {
        Dinero.DesgloseIgv r = Dinero.extraerIgv(d("10.00"), d("0.18"));
        assertThat(r.subtotal()).isEqualByComparingTo("8.47");
        assertThat(r.igv()).isEqualByComparingTo("1.53");
        assertThat(r.subtotal().add(r.igv())).isEqualByComparingTo("10.00");
    }

    @Test
    void calcularLinea_redondeaElTotal_noElPrecioUnitario() {
        ReglasVenta.Linea l = ReglasVenta.calcularLinea(d("12.50"), 3, 10);
        assertThat(l.cantidadBase()).isEqualTo(30);
        assertThat(l.totalLinea()).isEqualByComparingTo("37.50");
        assertThat(l.precioUnitarioBase()).isEqualByComparingTo("1.25");
    }

    @Test
    void repartirTotal_laUltimaFilaSeLlevaElResto() {
        List<BigDecimal> filas = ReglasVenta.repartirTotal(d("10.00"), List.of(1, 1, 1), 3);
        assertThat(filas).hasSize(3);
        assertThat(filas.get(0)).isEqualByComparingTo("3.33");
        assertThat(filas.get(1)).isEqualByComparingTo("3.33");
        assertThat(filas.get(2)).isEqualByComparingTo("3.34");
    }

    @Test
    void repartirTotal_unSoloLote_esElTotal() {
        assertThat(ReglasVenta.repartirTotal(d("7.90"), List.of(5), 5)).containsExactly(d("7.90"));
    }

    @Test
    void dejaStockCritico_esInclusiveEnElUmbral() {
        assertThat(ReglasVenta.dejaStockCritico(15, 15)).isTrue();
        assertThat(ReglasVenta.dejaStockCritico(16, 15)).isFalse();
    }

    @Test
    void semaforo_exactoLeveGrave() {
        assertThat(ReglasCaja.semaforo(d("0.00"), d("10.00"))).isEqualTo("EXACTO");
        assertThat(ReglasCaja.semaforo(d("-10.00"), d("10.00"))).isEqualTo("LEVE");
        assertThat(ReglasCaja.semaforo(d("10.01"), d("10.00"))).isEqualTo("GRAVE");
    }

    @Test
    void calcularCierre_esperadoEsAperturaMasEfectivo() {
        ReglasCaja.Cierre c = ReglasCaja.calcularCierre(d("100.00"), d("250.50"), d("340.00"), d("10.00"));
        assertThat(c.esperado()).isEqualByComparingTo("350.50");
        assertThat(c.diferencia()).isEqualByComparingTo("-10.50");
        assertThat(c.semaforo()).isEqualTo("GRAVE");
    }

    @Test
    void textos_igualesALosDelListener() {
        assertThat(TextosNotificacion.tituloDescuadreGrave(7)).isEqualTo("Descuadre grave en Caja #7");
        assertThat(TextosNotificacion.mensajeDescuadreGrave("Ana", 7, d("-20"), d("120"), d("100")))
                .isEqualTo("El cajero Ana cerró la caja #7 con una diferencia de S/ -20.00 (Esperado: S/ 120.00, Contado: S/ 100.00).");
        assertThat(TextosNotificacion.mensajeStockCritico("Paracetamol", 4))
                .isEqualTo("El producto Paracetamol ha llegado a un nivel crítico: solo quedan 4 unidades disponibles.");
    }
}
