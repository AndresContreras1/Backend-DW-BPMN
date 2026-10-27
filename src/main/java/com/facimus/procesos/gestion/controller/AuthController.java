package com.facimus.procesos.gestion.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.ReglaNegocioException;
import org.modelmapper.ModelMapper;

import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.LoginResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

/** HU-03: inicio y cierre de sesion con JWT. */
@Tag(name = "Autenticacion", description = "Inicio y cierre de sesion (HU-03).")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String TIPO_TOKEN = "Bearer";

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final ModelMapper modelMapper;

    public AuthController(UsuarioService usuarioService, JwtService jwtService, ModelMapper modelMapper) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.modelMapper = modelMapper;
    }

    @SecurityRequirements()
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion", description = "Valida credenciales y devuelve un JWT con empresaId y rol embebidos.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        Usuario usuario;
        try {
            usuario = usuarioService.autenticar(request.email(), request.password());
        } catch (ReglaNegocioException e) {
            // Sin la causa a proposito: Spring MVC buscaria un handler para ella y responderia 409.
            // Asi lo resuelve JwtAuthEntryPoint con 401 y el mismo mensaje generico, exista o no el correo (HU-03).
            throw new BadCredentialsException(e.getMessage());
        }
        String token = jwtService.generarToken(ApiPrincipal.of(usuario));
        return ResponseEntity.ok(new LoginResponse(token, TIPO_TOKEN, jwtService.getExpirationSeconds(),
                modelMapper.map(usuario, UsuarioResponse.class)));
    }

    /** Sin estado en el servidor: cerrar sesion es que el cliente descarte su token. */
    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesion", description = "Operacion stateless: no invalida el token, el cliente debe descartarlo.")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa, sin contenido.")
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
