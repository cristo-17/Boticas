# Regla de estado — quién es dueño de qué

La app corre **zoneless** (sin `zone.js`). Todo estado que cambie con el
tiempo vive en `signal()`/`computed()`, nunca en una propiedad plana —
una propiedad plana mutada fuera de un evento de plantilla no dispara
change detection (ver el bug del toast en la Tarea 2: un `setTimeout`
que mutaba una propiedad plana simplemente no se pintaba).

## El servicio es el único dueño de los datos del servidor

- Cada servicio guarda su estado en un `signal()` **privado** y expone
  la versión de solo lectura con `.asReadonly()`.
- Los métodos del servicio devuelven `Observable<T>` (para espejar
  `HttpClient` — ver más abajo) pero **también** actualizan el signal
  del propio servicio dentro de un `tap()`, antes de devolver el
  observable. Así el servicio queda consistente aunque el llamador no
  se suscriba con intención de leer el valor emitido.
- `cargando` y `error` de cada recurso son señales del servicio, al
  lado de los datos — no se recalculan ni se guardan en el componente.

## El componente nunca guarda el resultado de un `subscribe()`

Un componente de pantalla:
- Lee los signals del servicio directamente en la plantilla
  (`inventarioService.lotes()`, `inventarioService.cargando()`…).
- Llama a los métodos del servicio para **disparar** una acción
  (`.subscribe()` sin guardar nada, o encadenado a un efecto puntual
  como cerrar un modal o mostrar un toast — nunca `this.datos = ...`).

```ts
// BIEN — el componente solo lee signals y dispara acciones
readonly lotes = this.inventario.lotes;
readonly cargando = this.inventario.cargando;
cargar(): void {
  this.inventario.listarLotes(this.filtro()).subscribe();
}

// MAL — el componente se vuelve una segunda fuente de verdad
buscar(): void {
  this.inventario.listarLotes(this.filtro()).subscribe(data => (this.lotes = data));
}
```

## Estado local sí puede vivir en el componente

Lo que **no** viene del servidor — un filtro sin aplicar todavía, el
carrito de una venta en curso, el paso actual de un formulario, si un
acordeón está abierto — es estado de sesión de esa pantalla, no del
servicio. Ahí sí corresponde un `signal()` propio del componente (o de
un servicio *scoped* al feature, como `CarritoService` en punto de
venta, que tampoco es un recurso REST).

## Por qué `Observable<T>` y no solo signals

Los métodos del servicio devuelven `Observable<T>` a propósito: es la
firma que va a tener `HttpClient` cuando se conecte el backend real.
`simulate()` (en `mock-utils.ts`) envuelve el valor mock en
`of(...).pipe(delay(...))` para que el consumo (loading real, no
resuelto en el mismo tick) sea idéntico al de una llamada HTTP. El día
que se cambie el mock, solo cambia el cuerpo de cada método — nunca la
firma, y nunca la regla de quién es dueño del signal.
