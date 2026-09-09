# Contrato de API

Extraído de `frontend/src/app/core/services/*.ts` y `core/models/*.ts`, y
revisado en tres rondas de decisiones con el usuario (2026-09-08) — el
detalle de cada decisión, con su alternativa descartada, está en
`docs/DECISIONES.md`. Este documento es la fuente de verdad del contrato:
si el backend necesita algo distinto, se cambia acá primero y en el
frontend a la vez (Regla 11 de `CLAUDE.md`).

Los tipos TypeScript se anotan junto a cada campo, con su equivalente Java
sugerido cuando no es obvio.

Formato de error, igual en todos los módulos (Regla 7):
```json
{
  "timestamp": "2026-09-08T19:32:10-05:00",
  "status": 409,
  "error": "CAJA_YA_ABIERTA",
  "mensaje": "Ya existe una caja abierta para hoy",
  "path": "/api/caja/abrir"
}
```

**Multi-botica (decisión D1):** toda tabla operativa lleva `botica_id`, y
todo endpoint que toca esas tablas se filtra por el `botica_id` del JWT del
usuario autenticado — **nunca** por un parámetro que mande el cliente. No
se repite esta nota en cada endpoint; aplica a todos salvo que se diga lo
contrario.

**Ninguna columna de auditoría se rellena con un valor por convención.**
Si un dato de auditoría (como `origen_captura`) no aplica a una operación
concreta, la columna es `NULLABLE` y queda en `NULL` — nunca se inventa un
valor "razonable" para no dejarla vacía. Un `NULL` es honesto sobre lo que
no se sabe o no aplica; un valor inventado se convierte en un reporte
equivocado meses después, cuando ya nadie recuerda que era una
convención y no un dato real.

## Envoltura de paginación

Aplica a todo listado marcado "PAGINADO". Parámetros de query:
`?pagina=0&tamano=20&orden=fechaVencimiento,asc` (`pagina` default 0,
`tamano` default 20 y tope duro 100, `orden` validado contra lista blanca
por endpoint — Anexo D de `docs/prompts/PROMPT-AGENTE-BACKEND-QA.md`).

```json
{
  "contenido": [],
  "pagina": 0,
  "tamano": 20,
  "totalElementos": 137,
  "totalPaginas": 7
}
```

Listados paginados: lotes de inventario, ventas del día, movimientos de
caja, alertas, mermas. Búsqueda de productos **no** se pagina: límite duro
de 20 resultados + indicador de "sigue escribiendo" en el cliente.

## Configuración de negocio

### `GET /api/config`

Sin entrada. Se llama **una sola vez al iniciar la app** (guardado en un
signal de un nuevo `ConfigService`), no en cada pantalla.

```json
{
  "igv": 0.18,
  "umbralStockBajo": 15,
  "descuadreLeve": 10.00,
  "motivosMerma": ["Vencimiento", "Rotura", "Deterioro", "Robo o pérdida", "Otro"],
  "motivosQueRequierenObservacion": ["Robo o pérdida", "Otro"],
  "vencimiento": { "criticoDias": 30, "advertenciaDias": 90 }
}
```

**Excepción importante:** la tasa de IGV vive acá para cálculos en curso,
pero además viaja **congelada en la cabecera de cada venta** (`Venta.igv`
ya la trae calculada). `/api/config` dice cuánto es el IGV *hoy*; una venta
vieja no se recalcula si el IGV cambia mañana.

Reemplaza las constantes que hoy están hardcodeadas y duplicadas en el
cliente: `TASA_IGV` (`venta.service.ts` y `carrito.service.ts`),
`UMBRAL_STOCK_BAJO` (`inventario.service.ts` e `inventario.ts`),
`UMBRAL_DESCUADRE_LEVE` (`cierre.ts`), `MOTIVOS_MERMA` /
`MOTIVOS_QUE_REQUIEREN_OBSERVACION` (`merma.service.ts`), y los umbrales
FEFO de `fecha.util.ts` (`estadoFefo`).

---

## Auth

### `POST /api/auth/login`

