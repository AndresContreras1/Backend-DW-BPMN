package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoOrquestadorService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * Prueba de integracion pedida por la review del PR #37 (hallazgo bloqueante en
 * ProcesoController): si falla la creacion del pool inicial, el proceso no debe quedar
 * persistido sin su estructura inicial. Usa el contexto Spring real y la base H2 real; solo
 * PoolService se reemplaza por un mock para forzar el fallo del segundo paso de forma
 * deterministica (EmpresaService y UsuarioService siguen siendo los reales, para construir el
 * fixture).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:creacion-proceso-it;DB_CLOSE_DELAY=-1")
class CreacionProcesoTransaccionalTest {

    @Autowired
    private ProcesoOrquestadorService procesoOrquestadorService;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcesoRepository procesoRepository;

    @MockitoBean
    private PoolService poolService;

    @Test
    @DisplayName("Si falla crear el pool inicial, el proceso no queda persistido (rollback real)")
    void crearConPoolInicial_revierte_el_proceso_si_falla_el_pool() {
        Empresa empresa = empresaService.registrar("Acme Procesos", "900666666", "info@acme-procesos.com");
        Usuario admin = usuarioService.crearColaborador(empresa.getId(), "Admin", "admin@acme-procesos.com",
                "secret123", RolAcceso.ADMINISTRADOR);

        when(poolService.crear(any(), any(), anyString(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("fallo forzado"));

        assertThrows(RuntimeException.class, () -> procesoOrquestadorService.crearConPoolInicial(
                empresa.getId(), admin.getId(), "Proceso Atomico", "desc", "cat"));

        assertFalse(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(
                        empresa.getId(), "Proceso Atomico"),
                "El proceso no debio quedar persistido si su pool inicial no se pudo crear");
    }
}
