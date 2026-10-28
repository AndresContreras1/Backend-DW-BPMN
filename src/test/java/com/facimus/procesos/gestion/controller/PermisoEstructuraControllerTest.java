package com.facimus.procesos.gestion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.controller.dto.PermisoEstructuraRequest;
import com.facimus.procesos.gestion.controller.dto.PermisoEstructuraResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.PermisoEstructuraService;

@WebMvcTest(PermisoEstructuraController.class)
class PermisoEstructuraControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PermisoEstructuraService service;

    @Test
    void listar_retorna_permisos() throws Exception {
        PermisoEstructuraResponse p = permiso(RolAcceso.ADMINISTRADOR, false);
        given(service.consultarPorEmpresa(1L)).willReturn(List.of(p));

        mockMvc.perform(get("/api/v1/permisos-estructura")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rolAcceso").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[0].crearPool").value(true));
    }

    @Test
    void actualizar_retorna_configuracion() throws Exception {
        PermisoEstructuraResponse p = permiso(RolAcceso.EDITOR, true);
        given(service.actualizar(eq(1L), eq(RolAcceso.EDITOR), any(PermisoEstructuraRequest.class)))
                .willReturn(p);

        mockMvc.perform(put("/api/v1/permisos-estructura/EDITOR")
                .with(principal(RolAcceso.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"crearPool":true,"editarPool":true,"eliminarPool":true,
                     "crearLane":true,"editarLane":true,"eliminarLane":false}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAcceso").value("EDITOR"))
                .andExpect(jsonPath("$.eliminarPool").value(true));
    }

    @Test
    void editor_no_puede_configurar_permisos() throws Exception {
        mockMvc.perform(get("/api/v1/permisos-estructura")
                .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isForbidden());
    }

    private PermisoEstructuraResponse permiso(RolAcceso rol, boolean eliminarPool) {
        return new PermisoEstructuraResponse(rol, true, true, eliminarPool, true, true, false);
    }
}
