package com.facimus.procesos.gestion.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AceptarInvitacionRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres.") String password) {
}
