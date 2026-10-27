package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.RolAcceso;

public record UsuarioResponse(Long id, String nombre, String email, RolAcceso rolAcceso, boolean activo) {
}
