package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.controller.dto.ActualizarUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.CrearUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.LoginRequest;
import com.facimus.procesos.gestion.controller.dto.LoginResponse;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.security.JwtService;

/** Metodos del service que devuelven el DTO Response (los usa el controller). */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceResponseTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private UsuarioServiceImpl service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = spy(new UsuarioServiceImpl(usuarioRepository, empresaService, passwordEncoder, jwtService,
                new ModelMapperConfig().modelMapper()));

        Empresa empresa = new Empresa();
        empresa.setId(1L);
        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmpresa(empresa);
        usuario.setNombre("Juan");
        usuario.setEmail("juan@acme.com");
        usuario.setRolAcceso(RolAcceso.EDITOR);
        usuario.setActivo(true);
    }

    @Test
    void UsuarioServiceImpl_autenticarLogin_credencialesValidas_devuelveLoginResponse() {
        doReturn(usuario).when(service).autenticar("juan@acme.com", "secret123");
        given(jwtService.generarToken(any(ApiPrincipal.class))).willReturn("token-de-prueba");
        given(jwtService.getExpirationSeconds()).willReturn(1800L);

        LoginResponse respuesta = service.autenticar(new LoginRequest("juan@acme.com", "secret123"));

        assertThat(respuesta.accessToken()).isEqualTo("token-de-prueba");
        assertThat(respuesta.tokenType()).isEqualTo("Bearer");
        assertThat(respuesta.expiresIn()).isEqualTo(1800L);
        assertThat(respuesta.usuario()).isEqualTo(
                new UsuarioResponse(10L, "Juan", "juan@acme.com", RolAcceso.EDITOR, true));
    }

    @Test
    void UsuarioServiceImpl_autenticarLogin_credencialesInvalidas_lanzaBadCredentialsSinToken() {
        doThrow(new ReglaNegocioException("Correo o contrasena incorrectos."))
                .when(service).autenticar("juan@acme.com", "mala");

        assertThatThrownBy(() -> service.autenticar(new LoginRequest("juan@acme.com", "mala")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Correo o contrasena incorrectos.")
                .hasNoCause();

        verifyNoInteractions(jwtService);
    }

    @Test
    void UsuarioServiceImpl_consultarPorEmpresa_hayUsuarios_devuelveResponses() {
        doReturn(List.of(usuario)).when(service).listarPorEmpresa(1L);

        List<UsuarioResponse> respuesta = service.consultarPorEmpresa(1L);

        assertThat(respuesta).containsExactly(
                new UsuarioResponse(10L, "Juan", "juan@acme.com", RolAcceso.EDITOR, true));
    }

    @Test
    void UsuarioServiceImpl_consultarPorEmpresa_sinUsuarios_devuelveListaVacia() {
        doReturn(List.of()).when(service).listarPorEmpresa(1L);

        assertThat(service.consultarPorEmpresa(1L)).isEmpty();
    }

    @Test
    void UsuarioServiceImpl_crearColaboradorRequest_datosValidos_devuelveResponse() {
        doReturn(usuario).when(service).crearColaborador(1L, "Juan", "juan@acme.com", "secret123",
                RolAcceso.EDITOR);

        UsuarioResponse respuesta = service.crearColaborador(1L,
                new CrearUsuarioRequest("Juan", "juan@acme.com", "secret123", RolAcceso.EDITOR));

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.nombre()).isEqualTo("Juan");
        assertThat(respuesta.rolAcceso()).isEqualTo(RolAcceso.EDITOR);
    }

    @Test
    void UsuarioServiceImpl_consultarDetalle_usuarioExiste_devuelveResponse() {
        doReturn(usuario).when(service).obtener(1L, 10L);

        UsuarioResponse respuesta = service.consultarDetalle(1L, 10L);

        assertThat(respuesta.email()).isEqualTo("juan@acme.com");
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    void UsuarioServiceImpl_actualizarRequest_cambiaRolYEstado_devuelveResponse() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        usuario.setActivo(false);
        doReturn(usuario).when(service).actualizar(1L, 10L, RolAcceso.ADMINISTRADOR, false);

        UsuarioResponse respuesta = service.actualizar(1L, 10L,
                new ActualizarUsuarioRequest(RolAcceso.ADMINISTRADOR, false));

        assertThat(respuesta.rolAcceso()).isEqualTo(RolAcceso.ADMINISTRADOR);
        assertThat(respuesta.activo()).isFalse();
    }
}
