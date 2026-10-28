package com.facimus.procesos.gestion.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.LoginResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.UsuarioService;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private UsuarioService usuarioService;

    @Test
    @DisplayName("POST /api/v1/auth/login - credenciales validas devuelven el token (200)")
    void AuthController_login_credencialesValidas_devuelveToken() throws Exception {
        // Arrange
        LoginRequest request = new LoginRequest("juan@acme.com", "secret123");
        given(usuarioService.autenticar(request)).willReturn(new LoginResponse("token-de-prueba", "Bearer", 1800L,
                new UsuarioResponse(10L, "Juan", "juan@acme.com", RolAcceso.ADMINISTRADOR, true)));

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "secret123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-de-prueba"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.usuario.email").value("juan@acme.com"))
                .andExpect(jsonPath("$.usuario.rolAcceso").value("ADMINISTRADOR"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - credenciales invalidas devuelven 401 sin generar token")
    void AuthController_login_credencialesInvalidas_devuelve401() throws Exception {
        // Arrange
        given(usuarioService.autenticar(new LoginRequest("juan@acme.com", "mala")))
                .willThrow(new BadCredentialsException("Correo o contrasena incorrectos."));

        // Act + Assert
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("juan@acme.com", "mala"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - campos vacios devuelven 400")
    void AuthController_login_camposVacios_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - usuario autenticado recibe 204")
    void AuthController_logout_autenticado_devuelve204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(principal(RolAcceso.ADMINISTRADOR)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - sin autenticar devuelve 401")
    void AuthController_logout_sinAutenticar_devuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}
