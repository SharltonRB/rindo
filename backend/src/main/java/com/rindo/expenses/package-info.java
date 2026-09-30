/**
 * Modulo expenses: ciclo de vida del gasto y su maquina de estados
 *
 * <p>Los tipos declarados en este paquete forman la API publica del modulo y son
 * los unicos accesibles desde otros modulos. Todo lo demas vive en
 * {@code com.rindo.expenses.internal} y Spring Modulith impide su uso desde fuera.
 */
@org.springframework.modulith.ApplicationModule(displayName = "expenses")
package com.rindo.expenses;
