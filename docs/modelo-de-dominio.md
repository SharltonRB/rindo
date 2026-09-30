# Modelo de dominio

Diseño del dominio de Rindo: qué entidades existen, cómo se agrupan en agregados, qué módulos las
gobiernan y qué reglas aplican. Este documento precede a la implementación; ninguna clase Java
existe todavía.

- Diagrama entidad-relación: [`images/er-dominio.md`](images/er-dominio.md)
- Máquina de estados del gasto: [`images/maquina-estados-gasto.md`](images/maquina-estados-gasto.md)

---

## 1. Vocabulario

Un **gasto** (`Expense`) es un desembolso que un empleado adelanta y quiere que su empresa le
reembolse. Se respalda con un **recibo** (`Receipt`), que es el archivo de la factura o tique. Una
**política** (`Policy`) define qué gastos son admisibles y cuántos niveles de aprobación necesitan.
Cada decisión humana queda registrada como un **paso de aprobación** (`ApprovalStep`).

---

## 2. Agregados

Un **agregado** es un grupo de entidades que cambian juntas y que se cargan y guardan como una
unidad. Su raíz es la única entidad a la que se accede desde fuera; nadie modifica un miembro
interno saltándose la raíz. El límite del agregado es el límite de la transacción y de las
invariantes.

| Agregado (raíz) | Contiene | Invariante que protege |
|---|---|---|
| **Company** | `Department` | Un departamento no existe fuera de una empresa; su centro de coste es único dentro de ella |
| **User** | — | Email único por empresa; un usuario deshabilitado no puede actuar |
| **Category** | — | El árbol de categorías no tiene ciclos; el código es único por empresa |
| **Policy** | — | Los periodos de vigencia de políticas que aplican a la misma categoría no se solapan |
| **Receipt** | — | Un recibo `EXTRACTED` tiene `extracted_data`; uno `FAILED` tiene motivo de error |
| **Expense** | `ApprovalStep` | **La invariante central**: el estado solo avanza por transiciones válidas, y los pasos de aprobación son coherentes con `current_approval_level` |
| **AuditEvent** | — | Append-only: nunca se actualiza ni se borra. `hash` encadena con `previous_hash` |
| **Job** | — | Un trabajo `PROCESSING` tiene `locked_at` y `locked_by`; `attempts` nunca supera `max_attempts` |

### Por qué `ApprovalStep` vive dentro de `Expense`

Aprobar un gasto cambia dos cosas a la vez: se cierra el `ApprovalStep` del nivel actual y avanza
`Expense.status` o `current_approval_level`. Si fueran agregados separados, existiría un instante en
el que el paso está aprobado pero el gasto aún no lo refleja — y un fallo justo ahí dejaría el
sistema incoherente. Al compartir agregado, ambas escrituras entran en la misma transacción y el
bloqueo optimista de `Expense.version` resuelve las decisiones simultáneas.

### Referencias entre agregados

Un agregado **nunca** contiene a otro: lo referencia por identificador. `Expense` guarda
`category_id` y `receipt_id`, no objetos `Category` ni `Receipt` completos. Esto mantiene las
transacciones pequeñas y evita cargar media base de datos para aprobar un gasto.

---

## 3. Value objects

Un **value object** no tiene identidad propia: dos instancias con los mismos valores son la misma
cosa. Son inmutables y se validan al construirse.

| Value object | Composición | Por qué no es un campo suelto |
|---|---|---|
| **`Money`** | `amount` (`BigDecimal`) + `currency` (ISO 4217) | Un importe sin moneda no significa nada. Encapsularlos impide sumar colones con dólares por accidente |
| **`ExpenseStatus`** | Enumerado + tabla de transiciones válidas | La regla "de `APPROVED` solo se va a `REIMBURSED`" vive en el tipo, no repartida por los servicios |
| **`EmailAddress`** | `String` validado y normalizado a minúsculas | Evita que `Ana@x.com` y `ana@x.com` se traten como usuarios distintos |
| **`StorageKey`** | Ruta en el bucket S3/MinIO | Aísla el formato de la clave del resto del dominio |
| **`TaxId`** | Identificación fiscal con su país | Su formato y validación dependen del país |

