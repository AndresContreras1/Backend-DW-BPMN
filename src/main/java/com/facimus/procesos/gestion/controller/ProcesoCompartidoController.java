package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.facimus.procesos.gestion.controller.dto.CompartirProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoResponse;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-23: procesos compartidos entre empresas en modo solo lectura. */
@Tag(name = "Procesos compartidos", description = "Compartir procesos entre empresas en modo solo lectura (HU-23).")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProcesoCompartidoController {

    private final ProcesoCompartidoService procesoCompartidoService;
    private final ModelMapper modelMapper;

    @GetMapping("/procesos/{procesoId}/compartidos")
    @Operation(summary = "Listar con quien comparto un proceso", description = "Solo la empresa propietaria puede consultar esta lista.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProcesoCompartidoResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ProcesoCompartidoResponse>> listarCompartidos(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(procesoCompartidoService
                .listarCompartidosPorPropietario(principal.empresaId(), procesoId).stream()
                .map(c -> modelMapper.map(c, ProcesoCompartidoResponse.class)).toList());
    }

    @PostMapping("/procesos/{procesoId}/compartidos")
    @Operation(summary = "Compartir un proceso con otra empresa", description = "El acceso otorgado siempre es de solo lectura.")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = ProcesoCompartidoResponse.class)))
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
    public ResponseEntity<ProcesoCompartidoResponse> compartir(@PathVariable Long procesoId,
            @Validated @RequestBody CompartirProcesoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        ProcesoCompartido compartido = procesoCompartidoService.compartir(principal.empresaId(),
                principal.usuarioId(), procesoId, request.empresaInvitadaId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/procesos-compartidos/{id}").buildAndExpand(procesoId).toUri())
                .body(modelMapper.map(compartido, ProcesoCompartidoResponse.class));
    }

    @DeleteMapping("/procesos/{procesoId}/compartidos/{empresaInvitadaId}")
    @Operation(summary = "Revocar el acceso compartido", description = "Elimina el acceso de la empresa invitada a este proceso.")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> dejarDeCompartir(@PathVariable Long procesoId,
            @PathVariable Long empresaInvitadaId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        procesoCompartidoService.dejarDeCompartir(principal.empresaId(), principal.usuarioId(),
                procesoId, empresaInvitadaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/procesos-compartidos")
    @Operation(summary = "Listar los procesos que otras empresas comparten conmigo")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProcesoCompartidoResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ProcesoCompartidoResponse>> listarRecibidos(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(procesoCompartidoService.listarRecibidos(principal.empresaId()).stream()
                .map(c -> modelMapper.map(c, ProcesoCompartidoResponse.class)).toList());
    }

    @GetMapping("/procesos-compartidos/{procesoId}")
    @Operation(summary = "Ver el detalle de un proceso compartido recibido", description = "Solo lectura: la empresa invitada no puede editar el diagrama.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ProcesoCompartidoDetalleResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ProcesoCompartidoDetalleResponse> detalleCompartido(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(modelMapper.map(
                procesoCompartidoService.obtenerCompartido(principal.empresaId(), procesoId),
                ProcesoCompartidoDetalleResponse.class));
    }
}
