package com.facimus.procesos.modelado.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.modelado.controller.dto.ValidacionModeloResponse;
import com.facimus.procesos.modelado.service.ValidacionModeloService;

@WebMvcTest(ValidacionModeloController.class)
class ValidacionModeloControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ValidacionModeloService validacionModeloService;

    @Test
    void validar_conAdvertencias_retorna200ConLaLista() throws Exception {
        given(validacionModeloService.consultarValidacion(1L, 10L)).willReturn(
                new ValidacionModeloResponse(false, List.of("Falta un pool EMPRESA.", "Gateway sin salidas.")));

        mockMvc.perform(get("/api/v1/procesos/10/validacion").with(principal(RolAcceso.SOLO_LECTURA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.advertencias.length()").value(2))
                .andExpect(jsonPath("$.advertencias[0]").value("Falta un pool EMPRESA."));
    }

    @Test
    void validar_modeloCorrecto_retornaValidoTrue() throws Exception {
        given(validacionModeloService.consultarValidacion(1L, 10L))
                .willReturn(new ValidacionModeloResponse(true, List.of()));

        mockMvc.perform(get("/api/v1/procesos/10/validacion").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.advertencias").isEmpty());
    }

    @Test
    void validar_procesoDeOtraEmpresa_retorna404() throws Exception {
        given(validacionModeloService.consultarValidacion(1L, 99L))
                .willThrow(new RecursoNoEncontradoException("Proceso no encontrado."));

        mockMvc.perform(get("/api/v1/procesos/99/validacion").with(principal(RolAcceso.EDITOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    void validar_sinSesion_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/procesos/10/validacion")).andExpect(status().isUnauthorized());
    }
}
