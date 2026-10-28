package com.facimus.procesos.modelado.controller;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.modelado.controller.dto.ValidacionModeloResponse;
import com.facimus.procesos.modelado.service.ValidacionModeloService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@Tag(name = "Validacion del modelo", description = "Estado de coherencia del diagrama antes de publicar el proceso.")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ValidacionModeloController {

    private final ValidacionModeloService validacionModeloService;

    @GetMapping("/procesos/{procesoId}/validacion")
    @Operation(summary = "Validar el modelo de un proceso",
            description = "Devuelve todas las advertencias que impedirian publicar el proceso, sin publicar ni modificar nada.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ValidacionModeloResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ValidacionModeloResponse> validar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(validacionModeloService.consultarValidacion(principal.empresaId(), procesoId));
    }
}
