# ADR-004: Usar Garage como almacenamiento de objetos en el entorno local

- **Fecha:** 2026-10-03
- **Estado:** Aceptada

## Contexto

El stack de Rindo preveía **MinIO** como almacenamiento compatible con S3 en el entorno de
desarrollo, con **Supabase Storage** como equivalente en producción. La idea es que el backend hable
el protocolo S3 y no sepa cuál de los dos tiene delante.

Al construir el entorno local del ticket #05, MinIO resultó no ser instalable:

- El repositorio de MinIO se **archivó y pasó a solo lectura el 25 de abril de 2026**.
- Las imágenes `minio/minio` y `minio/mc` se **borraron de Docker Hub en septiembre de 2026**.
  Un `docker pull` responde `pull access denied ... repository does not exist`.
- El espejo de `quay.io` **dejó de permitir descargas anónimas el 24 de septiembre de 2026**.

Se comprobó empíricamente antes de decidir: fallan `minio/minio`, `minio/mc`,
`quay.io/minio/minio` y `ghcr.io/minio/minio`, mientras que `postgres:16-alpine` y otras imágenes se
resuelven con normalidad desde la misma máquina. No es un problema de red local.

Esto convierte a MinIO en un bloqueo real, no en una preferencia: un `docker compose up` que depende
de una imagen inexistente no es reproducible para nadie que clone el repositorio.

La restricción que importa es estrecha: **solo afecta al entorno local.** Producción usa Supabase
Storage, que no cambia. Lo que se necesita aquí es un servidor que hable S3 lo bastante bien como
para que el código que funcione contra él funcione también contra Supabase.

## Decisión

Usamos **Garage** (`dxflrs/garage:v2.4.1`) como servidor de objetos compatible con S3 en el entorno
local, en el servicio `storage` de `infra/docker/compose.local.yml`.

Se arranca con `--single-node --default-bucket`, lo que hace que el contenedor se auto-configure al
primer arranque: asigna el layout del clúster, crea el bucket y crea la clave de acceso a partir de
las variables de entorno. Sin esos indicadores, Garage exige cinco comandos manuales
(`layout assign`, `layout apply`, `bucket create`, `key create`, `bucket allow`) antes de aceptar un
solo objeto, lo que rompería el objetivo de levantar el entorno con un solo comando.

**Producción no cambia:** sigue siendo Supabase Storage. Garage no se despliega en ningún entorno
remoto.

### Alternativas consideradas

**`bitnamilegacy/minio`.**
Bitnami conserva pública una copia congelada de la última imagen de MinIO. Es el cambio de una sola
línea y conserva la consola web. Descartada: es un archivo muerto, sin parches de seguridad, y
Bitnami ya anunció la retirada de su catálogo *legacy*. Adoptarla sería cambiar un bloqueo de hoy
por el mismo bloqueo dentro de unos meses, con la diferencia de que entonces habría código
escrito contra ella.

**RustFS.**
Reimplementación de MinIO en Rust, con API S3 y consola web, mantenida activamente. Es la más
parecida a MinIO en uso diario. Descartada por madurez: es un proyecto joven y con poco rodaje en
producción, y aquí la propiedad que más importa es que el servidor siga existiendo dentro de un año.

**LocalStack.**
Emula S3 junto al resto de servicios de AWS. Útil si más adelante hiciera falta simular SES o SQS.
Descartada por [YAGNI](https://martinfowler.com/bliki/Yagni.html): hoy la única necesidad es guardar
recibos, y LocalStack pesa bastante más que un servidor de objetos para cubrir exactamente lo mismo.

**Usar Supabase Storage también en local.**
Eliminaría el emulador y garantizaría paridad exacta con producción. Descartada: obliga a tener
conexión y una cuenta para ejecutar los tests, convierte el entorno de desarrollo en dependiente de
un servicio externo y hace que dos desarrolladores compartan el mismo almacenamiento. El entorno
local debe poder levantarse sin red.

**Guardar los archivos en PostgreSQL.**
Sería coherente con el ADR-002 (*todo en PostgreSQL*). Descartada: ese ADR habla de datos
estructurados y de la cola de trabajos, no de binarios. Meter imágenes de recibos en la base de
datos infla los backups, compite por la caché de páginas y obliga a reescribir la capa de
almacenamiento el día que se despliegue contra Supabase Storage.

## Consecuencias

**A favor:**

- `docker compose up -d` vuelve a ser reproducible, que era el objetivo del ticket.
- Garage es notablemente más ligero que MinIO: un nodo de desarrollo consume del orden de 100 MB de
  memoria, frente a varios cientos.
- El contrato que ve el backend sigue siendo S3. La migración a Supabase Storage se reduce a cambiar
  endpoint y credenciales.
- El proyecto está mantenido por Deuxfleurs, con un modelo de gobernanza que no depende de una
  empresa que pueda cerrar el grifo. Esta es, precisamente, la lección de MinIO.

**En contra:**

- **No hay consola web.** MinIO traía una interfaz para ver los archivos subidos; Garage no. Los
  objetos se inspeccionan con `aws s3 ls` u otro cliente S3. Es la pérdida más tangible.
- **La paridad con S3 no es total.** Garage implementa el subconjunto de la API que cubre el uso
  normal, pero no las funciones más exóticas. Si algún día hiciera falta una que no soporta, el
  problema aparecería en local y no en producción, que es el orden preferible.
- **`--single-node` es específico de Garage 2.3 o superior.** Fijamos `v2.4.1`; bajar de versión
  rompe el arranque automático.
- **Una tecnología más que conocer.** Garage es menos conocido que MinIO y su documentación es más
  escueta.

### Cuándo reconsiderar esta decisión

Esta decisión deja de sostenerse si aparece alguna de estas señales:

- El backend necesita una función de la API de S3 que Garage no implementa.
- Supabase Storage deja de ser el destino de producción y el almacenamiento pasa a autogestionarse,
  en cuyo caso habría que evaluar Garage para un despliegue real y no solo para desarrollo.
- Aparece un sustituto de MinIO claramente asentado que aporte algo que hoy se echa de menos, como
  una consola web.
- La falta de interfaz gráfica resulta ser un estorbo real en el día a día y no una incomodidad
  puntual.

En ese caso se escribe un ADR nuevo que reemplace a este. Este archivo no se edita.

## Referencias

- [ADR-000: Registrar las decisiones de arquitectura como ADRs](000-usar-adrs.md)
- [ADR-002: Todo en PostgreSQL](002-todo-en-postgresql.md)
- [Garage, documentación oficial](https://garagehq.deuxfleurs.fr/documentation/quick-start/)
- [MinIO images disappeared from Docker Hub](https://www.stablebuild.com/blog/minio-images-disappeared-from-docker-hub) — cronología de la retirada
