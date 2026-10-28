package com.facimus.procesos.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RespuestasSegurasTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Seguridad_sinToken_devuelve401ProblemDetailSinTraza")
    void Seguridad_sinToken_devuelve401ProblemDetailSinTraza() throws Exception {
        mockMvc.perform(get("/api/v1/procesos").param("trace", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    @DisplayName("Seguridad_cualquierRespuesta_noPermiteEnmarcarElSitioDesdeOtroOrigen")
    void Seguridad_cualquierRespuesta_noPermiteEnmarcarElSitioDesdeOtroOrigen() throws Exception {
        mockMvc.perform(get("/api/v1/procesos"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    @DisplayName("Seguridad_preflightDeAngular_incluyeLasCabecerasCors")
    void Seguridad_preflightDeAngular_incluyeLasCabecerasCors() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/procesos")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
