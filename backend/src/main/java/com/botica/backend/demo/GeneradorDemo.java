package com.botica.backend.demo;

import com.botica.backend.demo.CatalogoDemo.BoticaSpec;
import com.botica.backend.demo.CatalogoDemo.ProductoSpec;
import com.botica.backend.demo.CatalogoDemo.UsuarioSpec;
import com.botica.backend.demo.DatosDemo.Botica;
import com.botica.backend.demo.DatosDemo.Caja;
import com.botica.backend.demo.DatosDemo.Detalle;
import com.botica.backend.demo.DatosDemo.Lote;
import com.botica.backend.demo.DatosDemo.Merma;
import com.botica.backend.demo.DatosDemo.MovCaja;
import com.botica.backend.demo.DatosDemo.MovStock;
import com.botica.backend.demo.DatosDemo.Notificacion;
import com.botica.backend.demo.DatosDemo.Presentacion;
import com.botica.backend.demo.DatosDemo.Producto;
import com.botica.backend.demo.DatosDemo.Receta;
import com.botica.backend.demo.DatosDemo.Usuario;
import com.botica.backend.demo.DatosDemo.Venta;
import com.botica.backend.service.ConfigService;
import com.botica.backend.util.Dinero;
import com.botica.backend.util.FechaNegocio;
import com.botica.backend.util.ReglasCaja;
import com.botica.backend.util.ReglasVenta;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Genera la historia de las tres boticas de demostración (Tarea D) simulando,
 * en orden cronológico, lo que habría hecho el servidor: ingresos de lotes,
 * aperturas, ventas con FEFO sobre el stock simulado, mermas, egresos y cierres.
 *
 * <p>No copia reglas del Service: usa las mismas funciones puras
 * ({@link Dinero#extraerIgv}, {@link ReglasVenta}, {@link ReglasCaja},
 * TextosNotificacion al insertar). Lo que no se puede compartir (el orden FEFO,
 * que vive en SQL) lo comprueba el arnés de invariantes por separado.
 *
 * <p>Determinista: semilla fija por botica y fechas relativas a {@code hoy}.
 * Toda la historia queda antes de las 00:00 de hoy (la Noche de ayer a hoy no
 * se genera). Termina verificando el escenario que pide la Tarea D y, si no se
 * cumple, lanza IllegalStateException: la migración falla en vez de cargar una
 * demo incompleta.
 */
public final class GeneradorDemo {

    public static final long SEMILLA = 20261103L;
    public static final int DIAS = 60;
    static final ZoneId LIMA = FechaNegocio.ZONA_LIMA;

    public record Parametros(LocalDate hoy, BigDecimal igv, int umbralStockBajo, BigDecimal descuadreLeve,
                             String passwordHash) {
    }

    private GeneradorDemo() {
    }

    public static DatosDemo generar(Parametros p) {
        DatosDemo datos = new DatosDemo();
        int indice = 0;
        for (BoticaSpec spec : CatalogoDemo.BOTICAS) {
            new Simulacion(spec, indice++, p, datos).ejecutar();
        }
        return datos;
    }

    private enum Rol { NORMAL, PRECIO, DESACTIVAR, LENTO, AGOTAR, CRITICO }

    private record Evento(OffsetDateTime t, long orden, Runnable accion) {
    }

    private static final class Simulacion {

        private static final int DIA_CAMBIO_PRECIO = 30;
        private static final int DIA_DESACTIVACION = 40;
        private static final int DIA_SIN_REPOSICION = 40;
        private static final int DIA_MERMA_VENCIDO = 31;
        private static final int LOTES_CALENDARIO = 8;
        private static final double PROPORCION_VENTAS_CON_RECETA = 0.135;

        private final BoticaSpec spec;
        private final int indice;
        private final Parametros p;
        private final DatosDemo d;
        private final SplittableRandom rnd;
        private final OffsetDateTime inicioHoy;

        private final PriorityQueue<Evento> eventos = new PriorityQueue<>(
                Comparator.comparing(Evento::t).thenComparingLong(Evento::orden));
        private long ordenEvento;
        private int ordenLote;

        private Botica botica;
        private Usuario admin;
        private final List<Usuario> tecnicos = new ArrayList<>();
        private final Map<Usuario, UsuarioSpec> specDe = new HashMap<>();
        private final List<Producto> productos = new ArrayList<>();
        private final Map<Producto, Rol> rolDe = new HashMap<>();
        private final Map<Producto, Double> pesoDe = new HashMap<>();
        private final Map<Producto, int[]> consumoDe = new HashMap<>();
        private final Map<Producto, Integer> pisoDe = new HashMap<>();
        private final Set<String> codigosBarras = new HashSet<>();
        private final EnumMap<Rol, List<Producto>> porRol = new EnumMap<>(Rol.class);
        private Lote loteVencidoConStock;
        private Lote loteVencidoAMermar;
        private int graves;
        private double creditoReceta;

        Simulacion(BoticaSpec spec, int indice, Parametros p, DatosDemo d) {
            this.spec = spec;
            this.indice = indice;
            this.p = p;
            this.d = d;
            this.rnd = new SplittableRandom(SEMILLA + 7919L * indice);
            this.inicioHoy = p.hoy().atStartOfDay(LIMA).toOffsetDateTime();
            this.creditoReceta = rnd.nextDouble();
            for (Rol r : Rol.values()) {
                porRol.put(r, new ArrayList<>());
            }
        }

        // ---------- tiempo ----------

        private LocalDate fecha(int dia) {
            return p.hoy().minusDays(DIAS - dia);
        }

        private int diaDe(LocalDate fecha) {
            return DIAS - (int) ChronoUnit.DAYS.between(fecha, p.hoy());
        }

        private OffsetDateTime en(LocalDate fecha, int hora, int minuto) {
            return fecha.atTime(LocalTime.of(hora, minuto)).atZone(LIMA).toOffsetDateTime();
        }

        /** Desfase aleatorio con segundos y milisegundos: dos eventos no caen en el mismo instante. */
        private OffsetDateTime mas(OffsetDateTime t, int segundosMin, int segundosMax) {
            return t.plusSeconds(segundosMin + rnd.nextInt(segundosMax - segundosMin + 1)).plusNanos(rnd.nextInt(1000) * 1_000_000L);
        }

        private LocalDate fechaLima(OffsetDateTime t) {
            return t.atZoneSameInstant(LIMA).toLocalDate();
        }

        private void programar(OffsetDateTime t, Runnable accion) {
            eventos.add(new Evento(t, ordenEvento++, accion));
        }

        // ---------- dinero (sin redondeo propio: céntimos enteros) ----------

        private static BigDecimal soles(long centimos) {
            return BigDecimal.valueOf(centimos, 2);
        }

        private static long aDecimos(double centimos) {
            return Math.max(10, Math.round(centimos / 10.0) * 10);
        }

        private static long centimos(BigDecimal soles) {
            return soles.movePointRight(2).longValueExact();
        }

        // ---------- ejecución ----------

        void ejecutar() {
            crearBoticaYUsuarios();
            crearCatalogo();
            crearLotesIniciales();
            programarHistoria();
            while (!eventos.isEmpty()) {
                eventos.poll().accion().run();
            }
            verificarEscenario();
        }

        private void crearBoticaYUsuarios() {
            botica = new Botica();
            botica.nombre = spec.nombre();
            botica.direccion = spec.direccion();
            botica.creadoEn = mas(en(fecha(0).minusDays(20), 9, 0), 0, 600);
            d.boticas.add(botica);

            for (UsuarioSpec u : spec.usuarios()) {
                Usuario usuario = new Usuario();
                usuario.botica = botica;
                usuario.rol = u.rol();
                usuario.nombre = u.nombre();
                usuario.usuario = u.usuario();
                usuario.passwordHash = p.passwordHash();
                usuario.activo = u.activo();
                if (CatalogoDemo.ADMINISTRADOR.equals(u.rol())) {
                    usuario.creadoEn = mas(en(fecha(0).minusDays(15), 10, 0), 0, 600);
                    admin = usuario;
                } else {
                    LocalDate alta = u.desdeDia() > 0 ? fecha(u.desdeDia() - 2) : fecha(0).minusDays(14);
                    usuario.creadoEn = mas(en(alta, 10, 30), 0, 1800);
                    usuario.creadoPor = admin;
                    tecnicos.add(usuario);
                }
                specDe.put(usuario, u);
                d.usuarios.add(usuario);
            }
        }

        private void crearCatalogo() {
            List<ProductoSpec> surtido = new ArrayList<>(CatalogoDemo.SURTIDO);
            barajar(surtido);
            int cantidad = 46 + rnd.nextInt(11);
            List<ProductoSpec> elegidos = surtido.subList(0, cantidad);

            OffsetDateTime alta = en(fecha(0).minusDays(10), 11, 0);
            for (int i = 0; i < elegidos.size(); i++) {
                ProductoSpec s = elegidos.get(i);
                Producto pr = new Producto();
                pr.botica = botica;
                pr.nombre = s.nombre();
                pr.laboratorio = CatalogoDemo.LABORATORIOS.get(rnd.nextInt(CatalogoDemo.LABORATORIOS.size()));
                pr.categoria = s.categoria();
                pr.unidadNombre = s.unidad();
                pr.requiereReceta = s.receta();
                pr.granel = s.granel();
                pr.codigoBarras = Ean13.generar(rnd, codigosBarras);
                pr.creadoEn = mas(alta.plusMinutes(i), 0, 50);
                pr.creadoPor = admin;

                long unidad = aDecimos(s.precioUnidadCentimos() * spec.factorPrecioPorMil() / 1000.0 * (0.9 + rnd.nextDouble() * 0.2));
                agregarPresentacion(pr, "Unidad", 1, unidad);
                if (!s.presentaciones().isEmpty()) {
                    for (String token : s.presentaciones().split(",")) {
                        int factor = Integer.parseInt(token.substring(1));
                        boolean blister = token.charAt(0) == 'B';
                        double descuento = blister ? (factor <= 3 ? 0.97 : 0.95) : 0.85;
                        agregarPresentacion(pr, blister ? "Blíster" : "Caja", factor, aDecimos(unidad * factor * descuento));
                    }
                }
                productos.add(pr);
                d.productos.add(pr);
                consumoDe.put(pr, new int[DIAS]);
            }

            // Roles: el orden de los productos ya es aleatorio; se reparte sobre otro barajado.
            // AGOTAR y CRITICO salen de los productos sin receta: tienen que venderse mucho para
            // llegar a su estado, y los de receta solo se venden en el ~13,5 % de las ventas.
            List<Producto> orden = new ArrayList<>(productos);
            barajar(orden);
            List<Producto> escasos = new ArrayList<>();
            for (Producto pr : orden) {
                if (!pr.requiereReceta && escasos.size() < 5) {
                    escasos.add(pr);
                }
            }
            orden.removeAll(escasos);
            orden.addAll(0, escasos);
            int k = 0;
            k = asignar(orden, k, 2, Rol.AGOTAR);
            k = asignar(orden, k, 3, Rol.CRITICO);
            k = asignar(orden, k, LOTES_CALENDARIO + 2, Rol.LENTO);
            k = asignar(orden, k, 1, Rol.DESACTIVAR);
            k = asignar(orden, k, 4, Rol.PRECIO);
            asignar(orden, k, orden.size() - k, Rol.NORMAL);
            int[] pisos = {4, 7, 11};
            for (int i = 0; i < porRol.get(Rol.CRITICO).size(); i++) {
                pisoDe.put(porRol.get(Rol.CRITICO).get(i), pisos[i]);
            }
        }

        private int asignar(List<Producto> orden, int desde, int cuantos, Rol rol) {
            for (int i = desde; i < desde + cuantos; i++) {
                Producto pr = orden.get(i);
                rolDe.put(pr, rol);
                porRol.get(rol).add(pr);
                double peso = switch (rol) {
                    case LENTO -> 0.35;
                    case AGOTAR, CRITICO -> 4.5;
                    default -> 1.0 + rnd.nextDouble() * 3.0;
                };
                pesoDe.put(pr, peso);
            }
            return desde + cuantos;
        }

        private void agregarPresentacion(Producto pr, String etiqueta, int factor, long centimos) {
            Presentacion pres = new Presentacion();
            pres.producto = pr;
            pres.etiqueta = etiqueta;
            pres.factor = factor;
            pres.precio = soles(centimos);
            pres.creadoEn = pr.creadoEn;
            pr.presentaciones.add(pres);
            d.presentaciones.add(pres);
        }

        private Presentacion unidad(Producto pr) {
            return pr.presentaciones.get(0);
        }

        /** Unidades base por día que se espera vender del producto (para dimensionar lotes). */
        private double demandaEstimada(Producto pr) {
            double pesoTotal = pesoDe.values().stream().mapToDouble(Double::doubleValue).sum();
            int cajasPorDia = spec.veinticuatroHoras() ? 3 : 2;
            double itemsPorDia = cajasPorDia * 20 * 1.6;
            double unidadesPorItem = pr.granel ? 7.0 : 1.3;
            return itemsPorDia * pesoDe.get(pr) / pesoTotal * unidadesPorItem;
        }

        private void crearLotesIniciales() {
            OffsetDateTime t = en(fecha(0), 6, 0);
            List<Producto> lentos = porRol.get(Rol.LENTO);
            for (Producto pr : productos) {
                Rol rol = rolDe.get(pr);
                t = mas(t, 15, 40);
                if (rol == Rol.LENTO) {
                    int i = lentos.indexOf(pr);
                    if (i < LOTES_CALENDARIO) {
                        // Vencimientos cada 10 días entre hoy+5 y hoy+155: cualquier día de los próximos 60
                        // tiene lotes CRITICO (0-30) y ADVERTENCIA (31-90).
                        crearLote(pr, t, p.hoy().plusDays(5 + 10L * i), 170);
                        crearLote(pr, mas(t, 1, 5), p.hoy().plusDays(85 + 10L * i), 60);
                    } else if (i == LOTES_CALENDARIO) {
                        loteVencidoConStock = crearLote(pr, t, p.hoy().minusDays(20), 130);
                        crearLote(pr, mas(t, 1, 5), p.hoy().plusDays(200 + rnd.nextInt(300)), 100);
                    } else {
                        loteVencidoAMermar = crearLote(pr, t, fecha(DIA_MERMA_VENCIDO - 1), 110);
                        crearLote(pr, mas(t, 1, 5), p.hoy().plusDays(200 + rnd.nextInt(300)), 100);
                    }
                    continue;
                }
                int dias = (rol == Rol.AGOTAR || rol == Rol.CRITICO) ? 15 : 25;
                int total = redondearA10(Math.max(40, demandaEstimada(pr) * dias));
                if (rnd.nextDouble() < 0.4) {
                    int primero = redondearA10(total * 0.4);
                    crearLote(pr, t, fecha(0).plusDays(150 + rnd.nextInt(150)), primero);
                    crearLote(pr, mas(t, 1, 5), fecha(0).plusDays(320 + rnd.nextInt(400)), Math.max(10, total - primero));
                } else {
                    crearLote(pr, t, fecha(0).plusDays(180 + rnd.nextInt(540)), total);
                }
            }
        }

        private static int redondearA10(double v) {
            return (int) Math.max(10, Math.ceil(v / 10.0) * 10);
        }

        private Lote crearLote(Producto pr, OffsetDateTime t, LocalDate vencimiento, int stock) {
            Lote l = new Lote();
            l.orden = ordenLote++;
            l.producto = pr;
            l.vencimiento = vencimiento;
            l.stock = stock;
            l.codigo = String.format("%s%s-%02d", pr.laboratorio.substring(0, 2).toUpperCase(),
                    vencimiento.format(DateTimeFormatter.ofPattern("yyMM")), pr.lotes.size() + 1);
            l.ubicacion = "E" + (1 + rnd.nextInt(6)) + "-" + (char) ('A' + rnd.nextInt(4));
            long costo = Math.max(1, Math.round(centimos(unidad(pr).precio) * (0.55 + rnd.nextDouble() * 0.17)));
            l.costo = soles(costo);
            l.creadoEn = t;
            l.creadoPor = admin;
            pr.lotes.add(l);
            d.lotes.add(l);

            MovStock ingreso = new MovStock();
            ingreso.lote = l;
            ingreso.tipo = "INGRESO";
            ingreso.cantidad = stock;
            ingreso.creadoEn = t;
            ingreso.creadoPor = admin;
            d.movimientosStock.add(ingreso);
            return l;
        }

        // ---------- programación ----------

        private Usuario responsable(String turno, int dia) {
            if ("Mañana".equals(turno) && fecha(dia).getDayOfWeek() == DayOfWeek.SUNDAY) {
                return admin; // el administrador cubre la Mañana del domingo
            }
            for (Usuario u : tecnicos) {
                UsuarioSpec s = specDe.get(u);
                if (s.turno().equals(turno) && s.desdeDia() <= dia && dia <= s.hastaDia()) {
                    return u;
                }
            }
            throw new IllegalStateException("Sin responsable para " + turno + " del día " + dia + " en " + spec.nombre());
        }

        private void programarHistoria() {
            List<String> turnos = spec.veinticuatroHoras() ? List.of("Mañana", "Tarde", "Noche") : List.of("Mañana", "Tarde");
            for (int dia = 0; dia < DIAS; dia++) {
                final int diaFinal = dia;
                LocalDate f = fecha(dia);
                if (dia >= 1) {
                    OffsetDateTime t = mas(en(f, 6, 30), 0, 900);
                    programar(t, () -> reponer(diaFinal, t));
                }
                if (dia == DIA_CAMBIO_PRECIO) {
                    programar(en(f, 5, 0), this::cambiarPrecios);
                }
                if (dia == DIA_DESACTIVACION) {
                    programar(en(f, 5, 0), () -> porRol.get(Rol.DESACTIVAR).forEach(pr -> pr.activo = false));
                }
                for (String turno : turnos) {
                    programarCaja(dia, turno);
                }
            }
        }

        private void programarCaja(int dia, String turno) {
            LocalDate f = fecha(dia);
            OffsetDateTime inicio = switch (turno) {
                case "Mañana" -> en(f, 7, 0);
                case "Tarde" -> en(f, 14, 0);
                default -> en(f, 22, 0);
            };
            OffsetDateTime fin = switch (turno) {
                case "Mañana" -> en(f, 14, 0);
                case "Tarde" -> en(f, 22, 0);
                default -> en(f.plusDays(1), 7, 0);
            };
            OffsetDateTime apertura = mas(inicio, 0, 540);
            OffsetDateTime cierre = mas(fin, -720, 300);
            if (!cierre.isBefore(inicioHoy)) {
                return; // la historia termina antes de hoy: la Noche de ayer a hoy no se genera
            }
            boolean quedaAbierta = spec.cajaDeAyerSinCerrar() && dia == DIAS - 1 && "Tarde".equals(turno);
            boolean grave = (dia == 17 && "Tarde".equals(turno)) || (dia == 43 && "Mañana".equals(turno));

            Caja caja = new Caja();
            caja.botica = botica;
            caja.usuario = responsable(turno, dia);
            caja.turno = turno;
            caja.horaApertura = apertura;
            caja.fecha = fechaLima(apertura); // como CajaService.abrir: la fecha de Lima al abrir
            long[] montos = {10000, 15000, 20000};
            caja.apertura = soles(montos[rnd.nextInt(montos.length)]);
            programar(apertura, () -> d.cajas.add(caja));

            OffsetDateTime desde = apertura.plusMinutes(4);
            long ventanaSeg = ChronoUnit.SECONDS.between(desde, cierre.minusMinutes(4));
            int ventas = "Noche".equals(turno) ? 10 + rnd.nextInt(7) : 10 + rnd.nextInt(21);
            for (int i = 0; i < ventas; i++) {
                OffsetDateTime t = mas(desde, 0, (int) ventanaSeg);
                programar(t, () -> vender(caja, t));
            }
            if ("Tarde".equals(turno) && dia % 7 == (indice + 2) % 7) {
                int n = 1 + rnd.nextInt(2);
                for (int i = 0; i < n; i++) {
                    OffsetDateTime t = mas(desde, 0, (int) ventanaSeg);
                    programar(t, () -> egreso(caja, t));
                }
            }
            if ("Mañana".equals(turno) && dia % 7 == (indice + 4) % 7) {
                int n = 1 + rnd.nextInt(2);
                for (int i = 0; i < n; i++) {
                    OffsetDateTime t = mas(desde, 0, (int) ventanaSeg);
                    programar(t, () -> mermaSemanal(caja, t));
                }
            }
            if ("Mañana".equals(turno) && dia == DIA_MERMA_VENCIDO) {
                OffsetDateTime t = mas(apertura, 1800, 2400);
                programar(t, () -> mermaVencido(caja, t));
            }
            if (!quedaAbierta) {
                programar(cierre, () -> cerrar(caja, cierre, grave));
            }
        }

        // ---------- operaciones ----------

        private int vendible(Producto pr, LocalDate fecha) {
            int total = 0;
            for (Lote l : pr.lotes) {
                if (l.stock > 0 && !l.vencimiento.isBefore(fecha)) {
                    total += l.stock;
                }
            }
            return total;
        }

        private List<Lote> lotesFefo(Producto pr, LocalDate fecha) {
            List<Lote> lotes = new ArrayList<>();
            for (Lote l : pr.lotes) {
                if (l.stock > 0 && !l.vencimiento.isBefore(fecha)) {
                    lotes.add(l);
                }
            }
            lotes.sort(Comparator.comparing((Lote l) -> l.vencimiento).thenComparingInt(l -> l.orden));
            return lotes;
        }

        private boolean sePuedeVender(Producto pr, int vendible) {
            if (!pr.activo || vendible <= 0) {
                return false;
            }
            return rolDe.get(pr) != Rol.CRITICO || vendible > pisoDe.get(pr);
        }

        private void vender(Caja caja, OffsetDateTime t) {
            LocalDate f = fechaLima(t);
            int dia = diaDe(f);
            double r = rnd.nextDouble();
            int items = r < 0.55 ? 1 : r < 0.85 ? 2 : 3;
            // Cada venta decide antes si es con receta, con un acumulador (no al azar): la proporción
            // queda en ~13,5 % por botica sin depender del surtido ni de la fecha (objetivo 12-15 %).
            creditoReceta += PROPORCION_VENTAS_CON_RECETA;
            boolean conReceta = creditoReceta >= 1.0;
            if (conReceta) {
                creditoReceta -= 1.0;
            }

            Venta venta = new Venta();
            venta.caja = caja;
            venta.fecha = t;
            List<Detalle> detalles = new ArrayList<>();
            List<MovStock> movimientos = new ArrayList<>();
            List<Notificacion> avisos = new ArrayList<>();
            Set<Producto> elegidos = new HashSet<>();
            BigDecimal totalVenta = BigDecimal.ZERO;

            for (int i = 0; i < items; i++) {
                // Solo la primera línea de una venta con receta es de receta; el resto, venta libre.
                Producto pr = elegirProducto(f, elegidos, conReceta && i == 0);
                if (pr == null && conReceta && i == 0) {
                    pr = elegirProducto(f, elegidos, false); // sin stock de receta: venta libre
                }
                if (pr == null) {
                    break;
                }
                elegidos.add(pr);
                int disponible = vendible(pr, f);
                int limite = rolDe.get(pr) == Rol.CRITICO ? disponible - pisoDe.get(pr) : disponible;

                Presentacion pres = unidad(pr);
                int cantidad = pr.granel ? 1 + rnd.nextInt(10) : (rnd.nextDouble() < 0.15 ? 2 : 1);
                double rp = rnd.nextDouble();
                for (Presentacion otra : pr.presentaciones) {
                    if (otra.factor >= 20 && rp < 0.02 && limite >= 3 * otra.factor) {
                        pres = otra;
                        cantidad = 1;
                    } else if (otra.factor > 1 && otra.factor < 20 && rp >= 0.02 && rp < 0.22 && limite >= 2 * otra.factor) {
                        pres = otra;
                        cantidad = rnd.nextDouble() < 0.1 ? 2 : 1;
                    }
                }
                if (cantidad * pres.factor > limite) {
                    pres = unidad(pr);
                    cantidad = Math.min(cantidad, limite);
                }
                if (cantidad <= 0) {
                    continue;
                }

                ReglasVenta.Linea linea = ReglasVenta.calcularLinea(pres.precio, cantidad, pres.factor);
                int restante = linea.cantidadBase();
                List<Lote> usados = new ArrayList<>();
                List<Integer> consumidos = new ArrayList<>();
                for (Lote l : lotesFefo(pr, f)) {
                    if (restante <= 0) {
                        break;
                    }
                    int consumir = Math.min(restante, l.stock);
                    usados.add(l);
                    consumidos.add(consumir);
                    restante -= consumir;
                }
                List<BigDecimal> totales = ReglasVenta.repartirTotal(linea.totalLinea(), consumidos, linea.cantidadBase());
                String origen = origen();
                for (int j = 0; j < usados.size(); j++) {
                    Lote l = usados.get(j);
                    Detalle det = new Detalle();
                    det.venta = venta;
                    det.producto = pr;
                    det.presentacion = pres;
                    det.lote = l;
                    det.nombreProducto = pr.nombre;
                    det.etiqueta = pres.etiqueta;
                    det.cantidad = consumidos.get(j);
                    det.precioUnitario = linea.precioUnitarioBase();
                    det.costoUnitario = l.costo;
                    det.totalLinea = totales.get(j);
                    det.origen = origen;
                    det.requiereReceta = pr.requiereReceta;
                    detalles.add(det);
                    l.stock -= det.cantidad;

                    MovStock m = new MovStock();
                    m.lote = l;
                    m.tipo = "VENTA";
                    m.cantidad = -det.cantidad;
                    m.origen = origen;
                    m.venta = venta;
                    m.creadoEn = t;
                    m.creadoPor = caja.usuario;
                    movimientos.add(m);
                }
                consumoDe.get(pr)[dia] += linea.cantidadBase();

                int stockRestante = disponible - linea.cantidadBase();
                if (ReglasVenta.dejaStockCritico(stockRestante, p.umbralStockBajo())) {
                    Notificacion n = new Notificacion();
                    n.botica = botica;
                    n.tipo = "STOCK_CRITICO";
                    n.rolDestinatario = null;
                    n.nombreProducto = pr.nombre;
                    n.stockRestante = stockRestante;
                    n.creadoEn = t;
                    n.leido = f.isBefore(p.hoy().minusDays(1));
                    avisos.add(n);
                }
                totalVenta = totalVenta.add(linea.totalLinea());
            }
            if (detalles.isEmpty()) {
                return;
            }

            BigDecimal total = Dinero.redondear(totalVenta);
            Dinero.DesgloseIgv desglose = Dinero.extraerIgv(total, p.igv());
            double rm = rnd.nextDouble();
            venta.metodoPago = rm < 0.6 ? "efectivo" : rm < 0.85 ? "yape" : "tarjeta";
            venta.total = total;
            venta.subtotal = desglose.subtotal();
            venta.igv = desglose.igv();
            venta.igvTasa = p.igv();
            venta.clave = uuid();
            d.ventas.add(venta);
            d.detalles.addAll(detalles);
            d.movimientosStock.addAll(movimientos);

            if (detalles.stream().anyMatch(x -> x.requiereReceta)) {
                Receta receta = new Receta();
                receta.venta = venta;
                receta.numero = "RX-" + (100000 + rnd.nextInt(900000));
                receta.cmp = String.valueOf(10000 + rnd.nextInt(65000));
                receta.medico = rnd.nextDouble() < 0.7 ? CatalogoDemo.MEDICOS.get(rnd.nextInt(CatalogoDemo.MEDICOS.size())) : null;
                receta.fechaEmision = rnd.nextDouble() < 0.7 ? f.minusDays(rnd.nextInt(7)) : null;
                d.recetas.add(receta);
            }

            MovCaja mc = new MovCaja();
            mc.caja = caja;
            mc.tipo = "venta";
            mc.venta = venta;
            mc.monto = total;
            mc.afectaEfectivo = "efectivo".equals(venta.metodoPago);
            mc.creadoEn = t;
            mc.creadoPor = caja.usuario;
            d.movimientosCaja.add(mc);
            if (mc.afectaEfectivo) {
                caja.efectivo = caja.efectivo.add(total);
            }
            d.notificaciones.addAll(avisos);
        }

        private Producto elegirProducto(LocalDate f, Set<Producto> excluidos, boolean deReceta) {
            double suma = 0;
            List<Producto> candidatos = new ArrayList<>();
            for (Producto pr : productos) {
                if (pr.requiereReceta == deReceta && !excluidos.contains(pr) && sePuedeVender(pr, vendible(pr, f))) {
                    candidatos.add(pr);
                    suma += pesoDe.get(pr);
                }
            }
            if (candidatos.isEmpty()) {
                return null;
            }
            double x = rnd.nextDouble() * suma;
            for (Producto pr : candidatos) {
                x -= pesoDe.get(pr);
                if (x <= 0) {
                    return pr;
                }
            }
            return candidatos.get(candidatos.size() - 1);
        }

        private String origen() {
            double r = rnd.nextDouble();
            return r < 0.7 ? "ESCANEO" : r < 0.95 ? "BUSQUEDA" : "MANUAL";
        }

        private UUID uuid() {
            long alto = (rnd.nextLong() & 0xffffffffffff0fffL) | 0x0000000000004000L;
            long bajo = (rnd.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
            return new UUID(alto, bajo);
        }

        private void egreso(Caja caja, OffsetDateTime t) {
            List<String> motivos = ConfigService.MOTIVOS_EGRESO;
            MovCaja mc = new MovCaja();
            mc.caja = caja;
            mc.tipo = "egreso";
            mc.descripcion = motivos.get(rnd.nextInt(motivos.size()));
            mc.nota = CatalogoDemo.NOTAS_EGRESO.get(rnd.nextInt(CatalogoDemo.NOTAS_EGRESO.size()));
            mc.monto = Dinero.redondear(soles(1500 + 50L * rnd.nextInt(211))).negate();
            mc.afectaEfectivo = true;
            mc.creadoEn = t;
            mc.creadoPor = caja.usuario;
            d.movimientosCaja.add(mc);
            caja.efectivo = caja.efectivo.add(mc.monto);
        }

        private void mermaSemanal(Caja caja, OffsetDateTime t) {
            LocalDate f = fechaLima(t);
            List<Lote> candidatos = new ArrayList<>();
            for (Producto pr : productos) {
                Rol rol = rolDe.get(pr);
                if (pr.activo && (rol == Rol.NORMAL || rol == Rol.PRECIO)) {
                    for (Lote l : pr.lotes) {
                        if (l.stock >= 10 && !l.vencimiento.isBefore(f)) {
                            candidatos.add(l);
                        }
                    }
                }
            }
            if (candidatos.isEmpty()) {
                return;
            }
            Lote l = candidatos.get(rnd.nextInt(candidatos.size()));
            double r = rnd.nextDouble();
            String motivo = r < 0.4 ? "Rotura" : r < 0.75 ? "Deterioro" : r < 0.9 ? "Robo o pérdida" : "Otro";
            String obs = CatalogoDemo.OBSERVACIONES_MERMA.get(rnd.nextInt(CatalogoDemo.OBSERVACIONES_MERMA.size()));
            registrarMerma(caja.usuario, l, 1 + rnd.nextInt(3), motivo, obs, t);
        }

        private void mermaVencido(Caja caja, OffsetDateTime t) {
            if (loteVencidoAMermar.stock > 0) {
                registrarMerma(caja.usuario, loteVencidoAMermar, loteVencidoAMermar.stock, "Vencimiento", null, t);
            }
        }

        private void registrarMerma(Usuario usuario, Lote l, int cantidad, String motivo, String obs, OffsetDateTime t) {
            Merma m = new Merma();
            m.lote = l;
            m.usuario = usuario;
            m.cantidad = cantidad;
            m.motivo = motivo;
            m.observacion = obs;
            // Como MermaService: a precio de la presentación Unidad vigente.
            m.valorVenta = Dinero.redondear(unidad(l.producto).precio.multiply(BigDecimal.valueOf(cantidad)));
            m.fecha = t;
            d.mermas.add(m);
            l.stock -= cantidad;

            MovStock ms = new MovStock();
            ms.lote = l;
            ms.tipo = "MERMA";
            ms.cantidad = -cantidad;
            ms.merma = m;
            ms.creadoEn = t;
            ms.creadoPor = usuario;
            d.movimientosStock.add(ms);
        }

        private void cerrar(Caja caja, OffsetDateTime t, boolean grave) {
            BigDecimal esperado = ReglasCaja.calcularCierre(caja.apertura, caja.efectivo, BigDecimal.ZERO, p.descuadreLeve()).esperado();
            long diferencia;
            String esperadoSemaforo;
            String observaciones = null;
            if (grave) {
                diferencia = -(3000 + 50L * rnd.nextInt(81));
                esperadoSemaforo = "GRAVE";
                observaciones = "Faltante al cierre; se revisará con la administración.";
            } else if (rnd.nextDouble() < 0.85) {
                diferencia = 0;
                esperadoSemaforo = "EXACTO";
            } else {
                long limite = centimos(p.descuadreLeve());
                diferencia = (10 + 10L * rnd.nextInt((int) (limite / 10))) * (rnd.nextBoolean() ? 1 : -1);
                esperadoSemaforo = "LEVE";
                observaciones = rnd.nextBoolean() ? "Diferencia por vuelto." : null;
            }
            ReglasCaja.Cierre cierre = ReglasCaja.calcularCierre(caja.apertura, caja.efectivo,
                    esperado.add(soles(diferencia)), p.descuadreLeve());
            if (!esperadoSemaforo.equals(cierre.semaforo())) {
                throw new IllegalStateException("Semáforo inesperado en " + spec.nombre() + ": " + cierre.semaforo());
            }
            caja.esperado = cierre.esperado();
            caja.contado = cierre.contado();
            caja.diferencia = cierre.diferencia();
            caja.semaforo = cierre.semaforo();
            caja.observaciones = observaciones;
            caja.horaCierre = t;
            caja.abierta = false;

            if ("GRAVE".equals(cierre.semaforo())) {
                graves++;
                Notificacion n = new Notificacion();
                n.botica = botica;
                n.tipo = "DESCUADRE_GRAVE";
                n.rolDestinatario = "ADMINISTRADOR";
                n.caja = caja;
                n.creadoEn = t;
                n.leido = fechaLima(t).isBefore(p.hoy().minusDays(1));
                d.notificaciones.add(n);
            }
        }

        /**
         * Los lotes repuestos se fechan desde el instante del evento, nunca antes: si no,
         * una venta procesada antes (p. ej. de la Noche a las 06:33) no habría visto un lote
         * fechado a las 06:33 y FEFO no cuadraría (lo detectó el invariante 4).
         */
        private void reponer(int dia, OffsetDateTime desde) {
            LocalDate f = fecha(dia);
            OffsetDateTime t = desde;
            for (Producto pr : productos) {
                Rol rol = rolDe.get(pr);
                boolean escaso = rol == Rol.AGOTAR || rol == Rol.CRITICO;
                boolean repone = switch (rol) {
                    case NORMAL, PRECIO -> true;
                    case DESACTIVAR -> pr.activo;
                    case AGOTAR, CRITICO -> dia < DIA_SIN_REPOSICION;
                    case LENTO -> false;
                };
                if (!repone) {
                    continue;
                }
                double promedio = promedioDiario(pr, dia);
                int puntoPedido = (int) Math.ceil(Math.max(escaso ? 10 : 25, (escaso ? 4 : 6) * promedio));
                if (vendible(pr, f) < puntoPedido) {
                    int cantidad = redondearA10(Math.max(escaso ? 30 : 60, (escaso ? 8 : 25) * promedio));
                    t = mas(t, 20, 90);
                    OffsetDateTime alta = t;
                    LocalDate vencimiento = f.plusDays(180 + rnd.nextInt(540));
                    // Cada lote entra al stock en su propio instante, igual que su INGRESO.
                    programar(alta, () -> crearLote(pr, alta, vencimiento, cantidad));
                }
            }
        }

        private double promedioDiario(Producto pr, int dia) {
            if (dia < 7) {
                return demandaEstimada(pr);
            }
            int[] c = consumoDe.get(pr);
            int suma = 0;
            for (int i = dia - 7; i < dia; i++) {
                suma += c[i];
            }
            return suma / 7.0;
        }

        private void cambiarPrecios() {
            for (Producto pr : porRol.get(Rol.PRECIO)) {
                for (Presentacion pres : pr.presentaciones) {
                    pres.precio = soles(aDecimos(centimos(pres.precio) * 1.10));
                }
            }
        }

        private <T> void barajar(List<T> lista) {
            for (int i = lista.size() - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                T x = lista.get(i);
                lista.set(i, lista.get(j));
                lista.set(j, x);
            }
        }

        // ---------- verificación del escenario ----------

        private void verificarEscenario() {
            List<String> fallas = new ArrayList<>();
            for (int t = 0; t <= DIAS; t++) {
                LocalDate dia = p.hoy().plusDays(t);
                boolean vencido = false, critico = false, advertencia = false, ok = false;
                for (Lote l : todosLosLotes()) {
                    if (l.stock <= 0) {
                        continue;
                    }
                    long dias = ChronoUnit.DAYS.between(dia, l.vencimiento);
                    if (dias < 0) vencido = true;
                    else if (dias <= 30) critico = true;
                    else if (dias <= 90) advertencia = true;
                    else ok = true;
                }
                if (!(vencido && critico && advertencia && ok)) {
                    fallas.add("hoy+" + t + " sin los cuatro estados de lote");
                }
            }
            if (loteVencidoConStock.stock <= 0) fallas.add("el lote vencido quedó sin stock");
            if (loteVencidoAMermar.stock != 0) fallas.add("el lote vencido a mermar conserva stock");
            for (Producto pr : porRol.get(Rol.AGOTAR)) {
                if (vendible(pr, p.hoy()) != 0) fallas.add(pr.nombre + " no quedó AGOTADO");
            }
            for (Producto pr : porRol.get(Rol.CRITICO)) {
                int v = vendible(pr, p.hoy());
                if (v <= 0 || v > p.umbralStockBajo()) fallas.add(pr.nombre + " no quedó en stock CRITICO (" + v + ")");
            }
            for (Producto pr : porRol.get(Rol.DESACTIVAR)) {
                if (d.detalles.stream().noneMatch(x -> x.producto == pr)) fallas.add(pr.nombre + " desactivado sin ventas");
            }
            if (graves != 2) fallas.add(graves + " cierres GRAVE en vez de 2");
            if (!fallas.isEmpty()) {
                throw new IllegalStateException("Datos demo de " + spec.nombre() + " (hoy " + p.hoy() + "): " + String.join("; ", fallas));
            }
        }

        private List<Lote> todosLosLotes() {
            List<Lote> todos = new ArrayList<>();
            productos.forEach(pr -> todos.addAll(pr.lotes));
            return todos;
        }
    }
}
