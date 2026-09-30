/**
 * Modulo companies: empresas y departamentos, raiz del multi-tenant
 *
 * <p>Los tipos declarados en este paquete forman la API publica del modulo y son
 * los unicos accesibles desde otros modulos. Todo lo demas vive en
 * {@code com.rindo.companies.internal} y Spring Modulith impide su uso desde fuera.
 */
@org.springframework.modulith.ApplicationModule(displayName = "companies")
package com.rindo.companies;
