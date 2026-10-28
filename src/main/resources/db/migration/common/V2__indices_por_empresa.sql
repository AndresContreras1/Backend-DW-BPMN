-- Indices por empresa: toda consulta del sistema filtra por empresa_id.
create index idx_arcos_empresa on arcos (empresa_id);
create index idx_correlaciones_empresa on correlaciones (empresa_id);
create index idx_historial_cambios_empresa on historial_cambios (empresa_id);
create index idx_invitaciones_usuario_empresa on invitaciones_usuario (empresa_id);
create index idx_lanes_empresa on lanes (empresa_id);
create index idx_mensajes_empresa on mensajes (empresa_id);
create index idx_nodos_flujo_empresa on nodos_flujo (empresa_id);
create index idx_permisos_estructura_empresa on permisos_estructura (empresa_id);
create index idx_pools_empresa on pools (empresa_id);
create index idx_procesos_empresa on procesos (empresa_id);
create index idx_procesos_compartidos_empresa on procesos_compartidos (empresa_id);
create index idx_roles_proceso_empresa on roles_proceso (empresa_id);
create index idx_usuarios_empresa on usuarios (empresa_id);
