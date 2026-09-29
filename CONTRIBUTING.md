# Guía de contribución

Este documento define las reglas de trabajo del repositorio. Son pocas y son obligatorias.

## Flujo de trabajo: GitHub Flow

`main` siempre debe ser desplegable. **No se permite push directo a `main`**: todo cambio entra por
Pull Request.

```
main (protegida)
  ├── feat/extraccion-facturas   → PR → merge a main
  ├── fix/login-redirect         → PR → merge a main
  └── docs/adr-003-cola-jobs     → PR → merge a main
```

1. Parte de `main` actualizada: `git checkout main && git pull`
2. Crea la rama: `git checkout -b feat/nombre-corto`
3. Trabaja en commits pequeños y con mensaje claro
4. Sube la rama: `git push -u origin feat/nombre-corto`
5. Abre el Pull Request y completa la plantilla
6. Merge solo con la CI en verde

### Nombres de rama

| Prefijo | Uso |
|---------|-----|
| `feat/` | Funcionalidad nueva |
| `fix/` | Corrección de un error |
| `docs/` | Solo documentación o ADRs |
| `refactor/` | Cambio interno sin alterar comportamiento |
| `test/` | Añadir o corregir pruebas |
| `chore/` | Dependencias, configuración, tareas de mantenimiento |
| `ci/` | Cambios en workflows o pipelines |
| `perf/` | Mejoras de rendimiento |

Mantén las ramas cortas (días, no semanas) y sincronizadas con `main`.

## Commits: Conventional Commits

```
<tipo>(<ámbito>): <asunto>

[cuerpo opcional: explica el porqué, no el qué]

[pie opcional: BREAKING CHANGE, Closes #123]
```

**Tipos permitidos:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`,
`build`, `revert`.

**Ámbitos habituales:** `backend`, `frontend`, `infra`, `docs`, `auth`, `facturas`, `auditoria`,
`ci`.

Reglas del asunto: imperativo, en minúscula, sin punto final, máximo 72 caracteres.

```bash
# Bien
git commit -m "feat(facturas): extraer importe y NIF con Gemini Flash"
git commit -m "fix(auth): renovar el access token antes de que expire"

# Mal
git commit -m "cambios"
git commit -m "WIP"
```

Un cambio que rompe la compatibilidad se marca con `!` o con el pie `BREAKING CHANGE:`:

```
feat(api)!: mover la autenticación a /api/v2/auth
```

## Pull Requests

- El título del PR sigue el mismo formato que los commits.
- Completa la plantilla ([`.github/pull_request_template.md`](.github/pull_request_template.md)).
- PRs pequeños: por debajo de 500 líneas de diff siempre que sea posible.
- Un PR resuelve una sola cosa.
- Antes de pedir revisión: CI en verde, autorrevisión hecha, sin secretos en el diff.

## Decisiones de arquitectura

Toda decisión técnica relevante y difícil de revertir se documenta como ADR en
[`docs/adr/`](docs/adr/), siguiendo el formato descrito en
[ADR-000](docs/adr/000-usar-adrs.md).

## Secretos

Nunca se commitea un secreto. Las credenciales van en variables de entorno y `.env` está ignorado.
El repositorio ejecuta `gitleaks` en CI; si un secreto llega a ser commiteado, hay que **rotarlo**,
no solo borrarlo del historial.
