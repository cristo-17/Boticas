# Informe de avance — BoticaSys

Sistema de gestión para boticas independientes: ventas con FEFO,
inventario por lotes, merma, caja diaria, alertas operativas,
autenticación real por roles y aislamiento entre boticas. Backend
Spring Boot 4.1.1 (JDBC puro, sin ORM) + Angular 22 (PWA zoneless) +
PostgreSQL, desplegado en Render + Vercel + Neon.

- **Frontend en vivo:** https://boticas.vercel.app
- **Backend en vivo:** https://botica-vbzh.onrender.com/api (plan
  gratuito de Render: se duerme tras 15 min sin tráfico, la primera
  petición después de eso tarda ~1 minuto)
- **Repositorio:** este mismo — rutas de código citadas abajo son
  relativas a la raíz del repositorio.

## Resumen de pruebas

123 tests de backend (JUnit 5 + Mockito + PostgreSQL real, código en
`backend/src/test/java/`), 20 de frontend (Vitest, código en
`frontend/src/app/**/*.spec.ts`) y 3 flujos E2E de punta a punta
contra la app real (Playwright, código en `frontend/e2e/`) — todos
verdes. Cobertura de instrucciones del backend: 82%.

## Criterio 1 — API RESTful con Spring Boot

8 controladores en `backend/src/main/java/com/botica/backend/controller/`,
~20 endpoints bajo `/api`. Capas estrictas: el controlador nunca toca
SQL ni valida reglas de negocio, el `service/` tiene toda la regla de
negocio, `dao/`/`dao/impl/` es el único lugar que sabe SQL.

