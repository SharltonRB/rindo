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

- API compatible con S3: **MinIO** en local y **Supabase Storage** en producción

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
├── backend/          # API Java 21 + Spring Boot (monolito modular)
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

## Cómo trabajamos

- **Ramas:** `main` está protegida. Todo cambio entra por Pull Request desde una rama
  `feat/…`, `fix/…`, `docs/…`, etc.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/).
- **Decisiones de arquitectura:** se registran como ADR en [`docs/adr/`](docs/adr/).
  Empieza por [ADR-000](docs/adr/000-usar-adrs.md).

Los detalles están en [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Licencia

[MIT](LICENSE).
