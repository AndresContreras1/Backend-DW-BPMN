package com.facimus.procesos.transaccionalidad;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.EmpresaOrquestadorService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * Tests de cumplimiento que verifican las invariantes de eliminacion logica,
 * bloqueo de modelado en procesos inactivos y unicidad de correo.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:invariantes-modelado-it;DB_CLOSE_DELAY=-1")
class InvariantesModeladoIntegracionTest {

    @Autowired private EmpresaService empresaService;
    @Autowired private EmpresaOrquestadorService empresaOrquestadorService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private ProcesoService procesoService;
    @Autowired private ProcesoRepository procesoRepository;
    @Autowired private PoolService poolService;
    @Autowired private PoolRepository poolRepository;
    @Autowired private LaneService laneService;
    @Autowired private RolProcesoService rolProcesoService;
    @Autowired private ActividadService actividadService;

    // ── 3.1 Eliminacion logica mantiene registro con activo=false ──

    @Test
    @DisplayName("Pool eliminado se borra fisicamente de la BD")
    void pool_eliminado_se_borra_fisicamente() {
        Empresa empresa = empresaService.registrar("Acme Elim Pool", "900111001", "elim-pool@acme.com");
        Proceso proceso = crearProceso(empresa, "Proceso elim pool");
        Pool pool = poolService.crear(empresa.getId(), proceso.getId(), "Pool temp", TipoParticipante.EMPRESA, false);

        poolService.eliminarRegistro(pool);

        assertTrue(poolRepository.findByIdAndEmpresaId(pool.getId(), empresa.getId()).isEmpty());
    }

    @Test
    @DisplayName("Actividad eliminada queda en BD pero no es accesible (baja logica)")
    void actividad_eliminada_queda_inaccesible() {
        Empresa empresa = empresaService.registrar("Acme Elim Act", "900111002", "elim-act@acme.com");
        Proceso proceso = crearProceso(empresa, "Proceso elim actividad");
        Pool pool = poolService.crear(empresa.getId(), proceso.getId(), "Pool", TipoParticipante.EMPRESA, false);
        RolProceso rol = rolProcesoService.crear(empresa.getId(), "Rol", "desc");
        Lane lane = laneService.crear(empresa.getId(), pool.getId(), "Lane", rol);
        Actividad actividad = actividadService.crear(empresa.getId(), lane.getId(), "Tarea A", "desc", 0, 0);

        actividadService.eliminar(empresa.getId(), actividad.getId());

        assertThrows(RecursoNoEncontradoException.class,
                () -> actividadService.obtener(empresa.getId(), actividad.getId()));
    }

    // ── 3.2 No se puede crear/editar en proceso inactivo ──

    @Test
    @DisplayName("No se puede crear pool en proceso inactivo")
    void crear_pool_en_proceso_inactivo_falla() {
        Empresa empresa = empresaOrquestadorService.registrarConAdministrador(
                "Acme Inact", "900222001", "inact@acme.com", "Admin", "admin-inact@acme.com", "pass123");
        Proceso proceso = crearProceso(empresa, "Proceso inactivo");
        Usuario admin = usuarioService.listarPorEmpresa(empresa.getId()).get(0);
        procesoService.eliminarLogico(empresa.getId(), proceso.getId(), admin.getId());

        assertThrows(RecursoNoEncontradoException.class,
                () -> poolService.crear(empresa.getId(), proceso.getId(), "Pool", TipoParticipante.EMPRESA, false));
    }

    @Test
    @DisplayName("No se puede editar pool en proceso inactivo")
    void editar_pool_en_proceso_inactivo_falla() {
        Empresa empresa = empresaOrquestadorService.registrarConAdministrador(
                "Acme Inact2", "900222002", "inact2@acme.com", "Admin", "admin-inact2@acme.com", "pass123");
        Proceso proceso = crearProceso(empresa, "Proceso inactivo edit");
        Pool pool = poolService.crear(empresa.getId(), proceso.getId(), "Pool", TipoParticipante.EMPRESA, false);
        Usuario admin = usuarioService.listarPorEmpresa(empresa.getId()).get(0);
        procesoService.eliminarLogico(empresa.getId(), proceso.getId(), admin.getId());

        assertThrows(ReglaNegocioException.class,
                () -> poolService.editar(empresa.getId(), pool.getId(), "Pool editado", TipoParticipante.EMPRESA));
    }

