# BoticaSys

BoticaSys es una aplicación web instalable (PWA) para administrar boticas independientes del Perú. El
técnico de farmacia vende desde el celular, la tablet o la computadora: busca o escanea el producto con
la cámara, el sistema descuenta primero del lote que vence antes (FEFO), pide la receta cuando el
medicamento la exige y sigue vendiendo aunque se caiga el internet. El administrador controla la caja
diaria con conteo ciego, el inventario por lotes y vencimientos, la merma, el Kardex de cada producto,
el reporte de resultados (con descarga a Excel) y los usuarios de su botica. Cada botica ve solo sus
propios datos.

## En producción

| Parte | URL |
|---|---|
| Aplicación (Vercel) | https://boticas.vercel.app |
| API (Render) | https://botica-vbzh.onrender.com/api |
| Versión desplegada | https://botica-vbzh.onrender.com/api/config (campo `"version"` = commit que sirve Render) |

Render en el plan gratuito se duerme tras 15 minutos sin tráfico: la primera petición después tarda
alrededor de un minuto. La base de datos es PostgreSQL en Neon.

## Stack y arquitectura

| Capa | Tecnología |
|---|---|
| Frontend | Angular 22, PWA zoneless con signals, service worker de Angular, IndexedDB para vender sin conexión, Bootstrap 5 solo para grid y layout |
| Backend | Spring Boot 4.1.1 (Java 25), Spring Security con JWT, **JDBC puro** (`NamedParameterJdbcTemplate`) con patrón DAO, sin JPA ni Hibernate |
| Base de datos | PostgreSQL, esquema versionado con **Flyway** (migraciones `V1` a `V13`, corren solas al arrancar) |
| Despliegue | Vercel (frontend), Render con Docker (backend, `backend/Dockerfile`), Neon (PostgreSQL) |

```
  Celular / tablet / PC
  ┌──────────────────────────────┐
  │ Angular PWA                  │
  │  service worker + IndexedDB  │──── sin conexión: la venta queda en una cola local
  └──────────────┬───────────────┘     y se reenvía sola con la misma clave (sin duplicar)
                 │ HTTPS + JWT
                 ▼
  ┌──────────────────────────────┐
  │ Spring Boot (Render)         │
  │  Controller → Service → DAO  │──── reglas de negocio y transacciones en el Service
  │  (interfaz) → DaoImpl (JDBC) │──── SQL a mano, siempre parametrizado
  └──────────────┬───────────────┘
                 │ JDBC (zona America/Lima)
                 ▼
  ┌──────────────────────────────┐
  │ PostgreSQL (Neon)            │──── Flyway aplica las migraciones al arrancar
  └──────────────────────────────┘
```

Decisiones que ordenan el código:

- **Capas estrictas en el backend:** `controller/` → `service/` → `dao/` (interfaz) → `dao/impl/` (JDBC).
  Toda escritura en más de una tabla va en una transacción del Service.
- **Dinero en `BigDecimal`** (`NUMERIC(10,2)` en la base) y el IGV se extrae del precio (÷ 1.18), no se suma.
- **Un solo formato de error** en toda la API: `{"status", "error", "mensaje", "path", "timestamp"}`, con un
  código estable en mayúsculas en `error` (por ejemplo `STOCK_INSUFICIENTE`, `RECETA_REQUERIDA`,
  `RUTA_NO_ENCONTRADA`).
- **Aislamiento entre boticas:** el id de la botica sale del JWT y va en cada consulta SQL, nunca del cliente.
- **Ventas idempotentes:** cada venta lleva una `claveIdempotencia`; repetirla devuelve la misma venta.
- **Producción solo con migraciones:** la base de Neon se cambia únicamente con migraciones Flyway versionadas.

Estructura del monorepo:

```
backend/     API Spring Boot (src/main/java/com/botica/backend, migraciones en src/main/resources/db)
frontend/    PWA Angular (src/app, E2E en e2e/)
postman/     colección y entornos de Postman (sin contraseñas)
test-vectores/  casos de dinero y códigos EAN compartidos por los tests del backend y del frontend
```

## Cómo levantarlo en local

Requisitos: **Java 25** (el wrapper `./mvnw` descarga Maven), **Node.js 24** con npm 11 y **PostgreSQL**.

### 1. Base de datos

Crear dos bases vacías: `botica_db` (desarrollo) y `botica_test` (la usan los tests, que la reconstruyen
desde cero en cada corrida; nunca H2).

```
createdb botica_db
createdb botica_test
```

No hace falta correr ningún SQL: al arrancar, el backend aplica con Flyway todas las migraciones (esquema,
seed de desarrollo y las tres boticas demo de la `V12`). Para volver a empezar con datos limpios, borrar y
crear de nuevo `botica_db` y arrancar el backend.

### 2. Backend (`backend/`, puerto 8080)

```
./mvnw spring-boot:run
```

### 3. Frontend (`frontend/`, puerto 4200)

