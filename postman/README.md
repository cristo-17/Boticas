# Pruebas de API con Postman

Colección de BoticaSys: una carpeta por módulo, con todos los endpoints de
`docs/API-CONTRATO.md`. Cada petición prueba el código HTTP y lo que importa
del cuerpo. Además, a nivel de colección se verifica que todo error tenga el
formato uniforme (`timestamp`, `status`, `error`, `mensaje`, `path`).

| Archivo | Qué es |
|---|---|
| `BoticaSys.postman_collection.json` | La colección (formato v2.1). |
| `local.postman_environment.json` | `baseUrl` local y usuarios del seed de desarrollo. |
| `produccion.postman_environment.json` | `baseUrl` de Render. Los usuarios demo se cargan en la Tarea D; hasta entonces van vacíos. |
| `reportes/` | Reportes HTML de Newman. Git los ignora porque incluyen tokens. |

**Ninguna contraseña está en estos archivos.** Las variables `password*` están
vacías: se cargan como *current value* en Postman (que no se exporta ni se
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

**Solo lectura** no escribe nada y hace su propio login. Es la única carpeta
que se corre contra producción, y solo con usuarios demo (R23).

## Producción es de solo lectura (R23)

Un script *pre-request* a nivel de colección corre antes de cada petición:
fuera del entorno local, toda petición que no sea `GET` (salvo
`POST /api/auth/login`) **no se envía** y queda como aserción fallida
`R23: bloqueada, …`. Se decide por el entorno, no por `baseUrl`: solo puede
escribir un entorno con `entorno = local` cuyo nombre no contenga
"produccion". Si alguien corre la colección completa con el entorno de
producción, la caja real no se cierra ni se crea ningún lote.

## Postman de escritorio

1. *Import* → arrastra los tres `.json` de esta carpeta.
2. Arriba a la derecha, elige el entorno **BoticaSys local**.
3. *Environments* → BoticaSys local → en **Current value** (no en *Initial
   value*) escribe `passwordTecnico`, `passwordAdmin` y
   `passwordAdminOtraBotica`. Guarda.
4. Con el backend local corriendo (`./mvnw spring-boot:run`, seed de
   desarrollo), clic derecho en la colección → **Run collection** → *Run
   BoticaSys*.
5. **Captura:** el resumen del Runner (peticiones, aserciones, 0 fallidas) con
   el reloj del sistema visible en la barra de tareas, para que se vea la fecha.

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