### Por qué el dinero nunca se modela con `double`

`double` es coma flotante binaria y **no puede representar exactamente** la mayoría de decimales del
sistema monetario. En Java, `0.1 + 0.2` da `0.30000000000000004`. Aplicado a dinero, cada operación
acumula un error que termina en cuadres contables que no cierran.

La regla en Rindo:

- **En Java:** `BigDecimal`, siempre construido desde `String` o `long`, nunca desde `double`
  (`new BigDecimal(0.1)` arrastra el error original). Redondeo explícito con `RoundingMode.HALF_UP`.
- **En PostgreSQL:** `NUMERIC(19,4)`, que es aritmética decimal exacta. Nunca `float` ni
  `double precision`.
- **Cuatro decimales, no dos:** los tipos de cambio y los prorrateos producen importes intermedios
  con más precisión que la moneda final. Redondear a dos en cada paso introduce sesgo acumulado.
- **La moneda viaja siempre con el importe**, como `char(3)` ISO 4217 (`CRC`, `USD`).

Un gasto en moneda extranjera guarda tres datos: `amount` + `currency` (lo que el empleado gastó),
`exchange_rate` (`NUMERIC(19,6)`, la tasa aplicada) y `amount_in_company_currency` (el importe
convertido, congelado en el momento del envío). Recalcular la conversión más tarde daría una cifra
distinta y rompería los informes ya emitidos.

---

## 4. Bounded context

Rindo es **un solo bounded context**: "gestión de rendición de gastos". Todo el sistema usa las
mismas palabras con el mismo significado — un `Expense` es lo mismo en aprobaciones que en informes.

Esto es una decisión, no una omisión. Múltiples bounded contexts se justifican cuando el mismo
término significa cosas distintas para áreas distintas (el "cliente" de ventas no es el "cliente" de
soporte). Aquí no ocurre. Lo que sí hay es **un contexto dividido en módulos**, que es exactamente
el problema que resuelve Spring Modulith y el motivo del [ADR-001](adr/001-monolito-modular.md).

---

## 5. Módulos y responsabilidades

Cada módulo es un paquete de primer nivel bajo `com.rindo`, con su API pública explícita y su
implementación interna oculta. La comunicación entre módulos es por llamada a la API pública
(síncrona) o por eventos de Spring Modulith (asíncrona y desacoplada).

| Módulo | Responsabilidad | Agregados que posee | Publica | Consume |
|---|---|---|---|---|
| `companies` | Empresas y departamentos. Raíz del multi-tenant | `Company`, `Department` | `CompanyCreated` | — |
| `users` | Usuarios, roles, autenticación y emisión de JWT | `User` | `UserInvited`, `UserDisabled` | `CompanyCreated` |
| `categories` | Árbol de categorías de gasto | `Category` | — | `CompanyCreated` |
| `receipts` | Subida y almacenamiento de archivos en S3/MinIO | `Receipt` | `ReceiptUploaded` | — |
| `extraction` | Extracción de datos del recibo con Spring AI + Gemini Flash | — (escribe en `Receipt` vía su API) | `ReceiptExtracted`, `ReceiptExtractionFailed` | `ReceiptUploaded` |
| `expenses` | Ciclo de vida del gasto y su máquina de estados | `Expense`, `ApprovalStep` | `ExpenseSubmitted`, `ExpenseApproved`, `ExpenseRejected`, `ExpenseReimbursed` | `ReceiptExtracted` |
| `policies` | Reglas de admisibilidad y niveles de aprobación requeridos | `Policy` | `PolicyViolated` | `ExpenseSubmitted` |
| `approvals` | Enrutado de aprobaciones: a quién le toca decidir | — (opera sobre `ApprovalStep` vía `expenses`) | `ApprovalRequested` | `ExpenseSubmitted`, `PolicyViolated` |
| `audit` | Registro append-only con hash encadenado | `AuditEvent` | — | **todos** los eventos de dominio |
| `reporting` | Consultas agregadas y métricas para los gráficos | — (solo lectura) | — | `ExpenseApproved`, `ExpenseReimbursed` |
| `jobs` | Cola de trabajos sobre PostgreSQL | `Job` | `JobFailed` | — |

