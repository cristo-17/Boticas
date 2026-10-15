# BoticaSys

PWA de gestión para boticas independientes: ventas con FEFO, inventario
por lotes, merma, caja diaria, alertas y avisos en vivo, autenticación
por roles (TECNICO / ADMINISTRADOR) y aislamiento entre boticas.

- **Frontend en vivo:** https://boticas.vercel.app
- **Backend en vivo:** https://botica-vbzh.onrender.com/api (plan
  gratuito de Render: se duerme tras 15 min sin tráfico; la primera
  petición después de eso tarda ~1 minuto)
- **Informe de avance:** [`INFORME-AVANCE.md`](./INFORME-AVANCE.md)

Monorepo:

- `backend/` — API en Spring Boot 4.1.1, JDBC puro (sin JPA/Hibernate).
- `frontend/` — Angular 22 PWA zoneless (Bootstrap 5 solo para grid/layout).

La documentación de trabajo (`docs/`) y `CLAUDE.md` se mantienen fuera
del repositorio, en la copia local del equipo.

Despliegue: Render (backend, `backend/Dockerfile`) + Vercel (frontend,
`frontend/vercel.json`) + Neon (PostgreSQL). Las migraciones Flyway
corren solas al arrancar el backend.

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
npm run e2e                  # Playwright, flujos de punta a punta
```

## Pruebas

- **Backend** (`backend/src/test/java/`, JUnit 5 + Mockito): reglas de
  negocio, dinero, controladores (401/403 y forma del error) y DAOs
  contra **PostgreSQL real** (`botica_test`, nunca H2), incluido el
  aislamiento entre boticas. Los tests de base de datos necesitan
  `DB_USERNAME` y `DB_PASSWORD` en el entorno; la suite reconstruye
  `botica_test` desde cero en cada corrida.
- **Frontend** (`frontend/src/app/**/*.spec.ts`, Vitest).
- **E2E** (`frontend/e2e/`, Playwright) contra la app levantada.
