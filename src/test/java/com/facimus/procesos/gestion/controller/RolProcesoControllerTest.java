package com.facimus.procesos.gestion.controller;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoConsultaResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.RolProcesoService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
   import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(RolProcesoController.class)
class RolProcesoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RolProcesoService rolProcesoService;


    @Test
    void consulta_paginada_retorna_procesos_de_uso() throws Exception {
        RolProcesoConsultaResponse consulta = new RolProcesoConsultaResponse(1L, "Analista", "Analiza",
                List.of("Compras"), true);
        given(rolProcesoService.consultarPagina(eq(1L), eq("ana"), eq(0), isNull()))
                .willReturn(new PageResponse<>(List.of(consulta), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/roles/consulta?nombre=ana&pagina=0")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Analista"))
                .andExpect(jsonPath("$.content[0].procesos[0]").value("Compras"))
                .andExpect(jsonPath("$.content[0].enUso").value(true));
    }

    @Test
    void consulta_paginada_acepta_tamano_valido() throws Exception {
        given(rolProcesoService.consultarPagina(eq(1L), isNull(), eq(2), eq(25)))
                .willReturn(new PageResponse<>(List.of(), 2, 25, 0, 0));

        mockMvc.perform(get("/api/v1/roles/consulta?pagina=2&tamano=25")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(25));
    }

    @Test
    void consulta_paginada_con_pagina_negativa_retorna_400_problemdetail() throws Exception {
        mockMvc.perform(get("/api/v1/roles/consulta?pagina=-1")
                .with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void consulta_paginada_con_tamano_fuera_de_rango_retorna_400() throws Exception {
        mockMvc.perform(get("/api/v1/roles/consulta?tamano=0").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/roles/consulta?tamano=101").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/roles - listar roles (200)")
    void listar_roles() throws Exception {
        given(rolProcesoService.consultarConUso(1L)).willReturn(List.of(vista(1L, "Analista", "Analiza procesos", 3)));

        mockMvc.perform(get("/api/v1/roles").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Analista"))
                .andExpect(jsonPath("$[0].procesosQueLoUsan").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/roles - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/roles/{id} - detalle rol (200)")
    void detalle_rol() throws Exception {
        given(rolProcesoService.consultarDetalle(1L, 1L)).willReturn(vista(1L, "Analista", "Analiza procesos", 3));

        mockMvc.perform(get("/api/v1/roles/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Analista"))
                .andExpect(jsonPath("$.procesosQueLoUsan").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/roles/{id} - sin sesion retorna 401")
    void detalle_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/roles/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/roles - crear rol como admin (201)")
    void crear_rol() throws Exception {
        given(rolProcesoService.crear(1L, new RolProcesoRequest("Supervisor", "Supervisa")))
                .willReturn(vista(2L, "Supervisor", "Supervisa", 0));

        mockMvc.perform(post("/api/v1/roles")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Supervisor","descripcion":"Supervisa"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/roles/2"))
                .andExpect(jsonPath("$.nombre").value("Supervisor"));
    }

    @Test
    @DisplayName("POST /api/v1/roles - editor no puede crear (403)")
    void crear_como_editor() throws Exception {
        mockMvc.perform(post("/api/v1/roles")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"X","descripcion":"Y"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/roles/{id} - editar rol (200)")
    void editar_rol() throws Exception {
        given(rolProcesoService.editar(1L, 1L, new RolProcesoRequest("Analista Sr", "Senior")))
                .willReturn(vista(1L, "Analista Sr", "Senior", 0));

        mockMvc.perform(put("/api/v1/roles/1")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Analista Sr","descripcion":"Senior"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Analista Sr"));
    }

    @Test
    @DisplayName("DELETE /api/v1/roles/{id} - eliminar rol (204)")
    void eliminar_rol() throws Exception {
        doNothing().when(rolProcesoService).eliminar(1L, 1L);

        mockMvc.perform(delete("/api/v1/roles/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/roles - validacion falla sin nombre (400)")
    void crear_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/roles")
                        .with(principal(RolAcceso.ADMINISTRADOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"","descripcion":"algo"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private RolProcesoVistaResponse vista(Long id, String nombre, String descripcion, long usos) {
        return new RolProcesoVistaResponse(id, nombre, descripcion, usos, usos > 0);
    }

}
