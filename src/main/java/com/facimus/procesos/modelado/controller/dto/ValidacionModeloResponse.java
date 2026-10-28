package com.facimus.procesos.modelado.controller.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de validar el modelo de un proceso antes de publicarlo.")
public record ValidacionModeloResponse(
        @Schema(description = "true si no hay advertencias y el proceso se puede publicar.") boolean valido,
        @Schema(description = "Textos de todo lo que impide publicar el proceso.") List<String> advertencias) {
}
