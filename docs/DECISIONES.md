# Decisiones

Decisiones de diseño o alcance tomadas por el usuario durante el
desarrollo, con su fecha y alternativas descartadas. Se **agrega** al
final, nunca se reescribe.

**Formato de entrada:**

```markdown
## AAAA-MM-DD — Título corto de la decisión

**Decisión:** qué se decidió.
**Alternativas descartadas:** qué otras opciones se consideraron y por
qué no se eligieron.
**Quién decidió:** usuario / agente con aprobación del usuario.
```

---

## 2026-09-08 — Ciclo de vida de la clave de idempotencia de venta

**Decisión:** `NuevaVentaRequest` gana `claveIdempotencia` (UUID v4,
string). Se genera en el frontend al momento en que el cajero confirma el
carrito (aprieta "Cobrar"), se guarda en el estado de `VentaService`, se
reusa en cada reintento del mismo POST, y se limpia solo cuando el backend
responde `200`.

**Alternativas descartadas:** generar el UUID dentro del método HTTP en
cada intento — se descartó porque cada reintento de red mandaría un UUID
distinto, y la idempotencia dejaría de servir para nada (el mismo bug que
se quería evitar).

**Quién decidió:** usuario.

## 2026-09-08 — Cierre de caja en dos pasos, conteo ciego

**Decisión:** `GET /api/caja/{id}/resumen-cierre` (antes de contar)
devuelve totales de ventas en efectivo/digital y cantidades, **sin**
`montoEsperado`. `POST /api/caja/cerrar` (después de contar, recibe
`montoContado`) es el único que devuelve `montoEsperado`, `diferencia` y
`semaforoDescuadre`. El total de ventas digitales sí se muestra antes de
contar, porque no se cuenta físicamente.

**Alternativas descartadas:** mostrar `montoEsperado` desde que se abre la
pantalla de cierre (lo que hace hoy el frontend construido) — se descartó
porque convierte el conteo en una confirmación en vez de un control: el
descuadre real nunca aparece si el cajero ya sabe qué número "debería"
salir.

**Quién decidió:** usuario.

## 2026-09-08 — Merma: selectores en cascada en vez de inventario completo

**Decisión:** `MermaScreen` selecciona primero el producto (vía
`GET /api/productos?buscar=`, límite 20) y recién entonces carga los
lotes de ese producto (`GET /api/lotes?productoId=`). Deja de llamar
`listarLotes({})` sin filtro.

**Alternativas descartadas:** mantener `listarLotes({})` y resolver la
paginación con un `tamano` grande o sin paginar ese endpoint en
particular — se descartó porque el problema real no era de paginación
sino de que Merma pedía todo el inventario para armar un selector; con
búsqueda por producto el problema desaparece de raíz.

**Quién decidió:** usuario.

## 2026-09-08 — Métodos declarados sin consumidor: se implementan igual

**Decisión:** `listarMovimientos` y `listarVentasDelDia` se implementan en
el backend aunque ningún componente los llame hoy — los necesita el
rediseño de Cierre de Caja de la Tarea 9. `listarLotesDeProducto` (ahora
`?productoId=`) pasa a ser necesario por el rediseño de Merma.
`AuthService.obtenerUsuarioActual` se implementa: es lo que rehidrata la
sesión al recargar en la Tarea 12. Ninguno se borra.

**Alternativas descartadas:** borrar los métodos sin consumidor actual —
descartada porque los cuatro tienen un consumidor concreto en una tarea ya
planeada, no son código muerto.

**Quién decidió:** usuario.

## 2026-09-08 — Métodos síncronos de lookup: internos del DAO, no endpoints públicos

**Decisión:** `ProductoService.obtenerPorId` e
`InventarioService.obtenerLotePorId` no se convierten en
`GET /api/productos/{id}` ni `GET /api/lotes/{id}`. Se implementan como
métodos del DAO usados internamente por `VentaService` y `MermaService`.
No se construyen endpoints "por si acaso".

