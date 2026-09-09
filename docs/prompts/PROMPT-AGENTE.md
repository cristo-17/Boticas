# Prompt para el agente de IA — BoticaSys

Este documento contiene el prompt que se usó para construir el frontend de BoticaSys con un agente de IA, ya corregido con todo lo que aprendimos durante el proceso.

**Cómo usarlo:** el bloque de contexto (Parte 1) va en el primer mensaje siempre. Las tareas se dan **de a una**, esperando aprobación entre cada una. Al final está la explicación de por qué cada regla existe.

---

## Parte 1 — Bloque de contexto

> Va en el primer mensaje. Después el agente ya tiene la conversación, no hay que repetirlo.

```
Contexto del proyecto:
Estoy construyendo "BoticaSys", una PWA de gestión para boticas
independientes. El backend es Spring Boot con JDBC puro (sin JPA ni
Hibernate, es un requisito) sobre PostgreSQL, y el frontend es Angular
con Bootstrap 5.

En la carpeta /design tengo el diseño ya aprobado como HTML estático:
- BoticaSys.dc.html: todas las pantallas de la app maquetadas
- Guia-de-estilos.dc.html: el sistema de diseño (paleta, tipografía,
  espaciados, catálogo de componentes)
- styles.css y styles_2.css: los estilos de cada uno

Esa carpeta es SOLO referencia visual. No se compila ni se importa.
Es la fuente de verdad de cómo debe verse la aplicación.

IGNORA el archivo readme.md que está en /design: describe un sistema
de diseño distinto (rojo, esquinas cuadradas, tipografía Archivo) que
viene de la plantilla original y NO corresponde a este proyecto.
El diseño real es verde salud con esquinas redondeadas suaves, tal
como se ve en Guia-de-estilos.dc.html y BoticaSys.dc.html.

También hay un support.js en /design: es de la demo estática, no lo
migres.

REGLAS QUE APLICAN A TODO EL PROYECTO

1. MODO ZONELESS
   La app no tiene zone.js. Todo estado que cambie en el tiempo debe
   ser signal() o computed(), nunca una propiedad plana. Un setTimeout,
   una promesa o un subscribe que mute un campo normal actualiza el
   valor pero no repinta la vista.
   Además: computed() solo reacciona a signals leídos dentro de él.
   Leer un @Input o un FormControl.value adentro compila, devuelve un
   valor una vez, y nunca se actualiza. Para formularios usa
   toSignal(form.valueChanges, { initialValue: form.getRawValue() })
   y deriva los computed() de ahí.

2. PROPIEDAD DEL ESTADO
   El servicio es el único dueño de los datos que vienen del servidor.
   Guarda un signal() privado, lo expone con .asReadonly(), y cada
   método actualiza ese signal dentro de un tap() antes de devolver.
   El componente nunca guarda el resultado de un subscribe en una
   propiedad propia: solo lee los signals del servicio y llama a sus
   métodos. Los estados de cargando y error también viven en el
   servicio.
   Excepción: estado genuinamente local de una pantalla (un modal
   abierto, un filtro sin aplicar, un carrito a medio armar) sí puede
   ser un signal del componente.

3. UN SOLO VOCABULARIO DE CLASES
   Todas las clases de componente llevan prefijo bs- (bs-btn, bs-card,
   bs-badge...). NUNCA uses las clases de componente de Bootstrap
   (.btn, .card, .badge, .table, .modal, .form-select). De Bootstrap
   solo tomamos el grid (container, row, col-*) y utilidades de layout
   (d-flex, gap-*, mb-*).

4. CERO ESTILOS INLINE
   Nada de style="..." en las plantillas. Si el diseño pide algo que
   el catálogo no tiene, agrega una clase a _components.scss con un
   comentario explicando qué detalle formaliza.

5. NO IMPROVISES
   Si algo del HTML de diseño no calza en Angular, o si el mockup se
   contradice con la guía de estilos, PREGUNTA antes de decidir.

6. COMPONENTES STANDALONE
   Sin NgModules. Formularios con Reactive Forms y validación visible
   en el campo. Áreas táctiles de 44px y contraste AA.
   Cada componente en su carpeta con .ts, .html y .scss propio si
   necesita estilos locales.
```

