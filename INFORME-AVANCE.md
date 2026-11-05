# Informe de avance — BoticaSys

Estado al 4 de noviembre de 2026 (commit `580aa5d`, entrega final).

Sistema de gestión para boticas independientes: ventas con FEFO y venta con
receta, inventario por lotes, merma, caja diaria con conteo ciego, Kardex,
reporte de resultados, reporte DIGEMID, alertas en tiempo real, venta sin
conexión, autenticación por roles y aislamiento total entre boticas. Backend
Spring Boot 4.1.1 (JDBC puro, sin ORM) + Angular 22 (PWA zoneless) +
PostgreSQL, desplegado en Render + Vercel + Neon.

- **Frontend en vivo:** https://boticas.vercel.app
- **Backend en vivo:** https://botica-vbzh.onrender.com/api (plan gratuito de
  Render: se duerme tras 15 min sin tráfico, la primera petición después de
  eso tarda alrededor de un minuto)
- **Versión desplegada:** https://botica-vbzh.onrender.com/api/config (campo
  `"version"` = commit que sirve Render)
- **Repositorio:** este mismo. Las rutas de código citadas abajo son relativas
  a la raíz.

## Resumen

|                |                                                                                                 |
| -------------- | ----------------------------------------------------------------------------------------------- |
| Requerimientos | **54: 52 implementados, 1 parcial (RF-42), 1 planificado (RF-52)**                              |
| Backend        | 14 controladores, 47 endpoints, 22 servicios, 12 DAO con su implementación JDBC y 18 RowMappers |
| Base de datos  | Migraciones Flyway `V1` a `V13` (la `V12` es una migración Java con tres boticas demo)          |
| Frontend       | 12 pantallas, 19 servicios, 3 interceptores, 2 guards, 18 componentes compartidos               |
| Pruebas        | 389 tests de backend (93 % de cobertura), 253 de frontend, 20 E2E, 220 peticiones de Postman    |

## Criterio 1 — API RESTful con Spring Boot

14 controladores en `backend/src/main/java/com/botica/backend/controller/`,
47 endpoints bajo `/api`. Capas estrictas: el controlador recibe, valida con
`@Valid` y delega; el `service/` tiene toda la regla de negocio y abre las
transacciones; `dao/` (interfaz) y `dao/impl/` (JDBC) son el único lugar que
sabe SQL.

| Controlador               | Endpoints                                                                                                                                                                                                                                                                                 |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AuthController`          | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/yo`                                                                                                                                                                                                                       |
| `ConfigController`        | `GET /api/config`                                                                                                                                                                                                                                                                         |
| `CajaController`          | `GET /api/caja/hoy`, `POST /api/caja/abrir`, `GET /api/caja/{id}/resumen-cierre`, `POST /api/caja/egresos`, `POST /api/caja/cerrar`, `GET /api/caja/{id}/movimientos`, `GET /api/caja/abiertas` (ADMINISTRADOR)                                                                           |
| `ProductoController`      | `GET /api/productos`, `GET /api/productos/codigo/{codigoBarras}`, `GET /api/productos/mas-vendidos`, `GET /api/productos/catalogo-offline`; catálogo (ADMINISTRADOR): `GET /api/productos/catalogo`, `POST /api/productos`, `PUT /api/productos/{id}`, `PATCH /api/productos/{id}/estado` |
| `LoteController`          | `GET /api/lotes` (paginado, filtros, orden con lista blanca), `POST /api/lotes`                                                                                                                                                                                                           |
| `VentaController`         | `POST /api/ventas` (idempotente, también sin conexión), `GET /api/ventas`, `GET /api/ventas/{id}`                                                                                                                                                                                         |
| `RevisionVentaController` | `GET /api/ventas/revisiones`, `POST /api/ventas/revisiones/{id}/resolver` (ADMINISTRADOR)                                                                                                                                                                                                 |
| `MermaController`         | `GET /api/mermas`, `POST /api/mermas`                                                                                                                                                                                                                                                     |
| `AlertaController`        | `GET /api/dashboard/resumen`, `GET /api/alertas/resumen`, `GET /api/alertas`                                                                                                                                                                                                              |
| `NotificacionController`  | `GET /api/notificaciones`, `PUT /api/notificaciones/{id}/leer`, `PUT /api/notificaciones/leer-todas`, `POST /api/notificaciones/ticket`, `GET /api/notificaciones/stream` (SSE)                                                                                                           |
| `UsuarioController`       | `GET /api/usuarios`, `POST /api/usuarios`, `PATCH /api/usuarios/{id}`, `POST /api/usuarios/{id}/restablecer-clave`, `POST /api/usuarios/{id}/desbloquear` (ADMINISTRADOR)                                                                                                                 |
| `KardexController`        | `GET /api/kardex`, `GET /api/kardex/xlsx` (ADMINISTRADOR)                                                                                                                                                                                                                                 |
| `ReporteController`       | `GET /api/reportes/resultados`, `GET /api/reportes/resultados/xlsx` (ADMINISTRADOR)                                                                                                                                                                                                       |
| `DigemidController`       | `GET /api/reportes/digemid`, `GET /api/reportes/digemid/xlsx` (ADMINISTRADOR)                                                                                                                                                                                                             |

