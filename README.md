# Rindo

Plataforma de rendición de gastos que digitaliza facturas y recibos, extrae sus datos con IA y deja
trazabilidad auditable de cada aprobación.

> **Estado: En construcción** 🚧
> El proyecto se construye ticket a ticket. **Hoy funciona:** el esqueleto del backend con sus once
> módulos de Spring Modulith, el esquema gobernado por Flyway, la compilación a binario nativo con
> GraalVM, la CI de GitHub Actions y el entorno local con Docker Compose.
> **Todavía no hay lógica de negocio ni frontend:** los módulos están declarados pero vacíos.

---

## Stack

> **Cómo leer estas tablas.** El stack está decidido de principio a fin, pero construido solo en
> parte. La columna de estado distingue lo uno de lo otro:
> **✅ ya está en el repositorio** · **🔜 decidido, todavía no implementado**

### Backend

| Área | Tecnología | Estado |
|------|------------|:---:|
| Lenguaje y framework | Java 21 + Spring Boot 4.1 | ✅ |
| Arquitectura | Spring Modulith (monolito modular) | ✅ |
| Persistencia | Spring Data JPA + PostgreSQL + Flyway | ✅ |
| Compilación nativa | GraalVM Native Image | ✅ |
| Seguridad | Spring Security | ✅ |
| Seguridad | JWT propio (access token y refresh token) | 🔜 |
| IA | Spring AI + Gemini Flash (extracción de facturas) | 🔜 |
| Resiliencia | Resilience4j (reintentos, circuit breaker) y Bucket4j (rate limiting) | 🔜 |
| Tiempo real | Server-Sent Events | 🔜 |
| Documentación de API | springdoc-openapi (Swagger) | 🔜 |

### Procesamiento asíncrono y auditoría

| Pieza | Estado |
|---|:---:|
| Eventos de Spring Modulith, persistidos en `event_publication` | ✅ |
| Cola de trabajos en PostgreSQL (`FOR UPDATE SKIP LOCKED`) | 🔜 |
| Auditoría append-only en PostgreSQL con hash encadenado | 🔜 |

### Almacenamiento de archivos

| Pieza | Estado |
|---|:---:|
| **Garage** en local, por API compatible con S3 | ✅ |
| **Supabase Storage** en producción, por la misma API | 🔜 |

Era MinIO hasta que dejó de distribuirse; el porqué del cambio está en el
[ADR-004](docs/adr/004-garage-en-lugar-de-minio.md).

### Frontend

Nada implementado todavía: `frontend/` está vacío. Lo decidido es React + TypeScript + Vite, con
TanStack Query, React Router, React Hook Form + Zod, Tailwind CSS + shadcn/ui y Recharts.

### Calidad

| Herramienta | Estado |
|---|:---:|
| JUnit 5, Mockito, AssertJ | ✅ |
| Testcontainers | ✅ |
| ArchUnit + verificación de Spring Modulith | ✅ |
| Awaitility, WireMock | 🔜 |
| JaCoCo (cobertura) | 🔜 |
| k6 (pruebas de carga) | 🔜 |

### DevOps

| Pieza | Estado |
|---|:---:|
| Docker + Docker Compose (entorno local) | ✅ |
| GitHub Actions: tests, verificación de módulos y compilación nativa | ✅ |
| Actuator | ✅ |
| Publicación de imagen en GitHub Container Registry | 🔜 |
| gitleaks + Trivy (seguridad) | 🔜 |
| Micrometer + logs JSON (Grafana Cloud opcional) | 🔜 |

### Despliegue ($0)

Ningún entorno está desplegado todavía. Los destinos elegidos:

| Componente | Servicio |
|------------|----------|
| Backend | Render |
| Base de datos | Neon |
| Archivos | Supabase Storage |
| Frontend | Vercel |
| IA | Google AI Studio (plan gratuito) |

---

## Por qué este stack

Las decisiones costosas de revertir están razonadas en sus ADR; lo demás, en una línea:

- **Monolito modular en vez de microservicios.** Un solo desplegable, con fronteras entre módulos
  verificadas por la build en lugar de por la red. [ADR-001](docs/adr/001-monolito-modular.md)
- **Todo en PostgreSQL.** La cola de trabajos y la auditoría viven en la misma base de datos que el
  dominio, lo que evita operar Redis o Kafka para un volumen que no los necesita y mantiene las
  transacciones en un solo sitio. [ADR-002](docs/adr/002-todo-en-postgresql.md)
- **Monorepo.** Un cambio de contrato de API y su cliente entran en el mismo PR.
  [ADR-003](docs/adr/003-monorepo.md)
- **Garage en local, Supabase Storage en producción.** Ambos hablan el protocolo S3, así que el
  backend no distingue uno de otro. [ADR-004](docs/adr/004-garage-en-lugar-de-minio.md)
- **Compilación nativa con GraalVM.** El plan gratuito de Render da 512 MB y suspende el servicio
  por inactividad: un binario nativo arranca en centésimas y consume una fracción de lo que necesita
  la JVM. A cambio, obliga a comprobar la compatibilidad de cada dependencia antes de añadirla
  (ver [backend/README.md](backend/README.md#notas-sobre-graalvm)).
- **Java 21.** Los hilos virtuales encajan con un backend dominado por llamadas bloqueantes a base
  de datos, almacenamiento y Gemini: dejan de retener un hilo del sistema operativo por petición.
- **Presupuesto de $0.** Render, Neon, Supabase, Vercel y Google AI Studio tienen plan gratuito
  suficiente para este proyecto, y esa restricción condiciona varias de las decisiones anteriores.

---

## Estructura del repositorio

```
.
├── backend/          # API Java 21 + Spring Boot (monolito modular) — ver backend/README.md
├── frontend/         # SPA React + TypeScript + Vite              — vacío todavía
├── infra/docker/     # Docker Compose del entorno local
├── docs/
│   ├── adr/          # Architecture Decision Records
│   ├── images/       # Diagramas y capturas usadas en la documentación
│   └── runbooks/     # Procedimientos de operación e incidentes   — vacío todavía
├── load-tests/       # Escenarios de carga con k6                 — vacío todavía
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

Para inspeccionar los archivos subidos (Garage no tiene consola web), con las variables del `.env`
ya cargadas en la shell:

```bash
docker run --rm --network host rclone/rclone ls :s3:$S3_BUCKET --config="" \
  --s3-provider=Other \
  --s3-endpoint=$S3_ENDPOINT \
  --s3-region=$S3_REGION \
  --s3-access-key-id=$S3_ACCESS_KEY \
  --s3-secret-access-key=$S3_SECRET_KEY
```

Si prefieres tenerlo a mano sin Docker, `brew install rclone` y el mismo comando a partir de `ls`.

> **Por qué rclone y no la CLI de AWS.** Las credenciales salen del `.env` que ya tienes, sin
> duplicarlas en un segundo archivo de configuración (`~/.aws/credentials` o `~/.s3cfg`). Es un
> binario único sin dependencias, habla con cualquier servidor compatible con S3 —el mismo comando
> servirá contra Supabase Storage cambiando endpoint y credenciales— y no arrastra la nomenclatura
> de AWS a un proyecto que no usa AWS. La CLI de Garage se descartó porque solo habla con Garage y
> no valdría para producción.

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