**Alternativas descartadas:** exponerlos como endpoints REST para espejar
la firma `Observable<T>` de todos los demás métodos del servicio —
descartada porque ningún componente entra por URL a un detalle de
producto o lote hoy; si aparece esa pantalla, el endpoint se agrega en ese
turno.

**Quién decidió:** usuario, confirmando la investigación de a quién
llama cada método.

## 2026-09-08 — Parámetro `turno` en productos más vendidos: eliminado

**Decisión:** `GET /api/productos/mas-vendidos` pierde el `?turno=` que
tenía el comentario del mock. Ni el método ni el componente lo mandaban.

**Alternativas descartadas:** implementar el filtro por turno que sugería
el comentario — descartada porque ninguna pantalla lo necesita y hubiera
sido una dimensión inventada por el agente, no pedida.

**Quién decidió:** usuario.

## 2026-09-08 — Comportamiento de "efectivo esperado" en Cierre: cambio deliberado, no bug

**Decisión:** que `cierre.html` muestre hoy "Efectivo esperado en caja" de
forma incondicional (antes de contar) es un comportamiento que la Tarea 9
tiene que **cambiar** a propósito, no un bug a corregir de vuelta a como
está. Ver la decisión de conteo ciego arriba.

**Alternativas descartadas:** ninguna — es la consecuencia directa de la
decisión de conteo ciego, documentada aparte para que quede explícito que
el cambio es intencional quince tareas después, cuando ya nadie recuerde
esta conversación.

**Quién decidió:** usuario, a partir de un hallazgo del agente.

## 2026-09-08 — Código de barras sin match: 404, silencioso en el cliente

**Decisión:** `GET /api/productos/codigo/{codigoBarras}` responde
`404 PRODUCTO_NO_ENCONTRADO` si no hay coincidencia (no `200` con cuerpo
vacío). El interceptor de errores del frontend no muestra toast para este
código específico — es un resultado esperado del flujo de escaneo.

**Alternativas descartadas:** `200` con cuerpo vacío/`null` — descartada
porque obliga al cliente a inventar su propia noción de "no encontrado" en
vez de usar el código de estado HTTP que ya existe para eso.

**Quién decidió:** usuario.

## 2026-09-08 — Ids como número, no string

**Decisión:** todos los ids viajan como `number` en el JSON (`"id": 1001`,
no `"id": "1001"`). Los modelos del frontend ya los declaran como
`number`, y `BIGSERIAL` entra sin problema en el rango seguro de `number`
en JavaScript (2^53).

**Alternativas descartadas:** ids como string, para evitar cualquier
problema de precisión — descartada por innecesaria: no se van a alcanzar
ids que rompan el rango seguro de JS en este proyecto.

**Quién decidió:** usuario.

## 2026-09-08 — Constantes de negocio vía `GET /api/config`, cargado una vez

**Decisión:** IGV, umbral de stock bajo, descuadre leve, motivos de merma
y umbrales FEFO se sirven desde un único `GET /api/config`, llamado una
vez al iniciar la app y guardado en un signal de un nuevo `ConfigService`.
Excepción: la tasa de IGV además viaja congelada en la cabecera de cada
venta (`Venta.igv`), porque `/api/config` dice cuánto es el IGV *hoy* y
una venta vieja no se recalcula si cambia mañana.

**Alternativas descartadas:** mandar cada constante en cada respuesta
relevante (p. ej. el umbral de stock bajo en cada fila de `GET /api/lotes`)
— descartada por ser ruido repetido que se puede desincronizar entre
filas de una misma respuesta.

**Quién decidió:** usuario.

## 2026-09-08 — `ResumenDashboard`: números crudos, no texto pre-formateado