Entrada (`CredencialesLogin`):
```json
{ "usuario": "rosa.quispe", "password": "1234", "turno": "Tarde" }
```
`turno`: `"Mañana" | "Tarde" | "Noche"`. El usuario lo elige libremente en
cada login — **no se valida contra un horario** (decisión, hueco 7). Lo
único que lo limita es la regla de caja: un mismo usuario no puede tener
dos cajas abiertas.

Salida (`Usuario`, hoy) → en la Tarea 12 esto se reemplaza por un JWT.
```json
{
  "id": 1,
  "nombre": "Rosa Quispe",
  "usuario": "rosa.quispe",
  "rol": "Técnica farmacéutica",
  "turno": "Tarde",
  "sede": "Botica San Lucas · Av. Grau 412"
}
```
`id`: **número**, no string (decisión, hueco 9 — `BIGSERIAL` entra sin
problema en el rango seguro de `number` en JS).

**Por D1 (multi-botica):** el JWT de la Tarea 12 debe llevar `id`, `rol`,
`turno` **y `botica_id`** — no solo los tres que decía el prompt original
de la Tarea 12. Anotado para no perderlo quince tareas después.

Errores: `401 CREDENCIALES_INVALIDAS`.

### `POST /api/auth/logout`

Sin entrada. Salida: `204 No Content`. Errores: `401 NO_AUTENTICADO`.

### `GET /api/auth/yo`

Sin entrada. Salida: `Usuario | null`. **Se implementa aunque hoy no lo
llame ningún componente** — es lo que rehidrata la sesión al recargar en
la Tarea 12 (confirmado por el usuario).

---

## Caja

Modelos: `CajaDiaria`, `MovimientoCaja`, `AbrirCajaRequest`,
`CerrarCajaRequest`, y dos nuevos: `ResumenCierreResponse` (pre-conteo) y
los campos que se agregan a `CajaDiaria` al cerrar.

### `GET /api/caja/hoy`

Sin entrada. Salida (`CajaDiaria | null`):
```json
{
  "id": 1001,
  "fecha": "2026-09-08",
  "usuarioId": 1,
  "usuarioNombre": "Rosa Quispe",
  "turno": "Tarde",
  "montoApertura": 100.00,
  "horaApertura": "2026-09-08T14:02:00-05:00",
  "horaCierre": null,
  "montoContado": null,
  "diferencia": null,
  "montoEsperado": null,
  "semaforoDescuadre": null,
  "observaciones": null,
  "abierta": true
}
```
`montoEsperado` y `semaforoDescuadre` son `null` mientras la caja sigue
abierta — **no se calculan ni se exponen hasta el cierre**, es la mitad
del control de conteo ciego (ver `POST /api/caja/cerrar`).

### `POST /api/caja/abrir`

Entrada (`AbrirCajaRequest`): `{ "montoInicial": 100.00, "turno": "Tarde" }`
Salida: `CajaDiaria` (`abierta: true`).
Errores: `409 CAJA_YA_ABIERTA`, `400 MONTO_INVALIDO`.

### `GET /api/caja/{id}/movimientos` — PAGINADO

Salida: envoltura de paginación con `contenido: MovimientoCaja[]`. Se
**implementa** aunque `CierreComponent` no lo llame hoy (lee el signal
poblado por `abrirCaja()`) — al conectar el backend real, `CierreComponent`
sí tendrá que llamarlo, porque ya no habrá datos precargados en memoria.
Errores: `404 CAJA_NO_ENCONTRADA`.

### `GET /api/caja/{id}/resumen-cierre` — nuevo, ANTES de contar

Sin cuerpo. Se llama al entrar a la pantalla de Cierre, **antes** de que
el cajero cuente el efectivo.
```json
{
  "totalVentasEfectivo": 1284.50,
  "totalVentasDigital": 340.00,
  "cantidadVentas": 52,
  "cantidadMovimientos": 4
}
```
**Deliberadamente NO incluye `montoEsperado`.** El total digital (Yape +
tarjeta) sí se muestra antes, porque no se cuenta físicamente y no
compromete el control. Errores: `404 CAJA_NO_ENCONTRADA`,
`409 CAJA_NO_ABIERTA`.

