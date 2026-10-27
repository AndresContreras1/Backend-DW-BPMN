package com.facimus.procesos.modelado.service;

import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.security.ApiPrincipal;

@ExtendWith(MockitoExtension.class)
class AuditoriaModeladoServiceTest {

    @Mock
    private UsuarioService usuarioService;
    @Mock
    private HistorialCambioService historialCambioService;

    @InjectMocks
    private AuditoriaModeladoService service;

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sin_autenticacion_no_registra() {
        Proceso proceso = proceso(1L);

        service.registrar(proceso, "Cambio");

        verifyNoInteractions(usuarioService, historialCambioService);
    }

    @Test
    void principal_de_otra_empresa_no_registra() {
        Proceso proceso = proceso(1L);
        ApiPrincipal principal = new ApiPrincipal(7L, 2L, RolAcceso.EDITOR, "user@demo.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));

        service.registrar(proceso, "Cambio");

        verifyNoInteractions(usuarioService, historialCambioService);
    }

    @Test
    void usuario_existente_registra_historial() {
        Proceso proceso = proceso(1L);
        Usuario usuario = new Usuario();
        usuario.setId(7L);

        ApiPrincipal principal = new ApiPrincipal(7L, 1L, RolAcceso.EDITOR, "user@demo.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        when(usuarioService.buscar(1L, 7L)).thenReturn(Optional.of(usuario));

        service.registrar(proceso, "Actividad creada");

        verify(historialCambioService).registrar(proceso, usuario, "Actividad creada");
    }

    @Test
    void usuario_no_encontrado_no_registra_historial() {
        Proceso proceso = proceso(1L);

        ApiPrincipal principal = new ApiPrincipal(7L, 1L, RolAcceso.EDITOR, "user@demo.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        when(usuarioService.buscar(1L, 7L)).thenReturn(Optional.empty());

        service.registrar(proceso, "Cambio");

        verifyNoInteractions(historialCambioService);
    }

    private Proceso proceso(Long empresaId) {
        Empresa empresa = new Empresa();
        empresa.setId(empresaId);
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        return proceso;
    }
}
