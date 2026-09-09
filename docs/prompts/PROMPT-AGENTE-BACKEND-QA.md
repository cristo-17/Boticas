# BoticaSys — Fase 2: backend, integración, despliegue y QA

Continuación de `PROMPT-AGENTE.md` (fase de frontend, ya terminada).
Este documento es el guion completo para el agente: cómo se organiza la
documentación del proyecto, el bloque de contexto que va en el primer
mensaje, y diez tareas que se dan **de a una**, esperando aprobación
entre cada una.

**Datos reales del proyecto, ya confirmados:**

| Dato | Valor |
|---|---|
| Build del backend | **Maven** (`pom.xml`, `mvnw`) — no Gradle |
| Java | 25 |
| Spring Boot | 4.1.1 |
| Paquete raíz | `com.botica.backend` |
| Dependencias ya elegidas | Spring Web, Validation, **Spring Security**, JDBC API, PostgreSQL Driver, Lombok, DevTools |
| Base de datos | `botica_db` ya creada en PostgreSQL local vía pgAdmin |
| Carpeta `design/` | Eliminada del repositorio |

> El `README.md` actual dice `./gradlew`. Está desactualizado: el proyecto es
> Maven. Los comandos correctos son `./mvnw spring-boot:run`, `./mvnw test`,
> `./mvnw clean package`. Corregirlo es parte de la Tarea 5.

---

## Índice

