# ADR-001: Monolito modular con Spring Modulith

- **Fecha:** 2026-09-29
- **Estado:** Aceptada

## Contexto

El backend de Rindo tiene once áreas funcionales identificadas en el
[modelo de dominio](../modelo-de-dominio.md): `companies`, `users`, `categories`, `receipts`,
`extraction`, `expenses`, `policies`, `approvals`, `audit`, `reporting` y `jobs`.

Varias de ellas tienen características incómodas para un diseño ingenuo:

- **`extraction` es lenta y poco fiable.** Depende de una API externa (Gemini Flash) que puede tardar
  segundos, fallar o agotar su cuota. No puede bloquear el hilo que responde al usuario.
- **`audit` debe enterarse de todo** sin que los demás módulos tengan que acordarse de llamarla.
- **`reporting` solo lee**, pero sus consultas son las más pesadas del sistema.
- **`expenses` concentra la lógica de negocio crítica** (la máquina de estados) y es donde más duele
  un acoplamiento accidental.

Las restricciones del proyecto:

- **Lo desarrolla una sola persona.** No hay equipos que necesiten desplegar por separado.
- **El presupuesto de infraestructura es $0.** El despliegue es un contenedor en el plan gratuito de
  Render y una base de datos en Neon.
- **El objetivo es demostrar criterio de diseño**, no volumen de infraestructura.

El riesgo real no es la escala: es que, sin límites explícitos, un proyecto de un solo desarrollador
degenera en un monolito donde `reporting` consulta las tablas de `expenses`, `expenses` llama
directamente al cliente de Gemini y cualquier cambio arrastra medio sistema. Ese deterioro es
gradual e invisible hasta que ya es caro de revertir.

## Decisión

Construimos un **monolito modular** con **Spring Modulith**: un único artefacto desplegable dividido
en módulos con límites verificados automáticamente.

**Qué implica en concreto:**

- Cada módulo es un paquete de primer nivel bajo `com.rindo`. Los tipos públicos del paquete raíz son
  su API; todo lo demás vive en subpaquetes internos que Spring Modulith declara inaccesibles desde
  fuera.
- La comunicación entre módulos es de dos formas y solo dos: **llamada a la API pública** cuando se
  necesita una respuesta inmediata, y **evento de dominio** cuando el emisor no necesita saber quién
  reacciona ni esperar a que lo haga.
- Los límites se verifican en CI. `ApplicationModules.of(RindoApplication.class).verify()` falla la
  build ante cualquier acceso a un paquete interno de otro módulo. **No es una convención escrita:
  es un test que rompe.**
- La documentación de módulos se genera desde el código con `Documenter`, así que no puede quedar
  desactualizada respecto a la realidad.
- Los eventos se publican con `@ApplicationModuleListener`, que los ejecuta de forma asíncrona y
  transaccional, y los persiste en la tabla de eventos de Modulith para poder reintentarlos si el
  consumidor falla.

## Alternativas consideradas

### Microservicios

Un servicio por área funcional, comunicados por HTTP o mensajería.

Descartada. Con un solo desarrollador y presupuesto cero, el coste es inmediato y el beneficio
hipotético: habría que operar once despliegues, un broker de mensajes, descubrimiento de servicios,
trazas distribuidas y una base de datos por servicio — nada de lo cual cabe en el plan gratuito de
Render. Peor aún: los límites entre módulos de Rindo **todavía no están validados**. Repartirlos
entre servicios los congelaría en la frontera más cara de mover que existe, la red. Un límite
equivocado dentro de un monolito se arregla moviendo clases; entre microservicios, se arregla
reescribiendo dos servicios y su contrato.

La lógica de aprobación también lo desaconseja: aprobar un gasto actualiza el `ApprovalStep` y el
`Expense` en la misma transacción. Separados, eso exigiría un saga o un patrón outbox para un
problema que una transacción local resuelve gratis.

### Monolito clásico en capas (`controller` / `service` / `repository`)

La estructura por omisión de la mayoría de proyectos Spring Boot.

Descartada. Organiza el código por **detalle técnico** en vez de por **capacidad de negocio**: todos
los servicios juntos, todos los repositorios juntos. El resultado es que nada impide que
`ReportingService` inyecte `ExpenseRepository` y consulte tablas ajenas. Sin barrera verificable, las
reglas dependen de que el desarrollador se acuerde — y meses después, con prisa, no se acuerda.
Además, al no haber módulos, no hay eventos naturales, y `audit` acabaría siendo una llamada
explícita que alguien olvidará en algún camino.

### Monolito modular "a mano" (paquetes + ArchUnit)

La misma idea, pero con reglas de ArchUnit escritas manualmente en lugar de Spring Modulith.

Descartada, aunque es la alternativa más cercana. Da los límites verificados, pero hay que escribir y
mantener cada regla a mano, y no aporta lo demás: publicación transaccional de eventos con
reintentos, documentación generada, ni los tests de integración por módulo (`@ApplicationModuleTest`)
que arrancan solo el contexto de un módulo. Spring Modulith trae todo eso con una dependencia. Nota:
**seguimos usando ArchUnit** para reglas que Modulith no cubre (convenciones de nombres, prohibir
`java.util.Date`, forzar inyección por constructor); las dos herramientas son complementarias.

## Consecuencias

**A favor:**

- Un solo artefacto que desplegar, un solo log que leer, una sola transacción de base de datos. Cabe
  en el plan gratuito.
- Los límites entre módulos son reales porque la build falla si se violan.
- `audit` se suscribe a los eventos: es imposible olvidarse de auditar una transición.
- Refactorizar un límite equivocado es mover paquetes, no renegociar un contrato de red.
- Si algún módulo llegara a necesitar despliegue propio, ya está aislado: extraerlo es un trabajo
  acotado, no una reescritura.

**En contra:**

- **Escalado todo-o-nada.** Si `reporting` consume CPU, hay que escalar el proceso entero. Con
  GraalVM Native Image el arranque y la memoria son bajos, lo que lo hace tolerable; deja de serlo si
  el tráfico de un módulo se desacopla del resto.
- **Un fallo puede tumbarlo todo.** Una fuga de memoria en `extraction` se lleva el proceso por
  delante. Se mitiga con Resilience4j (timeouts y circuit breaker sobre Gemini) y ejecutando la
  extracción fuera del hilo de request, vía la cola de `jobs`.
- **Los eventos asíncronos complican la depuración.** Un `ExpenseSubmitted` que nadie procesa no
  produce error visible. Hay que vigilar la tabla de eventos de Modulith y exponer su backlog como
  métrica de Actuator.
- **Tentación constante de saltarse el límite.** Es más rápido inyectar el repositorio ajeno que
  añadir un método a la API pública. La verificación en CI es lo único que lo impide; desactivarla
  "temporalmente" equivale a revertir este ADR.
- **Una sola versión de cada dependencia** para todo el backend. Actualizar una librería afecta a
  todos los módulos a la vez.

## Cuándo reconsiderar esta decisión

- Un módulo necesita escalar de forma independiente por carga sostenida y medida (no supuesta).
- Entran equipos distintos que se bloquean entre sí en cada despliegue.
- Un módulo requiere un stack incompatible con el resto (otro lenguaje, otro runtime).

Mientras ninguna ocurra, extraer un servicio sería pagar el coste sin recibir el beneficio.

## Referencias

- [ADR-000: Registrar las decisiones de arquitectura como ADRs](000-usar-adrs.md)
- [Modelo de dominio: módulos y responsabilidades](../modelo-de-dominio.md)
- [Documentación de Spring Modulith](https://docs.spring.io/spring-modulith/reference/)
