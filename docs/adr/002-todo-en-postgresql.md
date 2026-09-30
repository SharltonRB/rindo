# ADR-002: Usar PostgreSQL para datos, cola de trabajos y auditoría

- **Fecha:** 2026-09-29
- **Estado:** Aceptada

## Contexto

Rindo necesita tres capacidades que en una arquitectura convencional se resuelven con tres piezas de
infraestructura distintas:

1. **Datos transaccionales.** Empresas, usuarios, gastos, aprobaciones. Requieren integridad
   referencial y transacciones.
2. **Una cola de trabajos.** La extracción de datos de un recibo con Gemini Flash tarda segundos y
   puede fallar. No puede ejecutarse dentro del ciclo petición-respuesta: necesita encolarse,
   reintentarse con backoff y acabar en una cola de fallidos si agota los intentos.
3. **Un registro de auditoría append-only.** Toda transición de estado de un gasto debe quedar
   registrada de forma que una manipulación posterior sea detectable — de ahí el hash encadenado.

La reacción por defecto sería Redis o RabbitMQ para la cola, y quizá un almacén de solo escritura
para la auditoría. Pero las restricciones del proyecto son concretas:

- **Presupuesto de infraestructura: $0.** El despliegue es Render (plan gratuito) + Neon para la base
  de datos. Cada pieza adicional es otro servicio que provisionar, monitorizar, respaldar y, tarde o
  temprano, pagar.
- **Opera una sola persona.** Cada componente nuevo multiplica el trabajo de operación y los modos de
  fallo posibles.
- **El volumen es modesto.** Una PyME genera decenas o cientos de gastos al día, no millones de
  mensajes por segundo.

También hay una razón de corrección, no solo de coste: **encolar un trabajo y modificar datos deben
ser la misma transacción**. Si `receipts` guarda el recibo en PostgreSQL y publica el trabajo en
Redis, existe una ventana en la que el recibo está guardado pero el trabajo se perdió, o al revés.
Evitar esa ventana con infraestructura separada exige el patrón outbox — que consiste, precisamente,
en escribir primero en una tabla de PostgreSQL.

## Decisión

**PostgreSQL es el único almacén de estado del sistema.** Datos de negocio, cola de trabajos y
registro de auditoría viven en la misma base de datos y comparten transacciones.

### Cola de trabajos: `FOR UPDATE SKIP LOCKED`

La tabla `jobs` se consume con el patrón estándar de cola en PostgreSQL:

```sql
UPDATE jobs
SET status = 'PROCESSING', locked_at = now(), locked_by = :worker_id, attempts = attempts + 1
WHERE id = (
  SELECT id FROM jobs
  WHERE status = 'PENDING' AND available_at <= now()
  ORDER BY available_at
  LIMIT 1
  FOR UPDATE SKIP LOCKED
)
RETURNING *;
```

`SKIP LOCKED` (disponible desde PostgreSQL 9.5) hace que cada worker salte las filas que otro ya
bloqueó, en lugar de esperarlas. El resultado es que N workers consumen en paralelo sin contención y
sin que dos procesen el mismo trabajo. Los reintentos se implementan con `available_at`: al fallar,
se reencola con `available_at = now() + backoff(attempts)`. Al superar `max_attempts`, pasa a `DEAD`
y queda como cola de fallidos consultable con `SELECT`.

Un índice parcial mantiene la consulta barata aunque la tabla acumule histórico:

```sql
CREATE INDEX idx_jobs_pendientes ON jobs (available_at)
  WHERE status = 'PENDING';
```

### Auditoría: append-only con hash encadenado

Cada fila de `audit_event` guarda el hash SHA-256 de su contenido junto con el hash de la fila
anterior de la misma empresa. Alterar o borrar un evento rompe la cadena a partir de ese punto, y un
job de verificación lo detecta.

La restricción de "append-only" se aplica en la propia base de datos: el rol de la aplicación recibe
`INSERT` y `SELECT` sobre esa tabla, pero **no** `UPDATE` ni `DELETE`. Así la garantía no depende de
que el código se porte bien.

### Lo que esta decisión no cubre

Los **archivos** (imágenes y PDFs de los recibos) no van en PostgreSQL. Se almacenan en un servicio
compatible con S3 — MinIO en local, Supabase Storage en producción — y la base de datos guarda solo
la clave (`Receipt.storage_key`) y el checksum. Meter binarios de megabytes en la base de datos
infla los backups y degrada el rendimiento sin dar nada a cambio.

