package com.botica.backend.demo;

import java.util.List;

/**
 * Datos fijos de las tres boticas de demostración (Tarea D, D-F-3): nombres,
 * direcciones, usuarios y el surtido maestro del que cada botica toma entre
 * 40 y 60 productos. Todo es ficticio, incluidos los laboratorios.
 */
final class CatalogoDemo {

    private CatalogoDemo() {
    }

    /** Un usuario demo. desdeDia/hastaDia: días de la historia (0..59) en que abre caja en su turno. */
    record UsuarioSpec(String nombre, String usuario, String rol, String turno, int desdeDia, int hastaDia, boolean activo) {
    }

    record BoticaSpec(String clave, String nombre, String direccion, boolean veinticuatroHoras,
                      boolean cajaDeAyerSinCerrar, int factorPrecioPorMil, List<UsuarioSpec> usuarios) {
    }

    static final String ADMINISTRADOR = "ADMINISTRADOR";
    static final String TECNICO = "TECNICO";

    /** Día en que el técnico desactivado dejó de trabajar (último día con caja). */
    static final int ULTIMO_DIA_DESACTIVADO_24H = 29;
    static final int ULTIMO_DIA_DESACTIVADO = 33;

    static final List<BoticaSpec> BOTICAS = List.of(
            new BoticaSpec("carmen", "Botica Virgen del Carmen", "Av. Las Palmeras 3120, Los Olivos, Lima",
                    true, false, 1000, List.of(
                    new UsuarioSpec("Patricia Rojas Valdivia", "demo_carmen_patricia", ADMINISTRADOR, "Mañana", -1, -1, true),
                    new UsuarioSpec("Luis Huamán Cárdenas", "demo_carmen_luis", TECNICO, "Mañana", 0, 59, true),
                    new UsuarioSpec("Rocío Paredes Lino", "demo_carmen_rocio", TECNICO, "Tarde", 0, 59, true),
                    new UsuarioSpec("Miguel Ángel Soto", "demo_carmen_miguel", TECNICO, "Noche", ULTIMO_DIA_DESACTIVADO_24H + 1, 59, true),
                    new UsuarioSpec("José Quiroz Benites", "demo_carmen_jose", TECNICO, "Noche", 0, ULTIMO_DIA_DESACTIVADO_24H, false))),
            new BoticaSpec("sanmartin", "Botica San Martín", "Jr. Huiracocha 1585, Jesús María, Lima",
                    false, true, 950, List.of(
                    new UsuarioSpec("Elena Castañeda Ruiz", "demo_sanmartin_elena", ADMINISTRADOR, "Mañana", -1, -1, true),
                    new UsuarioSpec("Diego Ramírez Tello", "demo_sanmartin_diego", TECNICO, "Mañana", 0, 59, true),
                    new UsuarioSpec("Karina Vílchez Ponce", "demo_sanmartin_karina", TECNICO, "Tarde", ULTIMO_DIA_DESACTIVADO + 1, 59, true),
                    new UsuarioSpec("Hugo Salazar Meza", "demo_sanmartin_hugo", TECNICO, "Tarde", 0, ULTIMO_DIA_DESACTIVADO, false))),
            new BoticaSpec("esperanza", "Botica La Esperanza", "Av. Los Próceres 742, Santiago de Surco, Lima",
                    false, false, 1080, List.of(
                    new UsuarioSpec("Víctor Manrique Ochoa", "demo_esperanza_victor", ADMINISTRADOR, "Mañana", -1, -1, true),
                    new UsuarioSpec("Sofía Delgado Ríos", "demo_esperanza_sofia", TECNICO, "Mañana", 0, 59, true),
                    new UsuarioSpec("Andrés Pinto Zevallos", "demo_esperanza_andres", TECNICO, "Tarde", ULTIMO_DIA_DESACTIVADO + 1, 59, true),
                    new UsuarioSpec("Lucía Carrasco Vela", "demo_esperanza_lucia", TECNICO, "Tarde", 0, ULTIMO_DIA_DESACTIVADO, false))));

