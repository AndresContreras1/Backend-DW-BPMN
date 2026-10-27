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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.InvitacionUsuario;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.InvitacionUsuarioService;

@WebMvcTest(InvitacionUsuarioController.class)
@Import(ModelMapperConfig.class)
class InvitacionUsuarioControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean InvitacionUsuarioService service;

    @Test
    void crear_invitacion_retorna_201() throws Exception {
        Empresa empresa = new Empresa(); empresa.setId(1L);
        InvitacionUsuario invitacion = new InvitacionUsuario();
        invitacion.setId(7L);
        invitacion.setEmpresa(empresa);
        invitacion.setEmail("nuevo@demo.com");
        invitacion.setRolAcceso(RolAcceso.EDITOR);
        invitacion.setToken("abc");
        invitacion.setFechaExpiracion(LocalDateTime.now().plusHours(48));

        given(service.crear(1L, "nuevo@demo.com", RolAcceso.EDITOR)).willReturn(invitacion);

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
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setNombre("Ana");
        usuario.setEmail("ana@demo.com");
        usuario.setRolAcceso(RolAcceso.EDITOR);
        usuario.setActivo(true);

        given(service.aceptar(eq("token-ok"), eq("Ana"), anyString())).willReturn(usuario);

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
}
