# Despliegue

Guía de despliegue real (Docker, URL pública, variables de entorno)
llega en la Tarea 10. Esto solo cubre lo que la Tarea 7 pidió: cómo
reconstruir `botica_db` desde cero en local, y por qué el comando
cambia de forma a mitad de proyecto.

## Por qué esto importa: Flyway y el checksum

Flyway calcula un checksum de cada migración la primera vez que la
aplica. Si `V1__esquema.sql` (o cualquier otra ya aplicada) se edita
después de correr una vez contra una base, el arranque siguiente falla
con `checksum mismatch` — Flyway asume que un archivo que cambió de
contenido después de aplicado es un error de operación, no una edición
intencional, y se niega a seguir hasta que se resuelva.

## La frontera — no queda a criterio de nadie

**MIENTRAS ITERAMOS (ahora, todavía afinando el esquema):**
si hay que corregir `V1__esquema.sql` o `V2__seed.sql`, se corrige el
archivo tal cual y se reconstruye la base desde cero con el comando de
abajo. **No se agrega un `V3__ajuste.sql`** para corregir algo que
todavía no lo usó nadie de verdad — el historial de migraciones
quedaría lleno de parches de cosas que nunca existieron en una base
real.

**DESDE QUE HAYA DATOS QUE IMPORTEN** (el despliegue de la Tarea 10
esté vivo, con datos reales o de demostración que alguien más va a
ver): **ninguna migración ya aplicada se toca jamás.** Todo cambio de
esquema, por chico que sea, es una migración nueva (`V3__...`,
`V4__...`). Esa es la frontera, y cruzarla en cualquier dirección
después de la Tarea 10 es un error, no una decisión de turno.

## Comando único para reconstruir `botica_db` desde cero

Requiere las variables de entorno `DB_USERNAME` y `DB_PASSWORD` (las
mismas que usa `application.properties`; `DB_HOST`/`DB_PORT`/`DB_NAME`
son opcionales, por defecto `localhost`/`5432`/`botica_db`).

```bash
cd backend
DB_USERNAME=<tu_usuario> DB_PASSWORD=<tu_password> ./mvnw flyway:clean flyway:migrate
```

En PowerShell:

```powershell
cd backend
$env:DB_USERNAME="<tu_usuario>"; $env:DB_PASSWORD="<tu_password>"
./mvnw flyway:clean flyway:migrate
```

`flyway:clean` borra **todo** el contenido del esquema `public` de
`botica_db`, incluida la tabla `flyway_schema_history` — es
exactamente "borrar el esquema y volver a correr las migraciones" en
un solo paso, sin escribir `DROP SCHEMA` a mano. `flyway:migrate`
vuelve a aplicar `V1__esquema.sql` y el seed de desarrollo
(`db/seed/dev/V2__seed.sql`, ver "Seed real de producción" más abajo
para la versión sin datos de demostración que usa el perfil `prod`)
desde cero.

**`flyway:clean` está habilitado a propósito en `pom.xml`
(`cleanDisabled=false`) solo para este flujo de desarrollo local.**
Es una operación destructiva por diseño — nunca se activa en un perfil
de producción ni se corre contra una base que ya tenga datos reales.

### Verificar que corrió limpio

```bash
cd backend
./mvnw test -Dtest=BackendApplicationTests
```

Este test carga el contexto completo de Spring Boot, lo que dispara
Flyway igual que `spring-boot:run` — si alguna migración falla, el
test falla con el error de Flyway, no con un error de negocio
disfrazado.

## Despliegue real (Tarea 10)

**Infraestructura:** Render (backend, vía `backend/Dockerfile`) +
Vercel (frontend, build estático) + Neon (Postgres administrado).
Motivo y alternativa descartada (Supabase): `docs/DECISIONES.md`,
entrada "Tarea 10: infraestructura de despliegue".

### Guía de cuentas: orden, qué copiar, qué no compartir

**Orden: Neon → Render → Vercel.** El backend necesita la cadena de
conexión de Neon desde su primer deploy — con `spring-boot-starter-flyway`
(`[BE-003]`) las migraciones corren solas al arrancar, así que si la
base no existe todavía el primer deploy de Render falla, no queda
"vacío en silencio". Vercel va después porque necesita la URL ya
asignada de Render para `environment.prod.ts`, y Render necesita la
URL de Vercel para `CORS_ORIGEN` — ese segundo dato se completa
**después** de crear Vercel, volviendo a Render a editar una variable
(no bloquea nada, Render redeploya solo al guardar una env var nueva).

