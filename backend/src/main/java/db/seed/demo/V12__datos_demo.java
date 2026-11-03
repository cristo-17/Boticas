package db.seed.demo;

import com.botica.backend.demo.DatosDemo;
import com.botica.backend.demo.GeneradorDemo;
import com.botica.backend.demo.InsertadorDemo;
import com.botica.backend.util.FechaNegocio;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Map;

/**
 * Tarea D (D-F-3): tres boticas de demostración con 60 días de historia, hasta
 * ayer. Vive en classpath:db/seed/demo, que cargan los perfiles dev y prod y no
 * el de test. No toca ninguna fila existente: solo inserta boticas nuevas y lo
 * que cuelga de ellas.
 *
 * <p>Depende de las reglas puras que comparte con los Services (Dinero,
 * ReglasVenta, ReglasCaja, TextosNotificacion): si cambian, una base
 * reconstruida desde cero genera otra historia demo (DECISIONES 2026-11-03).
 * Las migraciones Java no tienen checksum, así que Flyway no lo avisa.
 *
 * <p>Placeholders (spring.flyway.placeholders.*): demoPasswordHash (BCrypt,
 * obligatorio), igv, umbralStockBajo y descuadreLeve (apuntan a
 * app.config-negocio.*) y demoHoy (opcional, solo para pruebas; vacío = hoy en
 * Lima).
 */
public class V12__datos_demo extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V12__datos_demo.class);

    @Override
    public void migrate(Context context) throws Exception {
        Map<String, String> ph = context.getConfiguration().getPlaceholders();
        String hash = obligatorio(ph, "demoPasswordHash");
        if (!hash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalStateException("demoPasswordHash no es un hash BCrypt");
        }
        String hoyTexto = valor(ph, "demoHoy");
        LocalDate hoy = hoyTexto == null || hoyTexto.isBlank()
                ? LocalDate.now(FechaNegocio.ZONA_LIMA)
                : LocalDate.parse(hoyTexto.trim());

        GeneradorDemo.Parametros p = new GeneradorDemo.Parametros(
                hoy,
                new BigDecimal(obligatorio(ph, "igv")),
                Integer.parseInt(obligatorio(ph, "umbralStockBajo")),
                new BigDecimal(obligatorio(ph, "descuadreLeve")),
                hash);

        long inicio = System.nanoTime();
        DatosDemo datos = GeneradorDemo.generar(p);
        long generado = System.nanoTime();

        Connection c = context.getConnection();
        InsertadorDemo.verificarUsuariosLibres(c, datos);
        InsertadorDemo.insertar(c, datos);
        long fin = System.nanoTime();

        log.info("Datos demo (hoy {}): {} filas, {} ventas; generación {} ms, inserción {} ms",
                hoy, datos.totalFilas(), datos.ventas(), (generado - inicio) / 1_000_000, (fin - generado) / 1_000_000);
    }

    private static String valor(Map<String, String> ph, String clave) {
        for (Map.Entry<String, String> e : ph.entrySet()) {
            if (e.getKey().equalsIgnoreCase(clave)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static String obligatorio(Map<String, String> ph, String clave) {
        String v = valor(ph, clave);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta el placeholder de Flyway " + clave);
        }
        return v.trim();
    }
}
