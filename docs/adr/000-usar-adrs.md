# ADR-000: Registrar las decisiones de arquitectura como ADRs

- **Fecha:** 2026-09-29
- **Estado:** Aceptada

## Contexto

Rindo es un proyecto de larga duración desarrollado por una sola persona y sin reuniones donde
quede constancia de lo acordado. A lo largo del desarrollo se toman decisiones técnicas que son
caras de revertir: monolito modular con Spring Modulith en lugar de microservicios, cola de trabajos
sobre PostgreSQL en lugar de un broker dedicado, JWT propio en lugar de un proveedor de identidad
gestionado, almacenamiento compatible con S3, etc.

Si esas decisiones solo viven en el código y en la memoria de quien las tomó, ocurren dos cosas:

1. Meses después es imposible saber **por qué** algo está hecho así, y se reabre el debate desde cero
   o, peor, se cambia sin entender qué restricción lo motivaba.
2. Cualquier persona que lea el repositorio —incluido un reclutador o un futuro colaborador— ve el
   resultado pero no el criterio.

El código explica el *qué*. Los commits explican el *cambio*. Falta un sitio que explique el *porqué*
y las alternativas descartadas.

## Decisión

Registramos cada decisión de arquitectura relevante como un **Architecture Decision Record (ADR)**
en `docs/adr/`, siguiendo el formato ligero propuesto por Michael Nygard.

**Convenciones:**

- Un archivo por decisión, nombrado `NNN-titulo-en-kebab-case.md`, con `NNN` correlativo empezando
  en `000`.
- Los ADRs son **inmutables**. Una decisión que deja de ser válida no se edita ni se borra: se marca
  como `Reemplazada por ADR-NNN` y se escribe un ADR nuevo.
- El ADR se escribe en el mismo Pull Request que introduce la decisión, no después.

**Estados posibles:** `Propuesta`, `Aceptada`, `Rechazada`, `Obsoleta`, `Reemplazada por ADR-NNN`.

**Secciones obligatorias:** Contexto, Decisión, Consecuencias.

**Qué merece un ADR:** elegir o descartar una tecnología del stack, definir un límite entre módulos,
un modelo de datos central, una estrategia de seguridad o de despliegue, o cualquier cosa que sea
costosa de deshacer.

**Qué no merece un ADR:** elegir el nombre de una variable, añadir una dependencia trivial, o
cualquier cambio reversible en una tarde.

### Plantilla

```markdown
# ADR-NNN: Título de la decisión

- **Fecha:** AAAA-MM-DD
- **Estado:** Propuesta | Aceptada | Rechazada | Obsoleta | Reemplazada por ADR-NNN

## Contexto

Qué fuerzas y restricciones existen. Sin juicio, solo los hechos.

## Decisión

Qué se decide hacer, en voz activa: "Usaremos…", "Adoptamos…".
Incluye las alternativas consideradas y por qué se descartaron.

## Consecuencias

Qué se vuelve más fácil y qué se vuelve más difícil a partir de ahora.
Lo bueno y lo malo, ambos.
```

### Alternativas consideradas

- **No documentar nada.** Es lo más barato hoy y lo más caro dentro de seis meses. Descartada.
- **Un documento de arquitectura único.** Se convierte en un archivo enorme que nadie actualiza y en
  el que no se distingue qué se decidió cuándo ni por qué. Descartada.
- **Wiki de GitHub.** Queda fuera del repositorio, no pasa por revisión en el PR y se desincroniza
  del código. Descartada.

## Consecuencias

**A favor:**

- El porqué de cada decisión queda junto al código, versionado y revisado en el mismo PR.
- El historial de ADRs muestra la evolución del criterio técnico del proyecto.
- Reduce el coste de retomar el proyecto después de una pausa.

**En contra:**

- Añade trabajo de escritura a los PRs que introducen decisiones importantes.
- Exige disciplina: un ADR escrito tarde, o no escrito, hace que el directorio deje de ser fiable.
- Hay que decidir caso por caso si algo merece un ADR; el límite no siempre es evidente.

## Referencias

- Michael Nygard, [Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [adr.github.io](https://adr.github.io/)
