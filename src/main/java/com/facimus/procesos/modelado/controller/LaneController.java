package com.facimus.procesos.modelado.controller;

import java.util.List;


import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.facimus.procesos.modelado.controller.dto.LaneRequest;
import com.facimus.procesos.modelado.controller.dto.LaneResponse;
import com.facimus.procesos.modelado.controller.dto.ReordenarLanesRequest;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.gestion.service.OperacionEstructura;
import com.facimus.procesos.gestion.service.PermisoEstructuraService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-22 y HU-24: lanes (divisiones internas de un pool). */
@Tag(name = "Lanes", description = "Carriles internos de un pool, asociados a un rol de proceso (HU-22 y HU-24).")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LaneController {

    private final LaneService laneService;
    private final RolProcesoService rolProcesoService;
    private final PermisoEstructuraService permisoEstructuraService;
    private final ModelMapper modelMapper;

    @GetMapping("/pools/{poolId}/lanes")
    @Operation(summary = "Listar las lanes de un pool", description = "Ordenadas por su posicion.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaneResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<LaneResponse>> listar(@PathVariable Long poolId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<LaneResponse> lanes = laneService.listarPorPool(empresaId, poolId).stream()
                .map(l -> modelMapper.map(l, LaneResponse.class))
                .toList();
        return ResponseEntity.ok(lanes);
    }

    @GetMapping("/lanes/{id}")
    @Operation(summary = "Ver el detalle de una lane")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = LaneResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<LaneResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Lane lane = laneService.obtener(empresaId, id);
        return ResponseEntity.ok(modelMapper.map(lane, LaneResponse.class));
    }

    @PostMapping("/pools/{poolId}/lanes")
    @Operation(summary = "Crear una lane en un pool", description = "El pool no puede ser de tipo caja negra.")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = LaneResponse.class)))
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
    public ResponseEntity<LaneResponse> crear(@PathVariable Long poolId,
            @Validated @RequestBody LaneRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        permisoEstructuraService.validar(empresaId, principal.rol(), OperacionEstructura.CREAR_LANE);
        RolProceso rolProceso = rolProcesoService.obtener(empresaId, request.rolProcesoId());
        Lane lane = laneService.crear(empresaId, poolId, request.nombre(), rolProceso);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/lanes/{id}").buildAndExpand(lane.getId()).toUri())
        .body(modelMapper.map(lane, LaneResponse.class));
    }

    @PutMapping("/lanes/{id}")
    @Operation(summary = "Editar una lane", description = "Permite reasignar el rol de proceso responsable.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = LaneResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<LaneResponse> editar(@PathVariable Long id,
            @Validated @RequestBody LaneRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        permisoEstructuraService.validar(empresaId, principal.rol(), OperacionEstructura.EDITAR_LANE);
        RolProceso rolProceso = rolProcesoService.obtener(empresaId, request.rolProcesoId());
        Lane lane = laneService.editar(empresaId, id, request.nombre(), rolProceso);
        return ResponseEntity.ok(modelMapper.map(lane, LaneResponse.class));
    }

    @PatchMapping("/pools/{poolId}/lanes/orden")
    @Operation(summary = "Reordenar las lanes de un pool", description = "Recibe la lista completa de IDs en el nuevo orden.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LaneResponse.class))))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<LaneResponse>> reordenar(@PathVariable Long poolId,
            @Validated @RequestBody ReordenarLanesRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        permisoEstructuraService.validar(empresaId, principal.rol(), OperacionEstructura.EDITAR_LANE);
        List<LaneResponse> lanes = laneService.reordenar(empresaId, poolId, request.laneIds()).stream()
                .map(l -> modelMapper.map(l, LaneResponse.class)).toList();
        return ResponseEntity.ok(lanes);
    }

    @DeleteMapping("/lanes/{id}")
    @Operation(summary = "Eliminar una lane", description = "Se rechaza si contiene actividades, gateways o eventos activos.")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conflicto: la operacion viola una regla de negocio.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        permisoEstructuraService.validar(empresaId, principal.rol(), OperacionEstructura.ELIMINAR_LANE);
        laneService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
