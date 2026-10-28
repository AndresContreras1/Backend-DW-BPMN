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


import com.facimus.procesos.modelado.controller.dto.EditarPoolRequest;
import com.facimus.procesos.modelado.controller.dto.PoolRequest;
import com.facimus.procesos.modelado.controller.dto.PoolResponse;
import com.facimus.procesos.gestion.service.OperacionEstructura;
import com.facimus.procesos.gestion.service.PermisoEstructuraService;
import com.facimus.procesos.modelado.service.PoolOrquestadorService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

@Tag(name = "Pools", description = "Participantes del proceso: empresa propia, cliente, proveedor o sistema externo (HU-21 y HU-23).")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PoolController {

    private final PoolService poolService;
    private final PoolOrquestadorService poolOrquestadorService;
    private final PermisoEstructuraService permisoEstructuraService;

    @GetMapping("/procesos/{procesoId}/pools")
    @Operation(summary = "Listar los pools de un proceso", description = "Ordenados por su posicion.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PoolResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<PoolResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(poolService.consultarPorProceso(principal.empresaId(), procesoId));
    }

    @GetMapping("/pools/{id}")
    @Operation(summary = "Ver el detalle de un pool")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = PoolResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PoolResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(poolService.consultarDetalle(principal.empresaId(), id));
    }

    @PostMapping("/procesos/{procesoId}/pools")
    @Operation(summary = "Crear un pool adicional en un proceso")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = PoolResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PoolResponse> crear(@PathVariable Long procesoId,
            @Validated @RequestBody PoolRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        permisoEstructuraService.validar(principal.empresaId(), principal.rol(), OperacionEstructura.CREAR_POOL);
        PoolResponse pool = poolService.crear(principal.empresaId(), procesoId, request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/pools/{id}").buildAndExpand(pool.id()).toUri())
                .body(pool);
    }

    @PutMapping("/pools/{id}")
    @Operation(summary = "Editar un pool", description = "No se puede marcar como caja negra si ya contiene lanes.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = PoolResponse.class)))
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
    public ResponseEntity<PoolResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarPoolRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        permisoEstructuraService.validar(empresaId, principal.rol(), OperacionEstructura.EDITAR_POOL);
        return ResponseEntity.ok(poolService.editar(empresaId, id, request));
    }

    @DeleteMapping("/pools/{id}")
    @Operation(summary = "Eliminar un pool", description = "Se rechaza si alguna de sus lanes tiene elementos activos; elimina las lanes en cascada.")
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
        permisoEstructuraService.validar(empresaId, principal.rol(), OperacionEstructura.ELIMINAR_POOL);
        poolOrquestadorService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
