# Pilot UAT Checklist

## Datos y acceso

- [ ] Dominio y HTTPS validos.
- [ ] Restaurante, zona horaria, salones y 40 mesas revisados.
- [ ] Inventario y 20 combinaciones revisados por manager.
- [ ] Owner, manager y staff acceden con cuentas no compartidas.
- [ ] Credenciales demo y registro publico no estan disponibles.

## Flujo manager

- [ ] Crear una reserva y confirmar que la hora no cambia.
- [ ] Abrir top 3 sin crear ni modificar una asignacion.
- [ ] Comparar capacidad, score, coste, preparacion y recursos.
- [ ] Aplicar una combinacion avanzada y verla en planning.
- [ ] Confirmar actor, instante, explicacion y recursos en historial.
- [ ] Reasignar y comprobar que el consumo anterior queda liberado.
- [ ] Simular dos selecciones simultaneas sobre inventario limitado.

## Flujo staff

- [ ] Ver planning, mesa, preparacion y recursos asignados.
- [ ] Cambiar llegada, sentado y completado segun permisos.
- [ ] Confirmar que no aparece la accion de aprobar sugerencias.

## Resiliencia

- [ ] Ejecutar 150 reservas/40 mesas/20 combinaciones.
- [ ] Planning responde por debajo de 2 s en VPS.
- [ ] Sugerencias responden por debajo de 1 s en VPS.
- [ ] Backup diario creado y copiado fuera del VPS.
- [ ] Restauracion realizada y documentada.
- [ ] Rollback ensayado con la version anterior.
- [ ] CI completamente verde y cero incidencias criticas abiertas.

Firmas: owner, manager, responsable tecnico y fecha de aprobacion.
