package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.modelmapper.ModelMapper;
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

import com.facimus.procesos.modelado.controller.dto.ActividadRequest;
import com.facimus.procesos.modelado.controller.dto.ActividadResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-08 a HU-10: actividades (tareas del proceso). */
@Tag(name = "Actividades", description = "Tareas concretas del proceso (HU-08 a HU-10).")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ActividadController {

    private final ActividadService actividadService;
    private final ModelMapper modelMapper;

    @PostMapping("/lanes/{laneId}/actividades")
    @Operation(summary = "Crear una actividad en una lane")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = ActividadResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ActividadResponse> crear(@PathVariable Long laneId,
            @Validated @RequestBody ActividadRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Actividad actividad = request.tipoActividad() == null
                ? actividadService.crear(empresaId, laneId, request.nombre(), request.descripcion(),
                        request.posicionX(), request.posicionY())
                : actividadService.crear(empresaId, laneId, request.nombre(), request.descripcion(),
                        request.posicionX(), request.posicionY(), request.tipoActividad());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/actividades/{id}").buildAndExpand(actividad.getId()).toUri())
                .body(modelMapper.map(actividad, ActividadResponse.class));
    }

    @GetMapping("/actividades/{id}")
    @Operation(summary = "Ver el detalle de una actividad")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ActividadResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ActividadResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(modelMapper.map(actividadService.obtener(principal.empresaId(), id),
                ActividadResponse.class));
    }

    @GetMapping("/lanes/{laneId}/actividades")
    @Operation(summary = "Listar las actividades de una lane")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ActividadResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ActividadResponse>> listar(@PathVariable Long laneId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(actividadService.listarPorLane(principal.empresaId(), laneId).stream()
                .map(a -> modelMapper.map(a, ActividadResponse.class)).toList());
    }

    @PutMapping("/actividades/{id}")
    @Operation(summary = "Editar una actividad", description = "Permite moverla a otra lane, incluso de otro pool si no tiene arcos conectados.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ActividadResponse.class)))
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
    public ResponseEntity<ActividadResponse> editar(@PathVariable Long id,
            @Validated @RequestBody ActividadRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Actividad actividad = request.tipoActividad() == null
                ? actividadService.editar(empresaId, id, request.nombre(), request.descripcion(),
                        request.posicionX(), request.posicionY(), request.laneId())
                : actividadService.editar(empresaId, id, request.nombre(), request.descripcion(),
                        request.posicionX(), request.posicionY(), request.laneId(), request.tipoActividad());
        return ResponseEntity.ok(modelMapper.map(actividad, ActividadResponse.class));
    }

    @GetMapping("/actividades/{id}/impacto-eliminacion")
    @Operation(summary = "Evaluar el impacto de eliminar una actividad", description = "Devuelve advertencias sobre arcos y nodos que quedarian sin conexion, sin eliminar nada.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ImpactoEliminacionResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ImpactoEliminacionResponse> impactoEliminacion(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(modelMapper.map(
                actividadService.evaluarImpactoEliminacion(principal.empresaId(), id),
                ImpactoEliminacionResponse.class));
    }

    @DeleteMapping("/actividades/{id}")
    @Operation(summary = "Eliminar (baja logica) una actividad", description = "Desactiva tambien los arcos conectados.")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        actividadService.eliminar(principal.empresaId(), id);
        return ResponseEntity.noContent().build();
    }
}
