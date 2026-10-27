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

import com.facimus.procesos.modelado.controller.dto.EventoMensajeRequest;
import com.facimus.procesos.modelado.controller.dto.EventoMensajeResponse;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.service.EventoMensajeService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

@Tag(name = "Eventos de mensaje", description = "Message Throw y Message Catch (HU-25 a HU-27).")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventoMensajeController {

    private final EventoMensajeService eventoMensajeService;
    private final ModelMapper modelMapper;

    @PostMapping("/lanes/{laneId}/eventos-mensaje")
    @Operation(summary = "Crear un evento de mensaje en una lane", description = "Puede ser Throw (envio) o Catch (recepcion, de inicio o intermedio).")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = EventoMensajeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<EventoMensajeResponse> crear(@PathVariable Long laneId,
            @Validated @RequestBody EventoMensajeRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        EventoMensaje evento = eventoMensajeService.crear(principal.empresaId(), laneId, request.nombre(),
                request.tipoEvento(), request.contenido(), request.claveCorrelacion(),
                request.posicionX(), request.posicionY(), request.origenExterno());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/eventos-mensaje/{id}").buildAndExpand(evento.getId()).toUri())
                .body(modelMapper.map(evento, EventoMensajeResponse.class));
    }

    @GetMapping("/eventos-mensaje/{id}")
    @Operation(summary = "Ver el detalle de un evento de mensaje")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = EventoMensajeResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<EventoMensajeResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(modelMapper.map(eventoMensajeService.obtener(principal.empresaId(), id), EventoMensajeResponse.class));
    }

    @GetMapping("/lanes/{laneId}/eventos-mensaje")
    @Operation(summary = "Listar los eventos de mensaje de una lane")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = EventoMensajeResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<EventoMensajeResponse>> listar(@PathVariable Long laneId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(eventoMensajeService.listarPorLane(principal.empresaId(), laneId).stream()
                .map(e -> modelMapper.map(e, EventoMensajeResponse.class)).toList());
    }

    @PutMapping("/eventos-mensaje/{id}")
    @Operation(summary = "Editar un evento de mensaje")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = EventoMensajeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<EventoMensajeResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EventoMensajeRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        EventoMensaje evento = eventoMensajeService.editar(principal.empresaId(), id, request.nombre(),
                request.tipoEvento(), request.contenido(), request.claveCorrelacion(),
                request.posicionX(), request.posicionY(), request.origenExterno());
        return ResponseEntity.ok(modelMapper.map(evento, EventoMensajeResponse.class));
    }

    @DeleteMapping("/eventos-mensaje/{id}")
    @Operation(summary = "Eliminar un evento de mensaje", description = "Desactiva tambien los arcos conectados.")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        eventoMensajeService.eliminar(principal.empresaId(), id);
        return ResponseEntity.noContent().build();
    }
}
