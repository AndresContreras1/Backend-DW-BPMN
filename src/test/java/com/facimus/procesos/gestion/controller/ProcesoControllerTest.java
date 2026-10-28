package com.facimus.procesos.gestion.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.controller.dto.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.EditarProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoDiagramaResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.ProcesoDiagramaService;
import com.facimus.procesos.gestion.service.ProcesoOrquestadorService;
import com.facimus.procesos.gestion.service.ProcesoService;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(ProcesoController.class)
class ProcesoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcesoService procesoService;

    @MockitoBean
    private ProcesoOrquestadorService procesoOrquestadorService;

    @MockitoBean
    private ProcesoDiagramaService procesoDiagramaService;

    @Test
    @DisplayName("GET /api/v1/procesos - listar procesos (200)")
    void listar_procesos() throws Exception {
        ProcesoResponse p = crearProceso(1L, "Ventas", EstadoProceso.BORRADOR, true);
        given(procesoService.consultarPagina(eq(1L), any(), any(), any(), any(), eq(0)))
                .willReturn(new PageResponse<>(List.of(p), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/procesos").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Ventas"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos?activo=false - filtro de inactivos acepta parametro (200)")
    void listar_procesos_inactivos() throws Exception {
        ProcesoResponse p = crearProceso(1L, "Eliminado", EstadoProceso.BORRADOR, false);
        given(procesoService.consultarPagina(eq(1L), any(), any(), any(), eq(false), eq(0)))
                .willReturn(new PageResponse<>(List.of(p), 0, 10, 1, 1));

        mockMvc.perform(get("/api/v1/procesos?activo=false").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Eliminado"));
    }

    @Test
    @DisplayName("GET /api/v1/procesos - sin sesion retorna 401")
    void listar_sin_sesion() throws Exception {
        mockMvc.perform(get("/api/v1/procesos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/procesos - crear proceso como editor (201)")
    void crear_proceso() throws Exception {
        ProcesoResponse p = crearProceso(2L, "Compras", EstadoProceso.BORRADOR, true);
        given(procesoOrquestadorService.crearConPoolInicial(eq(1L), eq(1L), any(ProcesoRequest.class)))
                .willReturn(p);

        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Compras","descripcion":"Proceso de compras","categoria":"Operativo"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/procesos/2"))
                .andExpect(jsonPath("$.nombre").value("Compras"));

        verify(procesoOrquestadorService).crearConPoolInicial(1L, 1L,
                new ProcesoRequest("Compras", "Proceso de compras", "Operativo"));
    }

    @Test
    @DisplayName("POST /api/v1/procesos - solo lectura retorna 403")
    void crear_proceso_solo_lectura() throws Exception {
        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.SOLO_LECTURA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"X","descripcion":"Y","categoria":"Z"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/procesos/{id} - detalle proceso (200)")
    void detalle_proceso() throws Exception {
        ProcesoResponse p = crearProceso(1L, "Ventas", EstadoProceso.BORRADOR, true);
        given(procesoService.consultarDetalle(1L, 1L)).willReturn(new ProcesoDetalleResponse(p, List.of()));

        mockMvc.perform(get("/api/v1/procesos/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso.nombre").value("Ventas"))
                .andExpect(jsonPath("$.historial").isArray());
    }

    @Test
    @DisplayName("PUT /api/v1/procesos/{id} - editar proceso (200)")
    void editar_proceso() throws Exception {
        ProcesoResponse p = crearProceso(1L, "Ventas v2", EstadoProceso.BORRADOR, true);
        given(procesoService.editarDatos(eq(1L), eq(1L), eq(1L), any(EditarProcesoRequest.class)))
                .willReturn(p);

        mockMvc.perform(put("/api/v1/procesos/1")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ventas v2","descripcion":"Desc","categoria":"Op"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ventas v2"));
    }

    @Test
    @DisplayName("PATCH /api/v1/procesos/{id}/publicar - publicar proceso (200)")
    void publicar_proceso() throws Exception {
        ProcesoResponse p = crearProceso(1L, "Ventas", EstadoProceso.PUBLICADO, true);
        given(procesoService.cambiarEstado(1L, 1L, 1L, new CambiarEstadoProcesoRequest(EstadoProceso.PUBLICADO)))
                .willReturn(p);
        mockMvc.perform(patch("/api/v1/procesos/1")
                .with(principal(RolAcceso.EDITOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"estado":"PUBLICADO"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));

        verify(procesoService).cambiarEstado(1L, 1L, 1L, new CambiarEstadoProcesoRequest(EstadoProceso.PUBLICADO));
    }


    @Test
    void obtener_diagrama_retorna_200() throws Exception {
        ProcesoResponse proceso = crearProceso(1L, "Ventas", EstadoProceso.BORRADOR, true);
        given(procesoDiagramaService.consultarDiagrama(1L, 1L))
                .willReturn(new ProcesoDiagramaResponse(proceso, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/procesos/1/diagrama")
                .with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proceso.nombre").value("Ventas"))
                .andExpect(jsonPath("$.pools").isArray());
    }

    @Test
    void consultar_historial_separado() throws Exception {
        given(procesoService.consultarHistorial(1L, 1L)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/procesos/1/historial").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("DELETE /api/v1/procesos/{id} - eliminar como admin (204)")
    void eliminar_proceso() throws Exception {
        doNothing().when(procesoService).eliminarLogico(1L, 1L, 1L);

        mockMvc.perform(delete("/api/v1/procesos/1").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/procesos/{id} - editor no puede eliminar (403)")
    void eliminar_como_editor() throws Exception {
        mockMvc.perform(delete("/api/v1/procesos/1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/procesos - pagina negativa retorna ProblemDetail 400")
    void listar_pagina_negativa() throws Exception {
        mockMvc.perform(get("/api/v1/procesos?pagina=-1").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/procesos - estado desconocido retorna ProblemDetail 400")
    void listar_estado_desconocido() throws Exception {
        mockMvc.perform(get("/api/v1/procesos?estado=DESCONOCIDO").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/procesos - JSON malformado retorna ProblemDetail 400")
    void crear_json_malformado() throws Exception {
        mockMvc.perform(post("/api/v1/procesos")
                        .with(principal(RolAcceso.EDITOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    private ProcesoResponse crearProceso(Long id, String nombre, EstadoProceso estado, boolean activo) {
        return new ProcesoResponse(id, nombre, "Descripcion", "Operativo", estado, activo,
                LocalDateTime.now(), LocalDateTime.now());
    }

}
