/**
 * Modulo audit: registro append-only con hash encadenado
 *
 * <p>Los tipos declarados en este paquete forman la API publica del modulo y son
 * los unicos accesibles desde otros modulos. Todo lo demas vive en
 * {@code com.rindo.audit.internal} y Spring Modulith impide su uso desde fuera.
 */
@org.springframework.modulith.ApplicationModule(displayName = "audit")
package com.rindo.audit;