    @Test
    @DisplayName("No se puede crear lane en proceso inactivo")
    void crear_lane_en_proceso_inactivo_falla() {
        Empresa empresa = empresaOrquestadorService.registrarConAdministrador(
                "Acme Inact3", "900222003", "inact3@acme.com", "Admin", "admin-inact3@acme.com", "pass123");
        Proceso proceso = crearProceso(empresa, "Proceso inactivo lane");
        Pool pool = poolService.crear(empresa.getId(), proceso.getId(), "Pool", TipoParticipante.EMPRESA, false);
        RolProceso rol = rolProcesoService.crear(empresa.getId(), "Rol", "desc");
        Usuario admin = usuarioService.listarPorEmpresa(empresa.getId()).get(0);
        procesoService.eliminarLogico(empresa.getId(), proceso.getId(), admin.getId());

        assertThrows(ReglaNegocioException.class,
                () -> laneService.crear(empresa.getId(), pool.getId(), "Lane", rol));
    }

    @Test
    @DisplayName("No se puede crear actividad en proceso inactivo")
    void crear_actividad_en_proceso_inactivo_falla() {
        Empresa empresa = empresaOrquestadorService.registrarConAdministrador(
                "Acme Inact4", "900222004", "inact4@acme.com", "Admin", "admin-inact4@acme.com", "pass123");
        Proceso proceso = crearProceso(empresa, "Proceso inactivo act");
        Pool pool = poolService.crear(empresa.getId(), proceso.getId(), "Pool", TipoParticipante.EMPRESA, false);
        RolProceso rol = rolProcesoService.crear(empresa.getId(), "Rol", "desc");
        Lane lane = laneService.crear(empresa.getId(), pool.getId(), "Lane", rol);
        Usuario admin = usuarioService.listarPorEmpresa(empresa.getId()).get(0);
        procesoService.eliminarLogico(empresa.getId(), proceso.getId(), admin.getId());

        assertThrows(ReglaNegocioException.class,
                () -> actividadService.crear(empresa.getId(), lane.getId(), "Tarea", "desc", 0, 0));
    }

    // ── 3.3 Email unico en todo el sistema ──

    @Test
    @DisplayName("No se puede crear colaborador con email ya registrado en otra empresa")
    void colaborador_email_duplicado_en_otra_empresa() {
        Empresa empresaA = empresaService.registrar("Emp Col A", "900333003", "admin-col-a@sistema.com");
        Empresa empresaB = empresaService.registrar("Emp Col B", "900333004", "admin-col-b@sistema.com");
        usuarioService.crearColaborador(empresaA.getId(), "Juan", "juan-dup@sistema.com", "pass123", RolAcceso.EDITOR);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.crearColaborador(empresaB.getId(), "Juan B", "juan-dup@sistema.com",
                        "pass123", RolAcceso.EDITOR));

        assertTrue(ex.getMessage().toLowerCase().contains("correo"));
    }

    @Test
    @DisplayName("No se puede registrar empresa con admin cuyo email ya existe")
    void registrar_empresa_con_email_admin_duplicado() {
        empresaOrquestadorService.registrarConAdministrador(
                "Emp Dup A", "900333005", "emp-dup-a@sistema.com", "Admin A", "admin-dup@sistema.com", "pass123");

        assertThrows(ReglaNegocioException.class,
                () -> empresaOrquestadorService.registrarConAdministrador(
                        "Emp Dup B", "900333006", "emp-dup-b@sistema.com", "Admin B", "admin-dup@sistema.com", "pass123"));
    }

    // ── Helpers ──

    private Proceso crearProceso(Empresa empresa, String nombre) {
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre(nombre);
        proceso.setDescripcion("desc");
        proceso.setCategoria("cat");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(LocalDateTime.now());
        proceso.setFechaModificacion(LocalDateTime.now());
        return procesoRepository.save(proceso);
    }
}