**Formato de error único:** `GlobalExceptionHandler`
(`backend/src/main/java/com/botica/backend/config/GlobalExceptionHandler.java`)
convierte las 37 excepciones de negocio
(`backend/src/main/java/com/botica/backend/exception/`) y los errores de Spring
(ruta inexistente 404, método no permitido 405, parámetro mal tipado 400) en
`{timestamp, status, error, mensaje, path}`, con el código en mayúsculas
(`STOCK_INSUFICIENTE`, `RECETA_REQUERIDA`, `CAJA_YA_ABIERTA`…). Un error
inesperado se registra completo en el servidor y al cliente solo le llega
`ERROR_INTERNO`: nunca un stack trace. Los 401 y 403 de seguridad los arman
`JwtAuthenticationEntryPoint` y `JwtAccessDeniedHandler` con la misma forma.

**Validación en dos niveles:** Bean Validation en cada DTO de entrada
(`@NotNull`, `@Positive`, `@Pattern`, `@Size`) más la regla real en el Service,
que nunca confía en lo que el cliente ya validó (el stock se valida contra lo
leído con `FOR UPDATE`, no contra lo que dice la pantalla). El cliente nunca
manda montos: el precio, el total y el IGV los calcula el servidor.

## Criterio 2 — Backend con base de datos

**JDBC puro, cero ORM** (alternativa aprobada por el profesor):
`NamedParameterJdbcTemplate` en `backend/src/main/java/com/botica/backend/dao/impl/`,
SQL siempre parametrizado, un `RowMapper` nombrado por tabla y el `ORDER BY`
armado solo desde listas blancas de columnas.

**Migraciones Flyway** (`backend/src/main/resources/db/`), que corren solas al
arrancar el backend:

| Versión           | Qué hace                                                                                                     |
| ----------------- | ------------------------------------------------------------------------------------------------------------ |
| `V1`              | Esquema completo: tablas operativas con `CHECK`, `ON DELETE RESTRICT`, índices y comentarios por tabla       |
| `V2`, `V4` (prod) | La botica real y sus usuarios, con valores por variables de entorno                                          |
| `V4` (dev)        | Datos de desarrollo deterministas: dos boticas                                                               |
| `V3`              | `productos.unidad_nombre`                                                                                    |
| `V5`–`V7`         | Notificaciones (y su corrección sin editar la `V5`)                                                          |
| `V8`              | Índice único parcial: una sola caja abierta por usuario                                                      |
| `V9`              | Catálogo editable (productos y presentaciones activables) y bloqueo de login                                 |
| `V10`             | Venta con receta, entradas iniciales de los lotes existentes y `movimientos_stock` **inmutable** por trigger |
| `V11`             | Quita el `DEFAULT` temporal de `requiere_receta`                                                             |
| `V12`             | Migración Java: tres boticas demo con 60 días de historia                                                    |
| `V13`             | Venta sin conexión y tabla `revisiones_venta`                                                                |

**Multi-botica:** toda tabla operativa lleva `botica_id`, que sale siempre del
JWT y va en cada `WHERE`. Una fila de otra botica es indistinguible de una que
no existe (404). Hay tests de aislamiento por módulo
(`backend/src/test/java/com/botica/backend/dao/*AislamientoTest.java` y los de
cada reporte).

**Dinero** en `BigDecimal` / `NUMERIC(10,2)`, redondeo `HALF_UP` y el IGV
**extraído** del total (÷ 1.18), nunca sumado; casos compartidos entre backend
y frontend en `test-vectores/dinero.json`. **Fechas** en zona `America/Lima`,
fijada en la aplicación y en cada conexión JDBC.

**Concurrencia e integridad:** FEFO con `SELECT … FOR UPDATE` (dos cajeros no
venden la misma última unidad), venta idempotente por `claveIdempotencia`
única, bloqueo de login con un `UPDATE` atómico, eventos de notificación
después del commit (`@TransactionalEventListener(AFTER_COMMIT)`).

