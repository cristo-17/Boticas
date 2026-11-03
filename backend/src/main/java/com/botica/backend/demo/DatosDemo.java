package com.botica.backend.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filas que genera {@link GeneradorDemo}, en memoria y sin ids: los ids los
 * pide {@link InsertadorDemo} a cada secuencia al insertar. Cada lista está en
 * orden cronológico dentro de su botica, que es el orden en que se insertan.
 */
public final class DatosDemo {

    final List<Botica> boticas = new ArrayList<>();
    final List<Usuario> usuarios = new ArrayList<>();
    final List<Producto> productos = new ArrayList<>();
    final List<Presentacion> presentaciones = new ArrayList<>();
    final List<Lote> lotes = new ArrayList<>();
    final List<Caja> cajas = new ArrayList<>();
    final List<Venta> ventas = new ArrayList<>();
    final List<Detalle> detalles = new ArrayList<>();
    final List<Receta> recetas = new ArrayList<>();
    final List<Merma> mermas = new ArrayList<>();
    final List<MovCaja> movimientosCaja = new ArrayList<>();
    final List<MovStock> movimientosStock = new ArrayList<>();
    final List<Notificacion> notificaciones = new ArrayList<>();

    public int totalFilas() {
        return boticas.size() + usuarios.size() + productos.size() + presentaciones.size() + lotes.size()
                + cajas.size() + ventas.size() + detalles.size() + recetas.size() + mermas.size()
                + movimientosCaja.size() + movimientosStock.size() + notificaciones.size();
    }

    public int ventas() {
        return ventas.size();
    }

    static final class Botica {
        long id;
        String nombre;
        String direccion;
        OffsetDateTime creadoEn;
    }

    static final class Usuario {
        long id;
        Botica botica;
        String rol;
        String nombre;
        String usuario;
        String passwordHash;
        boolean activo;
        OffsetDateTime creadoEn;
        Usuario creadoPor;
    }

    static final class Producto {
        long id;
        Botica botica;
        String nombre;
        String laboratorio;
        String categoria;
        String codigoBarras;
        String unidadNombre;
        boolean requiereReceta;
        boolean activo = true;
        boolean granel;
        OffsetDateTime creadoEn;
        Usuario creadoPor;
        final List<Presentacion> presentaciones = new ArrayList<>();
        final List<Lote> lotes = new ArrayList<>();
    }

    static final class Presentacion {
        long id;
        Producto producto;
        String etiqueta;
        int factor;
        BigDecimal precio;
        OffsetDateTime creadoEn;
    }

    static final class Lote {
        long id;
        int orden; // orden de creación: desempate de FEFO, igual que el id (ORDER BY fecha_vencimiento, id)
        Producto producto;
        String codigo;
        LocalDate vencimiento;
        int stock;
        String ubicacion;
        BigDecimal costo;
        OffsetDateTime creadoEn;
        Usuario creadoPor;
    }

    static final class Caja {
        long id;
        Botica botica;
        Usuario usuario;
        LocalDate fecha;
        String turno;
        BigDecimal apertura;
        OffsetDateTime horaApertura;
        OffsetDateTime horaCierre;
        BigDecimal contado;
        BigDecimal esperado;
        BigDecimal diferencia;
        String semaforo;
        String observaciones;
        boolean abierta = true;
        BigDecimal efectivo = BigDecimal.ZERO;
    }

    static final class Venta {
        long id;
        Caja caja;
        OffsetDateTime fecha;
        BigDecimal subtotal;
        BigDecimal igv;
        BigDecimal igvTasa;
        BigDecimal total;
        String metodoPago;
        UUID clave;
    }

    static final class Detalle {
        long id;
        Venta venta;
        Producto producto;
        Presentacion presentacion;
        Lote lote;
        String nombreProducto;
        String etiqueta;
        int cantidad;
        BigDecimal precioUnitario;
        BigDecimal costoUnitario;
        BigDecimal totalLinea;
        String origen;
        boolean requiereReceta;
    }

    static final class Receta {
        long id;
        Venta venta;
        String numero;
        String cmp;
        String medico;
        LocalDate fechaEmision;
    }

    static final class Merma {
        long id;
        Lote lote;
        Usuario usuario;
        int cantidad;
        String motivo;
        String observacion;
        BigDecimal valorVenta;
        OffsetDateTime fecha;
    }

    static final class MovCaja {
        long id;
        Caja caja;
        String tipo;
        String descripcion; // null en una venta: se arma con el id ("Venta #id") al insertar
        String nota;
        BigDecimal monto;
        boolean afectaEfectivo;
        OffsetDateTime creadoEn;
        Usuario creadoPor;
        Venta venta;
    }

    static final class MovStock {
        long id;
        Lote lote;
        String tipo;
        int cantidad;
        String origen;
        Venta venta;
        Merma merma;
        OffsetDateTime creadoEn;
        Usuario creadoPor;
    }

    static final class Notificacion {
        long id;
        Botica botica;
        String rolDestinatario;
        String tipo;
        boolean leido;
        OffsetDateTime creadoEn;
        // DESCUADRE_GRAVE: el texto lleva el id de la caja, se arma al insertar.
        Caja caja;
        // STOCK_CRITICO
        String nombreProducto;
        int stockRestante;
    }
}