**Decisión:** `ventasHoyTexto` y `cajaNota` desaparecen de
`ResumenDashboard`. El backend devuelve números (`ventasHoy: number`) y el
estado de caja como enum (`cajaEstado: "ABIERTA"|"CERRADA"`); el frontend
arma el texto final con `moneda.util.ts`.

**Alternativas descartadas:** mantener el texto pre-formateado en el
backend — descartada porque sobre un string como `"S/ 2 964.50"` no se
puede sumar, ordenar ni comparar, y cambiar el formato del texto obligaría
a redesplegar el backend en vez de tocar solo el frontend.

**Quién decidió:** usuario.

## 2026-09-08 — Reglas de generación de alertas, formalizadas

**Decisión:** valores por defecto (ajustables desde `/api/config`):
vencimiento en 4 estados (`VENCIDO` <hoy, `CRITICO` 0-30d, `ADVERTENCIA`
31-90d, `OK` >90d, calculado con la fecha de Lima); stock `CRITICO` ≤15,
`AGOTADO` =0; "caja sin cerrar" = existe una caja `ABIERTA` con fecha
operativa anterior a hoy (no por horas transcurridas). Un lote vencido con
stock 0 no genera alerta.

**Alternativas descartadas:** contar horas desde la apertura para "caja
sin cerrar" — descartada porque con boticas 24/7 y turno noche, el conteo
por horas da falsos positivos todas las madrugadas.

**Quién decidió:** usuario.

## 2026-09-08 — Turno de login: no se valida contra el usuario

**Decisión:** el turno que el usuario elige en el login no se valida
contra ningún horario asignado. La única validación real sigue siendo la
regla de caja: un mismo usuario no puede tener dos cajas abiertas. El
turno se guarda en la caja y (por D1) en el JWT, para trazabilidad.

**Alternativas descartadas:** una tabla de horarios por usuario/turno para
validar el login — descartada porque nadie la pidió y en una botica de
barrio los turnos se acomodan informalmente, no siguen un horario fijo.

**Quién decidió:** usuario.

## 2026-09-08 — D1: soporte multi-botica desde el esquema base

**Decisión:** el producto soporta varias boticas independientes desde la
Tarea 7, no como ampliación futura. Toda tabla operativa
(`usuarios`, `productos`, `presentaciones`, `lotes`, `ventas`,
`venta_detalle`, `caja_diaria`, `movimientos_caja`, `mermas`,
`movimientos_stock`) lleva `botica_id BIGINT NOT NULL`. El `botica_id` de
cada consulta sale del JWT del usuario autenticado, nunca de un parámetro
que mande el cliente. El seed crea dos boticas con datos distintos, y un
test de integración que verifica el aislamiento entre boticas es
obligatorio, no opcional.

**Alternativas descartadas:** implementar una sola botica ahora y migrar a
multi-botica después — descartada explícitamente por el usuario: aunque
el 30% de los casos de uso reales opere con una sola botica, el costo de
agregar `botica_id` después (migrando datos y tocando cada query ya
escrita) es mucho mayor que incluirlo desde el esquema base.

**Quién decidió:** usuario.

## 2026-09-08 — D2: captura de costo desde ahora, pantalla de reportes en Fase 2

**Decisión:** `lotes.costo_unitario NUMERIC(10,2) NOT NULL` (el costo vive
en el lote, no en el producto, porque el mismo medicamento se compra a
distinto precio en cada reposición). `venta_detalle.costo_unitario` se
congela al momento de la venta, igual que el precio. `movimientos_caja`
ya soporta el tipo `EGRESO` con descripción/nota. La pantalla de reportes
de ingresos/gastos/ganancia queda anotada como Fase 2 en
`docs/ESTADO.md` — no se construye ahora, pero el dato se captura desde
esta fase porque no se puede reconstruir retroactivamente.

**Alternativas descartadas:** capturar el costo cuando se construya la
pantalla de reportes en Fase 2 — descartada porque el costo de las ventas
y mermas de hoy se perdería para siempre; no hay forma de reconstruir qué
costaba un lote ya vendido o dado de baja.

