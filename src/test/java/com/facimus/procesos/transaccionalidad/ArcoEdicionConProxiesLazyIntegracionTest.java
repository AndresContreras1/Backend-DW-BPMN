package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

import java.time.LocalDateTime;

/**
 * Issue #42 (configurar FetchType.LAZY en todas las relaciones @ManyToOne): Arco.origen y
 * Arco.destino son LAZY, pero ArcoService.validarConexion hace {@code instanceof Gateway}/
 * {@code instanceof EventoMensaje} sobre ellos (NodoFlujo es SINGLE_TABLE). Un proxy LAZY sin
 * inicializar siempre falla ese instanceof sin importar la subclase real, lo que dejaria pasar
 * silenciosamente una edicion invalida (sin lanzar excepcion) en vez de fallar con un error
 * visible. Por eso ArcoRepository.findByIdAndEmpresaId declara
 * {@code @EntityGraph(attributePaths = {"origen", "destino"})}: fuerza a Hibernate a traer las
 * subclases reales en la misma query. Este test reproduce exactamente el escenario que
 * dependia de eso: editar un arco sin reenviar origenId/destinoId (el caso mas comun, solo
 * cambiar la etiqueta/condicion) debe seguir validando la regla del gateway exclusivo.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:arco-edicion-proxies-it;DB_CLOSE_DELAY=-1")
class ArcoEdicionConProxiesLazyIntegracionTest {

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private ProcesoRepository procesoRepository;
    @Autowired
    private PoolService poolService;
    @Autowired
    private RolProcesoService rolProcesoService;
    @Autowired
    private LaneService laneService;
    @Autowired
    private GatewayService gatewayService;
    @Autowired
    private ActividadService actividadService;
    @Autowired
    private ArcoService arcoService;

    @Test
    @DisplayName("Editar un arco sin reenviar origenId/destinoId sigue exigiendo condicion si el origen es un gateway exclusivo")
    void editar_sin_reenviar_ids_valida_gateway_exclusivo_via_proxy_lazy() {
        Empresa empresa = empresaService.registrar("Acme Arcos", "900999003", "info@acme-arcos.com");
        Long empresaId = empresa.getId();

        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre("Proceso con gateway");
        proceso.setDescripcion("desc");
        proceso.setCategoria("cat");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        Pool pool = poolService.crear(empresaId, proceso.getId(), "Interno", TipoParticipante.EMPRESA, false);
        RolProceso rol = rolProcesoService.crear(empresaId, "Analista", "desc");
        var lane = laneService.crear(empresaId, pool.getId(), "Lane 1", rol);

        Gateway gateway = gatewayService.crear(empresaId, lane.getId(), "Decision",
                TipoGateway.EXCLUSIVO, 0, 0);
        Actividad actividad = actividadService.crear(empresaId, lane.getId(), "Siguiente paso",
                "desc", 100, 0);

        Arco arco = arcoService.crear(empresaId, gateway.getId(), actividad.getId(), "si",
                "monto > 100");
        Long arcoId = arco.getId();

        assertThrows(ReglaNegocioException.class,
                () -> arcoService.editar(empresaId, arcoId, "si", "", null, null));
    }
}
