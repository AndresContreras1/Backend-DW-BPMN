package com.facimus.procesos.gestion.controller.dto;

/** Lane vista por la empresa invitada: sin el id del rol, que es un dato interno de la empresa propietaria. */
public record LaneCompartidaResponse(Long id, String nombre, int orden, Long poolId, String rolProcesoNombre) {
}
