package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


import com.facimus.procesos.gestion.controller.dto.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.CrearUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-02: administracion de colaboradores de la empresa (solo administrador). */
@Tag(name = "Usuarios", description = "Administracion de colaboradores de la empresa (HU-02).")
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar los colaboradores de la empresa", description = "Solo administrador.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UsuarioResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<UsuarioResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(usuarioService.consultarPorEmpresa(principal.empresaId()));
    }

    @PostMapping
    @Operation(summary = "Crear un colaborador", description = "Alta directa con contrasena asignada por el administrador.")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conflicto: la operacion viola una regla de negocio.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<UsuarioResponse> crear(@Validated @RequestBody CrearUsuarioRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        UsuarioResponse usuario = usuarioService.crearColaborador(principal.empresaId(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/usuarios/{id}").buildAndExpand(usuario.id()).toUri())
                .body(usuario);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver el detalle de un colaborador")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<UsuarioResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(usuarioService.consultarDetalle(principal.empresaId(), id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cambiar el rol de acceso y/o el estado activo de un colaborador")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Long id,
            @Validated @RequestBody ActualizarUsuarioRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(usuarioService.actualizar(principal.empresaId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactivar un colaborador", description = "Baja logica: no se borra el usuario ni su historial.")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> desactivar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        usuarioService.desactivar(principal.empresaId(), id);
        return ResponseEntity.noContent().build();
    }
}
