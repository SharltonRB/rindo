/**
 * Modulo users: usuarios, roles, autenticacion y emision de JWT
 *
 * <p>Los tipos declarados en este paquete forman la API publica del modulo y son
 * los unicos accesibles desde otros modulos. Todo lo demas vive en
 * {@code com.rindo.users.internal} y Spring Modulith impide su uso desde fuera.
 */
@org.springframework.modulith.ApplicationModule(displayName = "users")
package com.rindo.users;
