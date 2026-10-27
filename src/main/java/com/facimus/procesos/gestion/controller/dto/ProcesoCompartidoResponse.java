package com.facimus.procesos.gestion.controller.dto;

public record ProcesoCompartidoResponse(Long id, Long procesoId, String procesoNombre,
        Long empresaPropietariaId, String empresaPropietaria,
        Long empresaInvitadaId, String empresaInvitada, boolean soloLectura) {
}
