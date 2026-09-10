# BoticaSys

PWA de gestión para boticas independientes. Monorepo con tres carpetas:

- `backend/` — API en Spring Boot 4.1.1, JDBC puro (sin JPA/Hibernate).
- `frontend/` — Angular 22 PWA zoneless (Bootstrap 5 solo para grid/layout).
- `docs/` — documentación del proyecto: estado actual, contrato de API,
  modelo de datos, despliegue, plan de pruebas y bitácora de errores.
  Empieza por `docs/ESTADO.md` para saber en qué punto está cada módulo.

Para las reglas de arquitectura y las convenciones del proyecto, ver
[`CLAUDE.md`](./CLAUDE.md).

## Backend

Requiere el toolchain de Java 25 (Maven lo provisiona si no está
instalado). Desde `backend/`:

```
./mvnw spring-boot:run     # levanta la API
./mvnw test                 # todos los tests
./mvnw clean package        # build de producción
```

Paquete raíz: `com.botica.backend`. Base de datos: PostgreSQL
(`botica_db` en desarrollo local).

## Frontend

Desde `frontend/`:

```
npm install
npm start                   # ng serve, http://localhost:4200
npm test                    # ng test (Vitest)
npm run build                # build de producción a dist/
```
