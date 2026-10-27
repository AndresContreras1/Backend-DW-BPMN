package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.EmpresaOrquestadorService;
import com.facimus.procesos.gestion.service.UsuarioService;

/**
 * Prueba de integracion pedida por la review del PR #37 (hallazgo bloqueante en
 * EmpresaController): si falla la creacion del administrador, la empresa no debe quedar
 * persistida sin su administrador. Usa el contexto Spring real y la base H2 real; solo
 * UsuarioService se reemplaza por un mock para forzar el fallo del segundo paso de forma
 * deterministica.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:registro-empresa-it;DB_CLOSE_DELAY=-1")
class RegistroEmpresaTransaccionalTest {

    @Autowired
    private EmpresaOrquestadorService empresaOrquestadorService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @MockitoBean
    private UsuarioService usuarioService;

    @Test
    @DisplayName("Si falla crear el administrador, la empresa no queda persistida (rollback real)")
    void registrarConAdministrador_revierte_la_empresa_si_falla_el_administrador() {
        when(usuarioService.crearColaborador(any(), anyString(), anyString(), anyString(), eq(RolAcceso.ADMINISTRADOR)))
                .thenThrow(new RuntimeException("fallo forzado"));

        assertThrows(RuntimeException.class, () -> empresaOrquestadorService.registrarConAdministrador(
                "Acme Transaccional", "900555555", "info@acme-transaccional.com", "Admin",
                "admin@acme-transaccional.com", "secret123"));

        assertTrue(empresaRepository.findByNit("900555555").isEmpty(),
                "La empresa no debio quedar persistida si su administrador no se pudo crear");
    }
}