```
npm install
npm start
```

La app local apunta a `http://localhost:8080/api` (`src/environments/environment.ts`).

### Variables de entorno

Los valores de producción no están en el repositorio: se cargan en el panel de Render. En desarrollo casi
todas tienen un valor por defecto en `application.properties`; las únicas obligatorias son el usuario y la
contraseña de PostgreSQL.

**Backend, en local y en producción**

| Variable | Para qué sirve | En local |
|---|---|---|
| `DB_USERNAME` | Usuario de PostgreSQL | Obligatoria |
| `DB_PASSWORD` | Contraseña de PostgreSQL | Obligatoria |
| `DB_HOST` | Host de PostgreSQL | Opcional (`localhost`) |
| `DB_PORT` | Puerto de PostgreSQL | Opcional (`5432`) |
| `DB_NAME` | Nombre de la base | Opcional (`botica_db`) |
| `JWT_SECRET` | Clave con la que se firman los tokens (mínimo 32 bytes de entropía) | Opcional (hay una de desarrollo); **en producción, si falta, la app no arranca** |
| `JWT_EXPIRACION_HORAS` | Horas de validez del token | Opcional (12) |
| `CORS_ORIGEN` | Origen del frontend autorizado a llamar a la API | Opcional (`http://localhost:4200`) |
| `DEMO_PASSWORD_HASH` | Hash BCrypt de la contraseña de los usuarios de las boticas demo (se genera con `backend/tools/GenerarHashDemo.java`) | Opcional; **obligatoria en producción** |
| `DEMO_HOY` | Fecha "de hoy" para generar la historia de las boticas demo (solo para pruebas; vacío = hoy) | Opcional |

**Backend, solo en producción**

| Variable | Para qué sirve |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod`: activa la conexión con SSL y el seed de producción |
| `BOTICA_NOMBRE` | Nombre de la botica real que crea el seed de producción |
| `ADMIN_NOMBRE`, `ADMIN_USUARIO`, `ADMIN_TURNO` | Nombre, usuario y turno del administrador inicial |
| `ADMIN_PASSWORD_HASH` | Hash BCrypt de su contraseña |
| `TECNICO_NOMBRE`, `TECNICO_USUARIO` | Nombre y usuario del técnico inicial |
| `TECNICO_PASSWORD_HASH` | Hash BCrypt de su contraseña |
| `RENDER_GIT_COMMIT` | La define Render sola: es el commit que se muestra en `/api/config` (`local` si no existe) |

**Frontend y pruebas**

| Variable | Para qué sirve |
|---|---|
| `VERCEL_GIT_COMMIT_SHA` | La define Vercel sola en el build: es la versión de la app que muestra el panel de conexión |
| `E2E_BASE_URL` | URL de la app contra la que corre Playwright (por defecto `http://localhost:4200`) |

El detalle del despliegue (Render, Vercel y Neon, paso a paso) está en la documentación del equipo.

## Pruebas

Números de la QA final del 2026-11-04 (dos corridas seguidas de cada suite, 0 fallos).

| Suite | Herramienta | Resultado | Cómo se corre |
|---|---|---|---|
| Backend | JUnit 5 + Mockito, contra **PostgreSQL real** (`botica_test`) | **378/378** | `cd backend && ./mvnw test` (con `DB_USERNAME` y `DB_PASSWORD`) |
| Cobertura del backend | JaCoCo | **93 %** de instrucciones | Sale con `./mvnw test` en `backend/target/site/jacoco/index.html` |
| Frontend | Vitest | **248/248** (40 archivos) | `cd frontend && npm test` |
| De punta a punta | Playwright | **20/20** (9 flujos) | Backend y frontend levantados, `botica_db` con datos limpios; `cd frontend && npm run e2e` |
| API | Postman + Newman | **212 peticiones, 548 aserciones** | Ver abajo |

Qué cubren, en corto:

- **Backend:** reglas de negocio (FEFO, stock, receta, caja, merma), dinero con vectores compartidos,
  controladores (401, 403 y la forma del error), DAOs contra la base real, aislamiento entre boticas,
  idempotencia concurrente de la venta, venta sin conexión y que los datos demo cumplan sus invariantes.
- **Frontend:** servicios, interceptores, carrito, cola de ventas sin conexión, búsqueda con
  `debounceTime` + `switchMap`, actualización de la PWA y las pantallas principales.
- **E2E:** venta y cierre de caja, merma que descuenta stock, stock insuficiente, receta y Kardex, reporte
  con Excel, lector USB, cámara (con una cámara falsa), celular sin desborde y venta sin conexión.
- **Newman:** todos los endpoints del contrato, una carpeta por módulo, más Seguridad (sin token, token
  inválido, rol equivocado, otra botica, 404/405/400). La carpeta **Solo lectura** es la única que se corre
  contra producción (20 peticiones, 41 aserciones por botica demo).

Newman contra el backend local (las contraseñas van por la línea de comandos, nunca en los archivos):