### `POST /api/caja/cerrar` — DESPUÉS de contar

Entrada (`CerrarCajaRequest`):
```json
{ "montoContado": 1445.10, "observaciones": "Faltante por vuelto mal dado" }
```

Salida: `CajaDiaria` con `abierta: false` y, recién ahora, llenos:
```json
{
  "...": "...",
  "montoContado": 1445.10,
  "montoEsperado": 1449.50,
  "diferencia": -4.40,
  "semaforoDescuadre": "LEVE",
  "abierta": false
}
```
`semaforoDescuadre`: `"EXACTO" | "LEVE" | "GRAVE"`, calculado por el
servidor (`|diferencia| < 0.01` exacto, `≤ 10.00` leve — de `/api/config`
`descuadreLeve` —, el resto grave).

Errores: `409 CAJA_NO_ABIERTA`, `400 MONTO_INVALIDO`.

**Cambio de comportamiento requerido en el frontend (Tarea 9), no es un
bug de hoy:** `cierre.html` (líneas 20-23) muestra "Efectivo esperado en
caja" de forma incondicional, calculado en el cliente
(`caja.service.ts::efectivoEsperado`), visible **antes** de que el cajero
cuente. Eso contradice el conteo ciego que pide esta decisión. La Tarea 9
tiene que: quitar `efectivoEsperado` del cliente, mostrar solo
`totalVentasDigital` (de `resumen-cierre`) antes de contar, y mostrar
`montoEsperado`/`diferencia`/`semaforoDescuadre` recién en la respuesta de
`POST /cerrar`. Queda anotado para que nadie lo "arregle" de vuelta a como
está ahora.

---

## Productos

Modelos: `Producto`, `PresentacionProducto`.

### `GET /api/productos?buscar={query}` — límite duro 20, no paginado

Salida (`Producto[]`, máximo 20):
```json
[
  {
    "id": 1,
    "nombre": "Paracetamol 500 mg",
    "laboratorio": "Genfar",
    "categoria": "Analgésicos",
    "codigoBarras": "7751234000118",
    "alertaVencimiento": null,
    "presentaciones": [
      { "id": 11, "etiqueta": "Caja", "detalle": "100 tabletas · lote L-2405A", "precio": 12.90 },
      { "id": 12, "etiqueta": "Blíster", "detalle": "10 tabletas", "precio": 1.80 },
      { "id": 13, "etiqueta": "Unidad", "detalle": "1 tableta", "precio": 0.20 }
    ]
  }
]
```
Regla de negocio: `query` vacío devuelve los primeros resultados; si no,
filtra por nombre/laboratorio/código de barras. El backend trunca a 20; el
frontend debe mostrar "sigue escribiendo" mientras no llega la respuesta
(hoy no lo hace — pendiente para T9).

### `GET /api/productos/codigo/{codigoBarras}`

Salida: `Producto` si existe. **`404 PRODUCTO_NO_ENCONTRADO`** si no
(decisión, hueco 3 — no `200` con cuerpo vacío). **El interceptor de
errores del frontend no debe mostrar toast para este código**: es un
resultado esperado del flujo de escaneo, no una falla. Agregar
`PRODUCTO_NO_ENCONTRADO` a una lista de "códigos silenciosos" en
`error.interceptor.ts` (Tarea 9).

### `GET /api/productos/mas-vendidos`

Sin `?turno=` — se elimina del contrato (decisión: ni el método ni el
componente lo mandaban; no se agrega una dimensión que ninguna pantalla
usa). Salida: `Producto[]` (6, sin paginar — es un atajo de KPI, no un
listado que crezca).

### `obtenerPorId` deja de ser un endpoint

