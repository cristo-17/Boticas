# Estado del proyecto
Actualizado: 2026-09-08 · Última tarea completada: T9 + preparación de T10 (infraestructura elegida, despliegue en curso)

## Módulos
| Módulo     | Backend | Frontend | Tests | Notas                              |
|------------|---------|----------|-------|-------------------------------------|
| Caja       | ✅      | ✅ HTTP real | ✅ | Conectada de punta a punta, verificada en navegador |
| Inventario | esquema | mock     | —     | Sin Service/Controller aún |
| Ventas     | esquema | mock     | —     | Sin Service/Controller aún |
| Merma      | esquema | mock     | —     | Sin Service/Controller aún |
| Alertas    | esquema | mock     | —     | Sin Service/Controller aún |
| Auth       | esquema | mock     | —     | `usuarios`/`roles` listos, sin login real (T12) |

## Tarea en curso
**T9 completada y verificada en el navegador de verdad** (no solo
compila, no solo pasa tests): `caja.service.ts` reemplazado por
`HttpClient` sin cambiar su firma; `ConfigService` nuevo consumiendo
`GET /api/config` una sola vez al iniciar (`provideAppInitializer`);
`error.interceptor.ts` con la traducción de códigos y la lista de
silenciosos; `PaginacionComponent` nuevo en `shared/components/`;
`environments/` + `fileReplacements` en `angular.json`.

**Excepción aprobada aplicada:** `cierre.ts`/`cierre.html` rediseñados
para el conteo ciego — ya no muestran "efectivo esperado" antes de
contar (ni como preview mientras se escribe), solo el total de ventas
digitales; el resultado (esperado/contado/diferencia/semáforo) recién
aparece en la respuesta de `POST /cerrar`.

**Constantes movidas a `/api/config`** (ya no hardcodeadas en el
cliente): `TASA_IGV` (`venta.service.ts`, `carrito.service.ts`),
`UMBRAL_STOCK_BAJO` (`inventario.service.ts`, `inventario.ts`),
`UMBRAL_DESCUADRE_LEVE` (`cierre.ts`), `MOTIVOS_MERMA`/
`MOTIVOS_QUE_REQUIEREN_OBSERVACION` (`merma.service.ts`, con el ajuste
mecánico correspondiente en `merma.ts` — inevitable, `inject()` exige
contexto de inyección, no se puede leer un signal desde una constante
de módulo).

**Verificado en el navegador, con el backend real levantado** (Chrome,
sesión con `rosa.quispe`/`1234`), no solo descrito:
- Apertura exitosa: caja creada, datos del backend reflejados
  correctamente (monto, hora, usuario).
- Doble apertura: bloqueada por el propio cliente (ya sabe que hay
  caja abierta); el 409 real ya estaba probado por curl en la Tarea 8.
- Conteo ciego: confirmado en pantalla — "efectivo esperado" no
  aparece en ningún lado antes de cerrar, ni como preview mientras se
  escribe el monto contado. Solo el total de ventas digitales (S/0.00)
  se muestra antes.
- Cierre con descuadre LEVE: `-5.00`, tono ámbar, "esperado/contado"
  revelados recién después de cerrar. Coincide exacto con el cálculo
  del servidor.
- Cierre con descuadre GRAVE: `-50.00`, tono rojo, "el administrador
  recibirá una alerta". Coincide exacto.
- Backend apagado: la petición falla de verdad (confirmado en consola:
  `HttpErrorResponse`), el botón se recupera (ya no se queda
  "cargando" para siempre — bug real encontrado y corregido, ver
  `docs/BITACORA.md` `[FE-006]`), pero **no hay ningún mensaje visible
  para el usuario** — `AperturaComponent` no tiene manejo de error ni
  `<app-toast>` en su plantilla. Es un hueco preexistente del
  frontend (no introducido por esta tarea) y no se resolvió: Apertura
  no está en la lista de excepciones aprobadas a "los componentes no
  se tocan".
