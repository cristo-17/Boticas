# Arquitectura

Mapa de carpetas y las fronteras que ese árbol no muestra por sí solo.
Las fronteras (qué es de servidor y qué es local, dónde va cada tipo
de estilo, qué es compartido vs. de una pantalla) están resumidas
también en `CLAUDE.md`, sección "Mapa de carpetas y fronteras" — este
archivo tiene el detalle completo.

## Backend (`backend/src/main/java/com/botica/backend/`)

- `controller/` — endpoints REST, sin lógica de negocio ni SQL.
- `service/` — reglas de negocio, `@Transactional` donde corresponda.
- `dao/` — interfaces de acceso a datos; `dao/impl/` — implementación JDBC.
- `model/` — entidades de dominio (sin anotaciones JPA).
- `dto/` — request/response DTOs con Bean Validation.
- `config/` — `CorsConfig`, `SecurityConfig`, etc.
- `exception/` — excepciones de negocio + `GlobalExceptionHandler`.
- `util/` — `Dinero` y otros helpers puros.
- `resources/db/migration/` — migraciones Flyway (desde la Tarea 7).

La regla de capas (Controller → Service → DAO → DaoImpl, ver "Las 12
reglas del backend" en CLAUDE.md) es lo que un `ls` no dice: nada
fuera de `dao/impl/` toca JDBC directamente, y nada fuera de
`service/` decide una regla de negocio. Un controller que arma SQL o
un DAO que valida una regla de negocio rompe la frontera aunque el
archivo esté en la carpeta "correcta".

## Frontend (`frontend/src/app/`)

- `core/models/` — modelos de dominio, uno por entidad.
- `core/services/` — un servicio por recurso, dueño del estado de
  servidor (ver su `README.md` — léelo antes de tocar cualquier
  servicio o componente).
- `core/utils/` — helpers puros (`fecha.util`, `moneda.util`).
- `shared/components/` — catálogo de componentes `bs-*` reutilizables
  entre pantallas (botón, card, input, modal, toast, banner de error,
  paginación...). Antes de construir un widget nuevo en una feature,
  revisar aquí si ya existe.
- `features/<pantalla>/` — una carpeta por pantalla, routing lazy
  standalone. Componentes que viven aquí son de una sola pantalla —
  si se necesitan en una segunda, se mueven a `shared/components/`,
  no se copian.
- `layout/` — shell, sidebar, bottom nav, header.

### Estado: servidor vs. local

Un `ls` muestra que existe `core/services/`, no que ahí vive **solo**
el estado que viene del servidor (o va hacia él) — ver la regla de
propiedad de estado en `core/services/README.md`. Estado genuinamente
local a una pantalla o sesión (un modal abierto, un carrito a medio
armar, un filtro sin aplicar, el texto crudo de un formulario antes de
validar) es un `signal()` de componente, o de un servicio acotado a la
feature (p. ej. `CarritoService`), nunca del servicio dueño del
recurso de servidor. La línea divisoria es "¿esto vino del servidor o
va a ir al servidor?", no "¿está en un servicio?".

### Estilos: `styles/` vs. `.scss` de una feature

`frontend/src/styles/` es la única fuente de verdad de diseño:
tokens (`_variables.scss`), resets (`_base.scss`), el catálogo de
componentes `bs-*` (`_components.scss`), utilidades (`_utilities.scss`).
Cualquier clase reutilizable entre pantallas vive ahí. El `.scss` de
una feature (p. ej. `cierre.scss`) es solo layout local de esa
pantalla — grillas, breakpoints propios, nombres `feature__parte` que
no tienen sentido fuera de ese componente. Si una regla de un `.scss`
de feature empieza a parecer reutilizable, se sube a `_components.scss`
con su propia clase `bs-*`, no se copia a la siguiente feature que la
necesite.
