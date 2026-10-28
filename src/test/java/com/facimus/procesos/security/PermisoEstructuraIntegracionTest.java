package com.facimus.procesos.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/**
 * HU-24 de punta a punta: DELETE de pools y lanes ya no lo decide SecurityConfig por rol sino
 * PermisoEstructuraService, asi que un permiso concedido o revocado cambia la respuesta de la API.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:permisos-estructura-it;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermisoEstructuraIntegracionTest {

    private static final String ADMIN_DEMO = "admin@demo.com";
    private static final String CLAVE_DEMO = "Admin-Demo-2026";
    private static final String EDITOR = "editor@permisos.com";
    private static final String LECTOR = "lector@permisos.com";
    private static final String CLAVE_COLABORADOR = "colaborador123";

    // Un id que no existe: si la autorizacion deja pasar la peticion, el service responde 404; si no, 403.
    private static final long ID_INEXISTENTE = Long.MAX_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UsuarioService usuarioService;

    private final Map<RolAcceso, String> tokens = new EnumMap<>(RolAcceso.class);

    @BeforeAll
    void iniciarSesionConCadaRol() throws Exception {
        Long empresaId = usuarioService.autenticar(ADMIN_DEMO, CLAVE_DEMO).getEmpresa().getId();
        usuarioService.crearColaborador(empresaId, "Editor", EDITOR, CLAVE_COLABORADOR, RolAcceso.EDITOR);
        usuarioService.crearColaborador(empresaId, "Lector", LECTOR, CLAVE_COLABORADOR, RolAcceso.SOLO_LECTURA);

        tokens.put(RolAcceso.ADMINISTRADOR, login(ADMIN_DEMO, CLAVE_DEMO));
        tokens.put(RolAcceso.EDITOR, login(EDITOR, CLAVE_COLABORADOR));
        tokens.put(RolAcceso.SOLO_LECTURA, login(LECTOR, CLAVE_COLABORADOR));
    }

    @Test
    @DisplayName("PermisoEstructura_eliminar_editorConPermisoConcedidoYLuegoRevocado_pasaDe404A403")
    void PermisoEstructura_eliminar_editorConPermisoConcedidoYLuegoRevocado_pasaDe404A403() throws Exception {
        configurarPermisosDelEditor(true);
        mockMvc.perform(conToken(delete("/api/v1/pools/{id}", ID_INEXISTENTE), RolAcceso.EDITOR))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(delete("/api/v1/lanes/{id}", ID_INEXISTENTE), RolAcceso.EDITOR))
                .andExpect(status().isNotFound());

        configurarPermisosDelEditor(false);
        mockMvc.perform(conToken(delete("/api/v1/pools/{id}", ID_INEXISTENTE), RolAcceso.EDITOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(conToken(delete("/api/v1/lanes/{id}", ID_INEXISTENTE), RolAcceso.EDITOR))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PermisoEstructura_eliminar_soloLecturaConCualquierConfiguracion_siempre403")
    void PermisoEstructura_eliminar_soloLecturaConCualquierConfiguracion_siempre403() throws Exception {
        configurarPermisosDelEditor(true);

        mockMvc.perform(conToken(delete("/api/v1/pools/{id}", ID_INEXISTENTE), RolAcceso.SOLO_LECTURA))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(delete("/api/v1/lanes/{id}", ID_INEXISTENTE), RolAcceso.SOLO_LECTURA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PermisoEstructura_actualizar_soloLecturaConPermisos_devuelve409")
    void PermisoEstructura_actualizar_soloLecturaConPermisos_devuelve409() throws Exception {
        actualizarPermisos(RolAcceso.SOLO_LECTURA, true).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PermisoEstructura_actualizar_administradorSinPermisos_devuelve409YSigueEliminando")
    void PermisoEstructura_actualizar_administradorSinPermisos_devuelve409YSigueEliminando() throws Exception {
        actualizarPermisos(RolAcceso.ADMINISTRADOR, false).andExpect(status().isConflict());

        mockMvc.perform(conToken(delete("/api/v1/pools/{id}", ID_INEXISTENTE), RolAcceso.ADMINISTRADOR))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(get("/api/v1/permisos-estructura/mio"), RolAcceso.ADMINISTRADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eliminarPool").value(true))
                .andExpect(jsonPath("$.eliminarLane").value(true));
    }

    @Test
    @DisplayName("PermisoEstructura_mio_cadaRol_devuelveSusPropiosPermisos")
    void PermisoEstructura_mio_cadaRol_devuelveSusPropiosPermisos() throws Exception {
        configurarPermisosDelEditor(false);

        mockMvc.perform(conToken(get("/api/v1/permisos-estructura/mio"), RolAcceso.EDITOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAcceso").value("EDITOR"))
                .andExpect(jsonPath("$.crearPool").value(true))
                .andExpect(jsonPath("$.eliminarPool").value(false));
        mockMvc.perform(conToken(get("/api/v1/permisos-estructura/mio"), RolAcceso.SOLO_LECTURA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAcceso").value("SOLO_LECTURA"))
                .andExpect(jsonPath("$.crearPool").value(false));
    }

    private void configurarPermisosDelEditor(boolean eliminar) throws Exception {
        actualizarPermisos(RolAcceso.EDITOR, eliminar).andExpect(status().isOk());
    }

    private ResultActions actualizarPermisos(RolAcceso rol, boolean valor) throws Exception {
        String cuerpo = """
                {"crearPool":true,"editarPool":true,"eliminarPool":%1$s,
                 "crearLane":true,"editarLane":true,"eliminarLane":%1$s}
                """.formatted(valor);
        return mockMvc.perform(conToken(put("/api/v1/permisos-estructura/{rol}", rol.name()), RolAcceso.ADMINISTRADOR)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder conToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion, RolAcceso rol) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(rol));
    }

    private String login(String email, String password) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
