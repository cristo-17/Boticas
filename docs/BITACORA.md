# Bitácora

Errores que costó resolver, y su solución. Se **agrega** al final,
nunca se reescribe. No se lee entera: se busca.

```
grep -n "^## \[" docs/BITACORA.md
```

Si algún título se parece al problema que tienes enfrente, abre esa
entrada específica en vez de leer todo el archivo.

**Formato de entrada** (máximo diez líneas, ver Anexo B de
`docs/prompts/PROMPT-AGENTE-BACKEND-QA.md`):

```markdown
## [PREFIJO-NNN] Título corto del síntoma — AAAA-MM-DD

**Síntoma:** qué se observó.
**Causa:** por qué pasaba.
**Solución:** qué se cambió.
**Archivos:** rutas tocadas.
**Regla que dejó:** qué convención evita que se repita (si aplica).
```

Prefijos: `BE` backend, `FE` frontend, `DB` base de datos, `OPS`
despliegue.

---

## [FE-001] El toast no se cerraba solo — 2026-09-02

**Síntoma:** el auto-dismiss del toast nunca ocurría; el valor cambiaba
mentalmente pero la pantalla no se repintaba.
**Causa:** el `setTimeout` mutaba una propiedad de clase plana. La app
es zoneless (sin `zone.js`): nada dispara detección de cambios sobre
una propiedad que no sea `signal()`.
**Solución:** el estado del toast pasó a `signal()`, y el `setTimeout`
escribe sobre ese signal en vez de un campo plano.
**Archivos:** `shared/components/toast/`.
**Regla que dejó:** todo estado que cambie en el tiempo es `signal()`
o `computed()`, nunca una propiedad plana (CLAUDE.md, "Zoneless").

## [FE-002] computed() de Merma no reaccionaba al formulario — 2026-09-02

**Síntoma:** en Merma, la cantidad mayor al stock no invalidaba nada y
el motivo "Otro" no activaba la observación obligatoria, pese a que el
`computed()` parecía correcto y compilaba sin errores.
**Causa:** el `computed()` leía `form.controls.X.value` directamente.
`FormControl.value` no es un signal: `computed()` solo reacciona a
signals leídos dentro de él, así que se congelaba en su primer valor.
**Solución:** se bridgeó el formulario con
`toSignal(form.valueChanges, { initialValue: form.getRawValue() })` y
los `computed()` se derivan de ese signal (`formValue` en
`features/merma/merma.ts`).
**Archivos:** `features/merma/merma.ts`.
**Regla que dejó:** para derivar `computed()` de un `FormGroup`/
`FormControl`, primero bridgear con `toSignal(valueChanges)`. Nunca
leer `.value` directo dentro de un `computed()`.

## [FE-003] NG0203 al usar takeUntilDestroyed en ngOnInit — 2026-09-02

**Síntoma:** la pantalla explotaba en runtime con `NG0203` al llamar
`takeUntilDestroyed()` sin argumentos dentro de `ngOnInit()`. Compilaba
sin errores.
**Causa:** `takeUntilDestroyed()` necesita un contexto de inyección
para resolver `DestroyRef` implícitamente, y `ngOnInit()` no es un
contexto de inyección (a diferencia del constructor o un inicializador
de campo).
**Solución:** capturar `destroyRef = inject(DestroyRef)` como campo de
clase y pasarlo explícito: `takeUntilDestroyed(this.destroyRef)`.
**Archivos:** el componente afectado en `features/`.
**Regla que dejó:** `takeUntilDestroyed()` llamado fuera de un
constructor/inicializador de campo siempre necesita el `DestroyRef`
pasado explícito.

## [FE-004] registrarVenta perdía datos del producto agregado antes — 2026-09-02

**Síntoma:** un producto agregado al carrito de Punto de Venta antes de
hacer una nueva búsqueda se quedaba sin nombre ni precio al confirmar
la venta.
**Causa:** `registrarVenta` resolvía nombre y precio buscando el
producto en la lista de resultados de búsqueda actual, que cambia con
cada búsqueda nueva — no en los datos ya capturados en la línea del
carrito.
**Solución:** el carrito (`CarritoService`) guarda una copia completa
de los datos del producto al momento de agregarlo, no una referencia a
la lista de búsqueda.
**Archivos:** `features/punto-venta/` (servicio de carrito).
**Regla que dejó:** el servicio dueño del estado (el carrito) guarda
lo que necesita para operar; nunca resuelve datos leyendo el estado
transitorio de otro servicio (la lista de búsqueda).

## [DB-001] flyway:migrate falla con "error de sintaxis en o cerca de AS" en V2__seed.sql — 2026-09-08

