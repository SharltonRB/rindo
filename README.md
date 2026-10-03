# Rindo

Plataforma de rendición de gastos que digitaliza facturas y recibos, extrae sus datos con IA y deja
trazabilidad auditable de cada aprobación.

> **Estado: En construcción** 🚧
> El proyecto está en fase inicial. La estructura del repositorio y las convenciones de trabajo ya
> están definidas; los módulos de backend y frontend se irán incorporando ticket a ticket.

---

## Stack

### Backend

| Área | Tecnología |
|------|------------|
| Lenguaje y framework | Java 21 + Spring Boot |
| Arquitectura | Spring Modulith (monolito modular) |
| Seguridad | Spring Security + JWT propio (access token y refresh token) |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway |
| IA | Spring AI + Gemini Flash (extracción de facturas) |
| Resiliencia | Resilience4j (reintentos, circuit breaker) y Bucket4j (rate limiting) |
| Tiempo real | Server-Sent Events |
| Documentación de API | springdoc-openapi (Swagger) |
| Compilación nativa | GraalVM Native Image |

### Procesamiento asíncrono y auditoría

- Cola de trabajos en PostgreSQL (`FOR UPDATE SKIP LOCKED`)
- Eventos de Spring Modulith
- Auditoría append-only en PostgreSQL con hash encadenado

### Almacenamiento de archivos

- API compatible con S3: **Garage** en local y **Supabase Storage** en producción
  (antes MinIO; ver [ADR-004](docs/adr/004-garage-en-lugar-de-minio.md))

### Frontend

- React + TypeScript + Vite
- TanStack Query, React Router, React Hook Form + Zod
- Tailwind CSS + shadcn/ui
- Recharts (gráficos)

### Calidad

- JUnit 5, Mockito, AssertJ
- Testcontainers, Awaitility, WireMock
- ArchUnit + verificación de Spring Modulith
- JaCoCo (cobertura) y k6 (pruebas de carga)

### DevOps

- Docker + Docker Compose (entorno local)
- GitHub Actions (CI/CD) + GitHub Container Registry
- gitleaks + Trivy (seguridad)
- Actuator + Micrometer + logs JSON (Grafana Cloud opcional)

### Despliegue ($0)

| Componente | Servicio |
|------------|----------|
| Backend | Render |
| Base de datos | Neon |
| Archivos | Supabase Storage |
| Frontend | Vercel |
| IA | Google AI Studio (plan gratuito) |

---

## Estructura del repositorio

```
.
├── backend/          # API Java 21 + Spring Boot (monolito modular) — ver backend/README.md
├── frontend/         # SPA React + TypeScript + Vite
├── infra/docker/     # Docker Compose y Dockerfiles del entorno local
├── docs/
│   ├── adr/          # Architecture Decision Records
│   ├── images/       # Diagramas y capturas usadas en la documentación
│   └── runbooks/     # Procedimientos de operación e incidentes
├── load-tests/       # Escenarios de carga con k6
└── .github/          # Plantilla de PR y workflows de CI/CD
```

---

## Cómo correr en local

### Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker | Cualquiera reciente, con el plugin `compose` | Levantar PostgreSQL y el almacenamiento |
| JDK 21 | Temurin 21 o superior | Compilar y ejecutar el backend |

### 1. Configurar las variables de entorno

```bash
cp infra/docker/.env.example infra/docker/.env
```

El archivo `.env` está ignorado por Git y es el único sitio donde viven las credenciales. Los
valores que trae la plantilla son de juguete y sirven tal cual para desarrollo; **no hay ninguna
contraseña versionada en el repositorio.**

### 2. Levantar la infraestructura

```bash
docker compose -f infra/docker/compose.local.yml up -d --wait
```

`--wait` hace que el comando no devuelva el control hasta que los *healthchecks* pasen. Sin él, el
backend puede intentar conectarse antes de que PostgreSQL acepte conexiones.

| Servicio | Puerto | Qué es |
|---|---|---|
| `postgres` | 5432 | PostgreSQL 16. Flyway crea el esquema al arrancar el backend |
| `storage` | 3900 | API S3 de Garage. El bucket y la clave se crean solos al primer arranque |
| `storage` (admin) | 3903 | Endpoints `/health` y `/metrics` de Garage |

Todos los puertos se publican solo en `127.0.0.1`: no quedan expuestos a la red local.

### 3. Arrancar el backend

```bash
set -a; source infra/docker/.env; set +a      # carga las credenciales en la shell
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Comprobación:

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP", ...}
```

> **Por qué hay que cargar el `.env` a mano:** Spring no lee archivos `.env`, solo variables de
> entorno. Las credenciales del perfil `local` se declaran **sin valor por defecto** a propósito: si
> falta una, la aplicación aborta con `Could not resolve placeholder` en vez de arrancar contra una
> base de datos distinta de la que uno cree.

### Perfiles

| Perfil | Cuándo se usa | De dónde salen las credenciales |
|---|---|---|
| `local` | Desarrollo en tu máquina | `infra/docker/.env`, cargado en la shell |
| `test` | `./mvnw verify` y `spring-boot:test-run` | Las genera Testcontainers; no se declaran |
| `prod` | Render | Variables de entorno del proveedor |

### Comandos útiles

```bash
docker compose -f infra/docker/compose.local.yml ps        # estado y salud
docker compose -f infra/docker/compose.local.yml logs -f   # seguir los logs
docker compose -f infra/docker/compose.local.yml down      # parar (conserva los datos)
docker compose -f infra/docker/compose.local.yml down -v   # parar y BORRAR los datos
```

Para inspeccionar los archivos subidos (Garage no tiene consola web):

```bash
aws --endpoint-url http://localhost:3900 s3 ls s3://rindo-recibos/
```

### Si algo falla

| Síntoma | Causa y solución |
|---|---|
| `falta POSTGRES_DB; copia .env.example a .env` | No existe `infra/docker/.env`. Vuelve al paso 1 |
| `Could not resolve placeholder 'POSTGRES_DB'` | El backend no ve las variables: ejecuta el `source` del paso 3 en la misma shell |
| `port is already allocated` | Ya tienes otro PostgreSQL en el 5432. Cambia `POSTGRES_PORT` en tu `.env` |
| El backend no conecta aunque el contenedor esté arriba | Arrancaste antes de que estuviera sano. Usa `up -d --wait` |

---

## Cómo trabajamos

- **Ramas:** `main` está protegida. Todo cambio entra por Pull Request desde una rama
  `feat/…`, `fix/…`, `docs/…`, etc.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/).
- **Decisiones de arquitectura:** se registran como ADR en [`docs/adr/`](docs/adr/).
  Empieza por [ADR-000](docs/adr/000-usar-adrs.md).
- **Diseño del sistema:** el [modelo de dominio](docs/modelo-de-dominio.md) describe entidades,
  agregados y módulos, con el [diagrama ER](docs/images/er-dominio.md) y la
  [máquina de estados del gasto](docs/images/maquina-estados-gasto.md).

Los detalles están en [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Licencia

[MIT](LICENSE).