**1. Neon** — crear proyecto, copiar el endpoint **directo** (sin
`-pooler` en el host — ver "Conexión a Neon" arriba) de:
- host → `DB_HOST`
- puerto (usualmente 5432) → `DB_PORT`
- nombre de la base → `DB_NAME` (Neon asigna un nombre por defecto al
  crear el proyecto — usar ese nombre tal cual, o renombrar la base a
  `botica_db` desde el panel si Neon lo permite; no asumir el nombre
  sin mirarlo)
- usuario → `DB_USERNAME`
- contraseña → **no me la pegues acá** (ver abajo)

**2. Render** — nuevo Web Service, Docker, apuntando a `backend/Dockerfile`.
Elegir un nombre de servicio propio (no el generado al azar) — la URL
sale de ese nombre (`https://<nombre-elegido>.onrender.com`) y hace
falta fija para el paso 3. Variables de entorno completas:

| Variable | Valor | ¿Secreto? |
|---|---|---|
| `DB_HOST` | host directo de Neon | no |
| `DB_PORT` | puerto de Neon | no |
| `DB_NAME` | nombre de la base en Neon | no |
| `DB_USERNAME` | usuario de Neon | no |
| `DB_PASSWORD` | contraseña de Neon | **sí — pegar directo en Render, no en el chat** |
| `SPRING_PROFILES_ACTIVE` | `prod` | no |
| `BOTICA_NOMBRE` | nombre real de la botica | no |
| `ADMIN_NOMBRE` | nombre completo del administrador | no |
| `ADMIN_USUARIO` | usuario de login elegido | no |
| `ADMIN_PASSWORD_HASH` | hash BCrypt (ver abajo cómo generarlo) | preferible pegar directo en Render, no hace falta que yo lo vea |
| `ADMIN_TURNO` | `Mañana`, `Tarde` o `Noche` | no |
| `CORS_ORIGEN` | URL de Vercel — se completa en el paso 3 | no |

**Generar `ADMIN_PASSWORD_HASH` sin que la contraseña real salga de tu
máquina:** con el JDK ya instalado localmente y el jar de
`spring-security-crypto` que Maven ya descargó
(`~/.m2/repository/org/springframework/security/spring-security-crypto/`),
un programa de una línea (`new BCryptPasswordEncoder().encode("tu-contraseña-real")`)
corrido una sola vez en tu terminal. El resultado (`$2a$10$...`) es lo
único que pegas en Render — la contraseña en texto plano no necesita
tocar el chat en ningún momento.

**3. Vercel** — nuevo proyecto sobre `frontend/`, build `npm run
build`, output `dist/frontend/browser` (Vercel detecta Angular
automáticamente, confirmar que use ese output). Antes de conectar:
editar `frontend/src/environments/environment.prod.ts` con la URL real
de Render del paso 2 (`apiUrl: 'https://<tu-servicio>.onrender.com/api'`)
y hacer commit/push — Vercel construye desde ahí. Con la URL de Vercel
ya asignada, volver a Render y completar `CORS_ORIGEN` con esa URL.

**Cómo verifico que quedó bien, sin que me muestres el secreto:**
- El log de arranque de Render es seguro de pegar en el chat si algo
  falla: la URL JDBC que Flyway imprime (`Database: jdbc:postgresql://host:port/db`)
  nunca incluye usuario ni contraseña — esos van aparte en el driver,
  no en la URL.
- Para confirmar que `DB_PASSWORD`/conexión están bien: pásame el
  código HTTP de `curl -s -o /dev/null -w "%{http_code}" https://<tu-render>.onrender.com/api/config`
  (debe dar `200`) o el estado que muestra el panel de Render
  ("Live"/"Deploy failed" + el log de error si falla).
- Para confirmar que `ADMIN_PASSWORD_HASH` quedó bien: el propio login
  en la app desplegada con la contraseña real que elegiste — si entra,
  el hash es correcto; si no, se regenera y se vuelve a pegar en
  Render (nunca hace falta que yo vea ni la contraseña ni el hash).

### Backend en Render

- Imagen: `backend/Dockerfile`, multistage — `maven:3.9-eclipse-temurin-25`
  para compilar, `eclipse-temurin:25-jre-noble` para correr. Ambos tags
  verificados en Docker Hub antes de escribir el Dockerfile (regla
  "verificar antes de adivinar", CLAUDE.md): no hay variante Alpine
  publicada para Temurin 25.