**Quién decidió:** usuario.

## 2026-09-08 — D3: comprobante electrónico y reporte de resultados son cosas distintas

**Decisión:** "comprobante electrónico" (boleta/factura SUNAT, documento
tributario con validación/envío/contingencia) es Fase 3, tabla creada
vacía. "Reporte de resultados" (ingresos/gastos/ganancia para el dueño) es
un informe interno sin valor tributario, es lo de D2. La palabra "factura"
no se usa para el segundo caso en ningún documento del proyecto.

**Alternativas descartadas:** ninguna — es una aclaración de nomenclatura,
no una decisión con alternativas técnicas. Se registra igual porque
confundir los dos términos en el contrato o el esquema hubiera arrastrado
al proyecto hacia requisitos de SUNAT sin necesidad.

**Quién decidió:** usuario.

## 2026-09-08 — Nota 1: origen de captura en el carrito

**Decisión:** `LineaCarrito` (`carrito.service.ts`) gana `origenCaptura`.
`CarritoService.agregar(producto, presentacion, origen)` lo recibe como
parámetro nuevo, capturado en el momento exacto en que el producto entra
al carrito — después de ese momento ya no se puede reconstruir de dónde
vino. `PuntoVentaScreen` y `CarritoService` se agregan a la lista de
excepciones de pantallas que sí se tocan en la Tarea 9.

**Alternativas descartadas:** derivar el origen más tarde, al momento de
`cobrar()`, a partir de cómo se llegó al producto seleccionado —
descartada porque para ese momento el carrito puede tener varias líneas
agregadas por caminos distintos (una escaneada, otra buscada) y ya no hay
forma de saber cuál fue cuál.

**Quién decidió:** usuario.

## 2026-09-08 — Nota 2: detección de origen MANUAL sin UI nueva

**Decisión:** `MANUAL` no es una pantalla ni un campo de entrada aparte —
es una detección en el buscador que ya existe: si el texto tecleado hace
match exacto con un `codigoBarras` del catálogo, el origen es `MANUAL`; en
cualquier otro caso de selección desde resultados, es `BUSQUEDA`. `ESCANEO`
sigue viniendo solo del componente scanner. El cajero no ve ni elige nada
distinto.

**Alternativas descartadas:** agregar un campo de entrada dedicado para
"digitar código de barras", separado del buscador general — descartada
porque la distinción que importa (escáner falló vs. búsqueda por nombre)
ya se puede inferir sin pedirle al cajero una acción extra: un match
exacto de código de barras no ocurre por casualidad tecleando un nombre.

**Quién decidió:** usuario, a partir de una pregunta del agente que
decidió no resolver por su cuenta.

## 2026-09-08 — Nota 3: `Merma.valor` se renombra a `valorVenta` / `valorCosto`

**Decisión:** el campo `Merma.valor` se renombra a `valorVenta` (precio de
venta, lo que ve el cajero — sin cambio de cálculo ni de significado). El
costo de una merma (`valorCosto`) no es un campo de `Merma`: se calcula en
el reporte de Fase 2 vía `JOIN lotes ON mermas.lote_id = lotes.id`, sin
columna nueva en `mermas`, porque `lotes.costo_unitario` no cambia después
de creado.

**Alternativas descartadas:** dejar el campo como `valor` a secas y que el
significado se infiera por contexto — descartada porque en algún momento
alguien va a comparar el valor de venta con el de costo de la misma
merma, y dos números con el mismo nombre genérico es un error esperando a
pasar. Agregar una columna `valor_costo` congelada en `mermas` — descartada
porque el costo del lote no cambia después de creado, así que el `JOIN`
siempre da el mismo resultado; la columna sería puro dato redundante.

**Quién decidió:** usuario.

## 2026-09-08 — Nota 4: Inventario se alinea a los 4 estados de vencimiento de Alertas