## Criterio 3 — Frontend con Angular

Angular 22 **zoneless** (sin `zone.js`): todo el estado con `signal`,
`computed` y `effect`. Pantallas en `frontend/src/app/features/`:

| Pantalla                                                                       | Rol                                      |
| ------------------------------------------------------------------------------ | ---------------------------------------- |
| Login                                                                          | Público                                  |
| Dashboard y alertas                                                            | Ambos (el técnico sin montos)            |
| Punto de venta (búsqueda, cámara, lector USB, presentaciones, carrito, receta) | Ambos                                    |
| Historial de ventas                                                            | Ambos (el técnico, solo su caja)         |
| Inventario por lotes                                                           | Ambos                                    |
| Merma                                                                          | Ambos                                    |
| Caja: apertura, egresos, cierre ciego, cierre de cajas ajenas                  | Ambos (cajas ajenas, solo administrador) |
| Catálogo                                                                       | Administrador                            |
| Kardex                                                                         | Administrador                            |
| Usuarios                                                                       | Administrador                            |
| Ventas por revisar                                                             | Administrador                            |
| Reportes: Resultados y DIGEMID                                                 | Administrador                            |

El estado de servidor vive en `frontend/src/app/core/services/` (`signal`
privado + `.asReadonly()`); la búsqueda usa `debounceTime` + `switchMap`; el
lector de cámara usa `BarcodeDetector` nativo o ZXing en WebAssembly; el
carrito (`features/punto-venta/services/carrito.service.ts`) calcula total e
IGV en céntimos enteros con la misma regla del servidor. Responsive desde
360 px: en el celular el carrito es una hoja inferior y el menú una barra de
como mucho 5 accesos.

## Criterio 4 — Integración backend/frontend

`HttpClient` real en los 19 servicios de `core/services/`. Tres interceptores
(`frontend/src/app/core/interceptors/`):

- `conexion.interceptor.ts`: decide si hay conexión por las respuestas reales
  de la API; sin conexión, toda acción que no sea vender falla al instante.
- `auth.interceptor.ts`: adjunta el JWT.
- `error.interceptor.ts`: traduce el código de error a un mensaje en español y
  ante un `401` cierra la sesión y lleva a `/login`.

Guards: `auth.guard.ts` (sesión) y `rol.guard.ts` (pantallas de administrador).
Autenticación JWT HS256 escrita a mano
(`backend/src/main/java/com/botica/backend/util/JwtUtil.java`), firma
comparada en tiempo constante y, en cada petición, verificación en la base de
que el usuario sigue activo con el mismo rol. Notificaciones en vivo por SSE
con un ticket de un solo uso (el JWT nunca va en la URL).

**Venta sin conexión:** el catálogo queda en IndexedDB; cada venta se guarda en
el dispositivo con su clave antes de enviarse y se envía sola al volver la
señal, una a la vez y solo con la sesión de quien vendió. El servidor nunca
rechaza una venta que ya ocurrió: lo que no cuadra queda en "Ventas por
revisar".

## Criterio 5 — Despliegue

**Render** (backend, `backend/Dockerfile`, build multietapa con Maven + JDK 25
y ejecución con JRE 25) + **Vercel** (frontend estático, rewrite de SPA en
`frontend/vercel.json`) + **Neon** (PostgreSQL). Las migraciones corren solas
al arrancar. Toda configuración sensible (secreto JWT, hashes de contraseña,
credenciales de la base) va por variables de entorno, nunca en el repositorio.
`GET /api/config` expone el commit desplegado para comprobar cada despliegue.
La PWA se actualiza sola al detectar una versión nueva, sin cortar una venta
en curso.

## Criterio 6 — Funcionalidades implementadas