```
npx -p newman@6 newman run postman/BoticaSys.postman_collection.json \
  -e postman/local.postman_environment.json \
  --env-var passwordTecnico=... --env-var passwordAdmin=... --env-var passwordAdminOtraBotica=...
```

Más detalle en [`postman/README.md`](./postman/README.md).

## Boticas de demostración

La migración `V12` carga tres boticas con 60 días de historia (ventas, lotes, mermas, cajas y egresos),
en local y en producción. Las contraseñas no están en el repositorio.

| Botica | Dirección | Administrador | Técnicos |
|---|---|---|---|
| Botica Virgen del Carmen (24 horas) | Av. Las Palmeras 3120, Los Olivos, Lima | `demo_carmen_patricia` | `demo_carmen_luis`, `demo_carmen_rocio`, `demo_carmen_miguel` (`demo_carmen_jose`, desactivado) |
| Botica San Martín | Jr. Huiracocha 1585, Jesús María, Lima | `demo_sanmartin_elena` | `demo_sanmartin_diego`, `demo_sanmartin_karina` (`demo_sanmartin_hugo`, desactivado) |
| Botica La Esperanza | Av. Los Próceres 742, Santiago de Surco, Lima | `demo_esperanza_victor` | `demo_esperanza_sofia`, `demo_esperanza_andres` (`demo_esperanza_lucia`, desactivada) |

Al entrar se elige el turno (Mañana, Tarde o Noche).

## Funcionalidades

**53 requerimientos: 51 implementados, 1 parcial y 1 planificado.** Cada uno tiene su evidencia
ejecutable (el test JUnit, Vitest, E2E o la petición de Postman que lo prueba) en la matriz de evidencias,
`docs/EVIDENCIAS.md`, que se entrega junto con el informe (la carpeta `docs/` es la documentación de trabajo
del equipo y no está en este repositorio).

### Implementadas

- **Sesión y acceso:** login con usuario, contraseña y turno; bloqueo de 15 minutos al 5.º intento fallido;
  la sesión se recupera al recargar; roles TÉCNICO y ADMINISTRADOR; aislamiento total entre boticas;
  parámetros del negocio desde el servidor.
- **Usuarios (administrador):** alta, edición, activar o desactivar, restablecer contraseña y desbloquear.
- **Punto de Venta:** búsqueda con resultados al escribir, código de barras con la cámara del celular o con
  lector USB, los más vendidos de 30 días, presentaciones (blíster, caja), carrito con IGV extraído, FEFO
  entre lotes, nunca un lote vencido, validación de stock, venta sin duplicados, precio y costo congelados,
  caja abierta obligatoria e historial de ventas.
- **Venta con receta:** los productos marcados piden número de receta y CMP del médico antes de cobrar.
- **Catálogo (administrador):** productos con sus presentaciones, activar y desactivar.
- **Inventario y merma:** lotes con semáforo de vencimiento y de stock, registro de lotes, merma con motivo,
  observación obligatoria cuando corresponde y su valorización.
- **Kardex (administrador):** entradas, salidas (marcadas las que fueron con receta) y saldo acumulado por
  producto, con descarga a Excel; el saldo final coincide con el stock de los lotes.
- **Caja:** apertura por turno, egresos, resumen previo sin montos esperados, **conteo ciego** y semáforo de
  descuadre (exacto, leve o grave) con alerta al administrador.
- **Alertas y notificaciones:** dashboard, vencimientos y stock bajo, avisos en tiempo real (SSE) e historial.
- **Reporte de resultados (administrador):** ventas, costo, mermas, egresos y resultado neto por día, semana o
  mes, con gráfico y descarga a Excel.
- **Celular y tablet:** desde 360 px de ancho, sin desplazamiento horizontal.
- **Venta sin conexión:** el catálogo queda guardado en el dispositivo, la venta se encola y se envía sola al
  volver la señal, una sola vez; lo que no cuadró al recibirla (por ejemplo, stock insuficiente) queda para el administrador en "Ventas por
  revisar".
- **PWA:** se instala como app y se actualiza sola sin cortar una venta en curso.

### Parcial y planificado

- **RF-42, notificación nativa del sistema operativo — PARCIAL.** Está implementada y probada con tests, pero
  falta la prueba en un celular Android real.
- **RF-52, fraccionamiento (vender por unidad suelta de una presentación) — NO IMPLEMENTADO, planificado.** Se
  recortó antes de la entrega y queda como trabajo futuro.

Limitaciones conocidas: el listado de ventas hace una consulta por venta para armar su detalle (N+1); los
avisos en tiempo real viven en la memoria de una sola instancia; el token no se revoca al cerrar sesión; en
la venta sin conexión la hora registrada es la del servidor al recibirla.

## Más información

- [`INFORME-AVANCE.md`](./INFORME-AVANCE.md): informe de avance del proyecto.
- [`postman/README.md`](./postman/README.md): cómo correr la colección en Postman y Newman.
