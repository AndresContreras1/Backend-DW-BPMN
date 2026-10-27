package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.modelmapper.ModelMapper;

import com.facimus.procesos.modelado.controller.dto.CorrelacionRequest;
import com.facimus.procesos.modelado.controller.dto.CorrelacionResponse;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.service.CorrelacionOrquestadorService;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-28: correlacion de mensajes. */
@Tag(name = "Correlacion de mensajes", description = "Clave de correlacion entre instancias del proceso (HU-28).")
@RestController
@RequestMapping("/api/v1/mensajes/{mensajeId}/correlacion")
@RequiredArgsConstructor
public class CorrelacionController {

    private final CorrelacionService correlacionService;
    private final CorrelacionOrquestadorService correlacionOrquestadorService;
    private final ModelMapper modelMapper;

    @PutMapping
    @Operation(summary = "Definir la clave de correlacion de un mensaje", description = "Operacion de upsert; valida que no sea ambigua con otros mensajes del proceso.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = CorrelacionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conflicto: la operacion viola una regla de negocio.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<CorrelacionResponse> definir(@PathVariable Long mensajeId,
            @Validated @RequestBody CorrelacionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Correlacion correlacion = correlacionOrquestadorService.definir(empresaId, mensajeId, request.criterio());
        return ResponseEntity.ok(modelMapper.map(correlacion, CorrelacionResponse.class));
    }

    @GetMapping
    @Operation(summary = "Consultar la clave de correlacion de un mensaje")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = CorrelacionResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<CorrelacionResponse> obtener(@PathVariable Long mensajeId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Correlacion correlacion = correlacionService.obtener(empresaId, mensajeId);
        return ResponseEntity.ok(modelMapper.map(correlacion, CorrelacionResponse.class));
    }

    @DeleteMapping
    @Operation(summary = "Eliminar la clave de correlacion de un mensaje")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long mensajeId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        correlacionOrquestadorService.eliminar(empresaId, mensajeId);
        return ResponseEntity.noContent().build();
    }
}