---

## Parte 2 — Las tareas

> Una por mensaje. Espera aprobación antes de pasar a la siguiente.

### Tarea 1 — Consolidar el sistema de diseño

```
TAREA 1 — Consolidar el sistema de diseño

Lee ambos CSS y detecta duplicados y conflictos entre ellos. Genera
en frontend/src/styles/ una única fuente de verdad partida en:
_variables.scss (variables :root), _base.scss, _components.scss y
_utilities.scss, importados desde styles.scss.

Los tokens de Guia-de-estilos son los canónicos. Elimina el CSS que
solo servía para maquetar la demo estática.

Documenta en un comentario de cabecera qué conflictos encontraste y
cómo los resolviste.
```

**Qué revisar antes de aprobar:** que los colores sean los correctos (no los de la plantilla descartada), que el comentario de conflictos sea específico y no genérico, y que `ng serve` compile.

---

### Tarea 2 — Componentes compartidos

```
TAREA 2 — Extraer componentes compartidos

Del catálogo de la guía de estilos, crea componentes Angular
standalone en frontend/src/app/shared/components/ para: botón, campo
de formulario, card, badge, chip, modal de confirmación, toast, tabla
responsiva, skeleton loader, estado vacío e indicador
offline/sincronizando.

Cada componente usa exactamente las clases CSS que ya existen. Si
falta alguna, agrégala a _components.scss. Recibe estado por @Input
y emite por @Output.

Los campos de formulario deben implementar ControlValueAccessor para
integrarse de verdad con Reactive Forms: el estado de error se activa
solo cuando el control es inválido y fue tocado.

Al terminar quiero:
- Una página temporal en la ruta /playground que renderice todos los
  componentes en todos sus estados, para compararla contra
  Guia-de-estilos.dc.html en el navegador. La borraremos al final.
- Un resumen de qué clases agregaste a _components.scss y por qué.

Si algún componente del catálogo no tiene equivalente claro en el CSS,
pregúntame antes de improvisar.

CRITERIO DE ABSTRACCIÓN
Se abstrae lo que es el MISMO PATRÓN, no lo que simplemente "es un
campo" o "es una caja". Si un componente necesita tres @Input
condicionales para servir en dos sitios, probablemente no es el mismo
patrón y deben ser dos cosas distintas.
```

**Qué revisar antes de aprobar:** abre `/playground` y la guía de estilos en dos pestañas y compáralas componente por componente. Es el momento más barato para detectar diferencias visuales.

---

### Tarea 3 — Las pantallas, en tres bloques

```
TAREA 3 — Convertir las pantallas en features

Convierte cada pantalla de BoticaSys.dc.html en un feature bajo
frontend/src/app/features/, con su routing. El layout (sidebar de
escritorio, barra inferior móvil, header con estado de conexión) va
en frontend/src/app/layout/.

Reemplaza todos los datos hardcodeados por bindings a modelos
TypeScript en core/models/. Usa servicios con datos mock en
core/services/, pero con la firma real de la API.

FIRMAS DE SERVICIO
Cada método devuelve Observable<T> (para que la firma espeje
HttpClient) y lleva su endpoint anotado en un comentario encima:
  // GET /api/caja/hoy
  // POST /api/caja/abrir
Así, al conectar el backend, solo cambia el cuerpo del método.

ENTREGA POR BLOQUES — espera mi visto bueno entre cada uno:
  Bloque A: core/models + core/services con mocks + el layout completo
            (sidebar, barra inferior, header) y el routing vacío.
  Bloque B: punto-venta e inventario.
  Bloque C: caja (apertura y cierre), merma, alertas, login.

Mantén /playground funcionando hasta el final.
```

**Notas para cada bloque:**

*Bloque A* — Revisa los modelos y las firmas con calma: son el contrato con el backend. Corregirlos después obliga a tocar pantallas *y* DAOs de Spring.

*Bloque B* — Punto de venta es la pantalla más importante. Pide atención al flujo de escaneo (idle / buscando / encontrado / error), el modal de presentación con conversión de stock, el carrito con total siempre visible, y el badge de vencimiento. **Para el escáner de cámara, pide solo la UI con un botón de simulación** — la integración real con la cámara necesita librería, permisos y HTTPS, y va como tarea aparte.