`ProductoService.obtenerPorId(id)` (síncrono, hoy lee un catálogo completo
en memoria) **no se convierte en `GET /api/productos/{id}`**. Sus dos
llamadores (`VentaService.registrarVenta`, y `MermaScreen` — este último
de forma redundante, ver Merma más abajo) se resuelven como lookups
internos del DAO dentro del Service del backend (`ProductoDao.obtenerPorId`
usado por `VentaService`), nunca expuestos por HTTP. Si en el futuro
aparece una pantalla de detalle de producto navegable por URL, el endpoint
se agrega en ese turno — no antes.

---

## Inventario (lotes)

Modelos: `Lote`, `FiltroLotes`, `NuevoLoteRequest`.

### `GET /api/lotes?categoria=&vencimiento=&stockBajo=` — PAGINADO

Salida: envoltura de paginación con `contenido: Lote[]`:
```json
{
  "id": 1,
  "productoId": 1,
  "productoNombre": "Paracetamol 500 mg x100",
  "categoria": "Analgésicos",
  "codigo": "L-2405A",
  "fechaVencimiento": "2027-03-12",
  "stock": 240,
  "ubicacion": "A-2",
  "precioUnitario": 12.90,
  "estadoVencimiento": "OK",
  "stockEstado": "OK"
}
```
**`precioUnitario` ya no es una columna de `lotes` (revisión de
esquema 2026-09-08, `docs/DECISIONES.md`)** — el servidor lo resuelve
con un `JOIN` a `presentaciones` (la fila con `factor_conversion = 1`,
"Unidad") del producto del lote. El campo se queda en la respuesta con
el mismo nombre porque el modelo `Lote` del frontend no cambia; lo que
cambia es de dónde sale en el backend. El precio de venta es del
catálogo (no cambia por reposición); solo el costo es del lote.

Orden FEFO resuelto en SQL. **`costoUnitario` no se expone en esta
respuesta — confirmado.** El precio de compra es información del dueño,
no hay motivo para que llegue al navegador de un cajero. El reporte de
Fase 2 va a necesitar su propio endpoint (`GET /api/reportes/...`, a
definir cuando se construya) con restricción de rol `ADMINISTRADOR` —
anotado en `docs/ESTADO.md`, Fase 2.

**Decisión (nota 4): Inventario se alinea a los mismos 4 estados que
Alertas** — `estadoVencimiento: "VENCIDO" | "CRITICO" | "ADVERTENCIA" |
"OK"`, calculado por el servidor con la regla de la tabla de Alertas más
abajo. **El estado nunca se recalcula en el cliente**, aunque el cliente
tenga la fecha — es la regla de negocio (umbrales configurables) la que
decide qué vender primero, no debe depender del reloj del dispositivo.
Aplico el mismo principio al stock: `stockEstado: "OK" | "CRITICO" |
"AGOTADO"`, con las mismas reglas que usa Alertas (`≤15` / `=0`) — antes
solo había un booleano `soloStockBajo` de filtro, ahora el estado real
viaja en cada fila.

**Corrección: NO se agrega `diasParaVencer`.** `estadoVencimiento` es una
regla de negocio (correcto que la calcule el servidor y no se recalcule);
"cuántos días faltan" es una resta que caduca — si la pantalla queda
abierta toda la noche, un número fijo mandado por el servidor se vuelve
falso y nada lo actualiza. El servidor manda el dato (`fechaVencimiento`),
el cliente calcula el texto de display ("En 18 días") con la fecha del
propio dispositivo, exactamente como hace hoy. Si el reloj del celular
está mal, el texto de días se ve raro, pero el semáforo — lo que de verdad
decide qué vender — sigue siendo el del servidor.

`FiltroLotes.vencimiento` gana un cuarto valor:
`'todos' | 'ok' | 'advertencia' | 'critico' | 'vencido'`. El chip "Menos de
30 · vencido" de `inventario.ts` se separa en dos chips (Tarea 9).

