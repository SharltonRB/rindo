# ADR-001: Mantener backend y frontend en un único repositorio

- **Fecha:** 2026-09-29
- **Estado:** Aceptada

## Contexto

Rindo tiene dos artefactos desplegables que no comparten lenguaje ni ciclo de build:

- un backend en Java 21 + Spring Boot, que se despliega en Render;
- un frontend en React + TypeScript + Vite, que se despliega en Vercel.

Además hay artefactos que no son ninguno de los dos y que pertenecen al sistema completo: el Docker
Compose del entorno local (`infra/docker/`), los escenarios de carga de k6 (`load-tests/`), los ADRs
y los runbooks (`docs/`).

Las restricciones que condicionan la decisión:

- **Una sola persona desarrolla el proyecto.** No hay equipos separados de backend y frontend, ni
  necesidad de aislar permisos entre ellos.
- **El contrato de API cambia con frecuencia.** En fase inicial, casi cada funcionalidad toca el
  endpoint y la pantalla que lo consume. Son cambios que nacen acoplados.
- **El presupuesto de infraestructura es $0.** No hay margen para infraestructura de CI compleja ni
  para un registro de paquetes propio.
- **El repositorio es público y forma parte del portafolio.** Que un lector encuentre el sistema
  completo en un solo sitio tiene valor.

La decisión ya venía implícita en la estructura de carpetas acordada (`backend/`, `frontend/`,
`infra/docker/`, `docs/`, `load-tests/`), pero no estaba registrada en ninguna parte. Este ADR la
hace explícita.

## Decisión

Usamos un **único repositorio** (`SharltonRB/rindo`) que contiene backend, frontend, infraestructura,
documentación y pruebas de carga, cada uno en su directorio de primer nivel.

**No** adoptamos ninguna herramienta de gestión de monorepos (Nx, Turborepo, Bazel, Lerna). Cada
directorio conserva su propio sistema de build nativo e independiente: Maven o Gradle en `backend/`,
npm + Vite en `frontend/`. No hay orquestador que los coordine ni dependencias entre ellos a nivel de
build.

**Consecuencias prácticas de esta decisión:**

- La CI de GitHub Actions usa **filtros por ruta** (`paths:`): un cambio que solo toca `frontend/`
  no dispara la build de Java, y viceversa.
- Los despliegues siguen siendo independientes. Render y Vercel apuntan al mismo repositorio pero a
  directorios raíz distintos (`backend/` y `frontend/` respectivamente).
- El versionado es del repositorio completo. Los tags (`v1.2.0`) describen el estado del sistema, no
  de un componente aislado.
- Un cambio que toca el contrato de API y su cliente entra en **un solo Pull Request**, no en dos
  coordinados entre repos.

### Alternativas consideradas

**Dos repositorios separados (`rindo-backend` y `rindo-frontend`).**
Es lo habitual cuando hay equipos distintos con ciclos de release independientes. Descartada: aquí
no hay equipos distintos, y el coste real aparecería en cada cambio del contrato de API, que
obligaría a dos PRs coordinados, con una ventana en la que `main` de un repo es incompatible con
`main` del otro. Además duplica la configuración de CI, de plantillas de PR y de reglas de rama, y
obliga a decidir en cuál de los dos viven `infra/`, `docs/` y `load-tests/` — o a crear un tercer
repositorio para ellos.

**Monorepo con Nx o Turborepo.**
Aportan caché de builds, grafo de dependencias y ejecución selectiva de tareas. Descartada por
[YAGNI](https://martinfowler.com/bliki/Yagni.html): esas herramientas resuelven el problema de
coordinar **muchos** paquetes JavaScript que dependen entre sí. Aquí hay dos artefactos que no
comparten ni lenguaje ni dependencias, y el problema que resuelven —saber qué reconstruir— lo cubre
un filtro `paths:` de pocas líneas en GitHub Actions. Añadirlas sería introducir una capa de
configuración y un modo de fallo nuevo a cambio de nada.

**Submódulos de Git.**
Permiten componer repos manteniéndolos separados. Descartada: arrastran una complejidad operativa
conocida (estados detached, clones incompletos, punteros desincronizados) sin resolver ninguna
restricción de las listadas arriba.

## Consecuencias

**A favor:**

- Un cambio de contrato de API y su cliente viajan en el mismo commit y el mismo PR. El historial
  muestra el cambio completo, no la mitad.
- Una sola configuración de CI, una plantilla de PR, un conjunto de reglas de rama, un `.gitignore`,
  una licencia.
- `infra/`, `docs/` y `load-tests/` tienen un sitio evidente donde vivir.
- Clonar el repositorio da el sistema entero, listo para levantar con Docker Compose.
- El historial de commits cuenta la evolución del producto, no la de un componente suelto.

**En contra:**

- **La CI debe escribirse con filtros por ruta desde el principio.** Sin ellos, cada cambio de una
  línea de CSS dispararía la suite completa de Java con Testcontainers: minutos desperdiciados en
  cada push y una espera que desincentiva los PRs pequeños.
- **El repositorio crece de forma heterogénea.** Conviven artefactos de dos ecosistemas distintos
  (Maven y npm), así que hay que mantener el `.gitignore` disciplinado para que nada generado acabe
  versionado.
- **Los tags de release son ambiguos por componente.** `v1.2.0` no distingue si cambió el backend, el
  frontend o ambos. Mientras el despliegue sea conjunto no es un problema; si algún día dejan de
  desplegarse a la vez, habrá que adoptar tags con prefijo (`backend-v1.2.0`) y escribir un ADR que
  lo registre.
- **Los permisos son de todo o nada.** Quien tenga acceso de escritura al repositorio lo tiene sobre
  backend y frontend. Irrelevante con un solo desarrollador; sería el primer motivo real para
  revisar esta decisión si el proyecto suma colaboradores externos.

### Cuándo reconsiderar esta decisión

Esta decisión deja de sostenerse si aparece alguna de estas señales:

- Backend y frontend dejan de desplegarse de forma conjunta y sus ciclos de release divergen.
- Se incorporan colaboradores que deban tener acceso a un componente pero no al otro.
- Aparece un tercer artefacto desplegable con su propio ciclo de vida (por ejemplo, una app móvil).

En ese caso se escribe un ADR nuevo que reemplace a este. Este archivo no se edita.

## Referencias

- [ADR-000: Registrar las decisiones de arquitectura como ADRs](000-usar-adrs.md)
- GitHub Actions, [sintaxis de workflows: filtros `paths`](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
