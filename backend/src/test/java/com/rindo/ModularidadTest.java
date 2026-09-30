package com.rindo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica que la estructura de modulos del backend es valida.
 *
 * <p>Este test es analisis estatico de bytecode: no arranca el contexto de Spring ni
 * necesita base de datos, asi que corre en milisegundos y sin Docker. Si alguien accede
 * al paquete {@code internal} de otro modulo o introduce una dependencia ciclica, la
 * build falla aqui.
 */
class ModularidadTest {

	private final ApplicationModules modulos = ApplicationModules.of(RindoApplication.class);

	@Test
	@DisplayName("los modulos respetan sus limites y no tienen ciclos")
	void losModulosRespetanSusLimites() {
		modulos.verify();
	}

	@Test
	@DisplayName("genera la documentacion de modulos en target/spring-modulith-docs")
	void generaLaDocumentacionDeModulos() {
		new Documenter(modulos).writeDocumentation();
	}
}