### Reglas de dependencia

- **A `audit` no lo llama nadie.** Se suscribe a los eventos. Así ningún módulo puede olvidarse de
  auditar, y quitar la auditoría no rompería a nadie.
- **`extraction` no conoce `expenses`.** Publica `ReceiptExtracted` y se desentiende. Quien quiera
  reaccionar, se suscribe. Esto permite que la extracción por IA falle o se reintente sin bloquear
  el flujo del gasto.
- **`reporting` solo lee.** No posee agregados ni emite eventos.
- **`jobs` es infraestructura de dominio.** Lo usa `extraction` hoy; mañana, cualquier módulo que
  necesite trabajo diferido.
- **Nadie accede a las tablas de otro módulo.** ArchUnit y los tests de Spring Modulith lo verifican
  en CI; un acceso cruzado rompe la build.

### Por qué `approvals` está separado de `expenses`

`expenses` responde a *"¿puede este gasto pasar de este estado a este otro?"*. `approvals` responde a
*"¿a quién le toca decidir?"* — jerarquía del departamento, escalado cuando el aprobador es el propio
solicitante, delegación por vacaciones. Son dos ejes de cambio distintos: la política de enrutado
cambia mucho más a menudo que la máquina de estados, y mezclarlas convertiría `expenses` en el
módulo que todo el mundo toca.

---

## 6. Convenciones transversales

Aplican a **todas** las entidades de negocio.

| Convención | Regla | Motivo |
|---|---|---|
| **Multi-tenant** | `company_id` obligatorio, incluso si es deducible por la relación padre | Filtrado por tenant en una condición; habilita Row Level Security sin joins (ticket #33) |
| **Identificadores** | `id` `bigint` interno + `public_id` `uuid` expuesto | El `bigint` mantiene los índices compactos; el `uuid` impide enumerar recursos desde fuera |
| **Dinero** | `NUMERIC(19,4)` + `char(3)` ISO 4217, siempre juntos | Exactitud decimal y ausencia de ambigüedad de moneda |
| **Fechas** | `timestamptz` para instantes, `date` para la fecha del gasto | `timestamptz` normaliza a UTC; la fecha de un gasto no tiene hora ni zona |
| **Borrado** | Lógico (`deleted_at`), nunca físico, en entidades de negocio | Un gasto referenciado por un `AuditEvent` no puede desaparecer |
| **Concurrencia** | `version` (bloqueo optimista) en `Expense` | Dos aprobadores simultáneos no se pisan |
| **Auditoría** | Todo cambio de estado emite un `AuditEvent` | Trazabilidad verificable mediante la cadena de hashes |

---

## 7. Lo que este documento deja pendiente

Decisiones conscientemente fuera de alcance, para no inventar antes de tiempo:

- **Delegación de aprobaciones** (vacaciones, suplencias). El modelo la admite añadiendo un campo a
  `ApprovalStep`, pero la regla no está definida.
- **Adjuntos múltiples por gasto.** Hoy es `Receipt` → `Expense` uno a uno. Pasar a varios exige una
  tabla intermedia.
- **Origen de los tipos de cambio.** El modelo guarda `exchange_rate`; de dónde sale (API externa,
  tabla manual) es otra decisión.
- **Límites por periodo** ("máximo ₡200.000 al mes en transporte"). `Policy` hoy limita por gasto
  individual, no acumulado.

Cada una, cuando llegue, se decide en su propio ADR.
