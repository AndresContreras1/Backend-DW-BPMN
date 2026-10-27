package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDate;

public record EmpresaResponse(Long id, String nombre, String nit, String correoContacto, LocalDate fechaRegistro) {
}