| Módulo                                                                                       | Estado                                                                        |
| -------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| Autenticación (JWT, roles, guards, bloqueo a los 5 intentos)                                 | Completo                                                                      |
| Usuarios (alta, edición, activar/desactivar, contraseña, desbloqueo)                         | Completo                                                                      |
| Punto de venta (búsqueda, cámara, lector USB, presentaciones, FEFO, idempotencia)            | Completo                                                                      |
| Venta con receta (número y CMP obligatorios)                                                 | Completo                                                                      |
| Catálogo de productos y presentaciones                                                       | Completo                                                                      |
| Inventario por lotes con semáforos                                                           | Completo                                                                      |
| Merma con motivo y observación                                                               | Completo                                                                      |
| Caja: apertura, egresos, conteo ciego, semáforo, cierre de cajas ajenas por el administrador | Completo                                                                      |
| Alertas y notificaciones en tiempo real (SSE)                                                | Completo                                                                      |
| Kardex por producto, con Excel                                                               | Completo                                                                      |
| Reporte de resultados (día, semana, mes), con gráfico y Excel                                | Completo                                                                      |
| Reporte DIGEMID de dispensación con receta, con Excel (reporte interno)                      | Completo                                                                      |
| Venta sin conexión (IndexedDB, cola, revisiones)                                             | Completo                                                                      |
| PWA instalable y con actualización automática                                                | Completo                                                                      |
| Multi-botica (aislamiento de datos)                                                          | Completo, con tests dedicados                                                 |
| RF-42, notificación nativa del sistema operativo                                             | Parcial: implementada y probada con tests; falta la prueba en un Android real |
| RF-52, fraccionamiento (venta por unidad suelta)                                             | Planificado, fuera de esta entrega                                            |
| Comprobantes electrónicos (SUNAT)                                                            | Fuera del alcance                                                             |

## Criterio 7 — Pruebas y validaciones

| Suite            | Herramienta                                             | Cantidad                         |
| ---------------- | ------------------------------------------------------- | -------------------------------- |
| Backend          | JUnit 5 + Mockito contra **PostgreSQL real** (nunca H2) | 389 tests, cobertura JaCoCo 93 % |
| Frontend         | Vitest                                                  | 253 tests en 41 archivos         |
| De punta a punta | Playwright, contra la app real                          | 20 tests en 9 flujos             |
| API              | Postman + Newman                                        | 220 peticiones en 17 carpetas    |

La QA final del 4 de noviembre corrió dos veces seguidas cada suite con 0
fallos; el reporte DIGEMID se agregó después con sus propias pruebas (11
JUnit, 5 Vitest y 8 peticiones de Postman), ya sumadas arriba.

Qué cubren: reglas de negocio (FEFO, stock, receta, caja, merma), dinero con
vectores compartidos, controladores (401, 403, forma del error), DAOs contra la
base real, aislamiento entre boticas, idempotencia concurrente de la venta,
venta sin conexión, invariantes del Kardex (saldo = stock de los lotes) y de
los datos demo; en el frontend, servicios, interceptores, carrito, cola sin
conexión y pantallas; en E2E, venta y cierre de caja, merma, stock
insuficiente, receta y Kardex, reporte con Excel, lector USB, cámara, celular
y venta sin conexión.

**La suite detecta regresiones reales:** se rompieron reglas a propósito (por
ejemplo, la doble apertura de caja en `CajaService.abrir()`) y los tests
correspondientes se pusieron en rojo; al revertir, la suite volvió a verde.

## Criterio 8 — Despliegue exitoso

- Frontend: https://boticas.vercel.app
- Backend: https://botica-vbzh.onrender.com/api

Verificado en producción:

- La carpeta **Solo lectura** de Postman (20 peticiones) pasa contra Render con
  los usuarios de las boticas demo.
- Sin token, los endpoints protegidos responden `401 NO_AUTENTICADO`; el
  técnico recibe `403 SIN_PERMISO` en las rutas de administrador; una ruta
  inexistente da 404, un método no permitido 405 y un parámetro mal tipado 400,
  siempre con el formato de error único.
- Producción tiene tres boticas demo (`V12`) con 60 días de historia, para
  probar todas las pantallas con datos reales.
- La PWA se instala en el celular, abre sin conexión, vende sin señal y
  sincroniza al volver.

## Trabajo futuro

| Pieza                                               | Esfuerzo | Por qué                                                                                                                |
| --------------------------------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------- |
| Comprobantes electrónicos (SUNAT)                   | XL       | Integración externa con certificado digital, firma XML y envío a un sistema regulado                                   |
| Fraccionamiento (RF-52)                             | M        | Definir la regla de negocio de vender por unidad suelta de una presentación                                            |
| Prueba de la notificación nativa en Android (RF-42) | S        | Falta solo la prueba en un dispositivo real                                                                            |
| Listado de ventas sin N+1                           | S        | Hoy arma el detalle con una consulta por venta                                                                         |
| SSE en varias instancias                            | M        | Los avisos en vivo viven en la memoria de una sola instancia (se resolvería con `LISTEN/NOTIFY` de PostgreSQL o Redis) |
| Revocar el token al cerrar sesión                   | S/M      | Hoy el JWT vale hasta que vence; se mitiga con la verificación en base de cada petición                                |
