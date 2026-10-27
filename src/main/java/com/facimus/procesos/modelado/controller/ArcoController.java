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

import com.facimus.procesos.modelado.controller.dto.ArcoRequest;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import com.facimus.procesos.modelado.controller.dto.EditarArcoRequest;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-11 a HU-13: arcos (flujo entre nodos dentro de un pool). */
@Tag(name = "Arcos", description = "Flujo de secuencia entre nodos de un mismo pool (HU-11 a HU-13).")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArcoController {

    private final ArcoService arcoService;
    private final ModelMapper modelMapper;

    @PostMapping("/arcos")
    @Operation(summary = "Crear un arco entre dos nodos", description = "Ambos nodos deben pertenecer al mismo pool.")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = ArcoResponse.class)))
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
    public ResponseEntity<ArcoResponse> crear(@Validated @RequestBody ArcoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Arco arco = arcoService.crear(empresaId, request.origenId(), request.destinoId(), request.etiqueta(),
                request.condicion());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/arcos/{id}").buildAndExpand(arco.getId()).toUri())
                .body(modelMapper.map(arco, ArcoResponse.class));
    }

    @GetMapping("/arcos/{id}")
    @Operation(summary = "Ver el detalle de un arco")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ArcoResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ArcoResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Arco arco = arcoService.obtener(empresaId, id);
        return ResponseEntity.ok(modelMapper.map(arco, ArcoResponse.class));
    }

    @GetMapping("/pools/{poolId}/arcos")
    @Operation(summary = "Listar los arcos de un pool")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ArcoResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<ArcoResponse>> listar(@PathVariable Long poolId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<ArcoResponse> arcos = arcoService.listarPorPool(empresaId, poolId).stream()
                .map(a -> modelMapper.map(a, ArcoResponse.class)).toList();
        return ResponseEntity.ok(arcos);
    }

    @PutMapping("/arcos/{id}")
    @Operation(summary = "Editar un arco", description = "Permite reasignar origen y destino, reaplicando las mismas validaciones que al crear.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ArcoResponse.class)))
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
    public ResponseEntity<ArcoResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarArcoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        Arco arco = arcoService.editar(empresaId, id, request.etiqueta(), request.condicion(),
                request.origenId(), request.destinoId());
        return ResponseEntity.ok(modelMapper.map(arco, ArcoResponse.class));
    }

    @GetMapping("/arcos/{id}/impacto-eliminacion")
    @Operation(summary = "Evaluar el impacto de eliminar un arco", description = "Devuelve advertencias sin eliminar nada.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = ImpactoEliminacionResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ImpactoEliminacionResponse> impactoEliminacion(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(modelMapper.map(
                arcoService.evaluarImpactoEliminacion(principal.empresaId(), id),
                ImpactoEliminacionResponse.class));
    }

    @DeleteMapping("/arcos/{id}")
    @Operation(summary = "Eliminar un arco")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        arcoService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