- Backend recuperado: reintentar la misma acción funciona sin recargar
  la página.

## Ronda previa a T10 (2026-09-08, tres pedidos del usuario tras aprobar T9)

**1) Regla de error visible por pantalla, ya en CLAUDE.md y aplicada:**
"Toda pantalla que llama a un servicio muestra el estado `error` de ese
servicio" — sección "Reglas de frontend" de CLAUDE.md. Aplicada ahora
mismo en `AperturaComponent` (cerraba la deuda técnica que quedó
abierta en T9) y en `CierreComponent` (`<app-error-banner>` +
`readonly error = this.caja.error` + manejadores de error vacíos en
los `.subscribe()` sueltos de `ngOnInit`/`irAPagina`/
`cargarDatosDeCierre`). Verificado en el navegador con el backend
apagado a propósito: banner rojo visible en ambas pantallas, texto
traducido por `error.interceptor.ts`. Se aplicará por defecto (sin
volver a preguntar) al conectar cada módulo nuevo en la Tarea 11.

**2) Revisión de patrón `[FE-006]` en los demás servicios** (pedida
explícitamente como "no es bug de Caja, es un patrón" — no se corrige
nada todavía, solo se documenta y se deja el molde correcto en
CLAUDE.md, sección "Reglas de frontend" punto 2):

| Servicio | `_error` signal | `cargando` se apaga en |
|---|---|---|
| `producto.service.ts` | ❌ no tiene | solo en `tap()` |
| `venta.service.ts` | ❌ no tiene | solo en `tap()` |
| `merma.service.ts` | ❌ no tiene | solo en `tap()` |
| `alerta.service.ts` | ❌ no tiene | solo en `tap()` |
| `inventario.service.ts` | ✅ tiene | solo en `tap()` (falta `finalize()`) |
| `auth.service.ts` | ❌ no tiene | no tiene `cargando` (login maneja error ad-hoc en la pantalla) |
| `conexion.service.ts` | — exento | no es un recurso de servidor |
| `caja.service.ts` | ✅ tiene | ✅ `finalize()` — ya corregido en T9 |

Los cinco marcados con `tap()` solo van a repetir `[FE-006]` en cuanto
se conecten a HTTP real en la Tarea 11 — se corrigen ahí, no antes,
por instrucción explícita del usuario.

**3) Regla Jackson/Spring Boot ascendida a regla general** (ya no solo
`[BE-002]` en la bitácora): sección "Verificar antes de adivinar" de
CLAUDE.md — verificar clase/enum real del jar instalado antes de
probar variantes de memoria en cualquier propiedad `spring.*`.

**Bug encontrado "por accidente" verificando lo anterior — `[FE-007]`:**
`provideAppInitializer` sin `catchError` deja la app entera en blanco
si `/api/config` falla al arrancar (no es un fallo de pantalla, es un
fallo de bootstrap completo de Angular). Corregido en `app.config.ts`
con `.pipe(catchError(() => of(undefined)))`. Verificado en el
navegador en ambos sentidos: backend apagado desde el arranque → app
carga con banner de error + valores `_DEFECTO` de cada servicio;
backend recuperado → recarga muestra datos reales sin banner, sin
necesidad de tocar código. Detalle en `docs/BITACORA.md` `[FE-007]`.

## Tarea 10 — infraestructura elegida, preparación completa, despliegue en curso

**Infraestructura:** Render (backend) + Vercel (frontend) + Neon
(Postgres), decisión final del usuario. Detalle y alternativa
descartada (Supabase): `docs/DECISIONES.md`. Guía completa de
configuración, límites del plan gratuito y procedimiento de demo:
`docs/DESPLIEGUE.md`.

**Preparado y verificado en este entorno (no solo escrito):**
- `frontend/vercel.json` (rewrite SPA) y service worker de producción
  (`ng add @angular/pwa`) — build de producción confirmado generando
  `ngsw-worker.js`/`ngsw.json`; tests (`npm test`) y build (`npm run
  build`) verdes tras el cambio de dependencias.
