package com.facimus.procesos.gestion.controller;


import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.facimus.procesos.gestion.controller.dto.AceptarInvitacionRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.service.InvitacionUsuarioService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-02: invitacion y alta de colaboradores. */
@Tag(name = "Invitaciones de usuario", description = "Alta de colaboradores por invitacion (HU-02).")
@RestController
@RequestMapping("/api/v1/usuarios/invitaciones")
@RequiredArgsConstructor
public class InvitacionUsuarioController {

    private final InvitacionUsuarioService invitacionUsuarioService;
    private final ModelMapper modelMapper;

    @PostMapping
    @Operation(summary = "Invitar un colaborador", description = "Genera una invitacion por correo con un token de un solo uso.")
    @ApiResponse(responseCode = "201", description = "Recurso creado.",
            content = @Content(schema = @Schema(implementation = InvitacionUsuarioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conflicto: la operacion viola una regla de negocio.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<InvitacionUsuarioResponse> crear(
            @Validated @RequestBody InvitacionUsuarioRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        var invitacion = invitacionUsuarioService.crear(principal.empresaId(), request.email(), request.rolAcceso());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/v1/usuarios/invitaciones/{id}").buildAndExpand(invitacion.getId()).toUri())
                .body(modelMapper.map(invitacion, InvitacionUsuarioResponse.class));
    }

    @PostMapping("/{token}/aceptar")
    @Operation(summary = "Aceptar una invitacion", description = "Publico: crea la cuenta del colaborador usando el token recibido.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "El recurso solicitado no existe.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conflicto: la operacion viola una regla de negocio.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<UsuarioResponse> aceptar(@PathVariable String token,
            @Validated @RequestBody AceptarInvitacionRequest request) {
        return ResponseEntity.ok(modelMapper.map(
                invitacionUsuarioService.aceptar(token, request.nombre(), request.password()),
                UsuarioResponse.class));
    }
}
