package com.facimus.procesos.gestion.controller;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.controller.dto.AceptarInvitacionRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.InvitacionUsuarioService;

@WebMvcTest(InvitacionUsuarioController.class)
class InvitacionUsuarioControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean InvitacionUsuarioService service;

    @Test
    void crear_invitacion_retorna_201() throws Exception {
        InvitacionUsuarioResponse invitacion = new InvitacionUsuarioResponse(7L, "nuevo@demo.com",
                RolAcceso.EDITOR, "abc", LocalDateTime.now().plusHours(48));

        given(service.crear(1L, new InvitacionUsuarioRequest("nuevo@demo.com", RolAcceso.EDITOR)))
                .willReturn(invitacion);

        mockMvc.perform(post("/api/v1/usuarios/invitaciones")
                .with(principal(RolAcceso.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nuevo@demo.com","rolAcceso":"EDITOR"}
                    """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/usuarios/invitaciones/7"))
                .andExpect(jsonPath("$.email").value("nuevo@demo.com"))
                .andExpect(jsonPath("$.token").value("abc"));
    }

    @Test
    void aceptar_invitacion_es_publico_y_retorna_200() throws Exception {
        UsuarioResponse usuario = new UsuarioResponse(3L, "Ana", "ana@demo.com", RolAcceso.EDITOR, true);

        given(service.aceptar(eq("token-ok"), any(AceptarInvitacionRequest.class))).willReturn(usuario);

        mockMvc.perform(post("/api/v1/usuarios/invitaciones/token-ok/aceptar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre":"Ana","password":"password123"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.rolAcceso").value("EDITOR"));
    }

    @Test
    void crear_sin_admin_retorna_403() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/invitaciones")
                .with(principal(RolAcceso.EDITOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nuevo@demo.com","rolAcceso":"EDITOR"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_sin_email_retorna_400_y_no_llega_al_service() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/invitaciones")
                .with(principal(RolAcceso.ADMINISTRADOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rolAcceso":"EDITOR"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("email")));

        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
