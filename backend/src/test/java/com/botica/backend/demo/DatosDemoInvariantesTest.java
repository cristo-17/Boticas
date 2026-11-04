package com.botica.backend.demo;

import com.botica.backend.dao.impl.AlertaDaoJdbc;
import com.botica.backend.dao.impl.ReporteDaoJdbc;
import com.botica.backend.dto.FilaResultadoResponse;
import com.botica.backend.util.Dinero;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.ReglasCaja;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea D: arnés de los 11 invariantes de los datos demo. Arma una base aparte
 * (botica_demo_test, se recrea en cada corrida) con el camino de producción
 * (db/migration + db/seed/prod, placeholders de prueba) hasta V11, le agrega
 * filas con la forma de la botica real, toma la huella md5 de esas filas, aplica
 * la V12 (db/seed/demo) y comprueba:
 * <ul>
 *   <li>las consultas de demo/invariantes.sql (las mismas que se corren con psql
 *       en botica_prod_sim y en la rama de Neon), todas en 0;</li>
 *   <li>contra el código real: el semáforo y el IGV con ReglasCaja/Dinero, el
 *       reporte de resultados con ReporteDaoJdbc y las alertas con AlertaDaoJdbc;</li>
 *   <li>el invariante 11: la huella de las filas de la botica real no cambia.</li>
 * </ul>
 */
class DatosDemoInvariantesTest {

    private static final String BASE = "botica_demo_test";
    private static final String HASH = "$2a$10$Ip6WW/zd1eGkCM0rZv2lcOgYZYYOTTiqQMfAGRVKY6obCuMTwdXri";

    private static String url;
    private static String usuario;
    private static String password;
    private static Properties config;
    private static JdbcTemplate jdbc;
    private static List<Map<String, Object>> md5Antes;
    private static long msMigracion;
    private static final LocalDate HOY = LocalDate.now(FechaNegocio.ZONA_LIMA);

    @BeforeAll
    static void armarBase() throws Exception {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", "5432");
        usuario = env("DB_USERNAME", "postgres");
        password = env("DB_PASSWORD", "");
        config = new Properties();
        try (InputStream in = DatosDemoInvariantesTest.class.getResourceAsStream("/application.properties")) {
            config.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }

        try (Connection c = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/postgres", usuario, password);
             Statement st = c.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BASE + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + BASE);
        }
        url = "jdbc:postgresql://" + host + ":" + port + "/" + BASE;
        DriverManagerDataSource ds = new DriverManagerDataSource(url, usuario, password);
        jdbc = new JdbcTemplate(ds);

        Map<String, String> ph = new HashMap<>();
        ph.put("boticaNombre", "Botica Real de Prueba");
        ph.put("adminNombre", "Admin Real");
        ph.put("adminUsuario", "admin.real");
        ph.put("adminPasswordHash", HASH);
        ph.put("adminTurno", "Mañana");
        ph.put("tecnicoNombre", "Tecnico Real");
        ph.put("tecnicoUsuario", "tecnico.real");
        ph.put("tecnicoPasswordHash", HASH);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/prod")
                .placeholders(ph).target("11").load().migrate();

        // Forma de la botica real: una caja abierta y un producto con su lote (y su INGRESO).
        jdbc.execute("SET TIME ZONE 'America/Lima'");
        Long botica = jdbc.queryForObject("SELECT id FROM boticas", Long.class);
        Long admin = jdbc.queryForObject("SELECT id FROM usuarios WHERE usuario = 'admin.real'", Long.class);
        jdbc.update("INSERT INTO caja_diaria (botica_id, usuario_id, fecha, turno, monto_apertura) VALUES (?, ?, CURRENT_DATE, 'Mañana', 100)", botica, admin);
        Long producto = jdbc.queryForObject("INSERT INTO productos (botica_id, nombre, categoria, codigo_barras, unidad_nombre, requiere_receta, creado_por) "
                + "VALUES (?, 'Producto real', 'Otros', '7750000000017', 'tableta', false, ?) RETURNING id", Long.class, botica, admin);
        jdbc.update("INSERT INTO presentaciones (botica_id, producto_id, etiqueta, factor_conversion, precio) VALUES (?, ?, 'Unidad', 1, 1.50)", botica, producto);
        Long lote = jdbc.queryForObject("INSERT INTO lotes (botica_id, producto_id, codigo, fecha_vencimiento, stock, costo_unitario, creado_por) "
                + "VALUES (?, ?, 'REAL-1', CURRENT_DATE + 300, 50, 0.80, ?) RETURNING id", Long.class, botica, producto, admin);
        jdbc.update("INSERT INTO movimientos_stock (botica_id, lote_id, tipo, cantidad, creado_por) VALUES (?, ?, 'INGRESO', 50, ?)", botica, lote, admin);

