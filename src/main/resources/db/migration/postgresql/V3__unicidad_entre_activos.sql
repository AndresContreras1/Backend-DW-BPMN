-- Unicidad del nombre dentro de la empresa solo entre registros activos: un proceso o rol dado de
-- baja (eliminacion logica) no impide reutilizar su nombre. Los indices parciales son propios de
-- PostgreSQL; el servicio ademas valida el nombre para responder 409 con un mensaje claro.
create unique index uk_proceso_nombre_empresa_activo
    on procesos (empresa_id, lower(nombre)) where activo = true;

create unique index uk_rol_proceso_nombre_empresa_activo
    on roles_proceso (empresa_id, lower(nombre)) where activo = true;
