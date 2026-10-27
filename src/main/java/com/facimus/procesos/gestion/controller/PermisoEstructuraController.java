package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.modelmapper.ModelMapper;

import com.facimus.procesos.gestion.controller.dto.PermisoEstructuraRequest;
import com.facimus.procesos.gestion.controller.dto.PermisoEstructuraResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.PermisoEstructuraService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;

import lombok.RequiredArgsConstructor;

/** HU-24: configuracion de permisos de estructura por rol de acceso. */
@Tag(name = "Permisos de estructura", description = "Configuracion de que rol de acceso puede modificar pools y lanes (HU-24).")
@RestController
@RequestMapping("/api/v1/permisos-estructura")
@RequiredArgsConstructor
public class PermisoEstructuraController {

    private final PermisoEstructuraService permisoEstructuraService;
    private final ModelMapper modelMapper;

    @GetMapping
    @Operation(summary = "Consultar los permisos configurados", description = "Devuelve la configuracion de permisos por rol de acceso de la empresa.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = PermisoEstructuraResponse.class))))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<List<PermisoEstructuraResponse>> listar(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(permisoEstructuraService.listar(principal.empresaId()).stream()
                .map(p -> modelMapper.map(p, PermisoEstructuraResponse.class)).toList());
    }

    @PutMapping("/{rol}")
    @Operation(summary = "Actualizar los permisos de un rol", description = "Define si un rol de acceso puede crear, editar o eliminar pools y lanes.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa.",
            content = @Content(schema = @Schema(implementation = PermisoEstructuraResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud invalida (errores de validacion o JSON malformado).",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "No autenticado: token ausente o invalido.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "Sin permisos para esta operacion.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PermisoEstructuraResponse> actualizar(@PathVariable RolAcceso rol,
            @RequestBody PermisoEstructuraRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        var permiso = permisoEstructuraService.actualizar(principal.empresaId(), rol,
                request.crearPool(), request.editarPool(), request.eliminarPool(),
                request.crearLane(), request.editarLane(), request.eliminarLane());
        return ResponseEntity.ok(modelMapper.map(permiso, PermisoEstructuraResponse.class));
    }
}