    static final List<String> LABORATORIOS = List.of(
            "Andifarma", "Laboratorios Pacífico Sur", "Medisur", "Laboratorio Huascarán",
            "Biolatina Andina", "Farmacéutica Rímac", "Quimiandes", "Laboratorios Chavín");

    /**
     * Producto del surtido maestro. precioUnidadCentimos: precio de la unidad
     * base en la botica de referencia (factor 1000). presentaciones: "B10" =
     * Blíster de 10, "C100" = Caja de 100; la Unidad (factor 1) siempre existe.
     */
    record ProductoSpec(String nombre, String categoria, String unidad, boolean receta,
                        long precioUnidadCentimos, String presentaciones, boolean granel) {
    }

    private static ProductoSpec p(String nombre, String categoria, String unidad, boolean receta, long centimos, String pres) {
        return new ProductoSpec(nombre, categoria, unidad, receta, centimos, pres, "tableta".equals(unidad) || "cápsula".equals(unidad));
    }

    static final List<ProductoSpec> SURTIDO = List.of(
            p("Paracetamol 500 mg", "Analgésicos", "tableta", false, 30, "B10,C100"),
            p("Ibuprofeno 400 mg", "Analgésicos", "tableta", false, 50, "B10,C100"),
            p("Naproxeno 550 mg", "Analgésicos", "tableta", false, 90, "B10,C100"),
            p("Diclofenaco 50 mg", "Analgésicos", "tableta", false, 40, "B10,C100"),
            p("Metamizol 500 mg", "Analgésicos", "tableta", false, 50, "B10,C100"),
            p("Ketorolaco 10 mg", "Analgésicos", "tableta", false, 120, "B10"),
            p("Tramadol 50 mg", "Analgésicos", "cápsula", true, 150, "B10"),
            p("Paracetamol jarabe 120 mL", "Analgésicos", "frasco", false, 650, ""),
            p("Ibuprofeno suspensión 100 mL", "Analgésicos", "frasco", false, 900, ""),
            p("Amoxicilina 500 mg", "Antibióticos", "cápsula", true, 80, "B10,C100"),
            p("Azitromicina 500 mg", "Antibióticos", "tableta", true, 350, "B3"),
            p("Cefalexina 500 mg", "Antibióticos", "cápsula", true, 100, "B10,C100"),
            p("Ciprofloxacino 500 mg", "Antibióticos", "tableta", true, 120, "B10,C100"),
            p("Claritromicina 500 mg", "Antibióticos", "tableta", true, 300, "B10"),
            p("Amoxicilina + ácido clavulánico 875 mg", "Antibióticos", "tableta", true, 380, "B10"),
            p("Amoxicilina suspensión 250 mg/5 mL", "Antibióticos", "frasco", true, 1200, ""),
            p("Nitrofurantoína 100 mg", "Antibióticos", "cápsula", true, 90, "B10"),
            p("Loratadina 10 mg", "Antialérgicos", "tableta", false, 40, "B10,C100"),
            p("Cetirizina 10 mg", "Antialérgicos", "tableta", false, 50, "B10,C100"),
            p("Clorfenamina 4 mg", "Antialérgicos", "tableta", false, 20, "B10,C100"),
            p("Desloratadina 5 mg", "Antialérgicos", "tableta", false, 120, "B10"),
            p("Cetirizina jarabe 60 mL", "Antialérgicos", "frasco", false, 850, ""),
            p("Omeprazol 20 mg", "Gastrointestinal", "cápsula", false, 40, "B10,C100"),
            p("Pantoprazol 40 mg", "Gastrointestinal", "tableta", false, 120, "B10,C100"),
            p("Hioscina 10 mg", "Gastrointestinal", "tableta", false, 70, "B10,C100"),
            p("Loperamida 2 mg", "Gastrointestinal", "tableta", false, 50, "B10"),
            p("Sales de rehidratación oral", "Gastrointestinal", "sobre", false, 150, "C20"),
            p("Simeticona 80 mg", "Gastrointestinal", "tableta", false, 60, "B10"),
            p("Domperidona 10 mg", "Gastrointestinal", "tableta", false, 80, "B10"),
            p("Subsalicilato de bismuto 150 mL", "Gastrointestinal", "frasco", false, 1400, ""),
            p("Metoclopramida 10 mg", "Gastrointestinal", "tableta", true, 40, "B10"),
            p("Metformina 850 mg", "Crónicos", "tableta", true, 30, "B10,C100"),
            p("Losartán 50 mg", "Crónicos", "tableta", true, 40, "B10,C100"),
            p("Enalapril 10 mg", "Crónicos", "tableta", true, 30, "B10,C100"),
            p("Amlodipino 5 mg", "Crónicos", "tableta", true, 40, "B10,C100"),
            p("Atorvastatina 20 mg", "Crónicos", "tableta", true, 100, "B10,C100"),
            p("Glibenclamida 5 mg", "Crónicos", "tableta", true, 20, "B10,C100"),
            p("Levotiroxina 100 mcg", "Crónicos", "tableta", true, 60, "B10,C100"),
            p("Ácido acetilsalicílico 100 mg", "Crónicos", "tableta", false, 20, "B10,C100"),
            p("Clotrimazol crema 20 g", "Dermatológicos", "tubo", false, 550, ""),
            p("Betametasona crema 15 g", "Dermatológicos", "tubo", false, 700, ""),
            p("Ácido fusídico crema 15 g", "Dermatológicos", "tubo", true, 1800, ""),
            p("Aciclovir crema 5 g", "Dermatológicos", "tubo", false, 600, ""),
            p("Óxido de zinc pomada 60 g", "Dermatológicos", "tubo", false, 800, ""),
            p("Vitamina C 1 g efervescente", "Vitaminas", "tableta", false, 90, "B10"),
            p("Complejo B", "Vitaminas", "tableta", false, 40, "B10,C100"),
            p("Sulfato ferroso 300 mg", "Vitaminas", "tableta", false, 30, "B10,C100"),
            p("Ácido fólico 0,5 mg", "Vitaminas", "tableta", false, 20, "B10,C100"),
            p("Calcio + vitamina D", "Vitaminas", "tableta", false, 80, "B10"),
            p("Multivitamínico jarabe 240 mL", "Vitaminas", "frasco", false, 2200, ""),
            p("Ambroxol jarabe 120 mL", "Respiratorio", "frasco", false, 950, ""),
            p("Dextrometorfano jarabe 120 mL", "Respiratorio", "frasco", false, 1100, ""),
            p("Salbutamol inhalador 100 mcg", "Respiratorio", "inhalador", true, 1800, ""),
            p("Antigripal paracetamol + fenilefrina", "Respiratorio", "tableta", false, 80, "B10,C100"),
            p("Prednisona 20 mg", "Respiratorio", "tableta", true, 50, "B10"),
            p("Budesonida spray nasal", "Respiratorio", "frasco", true, 3500, ""),
            p("Alcohol 70° 120 mL", "Otros", "frasco", false, 450, ""),
            p("Gasa estéril 10 x 10 cm", "Otros", "sobre", false, 80, "C50"),
            p("Cloruro de sodio 0,9 % 1 L", "Otros", "frasco", false, 750, ""),
            p("Jeringa descartable 5 mL", "Otros", "jeringa", false, 50, "C100"),
            p("Agua oxigenada 120 mL", "Otros", "frasco", false, 300, ""),
            p("Banditas adhesivas", "Otros", "bandita", false, 20, "C100"));

    static final List<String> MEDICOS = List.of(
            "Dr. Raúl Espinoza", "Dra. Carmen Villanueva", "Dr. Jorge Alvarado", "Dra. Milagros Chávez",
            "Dr. Fernando Lazo", "Dra. Susana Ccori", "Dr. Martín Arévalo");

    static final List<String> NOTAS_EGRESO = List.of(
            "Pago parcial de pedido", "Recibo de luz del local", "Bolsas y papel para caja", "Útiles de limpieza",
            "Envío de encomienda", "Taxi para recoger pedido", "Agua de mesa para el local", "Reparación menor");

    static final List<String> OBSERVACIONES_MERMA = List.of(
            "Se cayó del estante al reponer", "Empaque dañado por humedad", "Faltante detectado en el conteo",
            "Frasco con el sello roto", "Producto aplastado en la recepción");
}