- Variables de entorno en el panel de Render: `DB_HOST`, `DB_PORT`,
  `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (ver "Conexión a Neon" abajo),
  `CORS_ORIGEN` (la URL de Vercel), `SPRING_PROFILES_ACTIVE=prod`, y
  las 5 variables del seed de producción (ver más abajo).
- Las migraciones corren solas al arrancar el jar (`spring-boot-starter-flyway`,
  ver `docs/BITACORA.md` `[BE-003]`) — no hace falta ningún paso extra
  en el `Dockerfile` ni un comando de start-up distinto a
  `java -jar app.jar`.
- Filesystem no persistente: nada que el backend escriba en disco
  sobrevive un redeploy o un reinicio por sueño. No aplica hoy (no se
  escribe nada a disco local), queda anotado por si se agrega algo
  (logs a archivo, uploads) más adelante.

### Frontend en Vercel

- `frontend/vercel.json`: `buildCommand: npm run build`,
  `outputDirectory: dist/frontend/browser`, y un rewrite `/(.*) →
  /index.html`. Sin esto, refrescar la página en cualquier ruta que no
  sea `/` (p. ej. `/caja`) devuelve 404 — Vercel por defecto sirve
  archivos estáticos 1:1 y no sabe que las rutas de una SPA las
  resuelve Angular Router en el cliente, no el servidor.
- Service worker activo en build de producción (`ng add @angular/pwa`,
  Tarea 10): `provideServiceWorker('ngsw-worker.js', { enabled:
  !isDevMode(), registrationStrategy: 'registerWhenStable:30000' })`
  en `app.config.ts`. Verificado con `npm run build`:
  `dist/frontend/browser/ngsw-worker.js` y `ngsw.json` se generan; en
  `ng serve` (modo dev) sigue deshabilitado (`isDevMode()`).

### Conexión a Neon: un solo datasource, endpoint directo (no el pooler)

Neon expone dos endpoints por proyecto: el directo
(`ep-xxx.<región>.aws.neon.tech`) y el *pooled*
(`ep-xxx-pooler.<región>.aws.neon.tech`, PgBouncer en modo
*transacción*). Verificado contra la documentación actual de Neon
antes de configurar nada (regla "verificar antes de adivinar",
CLAUDE.md):

- **Flyway** (`spring.flyway.*`): la documentación de Neon nombra
  explícitamente "schema migrations" y herramientas como Flyway/
  Liquibase como casos que deben usar el endpoint directo — el modo
  transacción del pooler no soporta `SET`/`RESET` de variables de
  sesión ni locks de sesión, que Flyway usa para coordinar
  migraciones.
- **La aplicación** (`spring.datasource.*`, HikariCP): también el
  endpoint directo — y no solo por la recomendación general de Neon
  para backends con pool propio (un Web Service persistente con
  HikariCP no necesita el pooler; el pooler es para funciones
  serverless que no mantienen su propio pool). Hay una razón propia
  del proyecto: `spring.datasource.hikari.connection-init-sql=SET TIME
  ZONE 'America/Lima'` (Tarea 8, Regla 5) fija la zona horaria de cada
  conexión al abrirla. Ese `SET` es exactamente el tipo de estado de
  sesión que el pooler en modo transacción puede perder entre
  transacciones, porque la conexión física puede cambiar de sesión de
  Postgres detrás de escena sin avisar. Con el endpoint pooled, la
  zona horaria de sesión dejaría de estar garantizada de forma
  silenciosa — el mismo tipo de riesgo que ya se descartó con
  `TIMESTAMP` + `ALTER DATABASE` (Decisión 3, `docs/DECISIONES.md`),
  esta vez a nivel de conexión en vez de esquema.

**Conclusión: no hacen falta dos datasources.** Un único endpoint
directo sirve tanto para `spring.flyway.url` como para
`spring.datasource.url` (mismas credenciales, mismo host, sin
`-pooler`). El endpoint pooled queda sin uso en este proyecto.

### Limitaciones del plan gratuito

- **Render (backend):** se duerme tras 15 minutos sin tráfico; el
  primer request tras dormir tarda ~1 minuto en responder (arranque en
  frío del contenedor); 750 horas/mes incluidas (alcanza para un solo
  servicio corriendo 24/7 todo el mes); filesystem no persistente entre
  reinicios/redeploys.
- **Vercel (frontend):** sin límite de tiempo ni sueño — son archivos
  estáticos servidos por CDN.
- **Neon (Postgres):** límites de cómputo/almacenamiento del plan
  gratuito sujetos a los términos vigentes al momento del despliegue —
  verificar en neon.tech/pricing en vez de asumir un número fijo aquí.

### Calentar la URL antes de una demo

Si el backend llevaba más de 15 minutos sin tráfico, la primera
petición real de la demo (login, `GET /api/config`) va a tardar ~1
minuto y puede parecer que la app está rota. Antes de cualquier demo:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://<url-de-render>/api/config
```

Repetir cada 10 s hasta ver `200` (normalmente 1–2 intentos). Recién
entonces abrir el frontend — no es opcional: sin este paso, la primera
acción del profesor en vivo puede ser la que dispare el arranque en
frío.

### Seed real de producción: sin datos de demostración

