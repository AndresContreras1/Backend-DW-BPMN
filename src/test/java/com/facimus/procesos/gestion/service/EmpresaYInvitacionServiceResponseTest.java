package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.controller.dto.AceptarInvitacionRequest;
import com.facimus.procesos.gestion.controller.dto.EmpresaResponse;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioRequest;
import com.facimus.procesos.gestion.controller.dto.InvitacionUsuarioResponse;
import com.facimus.procesos.gestion.controller.dto.RegistroEmpresaRequest;
import com.facimus.procesos.gestion.controller.dto.UsuarioResponse;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.InvitacionUsuario;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.InvitacionUsuarioRepository;

/** Metodos que devuelven Response del orquestador de empresa y del service de invitaciones. */
@ExtendWith(MockitoExtension.class)
class EmpresaYInvitacionServiceResponseTest {

    @Mock
    private EmpresaService empresaService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private InvitacionUsuarioRepository invitacionUsuarioRepository;

    private EmpresaOrquestadorServiceImpl orquestador;
    private InvitacionUsuarioServiceImpl invitaciones;

    @BeforeEach
    void setUp() {
        var mapper = new ModelMapperConfig().modelMapper();
        orquestador = spy(new EmpresaOrquestadorServiceImpl(empresaService, usuarioService, mapper));
        invitaciones = spy(new InvitacionUsuarioServiceImpl(invitacionUsuarioRepository, empresaService,
                usuarioService, mapper));
    }

    @Test
    void EmpresaOrquestadorServiceImpl_registrarConAdministradorRequest_datosValidos_devuelveEmpresaResponse() {
        Empresa empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme Corp");
        empresa.setNit("900123456");
        empresa.setCorreoContacto("info@acme.com");
        empresa.setFechaRegistro(LocalDate.of(2026, 1, 15));
        doReturn(empresa).when(orquestador).registrarConAdministrador("Acme Corp", "900123456", "info@acme.com",
                "Admin", "admin@acme.com", "secret123");

        EmpresaResponse respuesta = orquestador.registrarConAdministrador(new RegistroEmpresaRequest("Acme Corp",
                "900123456", "info@acme.com", "Admin", "admin@acme.com", "secret123"));

        assertThat(respuesta).isEqualTo(new EmpresaResponse(1L, "Acme Corp", "900123456", "info@acme.com",
                LocalDate.of(2026, 1, 15)));
    }

    @Test
    void InvitacionUsuarioServiceImpl_crearRequest_datosValidos_devuelveInvitacionResponse() {
        LocalDateTime expira = LocalDateTime.of(2026, 1, 17, 10, 0);
        InvitacionUsuario invitacion = new InvitacionUsuario();
        invitacion.setId(7L);
        invitacion.setEmail("nuevo@demo.com");
        invitacion.setRolAcceso(RolAcceso.EDITOR);
        invitacion.setToken("abc");
        invitacion.setFechaExpiracion(expira);
        doReturn(invitacion).when(invitaciones).crear(1L, "nuevo@demo.com", RolAcceso.EDITOR);

        InvitacionUsuarioResponse respuesta = invitaciones.crear(1L,
                new InvitacionUsuarioRequest("nuevo@demo.com", RolAcceso.EDITOR));

        assertThat(respuesta).isEqualTo(
                new InvitacionUsuarioResponse(7L, "nuevo@demo.com", RolAcceso.EDITOR, "abc", expira));
    }

    @Test
    void InvitacionUsuarioServiceImpl_aceptarRequest_tokenValido_devuelveUsuarioResponse() {
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setNombre("Ana");
        usuario.setEmail("ana@demo.com");
        usuario.setRolAcceso(RolAcceso.EDITOR);
        usuario.setActivo(true);
        doReturn(usuario).when(invitaciones).aceptar("token-ok", "Ana", "password123");

        UsuarioResponse respuesta = invitaciones.aceptar("token-ok",
                new AceptarInvitacionRequest("Ana", "password123"));

        assertThat(respuesta).isEqualTo(new UsuarioResponse(3L, "Ana", "ana@demo.com", RolAcceso.EDITOR, true));
    }
}