        md5Antes = jdbc.queryForList(recurso("/demo/md5-real.sql"));

        Map<String, String> phDemo = new HashMap<>(ph);
        phDemo.put("demoPasswordHash", HASH);
        phDemo.put("igv", config.getProperty("app.config-negocio.igv"));
        phDemo.put("umbralStockBajo", config.getProperty("app.config-negocio.umbral-stock-bajo"));
        phDemo.put("descuadreLeve", config.getProperty("app.config-negocio.descuadre-leve"));
        phDemo.put("demoHoy", "");
        long inicio = System.nanoTime();
        Flyway.configure().dataSource(ds)
                .locations("classpath:db/migration", "classpath:db/seed/prod", "classpath:db/seed/demo")
                .placeholders(phDemo).load().migrate();
        msMigracion = (System.nanoTime() - inicio) / 1_000_000;
    }

    @Test
    void v12QuedaAplicada() {
        // Desde la Tarea 22 hay migraciones después de V12 (V13): los invariantes corren con ellas encima.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '12' AND success", Integer.class)).isEqualTo(1);
        System.out.println("V12 en " + BASE + ": " + msMigracion + " ms");
        for (Map<String, Object> fila : jdbc.queryForList("""
                SELECT b.nombre,
                  (SELECT count(*) FROM usuarios WHERE botica_id = b.id) AS usuarios,
                  (SELECT count(*) FROM productos WHERE botica_id = b.id) AS productos,
                  (SELECT count(*) FROM lotes WHERE botica_id = b.id) AS lotes,
                  (SELECT count(*) FROM caja_diaria WHERE botica_id = b.id) AS cajas,
                  (SELECT count(*) FROM ventas WHERE botica_id = b.id) AS ventas,
                  (SELECT count(*) FROM venta_detalle WHERE botica_id = b.id) AS lineas,
                  (SELECT count(*) FROM recetas WHERE botica_id = b.id) AS recetas,
                  (SELECT count(*) FROM mermas WHERE botica_id = b.id) AS mermas,
                  (SELECT count(*) FROM movimientos_stock WHERE botica_id = b.id) AS mov_stock,
                  (SELECT count(*) FROM movimientos_caja WHERE botica_id = b.id) AS mov_caja,
                  (SELECT count(*) FROM notificaciones WHERE botica_id = b.id) AS notificaciones
                FROM boticas b ORDER BY b.id""")) {
            System.out.println("  " + fila);
        }
    }

    @Test
    void invariantesSql1a10EnCero() throws IOException {
        String leve = config.getProperty("app.config-negocio.descuadre-leve");
        String sql = recurso("/demo/invariantes.sql").replace("{{DESCUADRE_LEVE}}", leve);
        List<String> fallas = new ArrayList<>();
        int corridas = 0;
        for (String consulta : sql.split(";\\s*\\R")) {
            if (!consulta.contains("SELECT")) {
                continue;
            }
            Map<String, Object> fila = jdbc.queryForMap(consulta);
            corridas++;
            System.out.println("  " + fila.get("invariante") + " -> " + fila.get("fallas"));
            if (((Number) fila.get("fallas")).longValue() != 0) {
                fallas.add(fila.get("invariante") + ": " + fila.get("fallas"));
            }
        }
        assertThat(corridas).isEqualTo(11); // INV-0 (guardia) + INV-1 a INV-10
        assertThat(fallas).isEmpty();
    }

    @Test
    void invariante3y5ContraLasReglasDelCodigo() {
        BigDecimal leve = new BigDecimal(config.getProperty("app.config-negocio.descuadre-leve"));
        List<Map<String, Object>> cajas = jdbc.queryForList(
                "SELECT diferencia, semaforo_descuadre FROM caja_diaria c JOIN usuarios u ON u.id = c.usuario_id "
                        + "WHERE u.usuario LIKE 'demo\\_%' AND c.estado = 'CERRADA'");
        assertThat(cajas).isNotEmpty().allSatisfy(c ->
                assertThat(ReglasCaja.semaforo((BigDecimal) c.get("diferencia"), leve)).isEqualTo(c.get("semaforo_descuadre")));
        assertThat(cajas).filteredOn(c -> "GRAVE".equals(c.get("semaforo_descuadre"))).hasSize(6);

        List<Map<String, Object>> ventas = jdbc.queryForList(
                "SELECT v.total, v.subtotal, v.igv, v.igv_tasa FROM ventas v JOIN usuarios u ON u.id = v.usuario_id WHERE u.usuario LIKE 'demo\\_%'");
        assertThat(ventas).isNotEmpty().allSatisfy(v -> {
            Dinero.DesgloseIgv esperado = Dinero.extraerIgv((BigDecimal) v.get("total"), (BigDecimal) v.get("igv_tasa"));
            assertThat((BigDecimal) v.get("subtotal")).isEqualByComparingTo(esperado.subtotal());
            assertThat((BigDecimal) v.get("igv")).isEqualByComparingTo(esperado.igv());
        });
    }

    @Test
    void invariante10ContraElReporteDelServidor() {
        ReporteDaoJdbc reporte = new ReporteDaoJdbc(new NamedParameterJdbcTemplate(jdbc));
        for (Long botica : boticasDemo()) {
            LocalDate desde = HOY.minusDays(GeneradorDemo.DIAS);
            LocalDate hasta = HOY.minusDays(1);
            List<FilaResultadoResponse> filas = reporte.consultarFilasResultados(botica, desde, hasta, "dia");
            Map<String, BigDecimal> porDia = new HashMap<>();
            jdbc.queryForList("SELECT (fecha AT TIME ZONE 'America/Lima')::date::text AS dia, SUM(total) AS ventas FROM ventas "
                    + "WHERE botica_id = ? GROUP BY 1", botica).forEach(f -> porDia.put((String) f.get("dia"), (BigDecimal) f.get("ventas")));
            BigDecimal sumaFilas = BigDecimal.ZERO;
            for (FilaResultadoResponse f : filas) {
                assertThat(f.ventas()).as("día %s", f.periodo()).isEqualByComparingTo(porDia.getOrDefault(f.periodo(), BigDecimal.ZERO));
                sumaFilas = sumaFilas.add(f.ventas());
            }
            BigDecimal sumaCajas = jdbc.queryForObject(
                    "SELECT SUM(monto) FROM movimientos_caja WHERE botica_id = ? AND tipo = 'venta'", BigDecimal.class, botica);
            assertThat(sumaFilas).isEqualByComparingTo(sumaCajas);
        }
        // Una caja de Noche se reparte entre dos días de Lima.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM caja_diaria c WHERE c.turno = 'Noche'
                  AND (SELECT count(DISTINCT (v.fecha AT TIME ZONE 'America/Lima')::date) FROM ventas v WHERE v.caja_id = c.id) = 2""",
                Long.class)).isPositive();
    }

    @Test
    void escenarioVistoPorLasAlertasDelServidor() {
        AlertaDaoJdbc alertas = new AlertaDaoJdbc(new NamedParameterJdbcTemplate(jdbc));
        int umbral = Integer.parseInt(config.getProperty("app.config-negocio.umbral-stock-bajo"));
        int critico = Integer.parseInt(config.getProperty("app.config-negocio.vencimiento-critico-dias"));
        long cajasSinCerrar = 0;
        for (Long botica : boticasDemo()) {
            assertThat(alertas.contarProductosStockCritico(botica, umbral, HOY)).isPositive();
            assertThat(alertas.contarProductosStockAgotado(botica, HOY)).isPositive();
            assertThat(alertas.lotesVencidosOCriticos(botica, HOY, critico))
                    .anyMatch(l -> "VENCIDO".equals(l.estadoVencimiento()))
                    .anyMatch(l -> "CRITICO".equals(l.estadoVencimiento()));
            cajasSinCerrar += alertas.cajasSinCerrar(botica, HOY).size();
        }
        assertThat(cajasSinCerrar).isEqualTo(1);
        // Cada nombre de usuario demo sigue el formato acordado; el desactivado no tiene cajas al final de la historia.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE usuario LIKE 'demo\\_%' AND usuario !~ '^demo_[a-z]+_[a-z]+$'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM caja_diaria c JOIN usuarios u ON u.id = c.usuario_id
                WHERE u.usuario LIKE 'demo\\_%' AND NOT u.activo AND c.fecha > CURRENT_DATE - 20""", Long.class)).isZero();
    }

    @Test
    void invariante11LaBoticaRealNoCambia() throws IOException {
        List<Map<String, Object>> despues = jdbc.queryForList(recurso("/demo/md5-real.sql"));
        assertThat(despues).isEqualTo(md5Antes);
        assertThat(despues).anySatisfy(f -> assertThat(f.get("?column?")).isEqualTo("caja_diaria"));
    }

    private static List<Long> boticasDemo() {
        return jdbc.queryForList("SELECT DISTINCT botica_id FROM usuarios WHERE usuario LIKE 'demo\\_%' ORDER BY 1", Long.class);
    }

    private static String recurso(String ruta) throws IOException {
        try (InputStream in = DatosDemoInvariantesTest.class.getResourceAsStream(ruta)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return v == null || v.isBlank() ? porDefecto : v;
    }
}
