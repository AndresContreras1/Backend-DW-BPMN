package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * Criterio de aceptacion explicito de la review del PR #37: las reglas de negocio que se
 * movieron a eventos de dominio (ver ProcesoService.cambiarEstado y PoolService.editar) deben
 * cumplirse aunque el Service se llame directamente, sin pasar por ningun Controller ni
 * orquestador. No hay mocks en esta clase: todo el grafo de beans es real (contexto Spring +
 * H2 real), exactamente para probar que el listener sincrono se ejecuta sin necesidad de que
 * ProcesoService/PoolService dependan de ValidacionModeloService/LaneService directamente.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:invariantes-directas-it;DB_CLOSE_DELAY=-1")
class InvariantesDirectasEnServicioTest {

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private ProcesoService procesoService;
    @Autowired
    private ProcesoRepository procesoRepository;
    @Autowired
    private PoolService poolService;
    @Autowired
    private PoolRepository poolRepository;
    @Autowired
    private LaneService laneService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private GatewayService gatewayService;

    @Test
    @DisplayName("ProcesoService.cambiarEstado llamado directamente rechaza publicar un modelo BPMN invalido")
    void cambiarEstado_directo_rechaza_modelo_invalido() {
        Empresa empresa = empresaService.registrar("Acme Directo", "900999001", "info@acme-directo.com");
        Usuario admin = usuarioService.crearColaborador(empresa.getId(), "Admin", "admin@acme-directo.com",
                "secret123", RolAcceso.ADMINISTRADOR);

        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre("Proceso invalido");
        proceso.setDescripcion("desc");
        proceso.setCategoria("cat");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        Pool pool = poolService.crear(empresa.getId(), proceso.getId(), "Interno", TipoParticipante.EMPRESA, false);
        RolProceso rol = rolProcesoService.crear(empresa.getId(), "Analista", "desc");
        var lane = laneService.crear(empresa.getId(), pool.getId(), "Lane 1", rol);
        // Gateway divergente (0 entrantes) con menos de dos salidas: el modelo queda invalido
        // segun ValidacionModeloService.validarNodosYGateways, sin necesidad de arcos.
        gatewayService.crear(empresa.getId(), lane.getId(), "Decision", TipoGateway.EXCLUSIVO, 0, 0);

        Long procesoId = proceso.getId();
        assertThrows(ReglaNegocioException.class, () -> procesoService.cambiarEstado(
                empresa.getId(), procesoId, admin.getId(), EstadoProceso.PUBLICADO));

        Proceso enBd = procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresa.getId()).orElseThrow();
        assertEquals(EstadoProceso.BORRADOR, enBd.getEstado());
    }

    @Test
    @DisplayName("PoolService.editar llamado directamente rechaza marcar caja negra un pool con lanes")
    void editar_directo_rechaza_cajaNegra_con_lanes() {
        Empresa empresa = empresaService.registrar("Acme Directo Pool", "900999002", "info@acme-directo-pool.com");

        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre("Proceso con lanes");
        proceso.setDescripcion("desc");
        proceso.setCategoria("cat");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        Pool pool = poolService.crear(empresa.getId(), proceso.getId(), "Interno", TipoParticipante.EMPRESA, false);
        RolProceso rol = rolProcesoService.crear(empresa.getId(), "Analista", "desc");
        laneService.crear(empresa.getId(), pool.getId(), "Lane 1", rol);

        Long poolId = pool.getId();
        assertThrows(ReglaNegocioException.class, () -> poolService.editar(
                empresa.getId(), poolId, "Interno", TipoParticipante.EMPRESA, true));

        Pool enBd = poolRepository.findByIdAndEmpresaId(poolId, empresa.getId()).orElseThrow();
        assertFalse(enBd.isCajaNegra());
    }
}