`V2__seed.sql` (contraseñas `admin123`/`tecnico123`, ver tabla abajo)
**no corre contra la base pública.** Aplicado (confirmado por el
usuario) antes de cruzar la frontera de Flyway:

- `db/migration/V1__esquema.sql` — esquema, común a todo entorno.
- `db/seed/dev/V2__seed.sql` — el seed de siempre (2 boticas, 4
  usuarios, 30 productos, 90+ lotes de muestra). Perfil por defecto
  (sin `SPRING_PROFILES_ACTIVE`), el que usa `mvnw spring-boot:run` y
  `mvnw flyway:clean flyway:migrate` en local.
- `db/seed/prod/V2__seed.sql` — **sin ningún dato de demostración**:
  solo 1 botica y 1 usuario `ADMINISTRADOR`. Nada de productos ni
  lotes de ejemplo — la persona que use el sistema a diario carga su
  propio catálogo desde cero; los estados vacíos de cada pantalla ya
  están construidos para esto. Perfil `prod`
  (`application-prod.properties`, activado con
  `SPRING_PROFILES_ACTIVE=prod` en Render).

Los dos seeds viven en `db/seed/`, **hermano** de `db/migration/`, no
subcarpetas suyas — Flyway escanea `filesystem:`/`classpath:` de forma
recursiva, así que tenerlos anidados bajo `db/migration/` hacía que
ambos "V2" se recogieran a la vez y Flyway fallara con "Found more
than one migration with version 2" (encontrado probando el arranque
con el perfil `prod` contra una base limpia, antes de aplicarlo).

**Valores reales del seed de prod, por variable de entorno vía
placeholders de Flyway** (`spring.flyway.placeholders.*`, nunca en el
repo ni en la migración):

| Variable de entorno | Placeholder en el SQL | Valor |
|---|---|---|
| `BOTICA_NOMBRE` | `${boticaNombre}` | Nombre real de la botica |
| `ADMIN_NOMBRE` | `${adminNombre}` | Nombre completo del administrador |
| `ADMIN_USUARIO` | `${adminUsuario}` | Usuario de login |
| `ADMIN_PASSWORD_HASH` | `${adminPasswordHash}` | **Hash BCrypt ya calculado**, nunca la contraseña en texto plano |
| `ADMIN_TURNO` | `${adminTurno}` | `Mañana`, `Tarde` o `Noche` (columna `NOT NULL`, sin default) |

Ninguno tiene valor por defecto — si falta alguno, Flyway falla al
arrancar en vez de sembrar un dato inventado (`spring.flyway.placeholder-strict`,
comportamiento por defecto). `direccion`/`ruc` de la botica quedan
`NULL` en el seed (columnas nullable) — se completan después con un
`UPDATE` normal contra la tabla ya viva, no son parte de la migración.

**Cómo generar `ADMIN_PASSWORD_HASH` una sola vez, localmente** (nunca
pegar la contraseña en texto plano en ningún panel):

```bash
# Con el classpath del propio proyecto (spring-security-crypto ya es
# dependencia transitiva de spring-boot-starter-security):
java -cp "<ruta-a-spring-security-crypto.jar>:<ruta-a-commons-logging.jar>" GenHash "<contraseña-real-elegida>"
```

o, más simple, un test JUnit de una línea con
`new BCryptPasswordEncoder().encode("...")` corrido una vez y
descartado. El hash resultante (`$2a$10$...`) es lo único que se pega
en `ADMIN_PASSWORD_HASH` — la contraseña en texto plano no se guarda
en ningún lado una vez generado el hash.

**Verificado de punta a punta, no solo que compile** (`docs/BITACORA.md`
`[BE-003]`, hallazgo relacionado): con la base limpia (`flyway:clean`)
y `SPRING_PROFILES_ACTIVE=prod` + las 5 variables de arriba, la app
arranca, migra sola (`V1` + `db/seed/prod/V2`), y queda exactamente
con 1 botica / 1 usuario / 0 productos / 0 lotes — sin tocar el perfil
por defecto, que sigue sembrando los datos de demostración de siempre
para desarrollo local.

## Contraseñas del seed (texto plano, solo desarrollo)

| Usuario | Botica | Rol | Contraseña |
|---|---|---|---|
| `rosa.quispe` | Botica San Lucas | TECNICO | `tecnico123` |
| `carlos.mendoza` | Botica San Lucas | ADMINISTRADOR | `admin123` |
| `milagros.torres` | Botica Vida Sana | TECNICO | `tecnico123` |
| `jorge.fernandez` | Botica Vida Sana | ADMINISTRADOR | `admin123` |

Hasheadas con `BCryptPasswordEncoder` real (no inventadas a mano) —
ver `docs/BITACORA.md` si hace falta regenerarlas.