*Bloque C* — En Caja: botón deshabilitado con monto inválido, bloqueo si ya hay caja abierta, semáforo de descuadre, confirmación en modal. En Merma: es destructiva, confirmación obligatoria, resumen en vivo con `computed()`, cantidad topada al stock. En Alertas: las tarjetas y filas navegan con el filtro ya aplicado. En Login: sin guard todavía.

---

### Tarea 4 — Cierre y limpieza

```
TAREA 4 — Cierre

1) Elimina features/playground y cualquier componente de scaffolding
   que ya no tenga uso. Actualiza el redirect de '' en app.routes.ts.
   Verifica con grep que no queden referencias colgando.

2) Hazme un resumen de qué queda pendiente para cuando conectemos el
   backend real.

3) Actualiza CLAUDE.md para reflejar el estado final: los gotchas
   descubiertos, los patrones que quedaron y la estructura real de
   carpetas.
```

---

## Parte 3 — Por qué cada regla existe

Estas reglas no salieron de un manual. Cada una nació de un bug real que encontramos.

| Regla | El bug que la originó |
|---|---|
| Todo estado es `signal()` | El toast se auto-cerraba con `setTimeout` sobre una propiedad plana. El valor cambiaba, la pantalla no. En modo zoneless no hay nada que dispare el redibujado. |
| `computed()` solo lee signals | En Merma, varios `computed()` leían `form.controls.X.value` directo. Se congelaron en su primer valor: la cantidad mayor al stock no invalidaba nada, el motivo "Otro" no activaba la observación obligatoria. Compilaba perfecto. Solo se detectó probando en el navegador. |
| El servicio es dueño del estado | `registrarVenta` resolvía nombre y precio buscando en la lista de resultados de búsqueda, que cambia con cada búsqueda nueva. Un producto agregado al carrito antes de buscar otra cosa se quedaba sin datos. |
| Un solo vocabulario `bs-*` | El mockup de inventario usaba `<table class="table">` de Bootstrap con estilos inline, contradiciendo su propia guía de estilos. Mezclar los dos vocabularios genera colisiones impredecibles según el orden de carga. |
| Cero estilos inline | La guía resolvía varios detalles con `style="flex:1;min-width:0"` repetido en cada pantalla. Formalizarlos como clases significa que cambiar el diseño se hace en un solo lugar. |
| Pregunta antes de improvisar | El mockup tenía un tercer patrón de campo que la guía no documentaba. Si el agente lo hubiera resuelto solo, habríamos terminado con dos sistemas de inputs paralelos. |
| Entrega por bloques | Siete pantallas de golpe son ~60 archivos imposibles de revisar. Por bloques, cada error se detecta antes de que se propague al siguiente. |

### Otro gotcha que apareció

**`takeUntilDestroyed()` dentro de `ngOnInit()` explota en runtime (NG0203).** Compila bien, pero rompe la pantalla completa, porque `ngOnInit` no es un contexto de inyección. La solución es capturar `destroyRef = inject(DestroyRef)` como campo de clase y pasarlo explícito:

```typescript
private destroyRef = inject(DestroyRef);

ngOnInit() {
  this.form.valueChanges
    .pipe(takeUntilDestroyed(this.destroyRef))
    .subscribe(...);
}
```

---

## Parte 4 — Cómo trabajar con el agente

Tres cosas que hicieron la diferencia:

**Entrega por bloques, siempre.** Un agente puede generar 60 archivos en un solo turno. No vas a poder revisarlos. Divide y aprueba de a poco.

**Exige verificación en el navegador, no solo compilación.** Los tres bugs más graves del proyecto compilaban sin errores y pasaban el type checker. Solo aparecieron al abrir la pantalla y usarla.

**Cuando el agente pregunte, respóndele con criterio, no con "haz lo que veas mejor".** Preguntó por el alcance del componente de campo y ofreció cuatro opciones. Elegir la correcta (no la más amplia) evitó un componente inflado de condicionales.
