# Backend de Rindo

API de Rindo: monolito modular en Java 21 + Spring Boot 4.1, preparado desde el inicio para
compilarse a binario nativo con GraalVM.

Las decisiones que explican esta estructura están en
[ADR-001](../docs/adr/001-monolito-modular.md) y [ADR-002](../docs/adr/002-todo-en-postgresql.md);
el diseño del dominio, en [modelo-de-dominio.md](../docs/modelo-de-dominio.md).

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK 21 | Temurin 21 o superior | Compilar y ejecutar |
| Docker | Cualquiera reciente | Testcontainers levanta PostgreSQL en los tests |
| GraalVM CE 25 | `sdk install java 25.3.4+1.r25-graalce` | Solo para la compilación nativa |

> **Sobre la versión de GraalVM:** el `native-maven-plugin` que trae Spring Boot 4.1 usa el formato
> nuevo de *reachability metadata*. GraalVM 21 no lo entiende y la compilación falla antes de
> empezar. Se necesita GraalVM 25, que compila igual con *target* Java 21.

## Comandos

```bash
./mvnw verify                        # Compila y ejecuta los tests (necesita Docker)
./mvnw spring-boot:test-run          # Arranca la app con PostgreSQL en Testcontainers
./mvnw test -Dtest=ModularidadTest   # Solo la verificación de módulos (no necesita Docker)
```

Para desarrollar contra el entorno local de `infra/docker/` en lugar de Testcontainers:

```bash
set -a; source ../infra/docker/.env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

El paso a paso completo —incluido cómo levantar PostgreSQL y el almacenamiento— está en
[«Cómo correr en local»](../README.md#cómo-correr-en-local).

## Perfiles

| Perfil | Archivo | Cuándo se usa | De dónde salen las credenciales |
|---|---|---|---|
| `local` | `application-local.yml` | Desarrollo contra `infra/docker/` | `infra/docker/.env`, cargado en la shell |
| `test` | `application-test.yml` | `./mvnw verify` y `spring-boot:test-run` | Las inyecta Testcontainers; no se declaran |
| `prod` | `application-prod.yml` | Render | Variables de entorno del proveedor |

**Ningún perfil está activo por defecto.** Sin `--spring.profiles.active`, el `application.yml` base
no define `spring.datasource` y la aplicación no arranca. Es deliberado: el modo por defecto debería
fallar, no adivinar contra qué base de datos conectarse.

En `local` y `prod` las credenciales se declaran **sin valor por defecto**, de modo que una variable
ausente aborta el arranque con `Could not resolve placeholder` en lugar de conectarse a otro sitio.
`application-test.yml` es el caso contrario y no declara `spring.datasource` en absoluto: esos
valores solo se conocen en caliente, porque Testcontainers asigna un puerto distinto en cada
ejecución, y fijarlos aquí haría que los tests corrieran contra la base de datos equivocada.

Compilación nativa (requiere GraalVM como `JAVA_HOME`):

```bash
JAVA_HOME=~/.sdkman/candidates/java/25.3.4+1.r25-graalce \
  ./mvnw -Pnative native:compile -DskipTests
./target/rindo
```

## Estructura de módulos

Cada directorio bajo `com.rindo` es un módulo de Spring Modulith:

```
com.rindo
├── RindoApplication.java
├── companies/    empresas y departamentos, raíz del multi-tenant
├── users/        usuarios, roles, autenticación y emisión de JWT
├── categories/   árbol de categorías de gasto
├── receipts/     subida y almacenamiento de archivos en S3
├── extraction/   extracción de datos del recibo con Spring AI y Gemini Flash
├── expenses/     ciclo de vida del gasto y su máquina de estados
├── policies/     reglas de admisibilidad y niveles de aprobación
├── approvals/    enrutado de aprobaciones: a quién le toca decidir
├── audit/        registro append-only con hash encadenado
├── reporting/    consultas agregadas y métricas, solo lectura
└── jobs/         cola de trabajos sobre PostgreSQL
```

**La regla:** los tipos del paquete raíz de un módulo son su API pública. Todo lo demás vive en
`<modulo>/internal/` y **no es accesible desde otros módulos** — `ModularidadTest` falla la build
si alguien lo intenta. Para exponer algo, se publica un tipo en el paquete raíz del módulo.

La comunicación entre módulos es por llamada a la API pública (síncrona) o por eventos de dominio
(asíncrona). Los eventos se persisten en la tabla `event_publication` antes de entregarse, de modo
que un consumidor que falle pueda reintentarse.

## Decisiones de configuración

| Ajuste | Valor | Por qué |
|---|---|---|
| `spring.threads.virtual.enabled` | `true` | Las llamadas bloqueantes (BD, S3, Gemini) dejan de retener hilos del SO |
| `spring.jpa.open-in-view` | `false` | Evita consultas N+1 invisibles y retención de conexiones del pool |
| `spring.jpa.hibernate.ddl-auto` | `validate` | El esquema lo gobierna Flyway; Hibernate solo comprueba que coincide |
| `spring.flyway.baseline-on-migrate` | sin definir | Una base de datos con tablas pero sin historial debe fallar, no continuar a ciegas |
| Imagen de Testcontainers | `postgres:16-alpine` | Fijada: `latest` rompería la build sin que cambie el repositorio |

## Migraciones

El esquema lo gobierna Flyway, en `src/main/resources/db/migration/`. Hibernate solo valida que
las entidades coinciden con lo que hay (`ddl-auto: validate`); nunca crea ni altera tablas.

`V1` crea `event_publication`, la tabla que Spring Modulith usa para persistir los eventos antes
de entregarlos. Su DDL se generó con Hibernate a partir de las entidades de la librería, no a mano,
para que la validación no falle por una diferencia de tipo.

## Notas sobre GraalVM

La compilación nativa usa la *closed-world assumption*: todo lo que se ejecutará debe conocerse en
tiempo de compilación. La reflexión dinámica, los proxies creados en tiempo de ejecución y la carga
de clases por nombre fallan si no se declaran antes. Spring AOT genera esos metadatos
automáticamente para lo que conoce, pero **una librería de terceros sin soporte de GraalVM compila
y luego falla en tiempo de ejecución**, que es el peor momento para enterarse.

Por eso, antes de añadir una dependencia conviene comprobar que la soporta. La alternativa es
declarar sus metadatos a mano en `src/main/resources/META-INF/native-image/`.

### Un caso real: por qué faltan tres dependencias de Modulith

El `pom.xml` **no** incluye `spring-modulith-runtime`, `spring-modulith-actuator` ni
`spring-modulith-observability`, aunque el Initializr las genera por defecto.

Con ellas, el binario nativo compilaba, arrancaba y moría al inicializar el contexto:

```
Error creating bean with name 'meterRegistryPostProcessor': ExceptionInInitializerError
Caused by: ClassNotFoundException: com.tngtech.archunit.core.importer.ModuleImportPlugin
```

Esas tres librerías analizan la estructura de módulos **en tiempo de ejecución** usando ArchUnit,
que carga un plugin distinto por reflexión según la versión de Java que detecta en caliente. Es
exactamente lo que la *closed-world assumption* no permite.

- **Qué se pierde:** el endpoint `/actuator/modulith` y las trazas por módulo. Ambos informativos.
- **Qué no se pierde:** la verificación de modularidad. `ModularidadTest` usa
  `spring-modulith-starter-test`, en scope `test`, que nunca entra en el binario.

Es el ejemplo de por qué el ticket insiste en revisar la compatibilidad **antes** de añadir una
librería: el fallo no aparece al compilar, sino al ejecutar.
