-- HU-27: actividades que utilizan los datos de un evento Message Catch (relacion N:M entre nodos).
create table evento_mensaje_actividades (
    evento_id bigint not null,
    actividad_id bigint not null,
    primary key (evento_id, actividad_id)
);

alter table evento_mensaje_actividades
    add constraint fk_evento_mensaje_actividades_evento
    foreign key (evento_id) references nodos_flujo;

alter table evento_mensaje_actividades
    add constraint fk_evento_mensaje_actividades_actividad
    foreign key (actividad_id) references nodos_flujo;

create index idx_evento_mensaje_actividades_actividad on evento_mensaje_actividades (actividad_id);