**Decisión:** el semáforo de vencimiento de Inventario deja de tener 3
estados (`ok`/`pronto`/`critico`) y pasa a los mismos 4 que Alertas
(`VENCIDO`/`CRITICO`/`ADVERTENCIA`/`OK`), calculados por el servidor.
`GET /api/lotes` devuelve `estadoVencimiento` y `diasParaVencer` ya
resueltos; el cliente no recalcula fechas en ningún lado.
`fecha.util.ts` (`diasHasta`, `estadoFefo`) queda sin ningún consumidor
tras el rediseño de Inventario en la Tarea 9 (verificado por grep: ningún
otro archivo del frontend las usa) y se borra en ese mismo turno.

**Alternativas descartadas:** mantener Inventario en 3 estados porque
operativamente "vencido" y "por vencer ya" llevan a la misma acción del
técnico (retirar el lote) — descartada explícitamente por el usuario: dos
escalas distintas para la misma fecha es una discrepancia garantizada, y
la de Inventario es la que el cajero usa para decidir qué vender primero.

**Quién decidió:** usuario.

## 2026-09-08 — D4: origen de captura del código, auditado

**Decisión:** `venta_detalle` y `movimientos_stock` ganan
`origen_captura VARCHAR CHECK IN ('ESCANEO','MANUAL','BUSQUEDA')`. El
backend no distingue funcionalmente escaneo de tecleo para la búsqueda en
sí (ambos alimentan la misma búsqueda), pero sí registra de dónde vino
cada línea, para auditar (un turno 100% manual es sospechoso o el escáner
está roto) y para medir si la promesa de "sin hardware especializado" se
cumple en la práctica. El registro de lote nuevo también acepta ambas
formas de identificar el producto al darlo de alta.

**Alternativas descartadas:** no registrar el origen — descartada porque
cuesta una sola columna y habilita dos usos concretos (auditoría y
medición de producto) que de otro modo no se podrían responder nunca
retroactivamente.

**Quién decidió:** usuario.

## 2026-09-08 — Corrección: `movimientos_stock.origen_captura` es NULLABLE, nunca 'BUSQUEDA' por convención

**Decisión:** corrige la entrada "D4: origen de captura del código,
auditado" de más arriba. `movimientos_stock.origen_captura` pasa a ser
`NULLABLE` y queda en `NULL` para las filas que genera una merma — una
merma no se busca ni se escanea, es una selección de una lista, así que
`'BUSQUEDA'` sería un dato inventado. `venta_detalle.origen_captura` se
queda `NOT NULL`: ahí siempre hay un origen real. Regla general agregada
al contrato: ninguna columna de auditoría se rellena con un valor por
convención — si el dato no aplica, es `NULL`.

**Alternativas descartadas:** `origen_captura = 'BUSQUEDA'` fijo en el
servidor para las mermas (propuesta original del agente, aplicada por
defecto sin preguntar) — descartada porque contamina cualquier métrica
que cuente proporción de operaciones por escaneo/tecleo/búsqueda: una
merma metida en `BUSQUEDA` infla ese denominador con un valor falso que
nadie va a poder distinguir de una búsqueda real seis meses después.

**Quién decidió:** usuario, revirtiendo una decisión que el agente había
aplicado por defecto sin preguntar. Feedback explícito: una decisión que
toca el esquema (`NOT NULL` vs. `NULLABLE`, tipos, tablas) siempre se
pregunta antes, nunca se aplica por defecto — a diferencia de una decisión
reversible solo en código de aplicación, donde aplicar una propuesta
razonable por defecto sí está bien.

## 2026-09-08 — Corrección: sin `diasParaVencer` en `GET /api/lotes`, `diasHasta` sobrevive

