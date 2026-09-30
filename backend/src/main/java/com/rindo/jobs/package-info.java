/**
 * Modulo jobs: cola de trabajos sobre PostgreSQL
 *
 * <p>Los tipos declarados en este paquete forman la API publica del modulo y son
 * los unicos accesibles desde otros modulos. Todo lo demas vive en
 * {@code com.rindo.jobs.internal} y Spring Modulith impide su uso desde fuera.
 */
@org.springframework.modulith.ApplicationModule(displayName = "jobs")
package com.rindo.jobs;
