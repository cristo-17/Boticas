# Pruebas de API con Postman

Colección de BoticaSys: **220 peticiones en 17 carpetas**, una por módulo, con
todos los endpoints de la API (el contrato completo está en
`docs/API-CONTRATO.md`, en la documentación de trabajo del equipo, fuera de
este repositorio). Cada petición prueba el código HTTP y lo que importa del
cuerpo. Además, a nivel de colección se verifica que todo error tenga el
formato uniforme (`timestamp`, `status`, `error`, `mensaje`, `path`).

| Archivo                               | Qué es                                                                                                                                              |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BoticaSys.postman_collection.json`   | La colección (formato v2.1).                                                                                                                        |
| `local.postman_environment.json`      | `baseUrl` local (`http://localhost:8080`) y usuarios del seed de desarrollo (`rosa.quispe`, `carlos.mendoza`, `jorge.fernandez` de la otra botica). |
| `produccion.postman_environment.json` | `baseUrl` de Render. Los usuarios van vacíos: se usan los usuarios demo de la `V12` (ver la tabla de boticas demo en el `README.md` de la raíz).    |
| `reportes/`                           | Reportes HTML de Newman. Git los ignora porque incluyen tokens.                                                                                     |

**Ninguna contraseña está en estos archivos.** Las variables `password*` están
vacías: se cargan como _current value_ en Postman (que no se exporta ni se
sincroniza) o con `--env-var` en Newman.

## Orden y datos

Las carpetas se corren en orden: Auth guarda los tokens que usan las demás.
El flujo de caja empieza cerrando la caja que el TECNICO haya dejado abierta, y
cada lote que crea lleva un código nuevo. La venta en efectivo crea antes su
propio lote de 2 unidades, así que no gasta el stock del seed (`[QA-001]`). Los
dos lotes de la prueba FEFO vencen hoy y quedan en 0 al venderse, así que no
dejan stock vencido. Por eso las corridas se pueden repetir sin límite.

**Catálogo (ADMINISTRADOR)** crea un producto nuevo en cada corrida (código
`PC` + timestamp), lo edita, lo desactiva y lo reactiva. Cada ruta nueva tiene
su 403 para el TECNICO.

**Usuarios y bloqueo de login (ADMINISTRADOR)** crea un usuario único en cada
corrida (`pu.` + timestamp), verifica que `password_hash` no salga en el JSON,
degrada su rol para comprobar que el token viejo queda invalidado (`401`),
verifica que un administrador no pueda desactivarse ni quitarse el rol, prueba
el bloqueo por 5 intentos fallidos (`423 CUENTA_BLOQUEADA`), el desbloqueo, el
restablecimiento de clave y la baja lógica (que invalida el token activo). Cada
ruta tiene su 403 para el TECNICO.

**Reportes y Egresos (Tarea 18)** abre una caja del ADMINISTRADOR, registra un
egreso (`POST /api/caja/egresos`), verifica que reste del efectivo esperado al
cerrar la caja, consulta el reporte de resultados por día, por mes y por semana
(`GET /api/reportes/resultados`; la semana cortada por el rango se rotula
`"03/11 al 08/11"` y la suma de las semanas da el total al céntimo), comprueba
que cada fila cumpla `gastos = costo + mermas + egresos`, descarga el Excel
(`GET /api/reportes/resultados/xlsx`: tipo, nombre `reporte-resultados_<desde>_<hasta>.xlsx`
y cuerpo zip `PK`), valida los errores (`desde > hasta` y rango mayor a 1 año →
`400 RANGO_FECHAS_INVALIDO`; `agrupar=anio` → `400 FILTRO_INVALIDO`) y verifica el
`403 SIN_PERMISO` del TECNICO en el JSON y en el Excel.

**Receta y Kardex (Tarea 20)** crea un producto que requiere receta (y
comprueba que sin declarar `requiereReceta` el alta da 400), registra su lote
y verifica que el Kardex muestre la ENTRADA. Abre una caja del TECNICO y prueba
la venta: sin receta → `400 RECETA_REQUERIDA`, CMP que no son dígitos → 400,
fecha de emisión futura → `400 RECETA_INVALIDA`, y con receta → 200 (la venta
por id trae su receta). Después el Kardex muestra la `SALIDA_RECETA`; con
`desde` = mañana solo queda la fila del saldo anterior; descarga el `.xlsx`, y
valida los errores (sin `productoId` → 400, `desde > hasta` → 400, producto
inexistente o de otra botica → 404, TECNICO → 403 en el JSON y en el Excel,
sin token → 401). Al final cierra la caja.