- [Parte 0 — El sistema de documentación](#parte-0--el-sistema-de-documentación)
- [Parte 1 — Bloque de contexto](#parte-1--bloque-de-contexto-primer-mensaje-de-cada-sesión)
- [Parte 2 — Las diez tareas](#parte-2--las-diez-tareas)
- [Parte 3 — Riesgos conocidos](#parte-3--riesgos-conocidos-de-esta-fase)
- [Parte 4 — Cómo trabajar con el agente](#parte-4--cómo-trabajar-con-el-agente)
- [Anexo A — Plantilla de `ESTADO.md`](#anexo-a--plantilla-de-docsestadomd)
- [Anexo B — Plantilla de entrada de bitácora](#anexo-b--plantilla-de-entrada-de-docsbitacoramd)
- [Anexo C — Reglas de dinero](#anexo-c--reglas-de-dinero-cero-descuadres)
- [Anexo D — Reglas de paginación](#anexo-d--reglas-de-paginación)

---

## Parte 0 — El sistema de documentación

El problema es real: si metes todo el historial del proyecto en `CLAUDE.md`,
el agente lo carga completo en cada mensaje y gastas contexto en información
que casi nunca necesita. Si no lo escribes en ningún lado, cada sesión nueva
empieza de cero y repite errores que ya resolviste.

La solución es **separar por frecuencia de lectura**, no por tema.

### Los tres niveles

| Nivel | Archivo | Cuándo se lee | Tamaño máximo | Cómo crece |
|---|---|---|---|---|
| 1 | `CLAUDE.md` | Siempre, automático | ~120 líneas | **No crece.** Se reescribe |
| 2 | `docs/ESTADO.md` | Al inicio de cada sesión | ~50 líneas | **No crece.** Se sobrescribe |
| 3 | `docs/*.md` | Solo cuando la tarea los toca | Sin límite | Crece |

**Nivel 1 — `CLAUDE.md` es un índice, no una enciclopedia.**
Solo contiene: stack y comandos, las reglas que nunca se rompen (una línea
cada una, sin explicación), el mapa de carpetas, y una tabla que diga qué
documento abrir para cada tipo de tarea. El *porqué* de cada regla no va
acá: va en la bitácora. Si `CLAUDE.md` pasa de 120 líneas, algo se está
colando que debería estar un nivel más abajo.

**Nivel 2 — `docs/ESTADO.md` es una foto, no una película.**
Este es el archivo que resuelve "que siempre tenga contexto de lo que hubo
antes". Dice qué módulos están hechos, cuál está en curso, qué decisiones
están esperando tu respuesta y cuál es el siguiente paso concreto. Se
**sobrescribe** en cada actualización, nunca se le agrega al final. Cuarenta
líneas que reemplazan leer veinte archivos.

**Nivel 3 — los documentos especializados.**
`BITACORA.md`, `API-CONTRATO.md`, `MODELO-DATOS.md`, `DESPLIEGUE.md`,
`PLAN-DE-PRUEBAS.md`. Estos sí crecen. El agente los abre solo cuando la
tarea los toca, y la bitácora nunca se lee entera: se busca con `grep`.

### Estructura de archivos

```
boticas/
├── CLAUDE.md                  Nivel 1 — índice y reglas
├── README.md                  Para humanos: qué es y cómo levantarlo
└── docs/
    ├── ESTADO.md              Nivel 2 — foto del avance (se sobrescribe)
    ├── BITACORA.md            Errores y sus soluciones (se agrega)
    ├── API-CONTRATO.md        Endpoints, DTOs, códigos de error
    ├── MODELO-DATOS.md        Tablas, relaciones, diagrama
    ├── DESPLIEGUE.md          Pasos reproducibles y variables de entorno
    ├── PLAN-DE-PRUEBAS.md     Casos de prueba y evidencia
    ├── DECISIONES.md          Decisiones que tomaste tú, con su fecha
    └── prompts/               PROMPT-AGENTE.md y este archivo
```

> Guarda `PROMPT-AGENTE.md` y este documento **dentro del repositorio**, en
> `docs/prompts/`. Hoy los tienes fuera y eso es una pérdida: el agente puede
> leer una tarea anterior para entender por qué el frontend quedó como quedó,
> y si te cambias de máquina no pierdes el guion.

### La regla que hace que esto funcione

Sin esta regla el sistema se degrada en dos días. Va en `CLAUDE.md` y en el
bloque de contexto:

```
DEFINICIÓN DE TERMINADO
Ninguna tarea está terminada hasta que hayas hecho las tres cosas:
  1. El código funciona y lo verificaste (no solo compila)
  2. Sobrescribiste docs/ESTADO.md con la situación nueva
  3. Si algo falló y te costó resolverlo, agregaste una entrada a
     docs/BITACORA.md
No me digas "listo" antes de eso. Si una tarea no generó ningún error
que valga la pena registrar, dilo explícitamente en vez de inventar
una entrada de relleno.
```

### Por qué la bitácora se busca y no se lee

Cada entrada lleva un encabezado de formato fijo:

```
## [BE-003] La caja de hoy no aparece después de las 7pm — 2026-09-14
```

Con eso, el agente puede correr `grep -n "^## \[" docs/BITACORA.md` y ver
solo los títulos: veinte líneas en vez de mil. Si alguno se parece al
problema que tiene enfrente, abre esa entrada específica. El prefijo indica
la capa (`BE` backend, `FE` frontend, `DB` base de datos, `OPS` despliegue),
lo que hace la búsqueda todavía más barata.

---

## Parte 1 — Bloque de contexto (primer mensaje de cada sesión)

```
Contexto — BoticaSys, fase de backend.

ANTES DE RESPONDER, LEE EN ESTE ORDEN:
  1. CLAUDE.md
  2. docs/ESTADO.md
No leas docs/BITACORA.md completa. Si te encuentras con un error,
primero busca si ya pasó: grep -n "^## \[" docs/BITACORA.md y abre
solo la entrada que se parezca.

SITUACIÓN
El frontend Angular está terminado y funcionando con datos mock:
7 pantallas, sistema de diseño consolidado, 14 componentes
compartidos, modelos en core/models y servicios en core/services con
la firma real de la API ya anotada en comentarios.
El backend está vacío: solo el andamiaje de Spring Initializr.

BACKEND — DATOS REALES
Maven (no Gradle). Java 25. Spring Boot 4.1.1.
Paquete raíz: com.botica.backend
Dependencias ya en el pom: Spring Web, Validation, Spring Security,
JDBC API, PostgreSQL Driver, Lombok, DevTools.
Base de datos botica_db ya creada en PostgreSQL local.
Comandos: ./mvnw spring-boot:run | ./mvnw test | ./mvnw clean package

REGLAS QUE APLICAN A TODO EL BACKEND

1. PROHIBIDO JPA
   Nada de Hibernate, JPA, Spring Data JPA, spring-data-commons,
   @Entity ni ORM de ningún tipo. Todo el acceso a datos es
   JdbcTemplate o NamedParameterJdbcTemplate con SQL escrito a mano.
   Es un requisito del curso. No lo cuestiones ni propongas
   alternativas: si crees que algo es imposible sin JPA, dímelo y lo
   resolvemos con SQL.
   Ojo: esto también significa que NO tienes Page ni Pageable de
   Spring Data. La paginación se implementa a mano (ver regla 10).

2. CAPAS ESTRICTAS
   Controller -> Service -> DAO (interfaz) -> DaoImpl (JDBC) -> PostgreSQL
   - El Controller no valida reglas de negocio ni toca la base de
     datos. Recibe el DTO, llama al Service, devuelve ResponseEntity.
   - El Service tiene TODA la regla de negocio y no escribe SQL.
   - El DAO no sabe qué es HTTP. Devuelve modelos, no ResponseEntity.
   Cada DAO es una interfaz en dao/ con su implementación en dao/impl/.
   Inyección por constructor, nunca @Autowired en campos.

3. SQL SIEMPRE PARAMETRIZADO
   Nunca concatenes valores en el SQL. Siempre ? o :parametro.
   Cada consulta con su RowMapper explícito en una clase nombrada, no
   lambdas anónimas repetidas en cinco sitios.
   Única excepción a "no concatenar": la cláusula ORDER BY de la
   paginación, que no se puede parametrizar. Ahí se valida contra una
   lista blanca de columnas permitidas (ver regla 10).

4. DINERO
   BigDecimal en Java, NUMERIC(10,2) en Postgres. NUNCA double ni
   float, ni siquiera para un cálculo intermedio.
   Todo el redondeo pasa por una sola clase util Dinero, con
   RoundingMode.HALF_UP y escala 2. No hay setScale desperdigado.
   Comparaciones con compareTo(), nunca con equals().
   En Perú el precio de venta YA INCLUYE IGV: la base imponible se
   extrae dividiendo entre 1.18, no se suma encima. Si el contrato
   dice otra cosa, pregúntame antes de decidir.
   Detalle completo en docs/prompts/ Anexo C.

5. FECHAS Y ZONA HORARIA
   LocalDate / LocalDateTime, columnas DATE / TIMESTAMP.
   Zona horaria America/Lima fijada explícitamente en la aplicación y
   en la conexión JDBC. "La caja de hoy" se calcula con la fecha de
   Lima: un servidor en UTC cambia de día a las 7 de la tarde hora
   peruana y rompe el cierre de caja del turno noche.
   Jackson con JavaTimeModule y WRITE_DATES_AS_TIMESTAMPS en false.

6. TRANSACCIONES
   Toda operación que escribe en más de una tabla va en una
   transacción (@Transactional del Service, que sí está permitido:
   es Spring TX, no JPA). Registrar una venta escribe en ventas,
   venta_detalle, lotes y movimientos_caja: si algo falla, no queda
   nada a medias.

7. UN SOLO FORMATO DE ERROR
   Un @RestControllerAdvice global. Todas las respuestas de error
   con la misma forma:
     { "timestamp": "...", "status": 409, "error": "CAJA_YA_ABIERTA",
       "mensaje": "Ya existe una caja abierta para hoy",
       "path": "/api/caja/abrir" }
   Códigos: 400 formato inválido, 401 sin autenticar, 403 sin
   permiso, 404 no existe, 409 conflicto de regla de negocio,
   422 stock insuficiente. Nunca un 500 con stacktrace al cliente.
   El campo "error" es un código estable en mayúsculas: el frontend
   mapea contra él, no contra el texto del mensaje.

8. VALIDACIÓN EN DOS NIVELES
   Bean Validation (@NotNull, @Positive, @Size) en los DTO de entrada
   para el formato, y la regla de negocio real en el Service. El
   frontend ya valida también, pero el backend no confía en eso.
   Una validación que solo existe en la pantalla no existe.

9. AUDITORÍA
   Toda tabla que registre una operación lleva creado_en TIMESTAMP y
   creado_por BIGINT referenciando usuarios. La trazabilidad por
   usuario, fecha y hora es un requisito funcional de la propuesta,
   no un extra.

10. PAGINACIÓN OBLIGATORIA EN LISTADOS
    Ningún endpoint que devuelva una colección que pueda crecer
    devuelve la lista completa. Parámetros ?pagina=0&tamano=20&orden=
    y una envoltura de respuesta uniforme. Implementada a mano con
    LIMIT/OFFSET y una consulta COUNT. Detalle en el Anexo D.

11. NO INVENTES ENDPOINTS
    El contrato sale de los comentarios que ya están en los servicios
    del frontend. Si algo falta o no calza, PREGUNTA antes de decidir.
    Si hay que cambiar el contrato, se cambia en los dos lados a la
    vez y se anota en docs/API-CONTRATO.md.

12. ENTREGA POR BLOQUES
    Nunca más de un módulo por turno. Espera mi visto bueno.
    Al terminar cada bloque dime: qué archivos creaste, cómo lo
    pruebo yo mismo (comando curl o pasos en el navegador) y qué
    quedó pendiente.

DEFINICIÓN DE TERMINADO
Ninguna tarea está terminada hasta que:
  1. El código funciona y lo verificaste (no solo compila)
  2. Sobrescribiste docs/ESTADO.md
  3. Agregaste a docs/BITACORA.md cualquier error que costó resolver
```

---

## Parte 2 — Las diez tareas

### Resumen: qué hace cada una y por qué va en ese lugar

| # | Tarea | Qué produce | Por qué va aquí |
|---|---|---|---|
| 5 | ✅ Andamiaje de documentación — completada 2026-09-08 | `CLAUDE.md`, `docs/` con sus plantillas, README corregido | Es la infraestructura de contexto. Si no existe desde el turno uno, las tareas siguientes no tienen dónde registrar nada. Cuesta un turno barato. |
| 6 | ✅ Contrato de API — completada 2026-09-08 | `docs/API-CONTRATO.md`, `docs/DECISIONES.md` (23 entradas) | Tus servicios mock ya definen los endpoints. Congelarlos en un documento evita que el backend invente nombres de campo distintos y te obligue a retocar 7 pantallas. Ampliada con soporte multi-botica (D1), captura de costo (D2) y origen de captura (D4) — ver `docs/DECISIONES.md`. |
| 7 | Esquema de base de datos | `V1__esquema.sql`, `V2__seed.sql`, `docs/MODELO-DATOS.md` | Es el cimiento. Y como decidiste crear las 16 entidades de una vez, este es el turno donde eso se hace bien. |
| 8 | Módulo Caja completo | Backend de caja + tests + seguridad en modo dev | Un módulo entero de punta a punta antes que los demás. Valida CORS, fechas, decimales, formato de error y Spring Security mientras hay tiempo de corregir. |
| 9 | Integración frontend (solo Caja) | `environment.ts`, `caja.service.ts` con HttpClient, interceptor de errores | Si un módulo conecta bien, los demás son repetición. Si conecta mal, mejor descubrirlo con uno solo. |
| 10 | Despliegue temprano | Docker Compose + URL pública + `docs/DESPLIEGUE.md` | Son dos de los ocho criterios del profesor (5 y 8). Dejarlo para el final es donde se cae la gente. |
| 11 | Resto de módulos | Inventario, ventas, merma, alertas (tres bloques) | Ya con el camino abierto es repetir el patrón. Ventas va sola: es la operación delicada. |
| 12 | Autenticación real | JWT, roles, guard, interceptor, sesión persistente | Antes del vertical slice estorba para probar. Después es un reemplazo limpio de la configuración dev. |
| 13 | QA completo | Suite de pruebas + `docs/PLAN-DE-PRUEBAS.md` + cobertura | Criterio 7 del profesor. Los tests de cada módulo se escriben con el módulo; esta tarea cierra cobertura y arma la evidencia. |
| 14 | Cierre e informe | `docs/INFORME-AVANCE.md` con el mapeo a los 8 criterios | El profesor no va a buscar tu evidencia: hay que dársela ordenada. |

---

### Excepción a "los componentes no se tocan" (agregado 2026-09-08)

La regla general de las Tareas 9 y 11 sigue siendo: conectar el backend
real cambia el cuerpo de los métodos del servicio, nunca los componentes.
Cuatro excepciones ya aprobadas (detalle y motivo completo en
`docs/DECISIONES.md`, y en `docs/API-CONTRATO.md` sección "Pantallas del
frontend a ajustar"):

- **Caja · Cierre** — ocultar el efectivo esperado hasta después del
  `POST /api/caja/cerrar` (conteo ciego, hueco 2).
- **Merma** — rediseño completo del flujo de selección: de listar todo el
  inventario a búsqueda de producto → lotes de ese producto.
- **Inventario** — agregar control de paginación y leer los estados de
  vencimiento/stock que ahora calcula el servidor.
- **Alertas** — agregar control de paginación y adaptar `ResumenDashboard`
  reformado (números, no texto pre-formateado).

Cualquier otra excepción a "los componentes no se tocan" se pregunta antes
de tocarla y se anota en `docs/DECISIONES.md` — estas cuatro no son
licencia general para retocar pantallas a discreción.

---

### Tarea 5 — Andamiaje de documentación ✅ COMPLETADA — 2026-09-08

**Qué hace:** crea la infraestructura de contexto antes de escribir una línea de
Java, y corrige el README que hoy dice Gradle cuando el proyecto es Maven.

```
TAREA 5 — Andamiaje de documentación y contexto

Todavía no escribas código de negocio. Esta tarea prepara el terreno.

1) Crea CLAUDE.md en la raíz, con MÁXIMO 120 líneas. Contiene solo:
   - Stack y comandos exactos (Maven, no Gradle)
   - Las 12 reglas del bloque de contexto, UNA LÍNEA cada una, sin
     explicación (el porqué vive en la bitácora)
   - Mapa de carpetas de backend y frontend, una línea por carpeta
   - Una tabla "si vas a hacer X, abre el documento Y"
   - La definición de terminado
   Si te pasas de 120 líneas, algo pertenece a docs/ y no a este archivo.

2) Crea la carpeta docs/ con:
   - ESTADO.md (usa la plantilla que te paso más abajo, lleno con la
     situación actual: frontend hecho, backend vacío)
   - BITACORA.md con el encabezado, el formato de entrada explicado y
     las entradas de la fase de frontend que ya conocemos:
     el toast con setTimeout sobre propiedad plana, los computed()
     leyendo FormControl.value, takeUntilDestroyed dentro de ngOnInit
     (NG0203), y registrarVenta resolviendo datos desde la lista de
     búsqueda. Prefijo FE para esas cuatro.
   - DECISIONES.md vacío con su formato: fecha, decisión, alternativas
     descartadas, quién decidió
   - prompts/ y mueve ahí PROMPT-AGENTE.md y este documento cuando yo
     te los pase

3) Corrige README.md:
   - Los comandos son ./mvnw spring-boot:run, ./mvnw test,
     ./mvnw clean package. Hoy dice ./gradlew y está mal.
   - El paquete raíz es com.botica.backend, no com.botica
   - La sección "Estado actual y pendientes" queda como está: pasa a
     vivir en docs/ESTADO.md y el README solo la referencia

4) Verifica el pom.xml: dime qué dependencias hay realmente, si Lombok
   funciona con Java 25 en este proyecto (compila una clase de prueba
   con @Getter y confírmalo), y si falta algo que vayamos a necesitar.

Al terminar, muéstrame CLAUDE.md completo para aprobarlo. Es el archivo
que más va a influir en todo lo que venga después.
```

**Qué revisar:** que `CLAUDE.md` quepa en una pantalla y media. Si es largo, el agente lo va a cargar entero en cada mensaje y estás pagando por eso todo el proyecto.

---

### Tarea 6 — Contrato de la API ✅ COMPLETADA — 2026-09-08

**Qué hace:** extrae de tu propio frontend la lista exacta de endpoints, formas de
JSON y códigos de error, y le agrega paginación. Es el documento que impide que
backend y frontend se desincronicen. Terminó ampliada en tres rondas de
decisiones con soporte multi-botica (D1), captura de costo (D2),
nomenclatura de comprobantes (D3) y origen de captura auditado (D4) — el
contrato final vive en `docs/API-CONTRATO.md`, las decisiones con su
alternativa descartada en `docs/DECISIONES.md`.

```
TAREA 6 — Contrato de la API

Recorre frontend/src/app/core/services/ y frontend/src/app/core/models/
y extrae el contrato completo que el frontend ya asume.

Genera docs/API-CONTRATO.md con una sección por módulo (auth, caja,
productos, inventario, ventas, merma, alertas) y para cada endpoint:
  - Método y ruta exacta
  - DTO de entrada (JSON de ejemplo con tipos)
  - DTO de salida (JSON de ejemplo con tipos)
  - Códigos de error posibles con su código estable en mayúsculas
  - La regla de negocio que valida ese endpoint

PAGINACIÓN — agrégala al contrato
Todo listado que pueda crecer se pagina. Como mínimo:
  - lotes de inventario
  - ventas del día
  - movimientos de caja
  - alertas
  - mermas
  - búsqueda de productos (aquí un límite duro, no paginación:
    máximo 20 resultados y un indicador de "sigue escribiendo")
Parámetros: ?pagina=0&tamano=20&orden=fechaVencimiento,asc
Envoltura de respuesta uniforme:
  { "contenido": [...], "pagina": 0, "tamano": 20,
    "totalElementos": 137, "totalPaginas": 7 }
NO uses Page ni Pageable de Spring Data: no tenemos esa dependencia y
no la vamos a agregar.
Marca en el documento qué pantallas del frontend hay que ajustar por
esto, pero no las toques todavía.

SECCIÓN "HUECOS DETECTADOS"
Al final del documento, lista:
  - Endpoints que el frontend llama y no tienen respuesta clara
  - Datos que el frontend muestra y que ningún endpoint devuelve
    (conocido: el total de ventas digitales en Cierre de Caja)
  - Constantes hardcodeadas en el cliente que deberían venir del
    backend: IGV 18%, umbral de stock bajo, descuadre leve S/10,
    motivos de merma
  - Dónde el frontend asume una lista completa y ahora va paginada

NO escribas código Java todavía. Este documento es el entregable.
Cuando termines, pregúntame por cada hueco antes de resolverlo tú.
```

**Qué revisar:** que las rutas coincidan letra por letra con los comentarios del frontend, y que los "huecos" sean concretos, no una lista genérica.

---

### Tarea 7 — Base de datos completa

**Qué hace:** crea las 16 entidades de una vez, con migraciones versionadas para
que cambiarlas después sea barato y trazable.

> **Sobre tu decisión de crear todas las entidades ahora.** Es defendible y la
> respeto, con una condición: usar migraciones versionadas. El riesgo de diseñar
> una tabla para una funcionalidad que nadie implementó es que salga mal y te
> enteres en la Fase 3. Flyway convierte ese riesgo en un `V7__ajuste.sql` de
> tres líneas en vez de un `DROP` que te borra los datos de demostración. Flyway
> no es un ORM: sus migraciones son archivos `.sql` planos, así que el requisito
> del curso se sigue cumpliendo, y de hecho el profesor ve el SQL escrito a mano
> más ordenado que si estuviera suelto.

```
TAREA 7 — Esquema completo de base de datos

Agrega Flyway al pom y crea las migraciones en
backend/src/main/resources/db/migration/

Flyway NO es un ORM: sus migraciones son archivos .sql planos escritos
a mano. El requisito de JDBC puro se sigue cumpliendo.

V1__esquema.sql — LAS 16 ENTIDADES, agrupadas y comentadas por fase:

  NÚCLEO (Fase 1 — se implementa ahora)
    usuarios, roles, boticas, productos, presentaciones, lotes,
    ventas, venta_detalle, caja_diaria, movimientos_caja, mermas,
    movimientos_stock

  FUTURO (Fase 2/3 — tabla creada, sin código todavía)
    clientes, comprobantes_electronicos, kardex, reportes_digemid,
    lotes_fraccionados

  Cada tabla de la sección FUTURO lleva un COMMENT ON TABLE que diga
  explícitamente en qué fase se implementa y qué endpoint la va a usar.
  Manténlas mínimas: columnas obvias y sus llaves foráneas. Una tabla
  especulativa con veinte columnas inventadas es peor que una con seis.

REQUISITOS DEL ESQUEMA
- Claves primarias BIGSERIAL, foráneas con ON DELETE explícito
- Dinero NUMERIC(10,2). Cantidades en unidades base, enteras
- Toda tabla de operación con creado_en TIMESTAMP NOT NULL DEFAULT now()
  y creado_por BIGINT REFERENCES usuarios(id)
- lotes: índice sobre (botica_id, producto_id, fecha_vencimiento) — es la
  consulta del FEFO y corre en cada venta
- presentaciones: factor de conversión a unidad base (caja=100,
  blister=10, unidad=1) y precio por presentación
- caja_diaria: índice único parcial que impida dos cajas ABIERTAS del
  mismo usuario y turno el mismo día. A nivel de base de datos, además
  de la validación en el Service
- ventas: columna clave_idempotencia UUID con índice único. El cliente
  la genera; si la red falla y reintenta el POST, no se registran dos
  ventas. Es crítico en una app pensada para conexión inestable
- CHECK donde la regla es simple: montos >= 0, stock >= 0,
  estado IN ('ABIERTA','CERRADA')
- COMMENT ON TABLE en todas

MULTI-BOTICA (decisión D1, 2026-09-08 — docs/DECISIONES.md)
El producto soporta varias boticas independientes desde este esquema
base, no como ampliación futura:
- botica_id BIGINT NOT NULL REFERENCES boticas(id) en: usuarios,
  productos, presentaciones, lotes, ventas, venta_detalle, caja_diaria,
  movimientos_caja, mermas, movimientos_stock. NO en roles (catálogo
  global) ni en boticas misma.
- productos.codigo_barras deja de ser UNIQUE global: pasa a
  UNIQUE(botica_id, codigo_barras) — cada botica tiene su propio
  catálogo.
- Índices compuestos con botica_id primero en: ventas(botica_id, fecha),
  mermas(botica_id, fecha), movimientos_caja(botica_id, fecha) — sirven
  al aislamiento por botica y a las agregaciones de Alertas.
- El botica_id de cada operación sale de ContextoOperacion (ver Tarea 8),
  NUNCA de un parámetro que mande el cliente.
- V2__seed.sql crea DOS boticas con datos distintos, y el test de
  aislamiento entre boticas (Tarea 13) es obligatorio, no opcional.

COSTO Y GANANCIA (decisión D2, 2026-09-08 — docs/DECISIONES.md)
El dato de costo no se puede reconstruir después: se captura desde este
esquema aunque la pantalla de reportes sea Fase 2.
- lotes.costo_unitario NUMERIC(10,2) NOT NULL CHECK (costo_unitario >= 0)
  — el costo vive en el lote, no en el producto: el mismo medicamento se
  compra a distinto precio en cada reposición.
- venta_detalle.costo_unitario NUMERIC(10,2) NOT NULL — congelado al
  momento de la venta, igual que el precio.
- movimientos_caja: el CHECK de tipo incluye explícitamente 'EGRESO'
  (`tipo IN ('apertura','venta','ingreso','egreso','merma')`).
- mermas: SIN columna de costo nueva — el reporte de Fase 2 calcula la
  pérdida a costo con JOIN a lotes.costo_unitario, que no cambia después
  de creado el lote. mermas.valor pasa a llamarse mermas.valor_venta
  (nunca "valor" a secas — ver docs/API-CONTRATO.md, nota 3).

IGV CONGELADO EN LA VENTA (hueco 10, docs/DECISIONES.md)
- ventas.igv_tasa NUMERIC(5,4) NOT NULL (p. ej. 0.1800) — la tasa vigente
  al momento de la venta, congelada en la cabecera. /api/config dice
  cuánto es el IGV *hoy*; una venta vieja no se recalcula si la tasa
  cambia mañana.

ORIGEN DE CAPTURA (decisión D4, corregida 2026-09-08 — docs/DECISIONES.md)
- venta_detalle.origen_captura VARCHAR(10) NOT NULL
  CHECK (origen_captura IN ('ESCANEO','MANUAL','BUSQUEDA')) — ahí siempre
  hay un origen real.
- movimientos_stock.origen_captura VARCHAR(10) NULLABLE, mismo CHECK.
  NULL para las filas que genera una merma — una merma no se busca ni se
  escanea, y rellenar con un valor por convención inventaría un dato que
  contaminaría cualquier métrica sobre proporción de operaciones por
  origen. Regla general: ninguna columna de auditoría se rellena con un
  valor de conveniencia; si no aplica, es NULL.

V2__seed.sql — datos de demostración
  - DOS boticas con datos distintos (D1) — no una sola con la idea de
    "ya migro después"
  - Por cada botica: 2 usuarios (un TECNICO y un ADMINISTRADOR),
    contraseñas ya hasheadas con BCrypt (dime cuáles son en texto
    plano para poder entrar)
  - ~15 productos reales de botica peruana con sus presentaciones y su
    costo_unitario por lote, por botica (los catálogos son
    independientes, no se comparten productos entre boticas)
  - ~40 lotes por botica con vencimientos repartidos A PROPÓSITO:
    vencidos, menos de 30 días, menos de 90, lejanos. Si todos vencen
    en 2028 el semáforo FEFO se ve apagado y no demuestra nada
  - Volumen suficiente para que la paginación se note: al menos 3
    páginas de lotes con tamaño 20 (por botica)
  - Una caja cerrada de ayer con sus movimientos y ventas, para que
    la pantalla de alertas tenga datos
  - Datos suficientemente distintos entre las dos boticas para que el
    test de aislamiento (Tarea 13) tenga algo real que verificar, no
    dos copias idénticas con distinto id

ENTREGABLES
- Las migraciones corriendo contra botica_db sin errores
- docs/MODELO-DATOS.md con un diagrama Mermaid, una tabla por entidad
  (columnas, tipo, para qué sirve, si lleva botica_id) y la columna
  "Fase"
- El comando exacto para reconstruir la base desde cero
```

**Qué revisar:** conecta pgAdmin y mira los datos. Que haya lotes en los cuatro estados del semáforo y suficientes filas para que la paginación tenga sentido.

---

### Tarea 8 — Módulo Caja de punta a punta

**Qué hace:** el primer módulo completo. Además configura Spring Security en modo
desarrollo (que si no lo haces bloquea todos los endpoints con una contraseña
autogenerada y vas a perder una tarde), y dos piezas que necesitan existir desde
ahora aunque no haya login todavía: `ContextoOperacion` (identidad de
usuario/botica/turno, agregado 2026-09-08 por D1) y `/api/config`.

```
TAREA 8 — Backend del módulo Caja, completo

Implementa caja diaria, más dos piezas transversales que todo lo demás
va a necesitar (ContextoOperacion y /api/config):
  model/CajaDiaria, model/MovimientoCaja
  dto/AbrirCajaRequest, CerrarCajaRequest, CajaResponse,
      ResumenCierreResponse (GET /resumen-cierre, pre-conteo),
      PaginaResponse<T> (genérica, reutilizable)
  dao/CajaDao + dao/impl/CajaDaoJdbc
  service/CajaService
  controller/CajaController
  config/CorsConfig — origen por variable de entorno, no hardcodeado
  config/SecurityConfig — MODO DESARROLLO
  config/GlobalExceptionHandler — el @RestControllerAdvice
  config/ContextoOperacion (interfaz) + config/ContextoOperacionDev (impl)
  controller/ConfigController + service/ConfigService (/api/config)
  util/Dinero — la única clase que redondea (Anexo C)
  exception/ con las excepciones de negocio

CONTEXTOOPERACION (agregado 2026-09-08 — identidad antes de que exista login)
Multi-botica (D1) y las columnas de auditoría necesitan usuarioId,
boticaId y turno desde este turno, pero la autenticación real recién
llega en la Tarea 12. Sin una abstracción, boticaId termina hardcodeado
en el Service o, peor, viajando como parámetro que manda el cliente —
justo lo que D1 prohíbe.
  public interface ContextoOperacion {
    Long usuarioId();
    Long boticaId();
    String turno();
  }
En el perfil dev, ContextoOperacionDev devuelve valores fijos del seed
(el usuario/botica que existen en V2__seed.sql). Ningún Service llama a
esta clase directo: la reciben inyectada por constructor como
ContextoOperacion, sin saber si el valor salió de una constante o de un
JWT. La Tarea 12 REEMPLAZA ContextoOperacionDev por una implementación
que lee esos tres valores del JWT — no crea la interfaz, ya existe desde
aquí, y ningún Service cambia una línea.

/API/CONFIG (agregado 2026-09-08)
GET /api/config, sin autenticar, sin tabla propia en el esquema — valores
en application.properties o una clase @ConfigurationProperties (dato de
aplicación, no dato de negocio persistido; dilo si prefieres que sí sea
una tabla). Devuelve igv, umbralStockBajo, descuadreLeve, motivosMerma,
motivosQueRequierenObservacion, vencimiento.criticoDias/advertenciaDias —
ver docs/API-CONTRATO.md sección "Configuración de negocio" para la forma
exacta. Se llama una sola vez al iniciar la app (Tarea 9).

FILTRADO POR BOTICA (agregado 2026-09-08 — D1)
Todo SQL que toque una tabla con botica_id filtra por
contexto.boticaId() desde la primera consulta que se escriba, no como
ajuste posterior. Un WHERE botica_id = ? que falta no da un error visible:
da una fuga silenciosa entre boticas, y el test de aislamiento de la
Tarea 13 es lo único que lo va a atrapar si se te escapa.

ATENCIÓN CON SPRING SECURITY
Está en el pom. Sin configurar, bloquea TODOS los endpoints con una
contraseña autogenerada que aparece en el log de arranque. Crea un
SecurityConfig con un SecurityFilterChain que permita todo bajo el
perfil dev, con un comentario que diga que la Tarea 12 lo reemplaza
por JWT. No lo dejes por defecto ni desactives la dependencia.

REGLAS DE NEGOCIO EN EL SERVICE
- No se puede abrir caja si ya hay una ABIERTA para ese usuario, turno
  y día -> 409 CAJA_YA_ABIERTA
- No se puede cerrar una caja inexistente o ya cerrada
  -> 409 CAJA_NO_ABIERTA
- Montos >= 0 -> 400
- CONTEO CIEGO (hueco 2, corregido 2026-09-08): GET /api/caja/{id}/
  resumen-cierre, llamado ANTES de contar, devuelve totalVentasEfectivo,
  totalVentasDigital, cantidadVentas y cantidadMovimientos — nunca el
  monto esperado. El monto esperado (monto_inicial + ventas en efectivo -
  egresos), la diferencia y el semáforo de descuadre los calcula EL
  SERVIDOR y viajan recién en la respuesta de POST /api/caja/cerrar,
  después de que el cliente mandó su conteo físico. Mostrarlo antes
  convierte el conteo en una confirmación en vez de un control.
- El semáforo de descuadre (EXACTO / LEVE hasta S/10 / GRAVE) lo
  calcula el servidor y viaja como enum en la respuesta de cierre
- Los movimientos de caja se devuelven PAGINADOS

TESTS EN LA MISMA ENTREGA
- Unitarios del Service con JUnit 5 + Mockito, mockeando el DAO. Un
  test por regla, incluyendo los casos que deben fallar
- Del Controller con @WebMvcTest y MockMvc: códigos de estado y forma
  del JSON de error
- Uno específico de zona horaria: que "la caja de hoy" sea correcta
  simulando las 11 de la noche hora de Lima
- Uno específico de conteo ciego: que resumen-cierre nunca incluya
  montoEsperado/diferencia/semaforoDescuadre en su respuesta, aunque la
  caja ya tenga movimientos suficientes para calcularlos

Entrégame también docs/api.http con una llamada lista por endpoint, y
dime los comandos exactos para levantar todo y verificar.
```

**Qué revisar:** prueba con `api.http` o curl **antes** de mirar el código. Un 409 debe devolver el JSON acordado, no un stacktrace.

---

### Tarea 9 — Conectar el frontend

**Qué hace:** reemplaza el cuerpo de los métodos mock de caja por `HttpClient`
sin tocar los componentes. Si esto sale limpio, los demás módulos son copiar el patrón.

```
TAREA 9 — Integración frontend-backend (solo Caja)

1) environments/environment.ts y environment.prod.ts con apiUrl.
   Configura fileReplacements en angular.json.

2) core/services/config.service.ts (nuevo, agregado 2026-09-08): consume
   GET /api/config UNA SOLA VEZ al arrancar la app (no en cada pantalla),
   guarda la respuesta en un signal, expuesto con asReadonly(). Reemplaza
   las constantes hoy hardcodeadas: TASA_IGV (venta.service.ts y
   carrito.service.ts), UMBRAL_STOCK_BAJO (inventario.service.ts e
   inventario.ts), UMBRAL_DESCUADRE_LEVE (cierre.ts), MOTIVOS_MERMA /
   MOTIVOS_QUE_REQUIEREN_OBSERVACION (merma.service.ts).

3) En caja.service.ts reemplaza el cuerpo de cada método mock por
   HttpClient. LA FIRMA NO CAMBIA: sigue devolviendo Observable<T>,
   sigue actualizando el signal privado dentro del tap(), sigue
   exponiéndolo con asReadonly(). Los componentes no se tocan — SALVO
   la excepción de Cierre de caja (ocultar el efectivo esperado hasta
   después de cerrar, ver la nota al inicio de esta Parte 2). Deja los
   mocks de los otros servicios intactos.

4) core/interceptors/error.interceptor.ts que traduzca el campo
   "error" del backend a los mensajes que las pantallas ya muestran.
   El mapeo va en un objeto constante, no en ifs desperdigados. Incluye
   una lista de CÓDIGOS SILENCIOSOS (agregado 2026-09-08) que no
   muestran toast porque son resultados esperados, no fallas: empieza
   con PRODUCTO_NO_ENCONTRADO (flujo de escaneo en Punto de Venta,
   aunque ese módulo recién se conecta en la Tarea 11 — deja la lista
   preparada ahora).

5) Adapta la lista de movimientos de caja a la respuesta paginada.
   Si hace falta un componente de paginación en shared/components/,
   créalo con las clases bs-* existentes y muéstramelo antes.

6) Verifica en el navegador con el backend levantado y descríbeme qué
   viste en cada caso: apertura exitosa, doble apertura, cierre con
   descuadre leve, cierre con descuadre grave, backend apagado, y que
   "efectivo esperado" ya NO se vea antes de contar.

No pases a otros módulos hasta que yo apruebe este.
```

**Qué revisar:** apaga el backend a propósito y recarga. Y mira la consola: un error de CORS es mejor descubrirlo ahora que la noche antes de entregar.

---

### Tarea 10 — Despliegue temprano

**Qué hace:** pone la aplicación en una URL pública con un solo módulo funcionando,
para que los problemas de infraestructura aparezcan cuando todavía hay margen.

```
TAREA 10 — Despliegue, primera pasada

Quiero la aplicación accesible por URL pública AHORA, aunque solo
funcione caja. El resto se despliega encima de la misma infraestructura.

1) Docker Compose local: postgres con volumen persistente, backend
   (Dockerfile multi-stage con Maven y JDK 25), frontend (build de
   Angular servido por nginx con fallback a index.html para las rutas
   de la SPA). Un solo `docker compose up` levanta todo.

2) Despliegue público: propón DOS opciones concretas con pasos,
   límites del plan gratuito y tiempo estimado, y espera a que yo
   elija. Considera Postgres administrado frente a Postgres en
   contenedor, y frontend en CDN frente a servido por el mismo backend
   desde /static (menos piezas, menos CORS).

3) Requisitos que la opción elegida debe cumplir:
   - HTTPS obligatorio: la PWA y la cámara del lector no funcionan sin él
   - Variables de entorno para la conexión y la clave de firma.
     NADA de credenciales en el repositorio. Revisa que no haya
     quedado ninguna del desarrollo local
   - CORS por variable de entorno
   - Service worker de Angular activo en producción
   - Un endpoint de salud que responda sin autenticación

4) docs/DESPLIEGUE.md: pasos reproducibles desde cero, variables
   necesarias, cómo reconstruir la base y cómo revertir.
```

---

### Tarea 11 — Resto de módulos

**Qué hace:** repite el patrón de las tareas 8 y 9 para los módulos restantes.
Ventas va en su propio turno porque es la operación que puede corromper datos.

```
TAREA 11 — Módulos restantes

Mismo patrón de la Tarea 8 (modelo, DTO, DAO, Service, Controller,
tests) y de la Tarea 9 (conectar el servicio del frontend sin tocar
componentes). Un bloque por turno.

BLOQUE A — Productos e inventario
  Búsqueda por texto y por código de barras (límite de 20 resultados).
  GET /api/productos/codigo/{codigoBarras} sin match -> 404
  PRODUCTO_NO_ENCONTRADO (código silencioso en el interceptor, Tarea 9).
  Listado de lotes PAGINADO con filtros de vencimiento, y gana
  ?productoId= (reemplaza ?productoNombre=) — lo necesita el flujo
  rediseñado de Merma del Bloque C.
  El orden FEFO se resuelve en el SQL (ORDER BY fecha_vencimiento),
  no en Java.
  Semáforo (agregado 2026-09-08, alineado con Alertas — docs/DECISIONES.md
  nota 4): el servidor calcula y devuelve estadoVencimiento
  ("VENCIDO"|"CRITICO"|"ADVERTENCIA"|"OK") y stockEstado
  ("OK"|"CRITICO"|"AGOTADO") en cada fila, para que cliente y servidor no
  puedan discrepar. NO se agrega un campo de días-para-vencer calculado
  por el servidor: eso es una resta que caduca si la pantalla queda
  abierta, el cliente la sigue calculando con fecha.util.diasHasta a
  partir de fechaVencimiento (dato) y la fecha del dispositivo.
  fecha.util.estadoFefo/EstadoFefo quedan sin consumidor con este cambio
  y se borran en este bloque (diasHasta se queda, tiene otro uso).
  Registro de lote nuevo: el botón que hoy no hace nada. Captura
  costoUnitario (NOT NULL, D2) desde este bloque — no se puede agregar
  después sin perder los lotes ya creados sin costo. costoUnitario NO se
  expone en el listado de lotes (el costo es información del dueño, no
  del cajero) — el reporte de Fase 2 lo va a necesitar en su propio
  endpoint restringido a rol ADMINISTRADOR.
  El parámetro de orden se valida contra lista blanca (Anexo D).
  Componentes que se tocan en este bloque (excepción aprobada, ver nota
  al inicio de la Parte 2): Inventario (paginación + estados del
  servidor) y, si aplica, Alertas (paginación).

BLOQUE B — Ventas (va solo, es el módulo delicado)
  Registrar una venta debe, dentro de UNA transacción:
    1. Verificar la clave de idempotencia: si ya existe, devolver la
       venta anterior con 200 en vez de crear otra. La clave la genera
       el FRONTEND al confirmar el carrito (Tarea 9/11 frontend), no
       en cada intento HTTP — el backend solo la recibe y la respeta
    2. Validar que hay caja abierta -> 409 SIN_CAJA_ABIERTA
    3. Convertir cada línea de presentación a unidades base
    4. Descontar stock consumiendo lotes en orden FEFO, pudiendo
       repartir una línea entre varios lotes
    5. Bloquear las filas de lote con SELECT ... FOR UPDATE para que
       dos ventas simultáneas no vendan el mismo stock
    6. Si el stock no alcanza -> 422 STOCK_INSUFICIENTE y rollback
    7. Insertar cabecera y detalle con el precio CONGELADO al momento
       de la venta, no una referencia al precio actual del producto.
       CONGELA TAMBIÉN EL COSTO (D2, agregado 2026-09-08):
       venta_detalle.costo_unitario, mismo momento, misma inmutabilidad
       que el precio — lo necesita el reporte de Fase 2 y no se puede
       reconstruir después
    8. Registrar el movimiento de caja y el movimiento de stock, cada
       línea con origen_captura (D4: 'ESCANEO'|'MANUAL'|'BUSQUEDA',
       NOT NULL — siempre hay un origen real en una venta) que manda
       el cliente por línea
  Los totales, IGV (congelado también en ventas.igv_tasa) y costo los
  calcula/registra el servidor. El cliente manda cantidades,
  presentaciones y origen de captura, nunca montos.
  Redondeo: se redondea el total de línea, no el precio unitario
  (Anexo C).

BLOQUE C — Merma y alertas
  Merma: descuenta stock de un lote concreto, cantidad topada al stock
  real validada en el servidor, motivo obligatorio, observación
  obligatoria si el motivo es "Otro". Es destructiva: sin deshacer,
  pero queda registrada en movimientos_stock con origen_captura NULL
  (D4, corregido 2026-09-08 — una merma no se busca ni se escanea, no
  se inventa un valor de conveniencia).
  Respuesta de Merma: el campo se llama valorVenta (D2/nota 3, no
  "valor" a secas) y se calcula a precio de venta como hoy — el costo
  de la merma (valorCosto) no es un campo de Merma, el reporte de
  Fase 2 lo calcula con JOIN a lotes.costo_unitario.
  Flujo rediseñado (excepción aprobada a "los componentes no se tocan",
  ver nota al inicio de la Parte 2): MermaScreen deja de pedir todos
  los lotes sin filtro; primero busca el producto
  (GET /api/productos?buscar=), después pide GET /api/lotes?productoId=
  del producto elegido.
  Alertas: los KPIs se calculan con SQL agregado (COUNT, SUM,
  GROUP BY), no trayendo todo a Java y filtrando ahí. La lista de
  alertas va paginada. ResumenDashboard reformado (hueco 5): números
  crudos (ventasHoy, boletasHoy, etc.) y cajaEstado como enum, nunca
  texto pre-formateado — ver docs/API-CONTRATO.md para la forma exacta.
  UMBRALES CONCRETOS (hueco 6, formalizados 2026-09-08 — ajustables
  desde /api/config):
    Vencimiento (fecha de Lima, no del servidor):
      VENCIDO      -> fecha_vencimiento < hoy
      CRITICO      -> 0 a 30 días
      ADVERTENCIA  -> 31 a 90 días
      OK           -> más de 90 días, no genera alerta
    Stock:
      CRITICO -> stock <= 15 (config umbralStockBajo)
      AGOTADO -> stock = 0
    Caja sin cerrar: existe una caja ABIERTA cuya fecha operativa es
      anterior a hoy. NO por horas transcurridas: con boticas 24/7 y
      turno noche, contar horas da falsos positivos cada madrugada.
    Excepción: un lote vencido con stock = 0 NO genera alerta — no hay
      nada que hacer con él.

Después de cada bloque: conecta su servicio del frontend, verifica en
el navegador, dime qué viste, actualiza ESTADO.md. Espera aprobación.
```

---

### Tarea 12 — Autenticación

**Qué hace:** reemplaza la configuración de seguridad en modo desarrollo por JWT
real con roles, y arregla la sesión que hoy se pierde al recargar.

```
TAREA 12 — Autenticación y protección de rutas

Alcance deliberadamente mínimo pero real.

BACKEND
- POST /api/auth/login: valida contra usuarios, devuelve un JWT con
  id, rol, turno y boticaId (D1, agregado 2026-09-08 — multi-botica
  necesita este claim, no solo los tres originales)
- Contraseñas con BCrypt (el seed ya las trae hasheadas)
- SecurityConfig real que reemplaza el de desarrollo: valida el token
  en /api/** excepto /api/auth/** y el endpoint de salud
- REEMPLAZA config/ContextoOperacionDev (Tarea 8) por una implementación
  de ContextoOperacion que lee usuarioId/boticaId/turno del JWT — la
  interfaz ya existe desde la Tarea 8, esta tarea no la crea, y ningún
  Service cambia una línea al hacer el cambio
- Roles TECNICO y ADMINISTRADOR, con la diferencia aplicada en al
  menos un endpoint real (por ejemplo, solo ADMINISTRADOR cierra caja)
  para que el control por roles sea demostrable

FRONTEND
- Guard CanActivate sobre el Shell, redirige a /login
- Interceptor que adjunta el token en Authorization
- Persistencia: hoy AuthService guarda en memoria y se pierde al
  recargar. Guarda el token y rehidrata al arrancar
- Un 401 cierra sesión y redirige a login

Dime en una línea dónde guardaste el token y qué riesgo tiene. No
elijas por defecto sin decírmelo.
```

---

### Tarea 13 — QA

**Qué hace:** cierra la cobertura de pruebas y produce la evidencia presentable
para el criterio 7 del profesor.

```
TAREA 13 — Pruebas y validaciones

A) UNITARIAS DEL BACKEND (JUnit 5 + Mockito)
   Un test por regla, con el caso feliz y el que debe fallar:
   - CajaService: doble apertura, cierre sin apertura, monto negativo,
     cálculo del esperado, los tres niveles de descuadre, la fecha
     de "hoy" a las 23:00 hora de Lima
   - VentaService: FEFO reparte entre lotes, stock insuficiente hace
     rollback, conversión caja/blister/unidad, cálculo de IGV y total,
     venta sin caja abierta, idempotencia con clave repetida
   - MermaService: cantidad mayor al stock, motivo "Otro" sin
     observación
   - InventarioService: semáforo en los límites exactos
     (-1, 0, 1, 30, 31, 90, 91 días — el -1/0 separa VENCIDO de CRITICO,
     agregado 2026-09-08 al pasar de 3 a 4 estados). Los errores de "<"
     contra "<=" viven ahí

B) PRUEBAS DE DINERO — sección propia, no las mezcles
   El requisito es cero descuadres. Como mínimo:
   - La suma de los totales de línea es exactamente igual al total de
     cabecera, en 50 ventas generadas con cantidades y precios variados
   - monto_inicial + ventas en efectivo - egresos == monto esperado
   - Precios que rompen el punto flotante: 0.1+0.2, diez unidades a
     0.33, una caja de 30 unidades a S/25.00 (0.8333... por unidad)
   - Que el IGV extraído más la base den el total, sin céntimo perdido
   - Que ningún BigDecimal se compare con equals() en el código:
     verifícalo con grep y dime el resultado
   Detalle en el Anexo C.

C) INTEGRACIÓN DE LOS DAO
   SQL real contra Postgres real. Testcontainers si Docker está
   disponible; si no, un perfil contra una base botica_test con
   @JdbcTest y scripts @Sql que la dejen limpia entre tests.
   NO uses H2: el SQL es específico de Postgres y daría confianza falsa.
   Prueba: el índice único de caja abierta rechaza el duplicado, el
   FOR UPDATE del FEFO, que un rollback de venta fallida no deje stock
   descontado, y que la paginación devuelva totales correctos.

D) CONTROLADORES
   @WebMvcTest + MockMvc: códigos de estado, forma del JSON de error,
   payloads inválidos rechazados, ruta protegida sin token da 401,
   parámetro de orden malicioso rechazado por la lista blanca.

E) FRONTEND (Vitest)
   - Servicios con HttpTestingController: URL y método correctos, y el
     signal actualizado tras la respuesta
   - Utilidades puras: fecha.util y moneda.util
   - Al menos un componente con estado derivado: que un computed()
     realmente reaccione. Es el bug que ya nos mordió dos veces

F) E2E (Playwright), tres flujos
   1. Login -> abrir caja -> registrar venta -> cerrar caja cuadrada
   2. Registrar merma y verificar que el stock bajó en inventario
   3. Vender más stock del disponible y ver el error correcto

G) AISLAMIENTO ENTRE BOTICAS (nueva, agregada 2026-09-08 — D1)
   Obligatoria, no opcional. Con el seed de dos boticas (Tarea 7):
   - Ninguna consulta de listado (lotes, ventas, movimientos, mermas,
     alertas) de la botica A devuelve una sola fila de la botica B,
     autenticado como usuario de A
   - Intentar abrir/cerrar caja, registrar venta o merma de un id que
     pertenece a la otra botica (loteId, cajaId ajenos) falla, no
     devuelve datos ni los modifica
   - El índice único de caja abierta y el de codigo_barras no bloquean
     entre boticas: la botica B puede tener su propia caja abierta con
     el mismo turno, o un producto con el mismo código de barras que A

H) CONSISTENCIA DE COSTO Y GANANCIA (nueva, agregada 2026-09-08 — D2)
   - venta_detalle.costo_unitario queda congelado igual que el precio:
     cambiar lotes.costo_unitario de un lote YA VENDIDO (si algún día se
     permite editar) no cambia el costo de ventas pasadas
   - El cálculo de pérdida por merma a costo
     (cantidad × lotes.costo_unitario vía JOIN) da el mismo resultado
     sin importar cuándo se ejecute la consulta, porque el costo del
     lote no cambia después de creado
   - mermas.valor_venta nunca se confunde con el costo en ningún query
     de prueba — verifícalo con un caso donde precio y costo del mismo
     lote sean deliberadamente distintos, para que un error de columna
     dé un número visiblemente incorrecto y no uno que por casualidad
     coincide

I) EVIDENCIA
   docs/PLAN-DE-PRUEBAS.md con la tabla de casos manuales:
     ID | Módulo | Precondición | Pasos | Esperado | Obtenido | Estado
   Cubre las validaciones que el frontend ya implementa y deja la
   columna "Obtenido" lista para que yo pegue lo que observe.
   Incluye el comando de cobertura y dónde queda el reporte.

Ejecuta la suite completa y muéstrame el resumen. Después rompe a
propósito una regla de negocio (permite la doble apertura de caja) y
muéstrame el test en rojo. Luego revierte. Quiero comprobar que los
tests detectan algo.
```

---

### Tarea 14 — Cierre e informe

```
TAREA 14 — Cierre de la fase

1) Despliegue final con todos los módulos. Verifica en la URL pública:
   login real, venta completa, cierre de caja, PWA instalable y
   funcionando desde un celular.

2) Actualiza README.md, CLAUDE.md y docs/ESTADO.md al estado real.

3) Genera docs/INFORME-AVANCE.md mapeando cada criterio del profesor a
   evidencia concreta con ruta de archivo o URL:
     1. API RESTful con Spring Boot   -> controladores, endpoints
     2. Back-end con base de datos    -> migraciones, DAOs, SQL
     3. Front-end con Angular         -> pantallas, componentes
     4. Integración back/front        -> servicios HTTP, interceptor
     5. Despliegue                    -> infraestructura
     6. Funcionalidades implementadas -> lista con estado
     7. Pruebas y validaciones        -> resumen de la suite, cobertura
     8. Despliegue exitoso            -> URL viva + capturas
   Sé honesto con lo pendiente: una lista explícita se ve mejor que
   un vacío.

4) Dime qué falta para la Fase 3: SUNAT, Kardex, DIGEMID, offline real
   con IndexedDB, y el lector de cámara.
```

---

## Parte 3 — Riesgos conocidos de esta fase

| Riesgo | Cómo se manifiesta | Cómo lo evitas |
|---|---|---|
| Spring Security sin configurar | Todos los endpoints piden contraseña. Se pierde media tarde buscando un error de CORS que no era CORS | `SecurityConfig` de desarrollo en la Tarea 8 |
| Dejar el despliegue para el final | Funciona en tu máquina y en ninguna otra. Son dos de los ocho criterios | Tarea 10 va antes que la mitad de los módulos |
| Contrato inventado por el backend | Devuelve `expiryDate` y el frontend espera `fechaVencimiento`. Pantalla en blanco sin error visible | Tarea 6 antes de escribir Java |
| Zona horaria del servidor | En UTC, la caja del turno noche cambia de día a las 7pm. El cierre no encuentra la caja abierta | `America/Lima` explícito, con test |
| Doble POST de venta | Conexión inestable, el usuario reintenta, se registran dos ventas y el stock baja el doble | Clave de idempotencia con índice único |
| Dinero en `double` | Descuadres de céntimos imposibles de explicar en la demostración | Anexo C, y un test que lo verifica |
| La venta como un CRUD más | Se descuenta stock sin transacción y sin FEFO. Es la operación que demuestra el dominio | Bloque B en su propio turno |
| Tests escritos al final | Se escriben para que pasen, no para encontrar errores | Cada módulo trae sus tests; la Tarea 13 los verifica rompiendo una regla |
| `CLAUDE.md` inflado | El agente carga 600 líneas en cada mensaje y el contexto se agota a mitad de sesión | Límite de 120 líneas, revisado en cada tarea |
| `botica_id` retrofitteado (agregado 2026-09-08) | Migrar datos y tocar cada query ya escrita para agregar `botica_id` después es mucho más caro que incluirlo desde el esquema base — y una consulta sin filtrar por botica es una fuga silenciosa, no un error visible | D1: `botica_id` en el `V1__esquema.sql` inicial, `ContextoOperacion` desde la Tarea 8, test de aislamiento obligatorio en la Tarea 13 |
| Costo no capturado desde el inicio (agregado 2026-09-08) | El costo de una venta o merma ya registrada sin `costo_unitario` no se puede reconstruir retroactivamente — el reporte de ganancia de Fase 2 nace con huecos permanentes | D2: `lotes.costo_unitario` y `venta_detalle.costo_unitario` desde la Tarea 7, aunque la pantalla de reportes sea Fase 2 |

---

## Parte 4 — Cómo trabajar con el agente

Lo mismo que en la fase de frontend, más cuatro cosas propias del backend:

**Prueba la API a mano antes de conectar el frontend.** Con curl o el archivo
`.http`. Si un endpoint está mal y lo descubres desde la pantalla, vas a estar
depurando dos capas a la vez sin saber cuál falla.

**Exige ver el SQL, no solo el Java.** El SQL es lo que el profesor va a mirar
para evaluar JDBC puro y el patrón DAO. Un `JdbcTemplate` que ejecuta
`SELECT *` sin `RowMapper` explícito cumple la letra del requisito y no
demuestra nada.

**Una regla implementada solo en el frontend no cuenta.** El botón
deshabilitado con monto inválido está bien como experiencia de uso, pero si el
servidor acepta el POST igual, la validación no existe.

**Haz un commit por tarea aprobada.** Con el número de tarea en el mensaje. Si
un bloque sale mal, revertir es un comando en vez de una arqueología.

---

## Anexo A — Plantilla de `docs/ESTADO.md`

Se **sobrescribe** cada vez. Nunca se le agrega al final.

```markdown
# Estado del proyecto
Actualizado: 2026-09-08 · Última tarea completada: T7

## Módulos
| Módulo     | Backend | Frontend | Tests | Notas                    |
|------------|---------|----------|-------|--------------------------|
| Caja       | ✅      | ✅       | ✅    | Descuadre grave pendiente|
| Inventario | 🔨      | mock     | —     | En curso, bloque A       |
| Ventas     | —       | mock     | —     |                          |
| Merma      | —       | mock     | —     |                          |
| Alertas    | —       | mock     | —     |                          |
| Auth       | —       | mock     | —     | Security en modo dev     |

## Tarea en curso
T11 bloque A — inventario. Falta el endpoint de registro de lote.

## Esperando decisión mía
- Dónde guardar el token de sesión (T12)
- Proveedor de despliegue: opción 1 o 2 de docs/DESPLIEGUE.md

## Siguiente paso concreto
Implementar InventarioDaoJdbc.listarLotesPaginado con la lista blanca
de columnas de orden.

## Deuda técnica aceptada
- Constantes de negocio todavía hardcodeadas en el frontend
- Sin caché de catálogo de productos
```

---

## Anexo B — Plantilla de entrada de `docs/BITACORA.md`

Se **agrega** al final. Máximo diez líneas por entrada. El encabezado es de
formato fijo para poder listarlo con
`grep -n "^## \[" docs/BITACORA.md`.

```markdown
## [BE-003] La caja de hoy no aparece después de las 7pm — 2026-09-14

**Síntoma:** en el turno noche, `GET /api/caja/hoy` devolvía null aunque
la caja estaba abierta.
**Causa:** el contenedor corre en UTC. `LocalDate.now()` ya era el día
siguiente a las 19:00 hora de Lima.
**Solución:** zona horaria `America/Lima` fijada en la aplicación y en la
conexión JDBC, más una clase `FechaNegocio` que centraliza el "hoy".
**Archivos:** `config/ZonaHorariaConfig.java`, `util/FechaNegocio.java`
**Regla que dejó:** ninguna clase llama a `LocalDate.now()` directo.
```

Prefijos: `BE` backend, `FE` frontend, `DB` base de datos, `OPS` despliegue.

---

## Anexo C — Reglas de dinero (cero descuadres)

Este anexo se le pasa al agente completo cuando empiece la Tarea 8.

1. **`BigDecimal` siempre.** Ni `double` ni `float`, ni siquiera para un
   cálculo intermedio ni para una variable temporal.
2. **`NUMERIC(10,2)` en Postgres.** Nunca `FLOAT`, `REAL` ni `MONEY`.
3. **Una sola clase que redondea.** `util/Dinero` con
   `RoundingMode.HALF_UP` y escala 2. Ningún `setScale` fuera de ahí.
4. **Comparar con `compareTo()`, nunca con `equals()`.** `new BigDecimal("2.50").equals(new BigDecimal("2.5"))` es `false`: la escala es parte de la
   igualdad. Es una fuente clásica de bugs invisibles.
5. **Construir desde `String`, no desde `double`.** `new BigDecimal(0.1)`
   arrastra el error del punto flotante; `new BigDecimal("0.1")` no.
6. **Se redondea el total de línea, no el precio unitario.** Una caja de 30
   unidades a S/ 25.00 da 0.8333... por unidad. Si redondeas el unitario a
   0.83 y vendes 30, cobras 24.90 y pierdes 10 céntimos por caja. Se
   calcula `cantidad × precio_caja ÷ unidades_por_caja` y se redondea el
   resultado.
7. **El IGV se extrae, no se suma.** En Perú el precio de venta al público
   ya incluye IGV. Base = total ÷ 1.18; IGV = total − base. Si lo sumas
   encima, todos tus precios salen 18% más caros que la etiqueta. La tasa
   vigente se congela en `ventas.igv_tasa` (agregado 2026-09-08, hueco 10)
   — `/api/config` dice cuánto es el IGV *hoy*, una venta vieja no se
   recalcula si la tasa cambia mañana.
8. **El servidor calcula todos los montos.** El cliente manda cantidades y
   presentaciones. Si el cliente manda el total, el total es negociable.
9. **Invariante que debe cumplirse siempre:** la suma de los totales de
   línea es exactamente el total de la cabecera. Un test lo verifica con
   cincuenta ventas generadas.
10. **El costo sigue las mismas nueve reglas de arriba** (agregado
    2026-09-08, decisión D2 — sí hacía falta algo en este anexo).
    `lotes.costo_unitario` y `venta_detalle.costo_unitario` son
    `BigDecimal`/`NUMERIC(10,2)`, nunca `double`. El costo de un lote se
    captura una sola vez (inmutable después de creado) y se congela en
    `venta_detalle` al momento de la venta, igual que el precio — nunca
    se recalcula el costo histórico de una venta si cambia el costo de
    reposiciones futuras del mismo producto. `mermas.valor_venta` (a
    precio de venta) y el costo de una merma (calculado por `JOIN` a
    `lotes.costo_unitario`, sin columna propia) son dos números
    distintos — nunca se comparan como si fueran el mismo.

---

## Anexo D — Reglas de paginación

Como no hay Spring Data, se implementa a mano. Es poco código y evita una
dependencia prohibida.

**Parámetros de entrada:** `?pagina=0&tamano=20&orden=fechaVencimiento,asc`
- `pagina` por defecto 0, `tamano` por defecto 20 y **tope duro de 100**.
  Sin tope, un `tamano=999999` te trae toda la tabla y satura el frontend
  igual que si no hubiera paginación.

**Respuesta uniforme** (una clase genérica `PaginaResponse<T>`):

```json
{
  "contenido": [],
  "pagina": 0,
  "tamano": 20,
  "totalElementos": 137,
  "totalPaginas": 7
}
```

**En el DAO:** dos consultas, la de datos con `LIMIT ? OFFSET ?` y la de
`COUNT(*)` con los mismos filtros. Nada de traer todo y cortar en Java.

**El `ORDER BY` es el punto peligroso.** No se puede parametrizar con `?`,
así que hay que concatenarlo, y eso es exactamente donde entra una inyección
SQL. La regla: cada DAO define un `Map` de columnas permitidas
(`"fechaVencimiento" -> "l.fecha_vencimiento"`) y lo que no esté en el mapa
se rechaza con 400. Nunca se pasa el texto del parámetro al SQL.

**Orden estable.** Si dos filas empatan en el criterio de orden, Postgres
puede devolverlas en distinto orden en cada consulta y verás filas repetidas
entre páginas. Todo `ORDER BY` termina con `, id ASC` como desempate.

**En el frontend:** el signal del servicio guarda la página actual y el
total. Al cambiar de página se llama al servicio de nuevo; no se acumula en
memoria salvo que la pantalla use scroll infinito, y en ese caso hay que
decidirlo explícitamente.
