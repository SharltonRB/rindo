## Qué

<!-- Qué cambia este PR, en una o dos frases. -->

## Por qué

<!-- Motivación y contexto. Enlaza el ticket si aplica. -->

## Cómo

<!-- Decisiones de implementación que merece la pena destacar. -->

## Capturas

<!-- Solo si hay cambios de UI: antes / después. Si no aplica, borra esta sección. -->

---

## Checklist

### Pruebas

- [ ] Tests unitarios añadidos o actualizados
- [ ] Tests de integración añadidos o actualizados (si toca base de datos o API externa)
- [ ] La suite pasa en local
- [ ] Cobertura por encima del umbral del proyecto

### Documentación

- [ ] README o documentación afectada está actualizada
- [ ] Si hay una decisión de arquitectura, está registrada como ADR en `docs/adr/`
- [ ] Si cambia la API pública, la especificación OpenAPI queda actualizada

### Seguridad

- [ ] No hay secretos, credenciales ni tokens en el diff
- [ ] Las entradas de usuario nuevas están validadas
- [ ] No se exponen datos sensibles en logs ni en mensajes de error

### General

- [ ] El título del PR sigue Conventional Commits
- [ ] Autorrevisión del diff completada
- [ ] Sin código muerto, `console.log` ni `System.out.println` de depuración
- [ ] CI en verde

<!-- Closes #  -->
