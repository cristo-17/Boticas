package com.botica.backend.demo;

import com.botica.backend.demo.DatosDemo.Botica;
import com.botica.backend.demo.DatosDemo.Caja;
import com.botica.backend.util.TextosNotificacion;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Inserta {@link DatosDemo} con JDBC por lotes. Los ids no se escriben a mano:
 * se piden a la secuencia de cada tabla (una consulta por tabla) y se asignan
 * en el orden de las listas, que es cronológico dentro de cada botica; así
 * (creado_en, id) ordena igual que la simulación.
 */
public final class InsertadorDemo {

    private static final int TAMANO_LOTE = 1000;

    private InsertadorDemo() {
    }

    /** Falla, con la lista, si algún nombre de usuario demo ya existe. */
    public static void verificarUsuariosLibres(Connection c, DatosDemo d) throws SQLException {
        String[] nombres = d.usuarios.stream().map(u -> u.usuario).toArray(String[]::new);
        Array arreglo = c.createArrayOf("varchar", nombres);
        List<String> repetidos = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT usuario FROM usuarios WHERE usuario = ANY (?) ORDER BY usuario")) {
            ps.setArray(1, arreglo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    repetidos.add(rs.getString(1));
                }
            }
        }
        if (!repetidos.isEmpty()) {
            throw new IllegalStateException("Datos demo: nombres de usuario que ya existen: " + String.join(", ", repetidos));
        }
        List<String> boticas = new ArrayList<>();
        for (Botica b : d.boticas) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM boticas WHERE nombre = ?")) {
                ps.setString(1, b.nombre);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        boticas.add(b.nombre);
                    }
                }
            }
        }
        if (!boticas.isEmpty()) {
            throw new IllegalStateException("Datos demo: boticas que ya existen: " + String.join(", ", boticas));
        }
    }

    public static void insertar(Connection c, DatosDemo d) throws SQLException {
        Map<String, Long> roles = roles(c);

        asignar(c, "boticas", d.boticas, (b, id) -> b.id = id);
        asignar(c, "usuarios", d.usuarios, (u, id) -> u.id = id);
        asignar(c, "productos", d.productos, (p, id) -> p.id = id);
        asignar(c, "presentaciones", d.presentaciones, (p, id) -> p.id = id);
        asignar(c, "lotes", d.lotes, (l, id) -> l.id = id);
        asignar(c, "caja_diaria", d.cajas, (x, id) -> x.id = id);
        asignar(c, "ventas", d.ventas, (v, id) -> v.id = id);
        asignar(c, "venta_detalle", d.detalles, (x, id) -> x.id = id);
        asignar(c, "recetas", d.recetas, (x, id) -> x.id = id);
        asignar(c, "mermas", d.mermas, (x, id) -> x.id = id);
        asignar(c, "movimientos_caja", d.movimientosCaja, (x, id) -> x.id = id);
        asignar(c, "movimientos_stock", d.movimientosStock, (x, id) -> x.id = id);
        asignar(c, "notificaciones", d.notificaciones, (x, id) -> x.id = id);

        lote(c, "INSERT INTO boticas (id, nombre, direccion, ruc, activo, creado_en) VALUES (?, ?, ?, NULL, true, ?)",
                d.boticas, (ps, b) -> {
                    ps.setLong(1, b.id);
                    ps.setString(2, b.nombre);
                    ps.setString(3, b.direccion);
                    ps.setObject(4, b.creadoEn);
                });
        lote(c, "INSERT INTO usuarios (id, botica_id, rol_id, nombre, usuario, password_hash, activo, creado_en, creado_por) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.usuarios, (ps, u) -> {
                    ps.setLong(1, u.id);
                    ps.setLong(2, u.botica.id);
                    ps.setLong(3, roles.get(u.rol));
                    ps.setString(4, u.nombre);
                    ps.setString(5, u.usuario);
                    ps.setString(6, u.passwordHash);
                    ps.setBoolean(7, u.activo);
                    ps.setObject(8, u.creadoEn);
                    idONulo(ps, 9, u.creadoPor, x -> x.id);
                });
        lote(c, "INSERT INTO productos (id, botica_id, nombre, laboratorio, categoria, codigo_barras, unidad_nombre, "
                        + "requiere_receta, activo, creado_en, creado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.productos, (ps, p) -> {
                    ps.setLong(1, p.id);
                    ps.setLong(2, p.botica.id);
                    ps.setString(3, p.nombre);
                    ps.setString(4, p.laboratorio);
                    ps.setString(5, p.categoria);
                    ps.setString(6, p.codigoBarras);
                    ps.setString(7, p.unidadNombre);
                    ps.setBoolean(8, p.requiereReceta);
                    ps.setBoolean(9, p.activo);
                    ps.setObject(10, p.creadoEn);
                    ps.setLong(11, p.creadoPor.id);
                });
        lote(c, "INSERT INTO presentaciones (id, botica_id, producto_id, etiqueta, factor_conversion, precio, activo, creado_en) "
                        + "VALUES (?, ?, ?, ?, ?, ?, true, ?)",
                d.presentaciones, (ps, p) -> {
                    ps.setLong(1, p.id);
                    ps.setLong(2, p.producto.botica.id);
                    ps.setLong(3, p.producto.id);
                    ps.setString(4, p.etiqueta);
                    ps.setInt(5, p.factor);
                    ps.setBigDecimal(6, p.precio);
                    ps.setObject(7, p.creadoEn);
                });
        lote(c, "INSERT INTO lotes (id, botica_id, producto_id, codigo, fecha_vencimiento, stock, ubicacion, costo_unitario, "
                        + "creado_en, creado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.lotes, (ps, l) -> {
                    ps.setLong(1, l.id);
                    ps.setLong(2, l.producto.botica.id);
                    ps.setLong(3, l.producto.id);
                    ps.setString(4, l.codigo);
                    ps.setObject(5, l.vencimiento);
                    ps.setInt(6, l.stock);
                    ps.setString(7, l.ubicacion);
                    ps.setBigDecimal(8, l.costo);
                    ps.setObject(9, l.creadoEn);
                    ps.setLong(10, l.creadoPor.id);
                });
        lote(c, "INSERT INTO caja_diaria (id, botica_id, usuario_id, fecha, turno, monto_apertura, hora_apertura, hora_cierre, "
                        + "monto_contado, monto_esperado, diferencia, semaforo_descuadre, observaciones, estado) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.cajas, (ps, x) -> {
                    ps.setLong(1, x.id);
                    ps.setLong(2, x.botica.id);
                    ps.setLong(3, x.usuario.id);
                    ps.setObject(4, x.fecha);
                    ps.setString(5, x.turno);
                    ps.setBigDecimal(6, x.apertura);
                    ps.setObject(7, x.horaApertura);
                    ps.setObject(8, x.horaCierre, Types.TIMESTAMP_WITH_TIMEZONE);
                    ps.setBigDecimal(9, x.contado);
                    ps.setBigDecimal(10, x.esperado);
                    ps.setBigDecimal(11, x.diferencia);
                    ps.setString(12, x.semaforo);
                    ps.setString(13, x.observaciones);
                    ps.setString(14, x.abierta ? "ABIERTA" : "CERRADA");
                });
        lote(c, "INSERT INTO ventas (id, botica_id, usuario_id, caja_id, fecha, subtotal, igv, igv_tasa, total, metodo_pago, "
                        + "clave_idempotencia, sincronizada) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)",
                d.ventas, (ps, v) -> {
                    ps.setLong(1, v.id);
                    ps.setLong(2, v.caja.botica.id);
                    ps.setLong(3, v.caja.usuario.id);
                    ps.setLong(4, v.caja.id);
                    ps.setObject(5, v.fecha);
                    ps.setBigDecimal(6, v.subtotal);
                    ps.setBigDecimal(7, v.igv);
                    ps.setBigDecimal(8, v.igvTasa);
                    ps.setBigDecimal(9, v.total);
                    ps.setString(10, v.metodoPago);
                    ps.setObject(11, v.clave);
                });
        lote(c, "INSERT INTO venta_detalle (id, venta_id, botica_id, producto_id, presentacion_id, lote_id, nombre_producto, "
                        + "etiqueta_presentacion, cantidad, precio_unitario, costo_unitario, total_linea, origen_captura, requiere_receta) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.detalles, (ps, x) -> {
                    ps.setLong(1, x.id);
                    ps.setLong(2, x.venta.id);
                    ps.setLong(3, x.venta.caja.botica.id);
                    ps.setLong(4, x.producto.id);
                    ps.setLong(5, x.presentacion.id);
                    ps.setLong(6, x.lote.id);
                    ps.setString(7, x.nombreProducto);
                    ps.setString(8, x.etiqueta);
                    ps.setInt(9, x.cantidad);
                    ps.setBigDecimal(10, x.precioUnitario);
                    ps.setBigDecimal(11, x.costoUnitario);
                    ps.setBigDecimal(12, x.totalLinea);
                    ps.setString(13, x.origen);
                    ps.setBoolean(14, x.requiereReceta);
                });
        lote(c, "INSERT INTO recetas (id, botica_id, venta_id, numero, cmp, medico_nombre, fecha_emision, creado_en, creado_por) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.recetas, (ps, r) -> {
                    ps.setLong(1, r.id);
                    ps.setLong(2, r.venta.caja.botica.id);
                    ps.setLong(3, r.venta.id);
                    ps.setString(4, r.numero);
                    ps.setString(5, r.cmp);
                    ps.setString(6, r.medico);
                    ps.setObject(7, r.fechaEmision, Types.DATE);
                    ps.setObject(8, r.venta.fecha);
                    ps.setLong(9, r.venta.caja.usuario.id);
                });
        lote(c, "INSERT INTO mermas (id, botica_id, lote_id, usuario_id, cantidad, motivo, observacion, valor_venta, fecha) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.mermas, (ps, m) -> {
                    ps.setLong(1, m.id);
                    ps.setLong(2, m.lote.producto.botica.id);
                    ps.setLong(3, m.lote.id);
                    ps.setLong(4, m.usuario.id);
                    ps.setInt(5, m.cantidad);
                    ps.setString(6, m.motivo);
                    ps.setString(7, m.observacion);
                    ps.setBigDecimal(8, m.valorVenta);
                    ps.setObject(9, m.fecha);
                });
        lote(c, "INSERT INTO movimientos_caja (id, botica_id, caja_id, tipo, descripcion, nota, monto, afecta_efectivo, "
                        + "creado_en, creado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.movimientosCaja, (ps, m) -> {
                    ps.setLong(1, m.id);
                    ps.setLong(2, m.caja.botica.id);
                    ps.setLong(3, m.caja.id);
                    ps.setString(4, m.tipo);
                    // Como VentaTransaccion: "Venta #<id>".
                    ps.setString(5, m.venta != null ? "Venta #" + m.venta.id : m.descripcion);
                    ps.setString(6, m.nota);
                    ps.setBigDecimal(7, m.monto);
                    ps.setBoolean(8, m.afectaEfectivo);
                    ps.setObject(9, m.creadoEn);
                    ps.setLong(10, m.creadoPor.id);
                });
        lote(c, "INSERT INTO movimientos_stock (id, botica_id, lote_id, tipo, cantidad, origen_captura, referencia_venta_id, "
                        + "referencia_merma_id, creado_en, creado_por) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                d.movimientosStock, (ps, m) -> {
                    ps.setLong(1, m.id);
                    ps.setLong(2, m.lote.producto.botica.id);
                    ps.setLong(3, m.lote.id);
                    ps.setString(4, m.tipo);
                    ps.setInt(5, m.cantidad);
                    ps.setString(6, m.origen);
                    idONulo(ps, 7, m.venta, x -> x.id);
                    idONulo(ps, 8, m.merma, x -> x.id);
                    ps.setObject(9, m.creadoEn);
                    ps.setLong(10, m.creadoPor.id);
                });
        lote(c, "INSERT INTO notificaciones (id, botica_id, usuario_id, rol_destinatario, tipo, titulo, mensaje, leido, "
                        + "creado_en, creado_por) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, NULL)",
                d.notificaciones, (ps, n) -> {
                    ps.setLong(1, n.id);
                    ps.setLong(2, n.botica.id);
                    ps.setString(3, n.rolDestinatario);
                    ps.setString(4, n.tipo);
                    if ("DESCUADRE_GRAVE".equals(n.tipo)) {
                        Caja cj = n.caja;
                        ps.setString(5, TextosNotificacion.tituloDescuadreGrave(cj.id));
                        ps.setString(6, TextosNotificacion.mensajeDescuadreGrave(cj.usuario.nombre, cj.id,
                                cj.diferencia, cj.esperado, cj.contado));
                    } else {
                        ps.setString(5, TextosNotificacion.tituloStockCritico(n.nombreProducto));
                        ps.setString(6, TextosNotificacion.mensajeStockCritico(n.nombreProducto, n.stockRestante));
                    }
                    ps.setBoolean(7, n.leido);
                    ps.setObject(8, n.creadoEn);
                });
    }

    private interface Asignador<T> {
        void asignar(T fila, long id);
    }

    private interface Llenador<T> {
        void llenar(PreparedStatement ps, T fila) throws SQLException;
    }

    private static Map<String, Long> roles(Connection c) throws SQLException {
        Map<String, Long> roles = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT nombre, id FROM roles");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                roles.put(rs.getString(1), rs.getLong(2));
            }
        }
        if (!roles.containsKey(CatalogoDemo.ADMINISTRADOR) || !roles.containsKey(CatalogoDemo.TECNICO)) {
            throw new IllegalStateException("Datos demo: faltan los roles TECNICO/ADMINISTRADOR");
        }
        return roles;
    }

    private static <T> void asignar(Connection c, String tabla, List<T> filas, Asignador<T> asignador) throws SQLException {
        if (filas.isEmpty()) {
            return;
        }
        String sql = "SELECT nextval(pg_get_serial_sequence(?, 'id')) FROM generate_series(1, ?) ORDER BY 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tabla);
            ps.setInt(2, filas.size());
            try (ResultSet rs = ps.executeQuery()) {
                int i = 0;
                while (rs.next()) {
                    asignador.asignar(filas.get(i++), rs.getLong(1));
                }
                if (i != filas.size()) {
                    throw new IllegalStateException("Secuencia de " + tabla + " devolvió " + i + " ids para " + filas.size() + " filas");
                }
            }
        }
    }

    private static <T> void lote(Connection c, String sql, List<T> filas, Llenador<T> llenador) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int pendientes = 0;
            for (T fila : filas) {
                llenador.llenar(ps, fila);
                ps.addBatch();
                if (++pendientes == TAMANO_LOTE) {
                    ps.executeBatch();
                    pendientes = 0;
                }
            }
            if (pendientes > 0) {
                ps.executeBatch();
            }
        }
    }

    private static <T> void idONulo(PreparedStatement ps, int indice, T fila, ToLongFunction<T> id) throws SQLException {
        if (fila == null) {
            ps.setNull(indice, Types.BIGINT);
        } else {
            ps.setLong(indice, id.applyAsLong(fila));
        }
    }
}
