package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDateTime;

import com.facimus.procesos.gestion.model.RolAcceso;

/**
 * El token se entrega al administrador porque el proyecto aun no integra un proveedor SMTP.
 * Debe tratarse como credencial temporal y compartirse solo con la persona invitada.
 */
public record InvitacionUsuarioResponse(Long id, String email, RolAcceso rolAcceso,
        String token, LocalDateTime fechaExpiracion) {
}
