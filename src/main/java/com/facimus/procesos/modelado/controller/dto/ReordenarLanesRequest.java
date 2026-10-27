package com.facimus.procesos.modelado.controller.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ReordenarLanesRequest(
        @NotEmpty(message = "La lista de IDs no puede estar vacía.") List<Long> laneIds) {
}
