-- ============================================================
-- V2 (perfil prod) — arranque real, sin datos de demostración.
--
-- A diferencia de db/migration/dev/V2__seed.sql (30 productos, 90
-- lotes, ventas de ejemplo), esta migración NO crea ningún dato de
-- negocio de muestra: la persona que usa el sistema a diario carga
-- su propio catálogo desde cero. Los estados vacíos de cada pantalla
-- ya están construidos para esto (Tarea 9) — una pantalla de
-- Inventario sin lotes no es un error, es el punto de partida real.
--
-- Solo se crea lo mínimo indispensable para poder entrar al sistema
-- la primera vez: 1 botica y 1 usuario ADMINISTRADOR. Todos los
-- valores reales (nombre de la botica, nombre y usuario del admin,
-- hash BCrypt de su contraseña, turno) llegan por variable de
-- entorno vía placeholders de Flyway (spring.flyway.placeholders.*,
-- ver application-prod.properties) — ninguno vive en este archivo ni
-- en el repo. Una vez aplicada, esta migración no se vuelve a tocar
-- (docs/DESPLIEGUE.md, "La frontera"): cambios posteriores al admin o
-- a la botica son UPDATE de datos, no una edición de este archivo.
-- ============================================================

INSERT INTO boticas (nombre, activo) VALUES ('${boticaNombre}', true);

INSERT INTO roles (nombre) VALUES ('TECNICO'), ('ADMINISTRADOR');

INSERT INTO usuarios (botica_id, rol_id, nombre, usuario, password_hash, turno) VALUES
    ((SELECT id FROM boticas WHERE nombre = '${boticaNombre}'),
     (SELECT id FROM roles WHERE nombre = 'ADMINISTRADOR'),
     '${adminNombre}', '${adminUsuario}', '${adminPasswordHash}', '${adminTurno}');
