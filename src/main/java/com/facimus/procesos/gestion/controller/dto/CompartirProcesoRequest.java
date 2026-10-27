package com.facimus.procesos.gestion.controller.dto;

import jakarta.validation.constraints.NotNull;

public record CompartirProcesoRequest(
        @NotNull(message = "La empresa invitada es obligatoria.") Long empresaInvitadaId) {
}
