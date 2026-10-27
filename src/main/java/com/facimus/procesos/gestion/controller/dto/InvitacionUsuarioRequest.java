package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.RolAcceso;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record InvitacionUsuarioRequest(
        @Email(message = "El correo no es valido.") String email,
        @NotNull(message = "El rol es obligatorio.") RolAcceso rolAcceso) {
}