**Síntoma:** `mvnw flyway:clean flyway:migrate` fallaba al aplicar
`V2__seed.sql` con `SQL State 42601`, error de sintaxis cerca de `AS`,
dentro del bloque `DO $$` que genera las presentaciones.
**Causa:** `FOR v_datos IN (VALUES (...)) AS t(col1, col2, ...) LOOP`
no es válido en PL/pgSQL — el `FOR ... IN` sobre una consulta necesita
una consulta completa (`SELECT`), no un `VALUES` con alias colgado
directamente detrás.
**Solución:** `FOR v_datos IN SELECT * FROM (VALUES (...)) AS t(...) LOOP`.
**Archivos:** `backend/src/main/resources/db/migration/V2__seed.sql`.
**Regla que dejó:** todo `FOR ... IN` de PL/pgSQL sobre datos literales
envuelve el `VALUES` en `SELECT * FROM (...) AS t(...)`, nunca el
`VALUES` solo.

## [FE-005] Merma valorizaba unidades base al precio de la Caja — 2026-09-08

**Síntoma:** `MermaService.registrarMerma` (mock del frontend) calculaba
`valor = cantidad × lote.precioUnitario`. `cantidad` está en unidades
base (se valida contra `lote.stock`, que también son unidades base).
`lote.precioUnitario` en el mock replicaba el precio de la presentación
Caja (p. ej. 12.90 por una caja de 100), no el precio por unidad
(0.20). Una merma de 3 unidades se habría valorizado en 38.70 en vez
de 0.60 — un factor de ~65x. Nunca se notó en semanas de frontend con
datos mock porque cualquier cifra plausible pasa sin que nadie la
reconcilie contra nada.
**Causa:** un cálculo que multiplica cantidad × precio compilaba y
corría sin error con dos factores en unidades distintas: `cantidad` en
unidades base, `precioUnitario` en precio-por-caja. TypeScript y el
mock no tienen forma de detectar esa clase de error — solo apareció al
forzar la reconciliación real del esquema en la Tarea 7
(`docs/DECISIONES.md`, revisión de esquema 2026-09-08).
**Solución:** `lotes` ya no tiene columna de precio. El precio de venta
por unidad base sale de `presentaciones` (la fila con
`factor_conversion = 1`, "Unidad"), explícita en el nombre de qué
unidad representa. `mermas.valor_venta` se calcula contra esa fila.
**Archivos:** `backend/src/main/resources/db/migration/V1__esquema.sql`,
`V2__seed.sql`; originalmente `core/services/merma.service.ts` del
frontend (mock, ya reemplazado por el esquema real).
**Regla que dejó:** un cálculo que multiplica cantidades por precios
necesita que la unidad de ambos factores sea la misma y esté dicha
explícitamente. `unidades base × precio de caja` compila igual de bien
que el cálculo correcto — va a volver a aparecer en la Tarea 11 Bloque
B, donde la conversión entre presentaciones (caja/blíster/unidad) es
el corazón del módulo. Cada cantidad y cada precio que se multipliquen
ahí deben poder responder "¿unidades de qué?" antes de multiplicarse.

## [BE-002] SecurityConfig no arrancaba: WRITE_DATES_AS_TIMESTAMPS no existe donde este Spring Boot lo espera — 2026-09-08

**Síntoma:** `BackendApplicationTests` (y cualquier test que tocara
`SecurityConfig`) fallaba al arrancar el contexto completo con una
cadena larga de `UnsatisfiedDependencyException` terminando en
`Could not bind properties to 'JacksonProperties'`.
**Causa:** Spring Boot 4.1.1 usa Jackson 3.x (paquete `tools.jackson.*`,
no `com.fasterxml.jackson.*`). `WRITE_DATES_AS_TIMESTAMPS` se movió de
`SerializationFeature` a un nuevo `DateTimeFeature`, expuesto en
`JacksonProperties` bajo `datatype.datetime`, no bajo `serialization`
como en Jackson 2.x — la propiedad clásica
`spring.jackson.serialization.write-dates-as-timestamps` ya no aplica
y falla en el binder de Map<Enum,Boolean> (que además exige el nombre
EXACTO del enum, no kebab-case).
**Solución:** `spring.jackson.datatype.datetime.WRITE_DATES_AS_TIMESTAMPS=false`.
**Archivos:** `backend/src/main/resources/application.properties`.
**Regla que dejó:** en este proyecto (Spring Boot 4.1.1 / Jackson 3.x),
antes de escribir cualquier propiedad `spring.jackson.*` que exista de
memoria por experiencia con Jackson 2.x, verificar la clase real
`JacksonProperties` del jar instalado (`javap` o revisar el jar en
`~/.m2`) — la mayoría de las guías y respuestas ya escritas sobre
Spring Boot asumen Jackson 2.x y el paquete `com.fasterxml.jackson`,
que en este proyecto no es el que se usa en tiempo de ejecución.

## [FE-006] El botón de Apertura/Cierre se quedaba "cargando" para siempre tras un error — 2026-09-08

