package com.facimus.procesos.gestion.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.service.EmpresaOrquestadorService;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;

import com.facimus.procesos.config.ModelMapperConfig;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(EmpresaController.class)
@Import(ModelMapperConfig.class)
class EmpresaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmpresaOrquestadorService empresaOrquestadorService;

    @Test
    @DisplayName("POST /api/v1/empresas - registrar empresa exitoso (201)")
    void registrar_exitoso() throws Exception {
        Empresa empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme Corp");
        empresa.setNit("900123456");
        empresa.setCorreoContacto("info@acme.com");
        empresa.setFechaRegistro(LocalDate.now());

        given(empresaOrquestadorService.registrarConAdministrador(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString())).willReturn(empresa);

        mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreEmpresa": "Acme Corp",
                                  "nit": "900123456",
                                  "correoContacto": "info@acme.com",
                                  "nombreAdmin": "Admin",
                                  "emailAdmin": "admin@acme.com",
                                  "passwordAdmin": "secret123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/empresas/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Acme Corp"))
                .andExpect(jsonPath("$.nit").value("900123456"));

        verify(empresaOrquestadorService).registrarConAdministrador("Acme Corp", "900123456",
                "info@acme.com", "Admin", "admin@acme.com", "secret123");
    }

    @Test
    @DisplayName("POST /api/v1/empresas - validacion falla (400)")
    void registrar_validacion_falla() throws Exception {
        mockMvc.perform(post("/api/v1/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreEmpresa": "",
                                  "nit": "",
                                  "correoContacto": "no-es-email",
                                  "nombreAdmin": "",
                                  "emailAdmin": "invalido",
                                  "passwordAdmin": "12"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
