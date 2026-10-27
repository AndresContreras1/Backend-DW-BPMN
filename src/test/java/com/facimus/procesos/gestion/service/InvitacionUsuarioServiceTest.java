package com.facimus.procesos.gestion.service;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.InvitacionUsuario;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.repository.InvitacionUsuarioRepository;

@ExtendWith(MockitoExtension.class)
class InvitacionUsuarioServiceTest {

    @Mock
    private InvitacionUsuarioRepository invitacionUsuarioRepository;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private InvitacionUsuarioService invitacionUsuarioService;

    @Test
    void crear_rechaza_usuario_existente() {
        when(usuarioService.existePorEmail("nuevo@demo.com")).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> invitacionUsuarioService.crear(1L, "nuevo@demo.com", RolAcceso.EDITOR));
    }

    @Test
    void aceptar_token_valido_crea_usuario_y_marca_usada() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);
        InvitacionUsuario invitacion = new InvitacionUsuario();
        invitacion.setEmpresa(empresa);
        invitacion.setEmail("nuevo@demo.com");
        invitacion.setRolAcceso(RolAcceso.EDITOR);
        invitacion.setToken("token");
        invitacion.setFechaExpiracion(LocalDateTime.now().plusHours(2));

        Usuario usuario = new Usuario();
        usuario.setId(4L);

        when(invitacionUsuarioRepository.findByTokenAndUsadaFalse("token"))
                .thenReturn(Optional.of(invitacion));
        when(usuarioService.crearColaborador(1L, "Ana", "nuevo@demo.com", "password123", RolAcceso.EDITOR))
                .thenReturn(usuario);

        Usuario resultado = invitacionUsuarioService.aceptar("token", "Ana", "password123");

        assertSame(usuario, resultado);
        assertTrue(invitacion.isUsada());
        verify(invitacionUsuarioRepository).save(invitacion);
    }

    @Test
    void aceptar_token_expirado_falla() {
        InvitacionUsuario invitacion = new InvitacionUsuario();
        invitacion.setFechaExpiracion(LocalDateTime.now().minusMinutes(1));
        when(invitacionUsuarioRepository.findByTokenAndUsadaFalse("expirado"))
                .thenReturn(Optional.of(invitacion));

        assertThrows(ReglaNegocioException.class,
                () -> invitacionUsuarioService.aceptar("expirado", "Ana", "password123"));
    }

    @Test
    void aceptar_token_inexistente_falla() {
        when(invitacionUsuarioRepository.findByTokenAndUsadaFalse("x")).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> invitacionUsuarioService.aceptar("x", "Ana", "password123"));
    }


    @Test
    void crear_limpia_invitaciones_expiradas() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);

        when(usuarioService.existePorEmail("nuevo@demo.com")).thenReturn(false);
        when(invitacionUsuarioRepository
                .existsByEmpresaIdAndEmailIgnoreCaseAndUsadaFalseAndFechaExpiracionAfter(
                        any(), any(), any())).thenReturn(false);
        when(empresaService.obtener(1L)).thenReturn(empresa);
        when(invitacionUsuarioRepository.save(any(InvitacionUsuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        invitacionUsuarioService.crear(1L, "nuevo@demo.com", RolAcceso.EDITOR);

        verify(invitacionUsuarioRepository)
                .deleteAllByFechaExpiracionBeforeAndUsadaFalse(any());
    }
}
