package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;

/**
 * El caso de uso HU-01 (registrar empresa + administrador inicial) debe quedar atomico: si
 * UsuarioService.crearColaborador falla, la review de PR #37 exige que la Empresa no quede
 * persistida sin administrador. A nivel unitario no se puede probar el rollback real (eso lo
 * cubre la prueba de integracion), pero si se puede confirmar el orden de llamadas y que una
 * excepcion en el segundo paso se propaga sin swallow.
 */
@ExtendWith(MockitoExtension.class)
class EmpresaOrquestadorServiceTest {

    @Mock
    private EmpresaService empresaService;
    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private EmpresaOrquestadorService empresaOrquestadorService;

    private Empresa empresa;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme Corp");
    }

    @Test
    @DisplayName("Registra la empresa y despues crea el administrador, en ese orden")
    void registrarConAdministrador_llama_en_orden() {
        when(empresaService.registrar("Acme Corp", "900123456", "info@acme.com")).thenReturn(empresa);

        Empresa resultado = empresaOrquestadorService.registrarConAdministrador("Acme Corp", "900123456",
                "info@acme.com", "Admin", "admin@acme.com", "secret123");

        assertSame(empresa, resultado);
        InOrder orden = inOrder(empresaService, usuarioService);
        orden.verify(empresaService).registrar("Acme Corp", "900123456", "info@acme.com");
        orden.verify(usuarioService).crearColaborador(eq(1L), eq("Admin"), eq("admin@acme.com"),
                eq("secret123"), eq(RolAcceso.ADMINISTRADOR));
    }

    @Test
    @DisplayName("Si falla la creacion del administrador, la excepcion se propaga")
    void registrarConAdministrador_propaga_excepcion_del_segundo_paso() {
        when(empresaService.registrar(anyString(), anyString(), anyString())).thenReturn(empresa);
        when(usuarioService.crearColaborador(eq(1L), anyString(), anyString(), anyString(), eq(RolAcceso.ADMINISTRADOR)))
                .thenThrow(new ReglaNegocioException("correo ya usado"));

        assertThrows(ReglaNegocioException.class, () -> empresaOrquestadorService
                .registrarConAdministrador("Acme Corp", "900123456", "info@acme.com", "Admin",
                        "admin@acme.com", "secret123"));
    }

    @Test
    @DisplayName("Si la empresa ya existe (NIT duplicado), no se intenta crear el administrador")
    void registrarConAdministrador_no_crea_admin_si_falla_el_registro() {
        when(empresaService.registrar(anyString(), anyString(), anyString()))
                .thenThrow(new ReglaNegocioException("NIT duplicado"));

        assertThrows(ReglaNegocioException.class, () -> empresaOrquestadorService
                .registrarConAdministrador("Acme Corp", "900123456", "info@acme.com", "Admin",
                        "admin@acme.com", "secret123"));

        verify(usuarioService, never()).crearColaborador(anyLong(), anyString(), anyString(), anyString(),
                eq(RolAcceso.ADMINISTRADOR));
    }
}
