-- Tabla de publicacion de eventos de Spring Modulith.
--
-- Modulith persiste aqui cada evento de dominio publicado con @ApplicationModuleListener
-- antes de entregarlo. Si el consumidor falla, la fila queda sin completion_date y el
-- evento puede reintentarse: es lo que hace que los eventos asincronos no se pierdan.
--
-- El DDL se genero con Hibernate a partir de las entidades de spring-modulith-events-jpa
-- 2.1.1, no a mano, para que coincida exactamente con lo que valida `ddl-auto: validate`.

CREATE TABLE event_publication (
    id                     UUID         NOT NULL,
    listener_id            VARCHAR(255) NOT NULL,
    event_type             VARCHAR(255) NOT NULL,
    serialized_event       TEXT         NOT NULL,
    publication_date       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP(6) WITH TIME ZONE,
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    completion_attempts    INTEGER      NOT NULL,
    status                 VARCHAR(255) CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED')),
    PRIMARY KEY (id)
);

-- Indice parcial sobre lo unico que Modulith consulta en caliente: los eventos que aun no
-- se han completado. Mantiene barata la busqueda de pendientes aunque la tabla acumule
-- historico de eventos ya procesados.
CREATE INDEX idx_event_publication_incompletos
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;