**Decisión:** corrige la entrada "Nota 4: Inventario se alinea a los 4
estados..." de más arriba. El servidor calcula y devuelve
`estadoVencimiento` (regla de negocio, con umbrales configurables — el
cliente nunca la recalcula), pero **no** agrega `diasParaVencer`. El
servidor manda el dato (`fechaVencimiento`); el cliente sigue calculando
el texto de display ("En 18 días") con la fecha del propio dispositivo,
usando `fecha.util.diasHasta` — esa función no queda huérfana. Solo
`estadoFefo`/`EstadoFefo` pierden su consumidor (la derivación del
badge/estado, que ahora resuelve el servidor) y se borran en la Tarea 9.

**Alternativas descartadas:** que el servidor calcule y devuelva
`diasParaVencer` como número fijo (propuesta original del agente) —
descartada porque es una resta que caduca, no una regla de negocio: si la
pantalla queda abierta toda la noche, un número mandado por el servidor
se vuelve falso y nada lo actualiza. Un semáforo mal actualizado por reloj
desincronizado es tolerable (es solo texto informativo); un semáforo de
negocio recalculado en el cliente no lo es — de ahí la distinción entre
qué se manda como dato (`fechaVencimiento`) y qué se manda como regla
(`estadoVencimiento`).

**Quién decidió:** usuario, corrigiendo una propuesta del agente que
mezclaba "el cliente no recalcula reglas" con "el cliente no calcula
nada", cuando son principios distintos.

## 2026-09-08 — Revisión de esquema (post-T7, pre-T8): 4 correcciones

**Decisión 1 — UNIQUE de lotes:** pasa de `(botica_id, codigo)` a
`(botica_id, producto_id, codigo)`. El código de lote lo asigna el
laboratorio, no la botica — dos productos de fabricantes distintos
pueden traer el mismo código, y `(botica_id, codigo)` lo habría
rechazado como si fuera un duplicado real.
**Alternativas descartadas:** mantener `(botica_id, codigo)` y resolver
la colisión concatenando el código con el id de producto al guardar —
descartada porque ensucia un dato que el laboratorio ya trae limpio,
solo para acomodar una restricción mal alcanzada.
**Quién decidió:** usuario.

**Decisión 2 — el precio de venta es de `presentaciones`, no de
`lotes`.** `lotes.precio_unitario` se elimina. El precio que el
cliente ve en el estante es del catálogo (la presentación con
`factor_conversion = 1`, "Unidad") y no cambia por reposición; el
costo sí es del lote porque cada reposición se compra distinto (D2).
Al revisar el mock original se confirmó que `lotes.precioUnitario` ya
era un dato derivado y duplicado de la presentación "Caja" en los 7
productos que la tenían — y que `MermaService` lo multiplicaba por una
cantidad en **unidades base**, lo que habría inflado cualquier merma
parcial real ~65 veces (precio de una caja de 100 tabletas contra el
precio real por tableta). Ese bug se corrige de paso al eliminar la
columna: `mermas.valor_venta` ahora se calcula con la presentación
"Unidad" del producto del lote.
**Alternativas descartadas:** mantener ambas columnas y sincronizarlas
en el Service — descartada porque dos fuentes de verdad para el mismo
número es exactamente el patrón que ya produjo el bug de Merma; una
sola columna que no se puede desincronizar es más simple y más
correcta.
**Quién decidió:** usuario, proponiendo el criterio; el agente lo
confirmó con evidencia del mock original antes de aplicarlo (no se
tocó el esquema hasta tener esa confirmación, como se pidió).

