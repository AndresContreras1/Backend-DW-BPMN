package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.modelmapper.ModelMapper;

import com.facimus.procesos.modelado.controller.dto.EditarMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeRequest;
import com.facimus.procesos.modelado.controller.dto.MensajeResponse;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

@Tag(name = "Mensajes", description = "Comunicacion entre pools de un mismo proceso (HU-25 a HU-27).")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MensajeController {

    private final MensajeService mensajeService;
    private final ModelMapper modelMapper;

    @GetMapping("/procesos/{procesoId}/mensajes")
    @Operation(summary = "Listar los mensajes de un proceso")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MensajeResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<MensajeResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(mensajeService.listarPorProceso(principal.empresaId(), procesoId).stream()
                .map(m -> modelMapper.map(m, MensajeResponse.class)).toList());
    }

    @GetMapping("/mensajes/{id}")
    @Operation(summary = "Ver el detalle de un mensaje")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = MensajeResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<MensajeResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(modelMapper.map(mensajeService.obtener(principal.empresaId(), id), MensajeResponse.class));
    }

    @PostMapping("/procesos/{procesoId}/mensajes")
    @Operation(summary = "Crear un mensaje entre dos pools", description = "Debe estar asociado a un evento Message Throw.")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = MensajeResponse.class)))
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
    public ResponseEntity<MensajeResponse> crear(@PathVariable Long procesoId,
            @Validated @RequestBody MensajeRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Mensaje mensaje = request.eventoThrowId() == null
                ? mensajeService.crear(principal.empresaId(), procesoId, request.nombre(), request.contenido(),
                        request.poolOrigenId(), request.poolDestinoId())
                : mensajeService.crear(principal.empresaId(), procesoId, request.nombre(), request.contenido(),
                        request.poolOrigenId(), request.poolDestinoId(), request.eventoThrowId(), request.eventoCatchId(),
                        request.claveCorrelacion(), request.tipoDestinoExterno(), request.destinoExterno(),
                        request.politicaFalloNotificacion(), request.actividadErrorId(), request.politicaSinCaso());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/mensajes/{id}").buildAndExpand(mensaje.getId()).toUri())
                .body(modelMapper.map(mensaje, MensajeResponse.class));
    }

    @PutMapping("/mensajes/{id}")
    @Operation(summary = "Editar un mensaje")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = MensajeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<MensajeResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarMensajeRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        boolean edicionLegacy = request.eventoCatchId() == null && request.claveCorrelacion() == null
                && request.tipoDestinoExterno() == null && request.destinoExterno() == null
                && request.politicaFalloNotificacion() == null && request.actividadErrorId() == null
                && request.politicaSinCaso() == null;
        Mensaje mensaje = edicionLegacy
                ? mensajeService.editar(principal.empresaId(), id, request.nombre(), request.contenido())
                : mensajeService.editar(principal.empresaId(), id, request.nombre(), request.contenido(),
                        request.eventoCatchId(), request.claveCorrelacion(), request.tipoDestinoExterno(),
                        request.destinoExterno(), request.politicaFalloNotificacion(),
                        request.actividadErrorId(), request.politicaSinCaso());
        return ResponseEntity.ok(modelMapper.map(mensaje, MensajeResponse.class));
    }

    @DeleteMapping("/mensajes/{id}")
    @Operation(summary = "Eliminar un mensaje", description = "Elimina tambien su clave de correlacion si tenia una definida.")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        mensajeService.eliminar(principal.empresaId(), id);
        return ResponseEntity.noContent().build();
    }
}