| Controlador | Endpoints |
|---|---|
| `AuthController` | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/yo` |
| `CajaController` | `GET /api/caja/hoy`, `POST /api/caja/abrir`, `GET /api/caja/{id}/resumen-cierre`, `POST /api/caja/cerrar` (rol `ADMINISTRADOR`), `GET /api/caja/{id}/movimientos` |
| `ConfigController` | `GET /api/config` |
| `ProductoController` | `GET /api/productos`, `GET /api/productos/codigo/{codigoBarras}`, `GET /api/productos/mas-vendidos` |
| `LoteController` | `GET /api/lotes` (paginado, filtros, orden validado contra lista blanca), `POST /api/lotes` |
| `VentaController` | `POST /api/ventas` (idempotente por clave enviada desde el cliente) |
| `MermaController` | `GET /api/mermas`, `POST /api/mermas` |
| `AlertaController` | `GET /api/dashboard/resumen`, `GET /api/alertas` |

Formato de error único: `GlobalExceptionHandler`
(`backend/src/main/java/com/botica/backend/config/GlobalExceptionHandler.java`)
mapea 17 excepciones de negocio
(`backend/src/main/java/com/botica/backend/exception/`) a
`{timestamp, status, error, mensaje, path}`, con el código siempre en
mayúsculas (`STOCK_INSUFICIENTE`, `CAJA_YA_ABIERTA`, `NO_AUTENTICADO`,
etc.) — nunca un stack trace crudo.

Validación en dos niveles: Bean Validation en cada DTO de entrada
(`@NotNull`, `@Positive`, `@Pattern`) más una regla de negocio real en
el Service que nunca confía en lo que el cliente ya validó — ver por
ejemplo `backend/src/test/java/com/botica/backend/service/VentaTransaccionTest.java`
y `MermaServiceTest.java`: un botón deshabilitado en pantalla no
reemplaza la validación del servidor.

## Criterio 2 — Backend con base de datos

JDBC puro, cero ORM: `NamedParameterJdbcTemplate` en todos lados
(`backend/src/main/java/com/botica/backend/dao/impl/`), SQL
parametrizado, un `RowMapper` explícito y nombrado por tabla — nunca
una lambda repetida.

Migraciones Flyway (`backend/src/main/resources/db/`):
- `migration/V1__esquema.sql` — 17 tablas (12 en uso activo:
  `boticas`, `roles`, `usuarios`, `productos`, `presentaciones`,
  `lotes`, `caja_diaria`, `ventas`, `venta_detalle`,
  `movimientos_caja`, `mermas`, `movimientos_stock`; 5 reservadas y
  vacías para una fase futura: `clientes`, `comprobantes_electronicos`,
  `kardex`, `reportes_digemid`, `lotes_fraccionados`). Índice único
  parcial `idx_caja_abierta_unica`: una caja abierta por
  botica/usuario/turno/día.
- `migration/V3__productos_unidad_nombre.sql` — columna nueva sin
  romper el perfil de producción ya desplegado.
- `seed/dev/V4__seed.sql` — datos deterministas de desarrollo (nunca
  `random()`, para que reconstruir la base dé siempre el mismo
  resultado).
- `seed/prod/` — sin ningún dato de demostración: solo la botica y los
  usuarios reales, inyectados por variable de entorno.

Multi-botica desde el esquema base: toda tabla operativa lleva
`botica_id`, filtrado siempre desde el contexto de la sesión
autenticada — nunca un parámetro que mande el cliente. Verificado con
tests de aislamiento reales:
`backend/src/test/java/com/botica/backend/dao/LoteDaoAislamientoTest.java`,
`CajaDaoAislamientoTest.java`, `MermaDaoAislamientoTest.java`,
`AlertaDaoAislamientoTest.java` — ningún listado de una botica devuelve
una fila de otra, y el índice único de caja/código de barras NO
bloquea entre boticas distintas.

Dinero siempre en `BigDecimal`/`NUMERIC(10,2)`, comparado con
`compareTo()` — nunca `.equals()` (verificado con un test que hace
`grep` sobre todo el código de producción:
`backend/src/test/java/com/botica/backend/service/VentaDineroTest.java`).
Fechas en zona `America/Lima`, fijada tanto en la aplicación como en
la conexión JDBC.

## Criterio 3 — Frontend con Angular

Angular 22 **zoneless** (sin `zone.js`): todo el estado reactivo con
`signal`/`computed`. 6 pantallas de feature
(`frontend/src/app/features/`: `login`, `alertas`, `punto-venta`,
`inventario`, `merma`, `caja`) y 16 componentes compartidos
(`frontend/src/app/shared/components/`), usados solo cuando ≥2
pantallas los necesitan.

Estado de servidor vive únicamente en `frontend/src/app/core/services/`
(`signal` privado + `.asReadonly()` público); estado local de pantalla
es un signal del propio componente. Ejemplo con lógica real:
`frontend/src/app/features/punto-venta/services/carrito.service.ts`
deriva subtotal/IGV/total con `computed()` — extrae el IGV (÷1.18), no
lo suma.

## Criterio 4 — Integración backend/frontend

`HttpClient` real en los 8 servicios de `core/services/`, sin ningún
mock. Dos interceptores
(`frontend/src/app/core/interceptors/`):
- `auth.interceptor.ts` — adjunta el JWT a cada petición saliente.
- `error.interceptor.ts` — traduce el código de error del backend a un
  mensaje en español en un solo lugar, y cierra la sesión + redirige a
  `/login` en cualquier `401` fuera del propio login.

`frontend/src/app/core/guards/auth.guard.ts` protege todas las rutas
autenticadas. Autenticación JWT real de punta a punta: token HS256
generado a mano
(`backend/src/main/java/com/botica/backend/util/JwtUtil.java`, sin
librería de terceros), guardado en `localStorage` del navegador
(riesgo aceptado: no es `httpOnly`; mitigado con expiración a 12h y
revalidación contra el backend en cada arranque de la app).

## Criterio 5 — Despliegue

**Render** (backend, `backend/Dockerfile`, build multistage:
`maven:3.9-eclipse-temurin-25` para compilar, `eclipse-temurin:25-jre-noble`
para correr) + **Vercel** (frontend, build estático, rewrite de SPA en
`frontend/vercel.json`) + **Neon** (PostgreSQL administrado). Las
migraciones corren solas al arrancar el backend, sin ningún paso
manual. Toda configuración sensible (secreto JWT, hashes de
contraseña, credenciales de base de datos) vía variables de entorno,
nunca en el repositorio.

## Criterio 6 — Funcionalidades implementadas

| Módulo | Backend | Frontend | Estado |
|---|---|---|---|
| Autenticación (JWT, roles, guard) | ✅ | ✅ | Completo |
| Punto de venta (búsqueda, escaneo simulado, carrito, cobro) | ✅ | ✅ | Completo — FEFO, idempotencia, conversión de presentación |
| Inventario (lotes, semáforo de vencimiento, registrar lote) | ✅ | ✅ | Completo — paginado, filtros, orden validado |
| Merma (cascada producto→lote, motivo/observación) | ✅ | ✅ | Completo — destructiva, cantidad topada al stock real del servidor |
| Caja (apertura, cierre con conteo ciego, semáforo de descuadre) | ✅ | ✅ | Completo — cierre restringido a `ADMINISTRADOR` (ver nota abajo) |
| Alertas (KPIs agregados, lista urgente/atención) | ✅ | ✅ | Completo — todo por SQL agregado |
| Multi-botica (aislamiento de datos) | ✅ | ✅ | Completo, con tests de aislamiento dedicados |
| Escáner de código de barras | — | Simulado | Elige un producto al azar; no usa la cámara real todavía |
| Reporte de ganancias | ❌ | ❌ | No construido — el dato (costo por venta) ya se captura, falta el reporte |
| Comprobantes electrónicos (SUNAT) | ❌ | ❌ | No construido |
| Kardex | ❌ | ❌ | No construido |
| Reportes DIGEMID | ❌ | ❌ | No construido |
| Lotes fraccionados | ❌ | ❌ | No construido |
| Offline real (cola de sincronización) | Simulado | Simulado | Un botón alterna el estado de conexión en pantalla; no hay `IndexedDB` ni cola real de reintentos |
| PWA instalable | — | ✅ | Manifest + service worker activos en producción |

**Nota sobre cierre de caja — es un límite real de la implementación
actual, no solo una restricción de rol:** la función que cierra caja
busca la caja abierta del usuario que hace la petición, no cualquier
caja abierta de la botica. Un administrador que intenta cerrar la
caja que abrió un técnico no la encuentra, aunque sí exista una
abierta. En la práctica, hoy una caja abierta por un técnico no la
puede cerrar nadie más que esa misma cuenta — y un técnico no tiene el
rol para hacerlo. El flujo demostrado (login → venta → cierre)
funciona porque la misma cuenta administradora abre, vende y cierra;
no se probó "un técnico abre y vende, un administrador distinto
cierra". Pendiente de decisión sobre el arreglo correcto.

## Criterio 7 — Pruebas y validaciones

**Backend — 123 tests** (`backend/src/test/java/com/botica/backend/`):
unitarios de reglas de negocio (doble apertura de caja, FEFO, IGV,
semáforo de vencimiento en sus límites exactos), una sección de dinero
aparte (suma de líneas = total en 50 ventas generadas, precios que
rompen el punto flotante, grep de `.equals()` sin coincidencias),
integración de DAOs contra PostgreSQL real (nunca H2 — el SQL es
específico de Postgres), controladores (`@WebMvcTest`: 401/403, forma
del error, parámetros maliciosos rechazados), aislamiento entre
boticas, y consistencia de costo/precio (el costo de una venta queda
congelado aunque el lote se reponga después a otro costo).

**Frontend — 20 tests Vitest** (`frontend/src/app/**/*.spec.ts`):
servicios HTTP con `HttpTestingController`, utilidades puras, y un
componente con `computed()` reactivo verificado de verdad (no solo
que compile).

**E2E — 3 flujos Playwright** (`frontend/e2e/`), contra la app real
sin mocks: login → abrir caja → venta → cierre de caja cuadrado;
registrar merma → verificar en Inventario que el stock bajó; vender
más unidades que el stock disponible → ver el error correcto sin
perder el carrito.

**Demostración de que la suite detecta regresiones reales:** se rompió
a propósito la regla de doble apertura de caja
(`backend/src/main/java/com/botica/backend/service/CajaService.java`,
método `abrir()`) y el test correspondiente
(`CajaServiceTest`) se puso en rojo — 3 fallos exactamente en esa
regla. Se revirtió el cambio y la suite volvió a verde.

## Criterio 8 — Despliegue exitoso

- Frontend: https://boticas.vercel.app
- Backend: https://botica-vbzh.onrender.com/api

Verificado en producción, sin usar ninguna credencial real:
- Los endpoints protegidos (`GET /api/lotes`, `POST /api/caja/cerrar`,
  `GET /api/dashboard/resumen`) exigen token — responden
  `401 NO_AUTENTICADO` sin él.
- Un intento de login con usuario inexistente responde
  `401 CREDENCIALES_INVALIDAS`, con el formato de error correcto.
- `GET /api/auth/yo` sin sesión responde `200` vacío (nunca falla).
- CORS configurado correctamente entre el frontend y el backend.
- Las rutas internas de la SPA (`/caja`, `/inventario`) siguen
  funcionando al refrescar la página directamente.
- El service worker de producción está activo (`"activated"`) en
  tiempo de ejecución real, no solo generado en el build — la PWA
  cachea la app para uso sin conexión.
- El manifest de la PWA se sirve correctamente con los 8 tamaños de
  ícono necesarios para instalación.

**Evidencia visual** (`capturas/`, tomadas contra la URL pública):

![Login exitoso](capturas/01-login-exitoso.png)
![Venta completa](capturas/02-venta-completa.png)
![Cierre de caja](capturas/03-cierre-caja.png)
![PWA instalada en celular](capturas/04-pwa-instalada-celular.png)

## Qué falta para una siguiente fase

Esfuerzo relativo (S = poco, M = medio, L = grande, XL = el más
grande de la lista):

| Pieza | Esfuerzo | Por qué |
|---|---|---|
| Comprobantes electrónicos (SUNAT) | **XL** | Integración externa con certificado digital, firma XML, envío a un sistema regulado de terceros |
| Offline real (`IndexedDB` + cola de sincronización) | **L** | Hoy es un simulador manual; real implica cola persistente, reintentos, resolución de conflictos y sincronización en segundo plano |
| Reportes DIGEMID | **L** | Formato regulatorio a definir, más el mecanismo de envío |
| Lector de código de barras real (cámara) | **M** | Reemplazar la simulación por lectura de cámara real y una librería de decodificación, con permisos y manejo de errores |
| Kardex | **M** | Reporte agregado sobre movimientos de stock ya registrados — sin integración externa |
| Reporte de ganancias | **M** | El dato ya se captura por cada venta; falta el endpoint agregado y la pantalla |
| Lotes fraccionados | **M** | Requiere definir la regla de negocio del fraccionamiento antes de tocar código |
| Gestión de clientes | **S/M** | Alcance depende de si es solo un dato para boleta/factura o si suma historial de compras |
| Revisar la restricción de `ADMINISTRADOR` en cierre de caja | **S** | Cambio de código pequeño si se decide relajarlo — el trabajo real es la decisión de negocio |
| Personalizar manifest/ícono/título de la PWA | **S** | Cosmético — hoy usa los valores genéricos por defecto |