**Decisión 3 — `TIMESTAMPTZ` en toda columna de fecha+hora, nunca
`TIMESTAMP` + `ALTER DATABASE ... SET timezone`.** Con `TIMESTAMPTZ`
el valor almacenado es un instante inequívoco (internamente UTC)
sin importar la zona horaria de la sesión que escribe — una migración
corrida desde pgAdmin, el backend en `America/Lima`, y un servidor de
despliegue en UTC (Tarea 10) escriben el mismo instante real. Con
`TIMESTAMP` simple + `ALTER DATABASE`, la protección depende de que
**cada** sesión nueva efectivamente herede ese default — un pool de
conexiones, una herramienta de administración, o un entorno gestionado
que no preserve el `ALTER DATABASE` (común en Postgres administrado en
la nube) rompen la garantía sin avisar, exactamente el mismo bug que
ya mordió al frontend con `LocalDate.now()` sin zona explícita
(`docs/BITACORA.md`). Las columnas de fecha de **negocio**
(`fecha_vencimiento`, `caja_diaria.fecha`, `reportes_digemid.periodo`)
son `DATE` y no participan de esta discusión — confirmado: un día
calendario no tiene zona horaria propia, lo único que importa es que
la aplicación calcule con la fecha de Lima (Regla 5) cuál día es antes
de escribirlo, sin importar el tipo de columna.
**Alternativas descartadas:** `TIMESTAMP` + `ALTER DATABASE botica_db
SET timezone='America/Lima'` — descartada por depender de un ajuste
global que cualquier conexión puede no heredar, en vez de una garantía
que viaja con cada valor.
**Pendiente para la Tarea 8:** la conexión JDBC del backend todavía
necesita fijar su propia zona horaria de sesión explícitamente (Regla
5) — `TIMESTAMPTZ` resuelve el almacenamiento, no exime de eso.
**Quién decidió:** usuario, pidiendo recomendación con argumentos; el
agente recomendó `TIMESTAMPTZ` y quedó aplicado.

**Decisión 4 — `ON DELETE CASCADE` revisado FK por FK.** Toda FK que
colgaba directo de `boticas` (y por lo tanto un `DELETE` accidental de
una fila de `boticas` podía arrastrar el historial completo de esa
botica) pasa a `RESTRICT`: `productos`, `presentaciones`, `lotes`,
`caja_diaria`, `ventas`, `venta_detalle`, `movimientos_caja`,
`mermas`, `movimientos_stock`, `clientes`, `comprobantes_electronicos`,
`kardex`, `reportes_digemid`. Las boticas no se borran, se desactivan
(columna `activo`) — anotado en el `COMMENT` de la tabla. Los `CASCADE`
que se mantuvieron son composición real, donde la fila hija no tiene
sentido sin su padre y el padre ya está protegido de borrado accidental
por otras FK en `RESTRICT`: `presentaciones.producto_id`,
`venta_detalle.venta_id`, `movimientos_caja.caja_id`,
`kardex.producto_id`, `lotes_fraccionados.lote_id`.
**Alternativas descartadas:** dejar `CASCADE` en las FK hacia `boticas`
confiando en que nunca se ejecute un `DELETE` directo sobre esa tabla
— descartada porque el propósito de la FK es justamente no depender de
que nadie cometa ese error; `RESTRICT` lo hace estructuralmente
imposible en vez de solo indeseable.
**Quién decidió:** usuario.

## 2026-09-08 — Tarea 10: infraestructura de despliegue

**Decisión:** Render (backend, Docker) + Vercel (frontend, build
estático) + Neon (Postgres administrado). Detalle de configuración en
`docs/DESPLIEGUE.md`.

**Alternativas descartadas:** Supabase en vez de Neon para la base de
datos — descartada **no por límites técnicos** (con el uso diario de
este proyecto, ambos son equivalentes: mismo Postgres, mismos límites
de plan gratuito en la práctica), sino porque la propia propuesta
técnica del curso descarta Supabase por nombre. Usarlo igual habría
obligado a explicar ante el profesor la distinción entre "BaaS"
(Supabase, que además da auth/storage/realtime que este proyecto no
usa — la app ya tiene su propia capa JDBC/Spring Security) y "Postgres
administrado puro" (Neon, exactamente lo que el proyecto necesita) sin
ganar nada a cambio de esa explicación.

**Quién decidió:** usuario.