## Alternativas consideradas

**Redis para la cola.**
Es rápido y su API de listas es cómoda. Descartada por dos motivos. El primero es de corrección:
Redis no participa en la transacción de PostgreSQL, así que encolar y guardar dejan de ser atómicos;
recuperar la atomicidad exige el patrón outbox, que ya es una tabla en PostgreSQL. El segundo es
operativo: la persistencia de Redis es configurable y, en los planes gratuitos, a menudo está
desactivada — un reinicio perdería trabajos encolados.

**RabbitMQ o Kafka.**
Resuelven problemas reales (enrutado complejo, fan-out masivo, reprocesado histórico) que Rindo no
tiene: un único tipo de trabajo, un único consumidor, volumen bajo. Descartadas: añadirían un
servicio que operar y una fuente de fallos desproporcionada al beneficio. Kafka, además, no cabe en
el presupuesto.

**`@Scheduled` con un `ExecutorService` en memoria.**
La opción más barata de todas. Descartada porque el estado vive en el proceso: un reinicio de Render
—que en el plan gratuito ocurre con regularidad— perdería todos los trabajos en vuelo, sin rastro ni
posibilidad de reintento.

**Almacén dedicado para la auditoría** (ficheros append-only, base de datos separada).
Descartada: complica el respaldo y la restauración, y rompe la posibilidad de escribir el evento de
auditoría en la misma transacción que el cambio que lo origina. Esa atomicidad es justo lo que hace
la auditoría fiable.

## Consecuencias

**A favor:**

- **Atomicidad real.** Guardar un recibo y encolar su extracción es una sola transacción: o pasan
  ambas cosas o ninguna. Sin outbox, sin conciliación, sin estados imposibles.
- **Una sola pieza que operar**: un backup, una cadena de conexión, un panel de métricas, un punto de
  restauración.
- **Coste cero** en el plan gratuito de Neon.
- **La cola es consultable con SQL.** Depurar por qué un trabajo falló es un `SELECT` con `WHERE`, no
  aprender las herramientas de un broker.
- **La auditoría es inmutable por permisos de base de datos**, no por disciplina del programador.

**En contra:**

- **La cola compite por recursos con las consultas de negocio.** El sondeo de workers y el
  `reporting` pesado van al mismo motor. Mitigación: índice parcial sobre los pendientes, intervalo
  de sondeo razonable y vigilar el pool de HikariCP.
- **Hay techo de rendimiento.** Este patrón sostiene del orden de miles de trabajos por minuto, no
  cientos de miles por segundo. Muy por encima de lo que Rindo necesita, pero es un techo que existe.
- **Sondeo, no push.** Un worker que consulta cada N segundos introduce latencia (hasta N segundos) y
  genera consultas en vacío cuando no hay trabajo. Aceptable para una extracción que ya tarda
  segundos.
- **La tabla `jobs` crece.** Necesita purga periódica de los trabajos terminados, o el índice se
  degrada. Es una tarea de mantenimiento que un broker daría resuelta.
- **La auditoría no se puede corregir.** Un evento mal registrado se compensa con otro evento, nunca
  editando. Es el precio de la inmutabilidad, y es el punto.
- **Un solo punto de fallo.** Si PostgreSQL cae, cae todo: datos, cola y auditoría. Con
  infraestructura separada, la aplicación podría seguir sirviendo lecturas con la cola caída.

## Cuándo reconsiderar esta decisión

- El sondeo de la cola aparece entre las consultas más costosas de `pg_stat_statements`.
- La latencia introducida por el sondeo deja de ser aceptable para algún caso de uso.
- Aparecen consumidores múltiples que necesitan fan-out del mismo evento (ahí un broker sí aporta).
- El volumen de trabajos supera de forma sostenida lo que el patrón soporta con holgura.

Hasta entonces, añadir un broker sería resolver un problema que no tenemos con un coste que sí
tendríamos.

## Referencias

- [ADR-000: Registrar las decisiones de arquitectura como ADRs](000-usar-adrs.md)
- [ADR-001: Monolito modular con Spring Modulith](001-monolito-modular.md)
- [Modelo de dominio](../modelo-de-dominio.md)
- PostgreSQL, [cláusula de bloqueo `SKIP LOCKED`](https://www.postgresql.org/docs/current/sql-select.html)
