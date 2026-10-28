package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    private Empresa empresa;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme");

        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmpresa(empresa);
        usuario.setNombre("Juan");
        usuario.setEmail("juan@acme.com");
        usuario.setPasswordHash("hashed");
        usuario.setRolAcceso(RolAcceso.EDITOR);
        usuario.setActivo(true);
    }

    @Test
    @DisplayName("HU-02: crear colaborador con email unico")
    void crearColaborador_exitoso() {
        when(usuarioRepository.existsByEmailIgnoreCase("nuevo@acme.com")).thenReturn(false);
        when(empresaService.obtener(1L)).thenReturn(empresa);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario result = usuarioService.crearColaborador(1L, "Nuevo", "nuevo@acme.com", "pass",
                RolAcceso.SOLO_LECTURA);

        assertEquals("Nuevo", result.getNombre());
        assertEquals(RolAcceso.SOLO_LECTURA, result.getRolAcceso());
        assertTrue(result.isActivo());
    }

    @Test
    @DisplayName("HU-02: email duplicado lanza excepcion")
    void crearColaborador_email_duplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("juan@acme.com")).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> usuarioService.crearColaborador(1L, "Juan", "juan@acme.com", "pass",
                        RolAcceso.EDITOR));
    }

    @Test
    @DisplayName("HU-03: autenticar con credenciales validas retorna usuario")
    void autenticar_exitoso() {
        when(usuarioRepository.findByEmailIgnoreCase("juan@acme.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        Usuario result = usuarioService.autenticar("juan@acme.com", "pass");

        assertEquals(usuario.getId(), result.getId());
    }

    @Test
    @DisplayName("HU-03: autenticar con password incorrecta lanza excepcion")
    void autenticar_password_incorrecta() {
        when(usuarioRepository.findByEmailIgnoreCase("juan@acme.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.autenticar("juan@acme.com", "wrong"));

        assertTrue(ex.getMessage().toLowerCase().contains("correo") ||
                   ex.getMessage().toLowerCase().contains("contrasena"));
    }

    @Test
    @DisplayName("HU-03: autenticar usuario inactivo lanza excepcion")
    void autenticar_usuario_inactivo() {
        usuario.setActivo(false);
        when(usuarioRepository.findByEmailIgnoreCase("juan@acme.com")).thenReturn(Optional.of(usuario));

        assertThrows(ReglaNegocioException.class,
                () -> usuarioService.autenticar("juan@acme.com", "pass"));
    }

    @Test
    @DisplayName("HU-02: desactivar usuario pone activo=false")
    void desactivar_exitoso() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.desactivar(1L, 10L);

        assertFalse(usuario.isActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void actualizar_rol_y_estado() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        Usuario actualizado = usuarioService.actualizar(1L, 10L, RolAcceso.ADMINISTRADOR, false);

        assertEquals(RolAcceso.ADMINISTRADOR, actualizado.getRolAcceso());
        assertFalse(actualizado.isActivo());
    }

    @Test
    @DisplayName("Obtener usuario inexistente lanza RecursoNoEncontrado")
    void obtener_inexistente() {
        when(usuarioRepository.findByIdAndEmpresaId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> usuarioService.obtener(1L, 99L));
    }

    @Test
    @DisplayName("UsuarioServiceImpl_actualizar_ultimoAdministradorCambiaDeRol_lanzaReglaNegocio")
    void UsuarioServiceImpl_actualizar_ultimoAdministradorCambiaDeRol_lanzaReglaNegocio() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.actualizar(1L, 10L, RolAcceso.EDITOR, null));

        assertTrue(ex.getMessage().contains("ultimo administrador"));
        assertEquals(RolAcceso.ADMINISTRADOR, usuario.getRolAcceso());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UsuarioServiceImpl_actualizar_ultimoAdministradorSeDesactiva_lanzaReglaNegocio")
    void UsuarioServiceImpl_actualizar_ultimoAdministradorSeDesactiva_lanzaReglaNegocio() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThrows(ReglaNegocioException.class, () -> usuarioService.actualizar(1L, 10L, null, false));

        assertTrue(usuario.isActivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UsuarioServiceImpl_desactivar_ultimoAdministrador_lanzaReglaNegocio")
    void UsuarioServiceImpl_desactivar_ultimoAdministrador_lanzaReglaNegocio() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThrows(ReglaNegocioException.class, () -> usuarioService.desactivar(1L, 10L));

        assertTrue(usuario.isActivo());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("UsuarioServiceImpl_actualizar_hayOtroAdministradorActivo_permiteDegradar")
    void UsuarioServiceImpl_actualizar_hayOtroAdministradorActivo_permiteDegradar() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR)).thenReturn(2L);
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        Usuario actualizado = usuarioService.actualizar(1L, 10L, RolAcceso.EDITOR, null);

        assertEquals(RolAcceso.EDITOR, actualizado.getRolAcceso());
    }

    @Test
    @DisplayName("UsuarioServiceImpl_actualizar_administradorConservaSuRol_noConsultaElConteo")
    void UsuarioServiceImpl_actualizar_administradorConservaSuRol_noConsultaElConteo() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, 10L, RolAcceso.ADMINISTRADOR, true);

        verify(usuarioRepository, never()).countByEmpresaIdAndRolAccesoAndActivoTrue(any(), any());
    }

    @Test
    @DisplayName("UsuarioServiceImpl_autenticar_correoInexistente_ejecutaMatchesContraHashFicticio")
    void UsuarioServiceImpl_autenticar_correoInexistente_ejecutaMatchesContraHashFicticio() {
        when(usuarioRepository.findByEmailIgnoreCase("nadie@acme.com")).thenReturn(Optional.empty());

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.autenticar("nadie@acme.com", "pass"));

        assertEquals("Correo o contrasena incorrectos.", ex.getMessage());
        verify(passwordEncoder).matches(eq("pass"), startsWith("$2a$10$"));
    }

    @Test
    @DisplayName("UsuarioServiceImpl_autenticar_usuarioInactivo_ejecutaMatchesContraHashFicticio")
    void UsuarioServiceImpl_autenticar_usuarioInactivo_ejecutaMatchesContraHashFicticio() {
        usuario.setActivo(false);
        when(usuarioRepository.findByEmailIgnoreCase("juan@acme.com")).thenReturn(Optional.of(usuario));

        assertThrows(ReglaNegocioException.class, () -> usuarioService.autenticar("juan@acme.com", "pass"));

        verify(passwordEncoder).matches(eq("pass"), startsWith("$2a$10$"));
        verify(passwordEncoder, never()).matches("pass", "hashed");
    }

    @Test
    @DisplayName("UsuarioServiceImpl_autenticar_usuarioValido_ejecutaUnSoloMatchesContraSuHash")
    void UsuarioServiceImpl_autenticar_usuarioValido_ejecutaUnSoloMatchesContraSuHash() {
        when(usuarioRepository.findByEmailIgnoreCase("juan@acme.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        usuarioService.autenticar("juan@acme.com", "pass");

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }
}