**Síntoma:** al reescribir `caja.service.ts` con `HttpClient` (Tarea 9),
`abrirCaja()`/`cerrarCaja()`/`obtenerCajaDeHoy()` ponían
`_cargando.set(true)` y lo devolvían a `false` **dentro de `tap()`**.
Verificado en el navegador con el backend apagado a propósito: el botón
se quedaba con el spinner encendido para siempre después de un error de
red, porque `tap()` solo corre en `next`/`complete`, nunca en `error`.
**Causa:** el mismo patrón que ya usaba el mock (`simulate()` nunca
fallaba, así que el bug nunca se manifestó ahí) se copió tal cual al
`HttpClient` real, que sí puede fallar.
**Solución:** `finalize(() => this._cargando.set(false))` en el `pipe()`
de los tres métodos — `finalize` corre siempre, haya éxito o error.
**Archivos:** `frontend/src/app/core/services/caja.service.ts`.
**Regla que dejó:** cualquier servicio que ponga un signal de `cargando`
en `true` antes de una llamada HTTP real debe apagarlo con `finalize()`,
nunca solo dentro de `tap()` — un mock que nunca falla no expone este
bug, pero el backend real sí. Revisar esto mismo al conectar cada
servicio nuevo en la Tarea 11.

## [FE-007] `provideAppInitializer` sin catchError deja la app entera en blanco — 2026-09-08

**Síntoma:** al agregar la regla "toda pantalla muestra el error del
servicio" (CLAUDE.md) se probó apagando el backend desde el arranque
(no a mitad de sesión). Con el backend caído, la app no mostraba ni
siquiera el login: pantalla en blanco total, sin banner, sin nada.
**Causa:** `app.config.ts` llama `provideAppInitializer(() =>
firstValueFrom(configService.cargar()))` para cargar `/api/config` una
sola vez antes de arrancar. Si esa promesa se rechaza (backend caído),
Angular **aborta el bootstrap completo** — no es un error de una
pantalla, es un error de arranque de toda la aplicación.
**Solución:** `.pipe(catchError(() => of(undefined)))` antes de
`firstValueFrom`. Cada servicio que lee `config()` ya tenía su propio
valor `_DEFECTO` (agregados en la Tarea 9 por otra razón, sin saber que
esto los iba a necesitar) — la app arranca con esos defaults en vez de
no arrancar.
**Archivos:** `frontend/src/app/app.config.ts`.
**Regla que dejó:** cualquier `provideAppInitializer`/`APP_INITIALIZER`
que dependa de una llamada HTTP real necesita su propio `catchError`:
una promesa rechazada ahí no falla "una pantalla", tumba el arranque
completo de Angular. Se encontró por casualidad verificando otra cosa
(la regla de error visible) — probar "backend apagado" específicamente
desde el primer load, no solo a mitad de sesión, sigue siendo la única
forma de atrapar esta clase de bug.

## [BE-003] Flyway nunca corría solo, dependía siempre del plugin de Maven — 2026-09-08

**Síntoma:** al preparar el perfil `prod` de la Tarea 10 (Render no
tiene Maven en runtime, solo el jar), se asumió que `spring.flyway.*`
ya hacía que el propio arranque de Spring Boot aplicara las
migraciones — como documentaba `docs/DESPLIEGUE.md` ("dispara Flyway
igual que `spring-boot:run`"). Verificado empíricamente (regla
"verificar antes de adivinar", no solo inspección de jars): con
`mvnw flyway:clean` y arrancando la app **sin** correr
`flyway:migrate`, el contexto arrancaba sin errores y `GET
/api/productos` respondía `500` — `information_schema.tables` seguía
en 0 filas. La app entera funcionaba con una base vacía sin quejarse.
**Causa:** Spring Boot 4 modularizó la autoconfiguración por feature
(`spring-boot-jdbc`, `spring-boot-sql`, `spring-boot-webmvc`, ...) y
Flyway quedó en un módulo propio, `spring-boot-flyway`, que **no** se
agrega solo con `flyway-core`/`flyway-database-postgresql` (las
librerías de Flyway en sí). `pom.xml` nunca declaró
`spring-boot-starter-flyway` — sin él, `FlywayAutoConfiguration`/
`FlywayProperties` no existen en el classpath, `spring.flyway.*` no
hace nada, y el único mecanismo que de verdad aplicaba migraciones
hasta hoy era el `flyway-maven-plugin` (`mvnw flyway:migrate`), un
paso de build/CLI, nunca parte del arranque de la aplicación — pasó
inadvertido porque el flujo de dev local siempre corre ese comando
manualmente antes de levantar la app.
**Solución:** agregar `org.springframework.boot:spring-boot-starter-flyway`
a `pom.xml`. Reverificado empíricamente: misma prueba (clean + arrancar
sin migrate) ahora sí migra sola ("Migrating schema public to version
1 - esquema", "2 - seed") y `botica_db` queda con las 18 tablas.
**Archivos:** `backend/pom.xml`.
**Regla que dejó:** en Spring Boot 4, tener las librerías de una
integración (Flyway, y probablemente otras) en el classpath **no**
implica que su autoconfiguración esté presente — son módulos
separados. Antes de asumir que algo "ya corre solo" porque siempre
funcionó en dev, probarlo en las condiciones reales del entorno donde
va a correr sin ayuda (acá: sin el plugin de Maven disponible, como en
un contenedor solo con el jar).
