package com.rindo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/**
	 * Version fijada a proposito: {@code postgres:latest} haria que la build dejara de ser
	 * reproducible y que una version nueva de PostgreSQL pudiera romper los tests sin que
	 * nada haya cambiado en el repositorio.
	 */
	private static final DockerImageName IMAGEN_POSTGRES = DockerImageName.parse("postgres:16-alpine");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(IMAGEN_POSTGRES);
	}

}