**Consecuencia en `fecha.util.ts`, corregida:** con `estadoVencimiento` ya
resuelto por el servidor, `estadoFefo` (y su tipo `EstadoFefo`) pierden su
único uso real — `inventario.ts::filaDeLote()` deja de llamarla para
derivar el badge/estado. **`diasHasta` sí sobrevive**: sigue siendo la
función que `filaDeLote()` usa para calcular el texto de display ("En 18
días") a partir de `fechaVencimiento`, ahora con la fecha del dispositivo
en vez de con datos del mock. Solo `estadoFefo`/`EstadoFefo` quedan sin
consumidor y se borran en la Tarea 9; `diasHasta` se queda.

### `GET /api/lotes?productoId=` — PAGINADO, reemplaza `?productoNombre=`

Pasa a ser necesario: es el segundo paso del flujo de Merma rediseñado
(ver abajo). Cambia de filtrar por `productoNombre` (string parcial) a
filtrar por `productoId` (exacto) — más barato y sin ambigüedad de nombres
repetidos. Ajusta la firma en `inventario.service.ts`.

### `POST /api/lotes`

Entrada (`NuevoLoteRequest`), **gana `costoUnitario` (D2) y pierde
`precioUnitario`** (revisión de esquema 2026-09-08):
```json
{
  "productoId": 1,
  "codigo": "L-2601A",
  "fechaVencimiento": "2027-08-01",
  "stock": 100,
  "ubicacion": "A-3",
  "costoUnitario": 8.40
}
```
`costoUnitario`: obligatorio (`NOT NULL` en `lotes`), es el dato que no se
puede reconstruir después (D2). El formulario de esta pantalla, que hoy es
"el botón que no hace nada", tiene que capturarlo desde que se construya
en la Tarea 11 — no se puede agregar después sin perder los lotes ya
creados sin costo. **Ya no manda `precioUnitario`**: el precio de venta
es del catálogo (`presentaciones`, fijado al crear el producto), no algo
que se vuelve a capturar cada vez que llega un lote nuevo del mismo
producto.

Salida: `Lote` creado. Errores: `400`, `404 PRODUCTO_NO_ENCONTRADO`.

---

## Ventas

Modelos: `Venta`, `ItemVenta`, `NuevaVentaRequest`, `MetodoPago`.

### `GET /api/ventas?fecha=hoy` — PAGINADO

Se implementa aunque hoy no lo llame ningún componente (declarado en
`venta.service.ts`, sin consumidor — se implementa igual, análogo a
`listarMovimientos`). Salida: envoltura de paginación con
`contenido: Venta[]` (misma forma que la salida de `POST /api/ventas`
abajo).

### `POST /api/ventas`

Entrada (`NuevaVentaRequest`), **gana `claveIdempotencia` e
`items[].origenCaptura` (D4)**:
```json
{
  "claveIdempotencia": "b3f1c2a0-7e4d-4f2a-9c1e-0a1b2c3d4e5f",
  "items": [
    { "productoId": 1, "presentacionId": 12, "cantidad": 2, "origenCaptura": "BUSQUEDA" }
  ],
  "metodoPago": "efectivo"
}
```
`claveIdempotencia`: UUID v4. **Se genera al confirmar el carrito (el
momento en que el cajero aprieta "Cobrar"), no dentro del método HTTP.**
Vive en el estado del servicio de venta (nuevo signal en `VentaService`,
no en `CarritoService` — el carrito se vacía al cobrar, la clave tiene que
sobrevivir un reintento), se reusa en cada reintento de red del mismo
POST, y se limpia recién cuando el backend responde `200`. Si se genera
dentro de `registrarVenta()` en cada llamada, cada reintento manda un UUID
distinto y la idempotencia no sirve de nada — es el punto central de esta
decisión.

`origenCaptura`: `"ESCANEO" | "MANUAL" | "BUSQUEDA"`, por línea. **`MANUAL`
no es una pantalla aparte, es una detección en el buscador que ya existe
(nota 2):** en `PuntoVentaScreen`, cuando el texto tecleado hace match
exacto con un `codigoBarras` del catálogo, el origen que se guarda es
`MANUAL`; en cualquier otro caso de selección desde resultados o más
vendidos, es `BUSQUEDA`. `ESCANEO` sigue viniendo exclusivamente de
`simularEscaneo()` (componente scanner). El cajero no ve ni elige nada
distinto — es solo la lógica de `buscar()`/`elegirProducto()` la que
decide qué guardar en la línea del carrito. Razón: un match exacto de
código de barras no se logra por casualidad tecleando un nombre.

`metodoPago`: sin cambios. El cliente solo manda ids, cantidad, método de
pago y origen — nombre, precio, subtotal, IGV y total los calcula el
servidor (Regla 8).

Salida: `Venta` (agrega `origenCaptura` por ítem, resto igual). Si
`claveIdempotencia` ya existe, responde `200` con la venta ya registrada
en vez de crear una segunda.

Errores: `409 SIN_CAJA_ABIERTA`, `422 STOCK_INSUFICIENTE`,
`400 PRESENTACION_INVALIDA`.

**Cambio requerido en el frontend (Tarea 9, nota 1):** `LineaCarrito`
(`carrito.service.ts`) gana `origenCaptura: OrigenCaptura`.
`CarritoService.agregar(producto, presentacion, origen: OrigenCaptura)`
recibe el origen como parámetro nuevo — se captura en el momento exacto
en que el producto entra al carrito, porque después de ese momento ya no
hay forma de reconstruirlo (no queda registro de si esa línea vino de un
escaneo o de una búsqueda). Excepción a "no se tocan componentes":
`PuntoVentaScreen` (llamador de `agregar()`) y `CarritoService` mismo.

---

## Merma

Modelos: `Merma`, `NuevaMermaRequest`, `MotivoMerma`.

### Flujo rediseñado: selectores en cascada, sin `listarLotes({})`

`MermaScreen` deja de pedir **todos** los lotes sin filtro
(`listarLotes({})`) para armar el selector producto→lote. El flujo nuevo:
1. El cajero busca el producto con `GET /api/productos?buscar=` (el mismo
   buscador de 20 resultados que usa Punto de Venta) — no hace falta traer
   todo el inventario.
2. Al elegir un producto, se piden sus lotes con
   `GET /api/lotes?productoId={id}` (ver arriba) — una lista corta,
   típicamente 1-3 lotes por producto, no necesita paginación real aunque
   el endpoint la soporte.

Esto elimina de raíz el problema de paginación en Merma (no hace falta
traer una lista completa) y también vuelve redundante la llamada a
`ProductoService.obtenerPorId` que hacía `MermaScreen.productosDisponibles`
— cada resultado de búsqueda ya trae `nombre`.

### `GET /api/mermas?fecha=hoy` — PAGINADO

Salida: envoltura de paginación con `contenido: Merma[]`:
```json
{
  "id": 3001,
  "loteId": 6,
  "productoNombre": "Omeprazol 20 mg x30",
  "loteCodigo": "L-2311D",
  "cantidad": 3,
  "motivo": "Vencimiento",
  "observacion": null,
  "valorVenta": 34.50,
  "usuarioId": 1,
  "fecha": "2026-09-08T11:15:00-05:00"
}
```
**Decisión (nota 3):** el campo se renombra de `valor` a `valorVenta` —
son dos números distintos (precio de venta vs. costo) y en algún momento
alguien los va a comparar, así que nunca se llama solo "valor". Se calcula
a precio de venta (`cantidad × precio de la presentación "Unidad" del
producto del lote` — ya no `lote.precioUnitario`, que no existe;
revisión de esquema 2026-09-08): es lo que ve el cajero en pantalla
("S/ 34.50 dados de baja"). El
costo (`valorCosto`) **no es un campo de `Merma`** — vive únicamente en el
reporte de Fase 2, calculado con `JOIN lotes ON mermas.lote_id = lotes.id`
en el momento de generar el reporte, sin columna nueva en `mermas`:
`lotes.costo_unitario` no cambia después de creado, así que el `JOIN` da
siempre el mismo resultado — no hay nada que congelar.

### `POST /api/mermas`

Entrada (`NuevaMermaRequest`): sin cambios —
`{ "loteId": 6, "cantidad": 3, "motivo": "Vencimiento", "observacion": null }`.
**No lleva `origenCaptura`**: el flujo de merma siempre selecciona el lote
de una lista, nunca escanea ni digita un código. **Corrección:**
`movimientos_stock.origen_captura` para las filas que genera una merma
queda en `NULL`, no `'BUSQUEDA'` — una merma no se busca ni se escanea, y
etiquetarla `BUSQUEDA` inventa un dato que contamina la métrica que
justifica la columna (si se cuenta qué proporción de operaciones fue por
escaneo, una merma metida en `BUSQUEDA` infla el denominador con un valor
falso). Por esto, `movimientos_stock.origen_captura` es **`NULLABLE`**,
a diferencia de `venta_detalle.origen_captura` que sigue siendo
`NOT NULL` (ahí siempre hay un origen real: toda venta pasa por escaneo,
tecleo o búsqueda).

Salida: `Merma` creada. Errores: `404 LOTE_NO_ENCONTRADO`,
`422 CANTIDAD_EXCEDE_STOCK`, `400 OBSERVACION_REQUERIDA`.

### `obtenerLotePorId` deja de ser un endpoint

Igual que `ProductoService.obtenerPorId`: `InventarioService
.obtenerLotePorId(loteId)` no se convierte en `GET /api/lotes/{id}`. Su
único llamador (`MermaService.registrarMerma`, para validar stock real
contra lo que manda el cliente) es un lookup interno del DAO dentro del
Service — el servidor nunca debe confiar en el stock que el cliente cree
tener, así que este lookup pasa a ser obligatorio en el backend real, no
opcional.

---

## Alertas

Modelos: `Alerta`, `ResumenDashboard` (reformado, ver abajo).

### `GET /api/dashboard/resumen`

Salida (`ResumenDashboard`, **reformada** — decisión, hueco 5: números
crudos, no frases; `ventasHoyTexto` y `cajaNota` desaparecen):
```json
{
  "ventasHoy": 2964.50,
  "ventasHoyVariacionPct": 12,
  "boletasHoy": 64,
  "productosPorVencer": 14,
  "productosPorVencerCriticos": 5,
  "stockCritico": 6,
  "stockAgotado": 2,
  "cajaEstado": "ABIERTA",
  "cajaHoraApertura": "2026-09-08T08:02:00-05:00"
}
```
Nombres de campo propuestos por mí (el usuario solo fijó "números, no
frases" y "enum, no frase" para `cajaEstado`) — ajústalos si no calzan con
lo que arma la pantalla de Alertas en la Tarea 11. **Deliberadamente no
incluye ningún monto esperado de caja** — consistente con el conteo ciego
del hueco 2, el dashboard tampoco debe filtrarlo antes de que se cuente.
El frontend arma los textos (`"+12% vs. ayer · 64 boletas"`,
`"Desde 08:02"`) con estos números usando `moneda.util.ts` y
`Intl.DateTimeFormat`, en vez de recibirlos ya armados.

Regla de negocio: todos los KPI con SQL agregado (`COUNT`/`SUM`/
`GROUP BY`), nunca trayendo todo a Java.

### `GET /api/alertas` — PAGINADO

Salida: envoltura de paginación con `contenido: Alerta[]` (forma sin
cambios respecto a la versión anterior de este documento).

**Reglas de generación, formalizadas (hueco 6)** — los umbrales salen de
`/api/config`, estos son los valores por defecto:

| Alerta | Regla |
|---|---|
| Vencimiento — `VENCIDO` | `fecha_vencimiento < hoy` (hoy = fecha de Lima) |
| Vencimiento — `CRITICO` | 0 a 30 días |
| Vencimiento — `ADVERTENCIA` | 31 a 90 días |
| Vencimiento — `OK` | más de 90 días — no genera alerta |
| Stock — `CRITICO` | `stock <= 15` (config `umbralStockBajo`) |
| Stock — `AGOTADO` | `stock = 0` |
| Caja sin cerrar | existe una caja `ABIERTA` cuya fecha operativa es anterior a hoy (**no** por horas transcurridas — con boticas 24/7 y turno noche, contar horas da falsos positivos cada madrugada) |

**Excepción explícita:** un lote vencido con `stock = 0` **no genera
alerta** — no hay nada que hacer con él.

---

## Métodos síncronos removidos del contrato

`ProductoService.obtenerPorId` e `InventarioService.obtenerLotePorId` no
tienen ni van a tener equivalente HTTP público
(`GET /api/productos/{id}`, `GET /api/lotes/{id}`). Se implementan
**exclusivamente** como métodos del DAO usados internamente por los
Services que los necesitan (`VentaService`, `MermaService`). No se
construyen "por si acaso" — si en el futuro aparece una pantalla de
detalle navegable por URL, el endpoint se agrega en ese turno.

## Pantallas del frontend a ajustar — excepciones a "no se tocan componentes" (Tarea 9 / 11)

| Pantalla | Cambio |
|---|---|
| Merma | Rediseño completo del flujo de selección: búsqueda de producto (`GET /api/productos?buscar=`) → lotes de ese producto (`GET /api/lotes?productoId=`). Deja de pedir `listarLotes({})`. Renombrar `merma.valor` a `valorVenta` en el mock y en `merma.html`/`merma.ts` (nota 3). |
| Caja · Cierre | Quitar `efectivoEsperado` calculado en cliente. Mostrar `totalVentasDigital` de `GET /resumen-cierre` antes de contar. Mostrar `montoEsperado`/`diferencia`/`semaforoDescuadre` solo tras `POST /cerrar`. Empezar a llamar `GET /{id}/movimientos` en vez de leer el signal precargado. |
| Punto de Venta | `LineaCarrito` gana `origenCaptura`; `CarritoService.agregar()` lo recibe como parámetro nuevo (nota 1). `PuntoVentaScreen` detecta `MANUAL` por match exacto de código de barras en el buscador (nota 2). `VentaService` genera y guarda `claveIdempotencia` al confirmar el carrito, no dentro de `registrarVenta()`. Agregar indicador de "sigue escribiendo" en la búsqueda (límite 20). |
| Alertas | Adaptar `AlertasScreen.kpis` al `ResumenDashboard` reformado (números en vez de texto) y formatear en el cliente. |
| Inventario | Alinear a los 4 estados de vencimiento del servidor (nota 4): `filaDeLote()` deja de calcular `estado` (lo lee de `estadoVencimiento`/`stockEstado`), pero sigue calculando `dias` con `diasHasta(lote.fechaVencimiento)` para el texto de display — esa función no desaparece. `FiltroLotes.vencimiento` gana `'vencido'`. `GET /api/lotes` pasa a estar paginado — agregar control de paginación. Solo `estadoFefo`/`EstadoFefo` de `fecha.util.ts` quedan sin consumidor y se borran en este turno; `diasHasta` se queda. |
| Registrar lote (nueva) | El formulario, cuando se construya en la Tarea 11, captura `costoUnitario` desde el día uno — no se puede agregar después sin perder los lotes ya creados sin costo. |

## Cierre de esta ronda de decisiones

Las 5 notas abiertas de la ronda anterior quedaron resueltas por el
usuario (detalle en `docs/DECISIONES.md`, entradas del 2026-09-08): nota 1
(`origenCaptura` en el carrito), nota 2 (detección de `MANUAL`), nota 3
(`valorVenta`/`valorCosto`) y nota 4 (Inventario a 4 estados, corregida
después: `diasParaVencer` no se agrega, `diasHasta` sobrevive).

Los dos puntos menores que el agente había cerrado con su propia
propuesta por defecto se revisaron con dos resultados distintos, que
quedan como principio de trabajo para el resto del proyecto:
- **`costoUnitario` fuera de `GET /api/lotes`** — confirmado tal cual (era
  reversible en código, sin tocar esquema).
- **`origen_captura = 'BUSQUEDA'` fijo para mermas** — corregido a `NULL`
  (era una decisión de esquema, `NOT NULL` vs. `NULLABLE`, y esas se
  preguntan siempre, nunca se aplican por defecto aunque parezcan de bajo
  impacto — ver la regla de columnas de auditoría al inicio de este
  documento).