- `backend/Dockerfile` multistage — imágenes base verificadas en
  Docker Hub antes de escribirlas, no en memoria. **No verificado con
  `docker build` local** (no hay Docker instalado en este entorno) —
  el primer build real va a ser en Render.
- Conexión a Neon verificada contra su documentación actual antes de
  configurar nada: un solo datasource, endpoint **directo** (no el
  pooler) para Flyway y para la app — detalle y motivo en
  `docs/DESPLIEGUE.md`.
- **Hallazgo importante corregido:** Flyway nunca corría solo al
  arrancar Spring Boot, solo vía el plugin de Maven (`[BE-003]`,
  `docs/BITACORA.md`) — habría dejado la base de Render vacía en el
  primer deploy. Agregado `spring-boot-starter-flyway`; reverificado
  empíricamente (clean + arrancar sin `flyway:migrate` manual) que
  ahora sí migra sola.
- **Seed dev/prod separado**, aplicado y verificado de punta a punta
  contra una base limpia real (no solo que compile): perfil por
  defecto sigue sembrando los datos de demostración de siempre
  (`db/seed/dev/`); perfil `prod` (`db/seed/prod/`, activado con
  `SPRING_PROFILES_ACTIVE=prod`) crea solo 1 botica + 1 usuario
  `ADMINISTRADOR`, **cero** productos/lotes/ventas de muestra, valores
  reales inyectados por variable de entorno vía placeholders de
  Flyway. Confirmado con consultas SQL directas tras el arranque:
  conteos exactos (1/1/0/0), turno con tilde correcto (bug de
  visualización de la terminal descartado comparando contra el
  literal en una consulta aparte). Suite completa de tests del backend
  (23 tests) verde después de todos estos cambios.
- Tests de frontend y backend, y compilación de ambos, verificados
  tras todo lo anterior — todo pasa.

## Esperando decisión mía
Ninguna de lo ya resuelto arriba. Pendiente para cuando el usuario
cree las cuentas: guía de orden y variables de entorno exactas para
Neon → Render → Vercel (en preparación, se entrega en el siguiente
turno de esta misma tarea).

## Siguiente paso concreto
Tarea 10 en curso: el usuario va a crear las cuentas de Render/Vercel/
Neon siguiendo una guía paso a paso (pendiente de entregar). Después,
con URL real: calentar la URL antes de cualquier demo
(`docs/DESPLIEGUE.md`) y verificar zona horaria con una consulta SQL
real en Neon contra un registro creado desde la app desplegada — y
anotar en `docs/DESPLIEGUE.md`, con fecha y hora exactas, el momento
en que se cruza la frontera de Flyway (desde ahí, ninguna migración
aplicada se vuelve a tocar).

## Deuda técnica aceptada
- `producto`, `venta`, `merma`, `alerta` sin `_error` signal y con
  `cargando` apagado solo en `tap()`; `inventario` tiene `_error` pero
  igual le falta `finalize()` — los cinco heredan `[FE-006]` en cuanto
  se conecten a HTTP real. Se corrigen al conectarlos en Tarea 11, no
  antes (instrucción explícita del usuario, ver tabla arriba).
- Inventario, Ventas, Merma, Alertas y Auth siguen 100% mock — Tarea
  11 y 12.
- Bundle inicial del frontend excede el budget de 500kB por 40kB
  (warning, no error) tras agregar `HttpClient`/interceptor/modelos
  nuevos — no crítico, revisar si crece más en Tarea 11.

## Fase 2 (fuera de esta fase, no se construye ahora)
- **Pantalla de reportes de resultados**. Dato ya capturado desde la
  Tarea 7. Endpoint restringido a `ADMINISTRADOR`, sin definir todavía.
- Comprobantes electrónicos (SUNAT), Kardex, reportes DIGEMID, lotes
  fraccionados: tablas ya creadas vacías en `V1__esquema.sql`.