**Reporte DIGEMID (RF-54)** consulta como ADMINISTRADOR el reporte de hoy y
verifica la línea de la venta con receta de la carpeta anterior (producto,
cantidad, lote y vencimiento, número de receta, CMP, médico y técnico), el
total por producto al pie, el rótulo de reporte interno y que la paginación
cuadre con los totales; descarga el `.xlsx`
(tipo, nombre `reporte-digemid_<desde>_<hasta>.xlsx` y cuerpo zip `PK`),
valida los errores de rango (`desde > hasta`, rango mayor a 1 año, sin
`desde` → `400 RANGO_FECHAS_INVALIDO`) y comprueba el `403 SIN_PERMISO` del
TECNICO en el JSON y en el Excel y el 401 sin token. Por eso va después de
**Receta y Kardex**.

**Sin conexión (Tarea 22)** abre una caja del TECNICO, crea su propio producto
(`SC` + timestamp) con un lote de 3 unidades y pide la foto del catálogo
(`GET /api/productos/catalogo-offline`, los dos roles; 401 sin token). Manda
ventas con `sinConexion`: 5 unidades con 3 en stock se registran igual con la
revisión `STOCK_INSUFICIENTE` (y el reenvío con la misma clave devuelve la misma
venta); un `precioCobrado` distinto se respeta con su revisión `PRECIO_DISTINTO`;
sin `precioCobrado` → 400; en la caja de otro usuario → 403. Comprueba
`cantidadVentasSinConexion` en el resumen de cierre, lista y resuelve las
revisiones como ADMINISTRADOR (`FALTANTE` sin nota → 400, con nota → 200, otra
vez → 409, inexistente → 404), el contador `ventasPorRevisar` del dashboard
(`null` para el TECNICO) y el 403 del TECNICO en las dos rutas de revisiones.
Al final cierra la caja.

**Solo lectura** (20 peticiones) no escribe nada y hace su propio login:
config, sesión, caja de hoy, productos, lotes, ventas, mermas, dashboard,
alertas, notificaciones, cajas abiertas, Kardex, los 403 del TECNICO y los 401
sin token o con el JWT en la URL. Es la única carpeta que se corre contra
producción, y solo con usuarios demo (R23).

## Producción es de solo lectura (R23)

Un script _pre-request_ a nivel de colección corre antes de cada petición:
fuera del entorno local, toda petición que no sea `GET` (salvo
`POST /api/auth/login`) **no se envía** y queda como aserción fallida
`R23: bloqueada, …`. Se decide por el entorno, no por `baseUrl`: solo puede
escribir un entorno con `entorno = local` cuyo nombre no contenga
"produccion". Si alguien corre la colección completa con el entorno de
producción, la caja real no se cierra ni se crea ningún lote.

## Postman de escritorio

1. _Import_ → arrastra los tres `.json` de esta carpeta.
2. Arriba a la derecha, elige el entorno **BoticaSys local**.
3. _Environments_ → BoticaSys local → en **Current value** (no en _Initial
   value_) escribe `passwordTecnico`, `passwordAdmin` y
   `passwordAdminOtraBotica`: son las contraseñas del seed de desarrollo
   (están en el comentario de cabecera de
   `backend/src/main/resources/db/seed/dev/V4__seed.sql`). Guarda.
4. Con el backend local corriendo (`./mvnw spring-boot:run` contra
   `botica_db`, ver "Cómo levantarlo en local" en el `README.md` de la raíz),
   clic derecho en la colección → **Run collection** → _Run BoticaSys_.
5. **Captura:** el resumen del Runner (peticiones, aserciones, 0 fallidas) con
   el reloj del sistema visible en la barra de tareas, para que se vea la fecha.

Contra producción, solo la carpeta **Solo lectura**: entorno **BoticaSys
producción**, en _Current value_ `usuarioTecnico`, `passwordTecnico`,
`usuarioAdmin` y `passwordAdmin` de una botica demo, y clic en la carpeta →
_Run_. Si se corre la colección completa contra producción, las peticiones que
escriben salen como fallidas `R23: bloqueada` (es lo esperado).

## Newman (línea de comandos)

```
npx -p newman@6 -p newman-reporter-htmlextra newman run postman/BoticaSys.postman_collection.json \
  -e postman/local.postman_environment.json \
  --env-var passwordTecnico=... --env-var passwordAdmin=... --env-var passwordAdminOtraBotica=... \
  -r cli,htmlextra --reporter-htmlextra-export postman/reportes/corrida.html
```

Contra producción, solo la carpeta de lectura:

```
npx -p newman@6 newman run postman/BoticaSys.postman_collection.json \
  -e postman/produccion.postman_environment.json --folder "Solo lectura" \
  --env-var usuarioTecnico=... --env-var passwordTecnico=... \
  --env-var usuarioAdmin=... --env-var passwordAdmin=...
```

## Lo que no se prueba aquí

El stream SSE abierto con un ticket válido (`GET /api/notificaciones/stream`)
no termina nunca, así que Newman se quedaría esperando. La colección prueba
sus dos rechazos (ticket inventado y JWT en la query → 401), y el caso que
funciona lo cubre `NotificacionTicketSeguridadTest`.
