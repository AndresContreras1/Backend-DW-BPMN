package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.modelmapper.ModelMapper;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoConsultaResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-17 a HU-20: roles de proceso (solo administrador crea/edita/elimina). */
@Tag(name = "Roles de proceso", description = "Funciones asignables a lanes, no permisos de acceso (HU-17 a HU-20).")
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RolProcesoController {

    private final RolProcesoService rolProcesoService;
    private final ModelMapper modelMapper;

    @GetMapping
    @Operation(summary = "Listar los roles de proceso de la empresa", description = "Indica para cada rol cuantos procesos lo usan.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RolProcesoVistaResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<RolProcesoVistaResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        List<RolProcesoVistaResponse> roles = rolProcesoService.listarConUso(empresaId).stream()
                .map(v -> modelMapper.map(v, RolProcesoVistaResponse.class))
                .toList();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/consulta")
    @Operation(summary = "Buscar roles de proceso por nombre", description = "Version paginada, incluye en que procesos esta en uso cada rol.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PageResponse<RolProcesoConsultaResponse>> buscar(
            @RequestParam(required = false) String nombre,
            @RequestParam(defaultValue = "0") int pagina,
            @AuthenticationPrincipal ApiPrincipal principal) {
        final int tamano = 10;
        var page = rolProcesoService.buscarConProcesos(
                principal.empresaId(), nombre, PageRequest.of(pagina, tamano))
                .map(consulta -> modelMapper.map(consulta, RolProcesoConsultaResponse.class));
        return ResponseEntity.ok(PageResponse.from(page));
    }

    @PostMapping
    @Operation(summary = "Crear un rol de proceso", description = "Solo administrador. El nombre debe ser unico en la empresa.")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = RolProcesoVistaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conflicto: la operacion viola una regla de negocio.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<RolProcesoVistaResponse> crear(@Validated @RequestBody RolProcesoRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.crear(empresaId, request.nombre(), request.descripcion());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/roles/{id}").buildAndExpand(rol.getId()).toUri())
                .body(new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), 0, false));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver el detalle de un rol de proceso")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = RolProcesoVistaResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<RolProcesoVistaResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.obtener(empresaId, id);
        long usos = rolProcesoService.contarUsos(empresaId, id);
        return ResponseEntity.ok(new RolProcesoVistaResponse(
                rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar un rol de proceso", description = "Solo administrador.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = RolProcesoVistaResponse.class)))
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
    public ResponseEntity<RolProcesoVistaResponse> editar(@PathVariable Long id,
            @Validated @RequestBody RolProcesoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        RolProceso rol = rolProcesoService.editar(empresaId, id, request.nombre(), request.descripcion());
        long usos = rolProcesoService.contarUsos(empresaId, id);
        return ResponseEntity.ok(
                new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un rol de proceso", description = "Se rechaza si el rol esta en uso en alguna lane.")
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
        rolProcesoService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
