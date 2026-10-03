# Diagrama entidad-relación

Modelo de datos de Rindo. El diagrama está escrito en Mermaid para que GitHub lo renderice
directamente y para que los cambios se puedan revisar en un diff, algo imposible con una imagen
binaria.

Los campos y su justificación están en [`docs/modelo-de-dominio.md`](../modelo-de-dominio.md).

```mermaid
erDiagram
    COMPANY ||--o{ DEPARTMENT : "tiene"
    COMPANY ||--o{ USER : "emplea"
    COMPANY ||--o{ CATEGORY : "define"
    COMPANY ||--o{ POLICY : "define"
    COMPANY ||--o{ RECEIPT : "almacena"
    COMPANY ||--o{ EXPENSE : "registra"
    COMPANY ||--o{ AUDIT_EVENT : "audita"

    DEPARTMENT ||--o{ USER : "agrupa"

    USER ||--o{ EXPENSE : "reporta"
    USER ||--o{ RECEIPT : "sube"
    USER ||--o{ APPROVAL_STEP : "decide"

    CATEGORY ||--o{ CATEGORY : "es padre de"
    CATEGORY ||--o{ EXPENSE : "clasifica"
    CATEGORY ||--o{ POLICY : "restringe"

    RECEIPT ||--o| EXPENSE : "respalda"

    EXPENSE ||--o{ APPROVAL_STEP : "requiere"

    COMPANY {
        bigint id PK
        uuid public_id UK
        text name
        text tax_id
        char default_currency "char(3), ISO 4217"
        timestamptz created_at
    }

    DEPARTMENT {
        bigint id PK
        uuid public_id UK
        bigint company_id FK
        bigint manager_user_id FK "nullable"
        text name
        text cost_center
    }

    USER {
        bigint id PK
        uuid public_id UK
        bigint company_id FK
        bigint department_id FK "nullable"
        citext email "unico por company"
        text password_hash
        text full_name
        text role "EMPLOYEE|APPROVER|FINANCE|ADMIN"
        text status "INVITED|ACTIVE|DISABLED"
    }

    CATEGORY {
        bigint id PK
        uuid public_id UK
        bigint company_id FK
        bigint parent_id FK "nullable"
        text name
        text code "unico por company"
        boolean active
    }

    POLICY {
        bigint id PK
        uuid public_id UK
        bigint company_id FK
        bigint category_id FK "nullable = aplica a todas"
        text name
        numeric max_amount "NUMERIC(19,4), nullable"
        char currency "char(3)"
        boolean requires_receipt
        int max_days_old
        int approval_levels
        date effective_from
        date effective_to "nullable"
    }

    RECEIPT {
        bigint id PK
        uuid public_id UK
        bigint company_id FK
        bigint uploaded_by_user_id FK
        text storage_key "clave en S3"
        text original_filename
        text content_type
        bigint size_bytes
        bytea checksum_sha256
        text status "UPLOADED|PROCESSING|EXTRACTED|FAILED"
        jsonb extracted_data "salida de Gemini, nullable"
        numeric extraction_confidence "nullable"
        timestamptz uploaded_at
    }

    EXPENSE {
        bigint id PK
        uuid public_id UK
        bigint company_id FK
        bigint employee_user_id FK
        bigint department_id FK
        bigint category_id FK
        bigint receipt_id FK "nullable"
        text status "ver maquina de estados"
        text description
        text merchant_name
        numeric amount "NUMERIC(19,4)"
        char currency "char(3), ISO 4217"
        numeric amount_in_company_currency "NUMERIC(19,4)"
        numeric exchange_rate "NUMERIC(19,6)"
        date expense_date
        int current_approval_level
        bigint version "bloqueo optimista"
        timestamptz submitted_at
        timestamptz decided_at
        timestamptz reimbursed_at
    }

    APPROVAL_STEP {
        bigint id PK
        uuid public_id UK
        bigint company_id FK
        bigint expense_id FK
        bigint approver_user_id FK
        int level
        text decision "PENDING|APPROVED|REJECTED"
        text comment
        timestamptz decided_at
    }

    AUDIT_EVENT {
        bigint id PK "secuencial, append-only"
        bigint company_id FK
        bigint actor_user_id FK "nullable = sistema"
        text event_type
        text aggregate_type
        bigint aggregate_id
        jsonb payload
        bytea previous_hash
        bytea hash "SHA-256 encadenado"
        timestamptz occurred_at
    }

    JOB {
        bigint id PK
        bigint company_id FK "nullable"
        text type "EXTRACT_RECEIPT|..."
        jsonb payload
        text status "PENDING|PROCESSING|SUCCEEDED|FAILED|DEAD"
        int attempts
        int max_attempts
        timestamptz available_at "para el backoff"
        timestamptz locked_at "nullable"
        text locked_by "nullable"
        text last_error
    }
```

## Notas de lectura

- **`company_id` en toda entidad de negocio.** Incluso donde sería deducible por la relación padre
  (`ApprovalStep` podría llegar a `Company` a través de `Expense`). Es redundancia deliberada:
  permite filtrar por tenant con una sola condición y habilita Row Level Security tabla por tabla
  sin joins. Prepara el multi-tenant del ticket #33.
- **Doble identificador.** `id` (`bigint`) es la clave interna y la que usan las claves foráneas: más
  compacta en los índices y secuencial, lo que evita la fragmentación de páginas que provoca un UUID
  aleatorio como clave primaria. `public_id` (`uuid`) es el único que sale en la API y en las URLs,
  de modo que nadie puede enumerar recursos ni deducir cuántos gastos tiene una empresa.
- **`Job` aparece suelto, sin relaciones.** Es deliberado: la cola es genérica y el identificador del
  recibo viaja dentro de `payload` (`jsonb`), no como clave foránea. Así la misma tabla sirve para
  tipos de trabajo futuros sin migrar el esquema.
- **`Receipt` → `Expense` es 1:0..1.** Un recibo puede existir sin gasto (se sube, se extrae y luego
  se descarta) y un gasto puede no tener recibo si la política lo permite
  (`Policy.requires_receipt = false`).
- **`Department.manager_user_id` no se dibuja como relación** para no generar un ciclo visual con
  `DEPARTMENT ||--o{ USER`. Existe como clave foránea nullable hacia `USER`.
