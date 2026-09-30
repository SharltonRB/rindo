package com.rindo;

import java.util.UUID;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Declara lo que la imagen nativa necesita usar por reflexion.
 *
 * <p>GraalVM compila bajo la <em>closed-world assumption</em>: solo mete en el binario lo
 * que puede demostrar alcanzable en tiempo de compilacion. Todo lo que se resuelva por
 * nombre en ejecucion hay que declararlo aqui, o el binario compila, arranca y falla.
 *
 * <p>Se registra en Java y no en {@code reachability-metadata.json} porque Spring AOT
 * procesa estos hints y genera el JSON por nosotros: asi el registro se compila, se puede
 * refactorizar y no depende de acertar con el esquema ni con la ruta del fichero.
 */
class NativeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		// Hibernate instancia arrays del tipo de la clave primaria al construir el
		// EntityManagerFactory. La entidad de publicacion de eventos de Spring Modulith
		// usa UUID como id, asi que sin esto el arranque muere con:
		//   Cannot reflectively instantiate the array class 'java.util.UUID[]'
		hints.reflection().registerType(UUID[].class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
		hints.reflection().registerType(UUID.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
	}
}
