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
cada lote que crea lleva un código nuevo. Por eso dos corridas seguidas dan el
mismo resultado. Los dos lotes de la prueba FEFO vencen hoy y quedan en 0 al
venderse, así que no dejan stock vencido.

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
