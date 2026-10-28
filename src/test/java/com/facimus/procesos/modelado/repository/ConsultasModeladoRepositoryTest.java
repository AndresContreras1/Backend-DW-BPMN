package com.facimus.procesos.modelado.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.EventoMensaje;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoEventoMensaje;
import com.facimus.procesos.modelado.model.TipoParticipante;

/** Consultas JPQL nuevas de modelado: siempre acotadas por empresa y por estado activo. */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:consultas-modelado-repo;DB_CLOSE_DELAY=-1")
// Base propia con pool (no la embebida por defecto): H2 2.4 falla al evaluar el check "in (...)" de nodos_flujo
// cuando la sesion que creo el esquema ya se cerro.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ConsultasModeladoRepositoryTest {

    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private ProcesoRepository procesoRepository;
    @Autowired private RolProcesoRepository rolProcesoRepository;
    @Autowired private PoolRepository poolRepository;
    @Autowired private LaneRepository laneRepository;
    @Autowired private NodoFlujoRepository nodoFlujoRepository;
    @Autowired private MensajeRepository mensajeRepository;

    private Empresa empresaA;
    private Empresa empresaB;
    private Proceso procesoActivo;
    private Proceso procesoInactivo;
    private Proceso procesoDeB;
    private Pool poolActivo;
    private Pool poolInactivo;
    private Pool poolDeB;
    private Lane laneActivo;
    private Lane laneInactivo;
    private Lane laneDeB;

    @BeforeEach
    void datos() {
        empresaA = empresaRepository.save(empresa("Empresa A", "900100001-1"));
        empresaB = empresaRepository.save(empresa("Empresa B", "900100002-2"));
        procesoActivo = procesoRepository.save(proceso(empresaA, "Compras", true));
        procesoInactivo = procesoRepository.save(proceso(empresaA, "Archivo", false));
        procesoDeB = procesoRepository.save(proceso(empresaB, "Compras", true));
        poolActivo = poolRepository.save(pool(empresaA, procesoActivo, "Empresa"));
        poolInactivo = poolRepository.save(pool(empresaA, procesoInactivo, "Empresa"));
        poolDeB = poolRepository.save(pool(empresaB, procesoDeB, "Empresa"));
    }

    @Test
    @DisplayName("LaneRepository_procesosActivosPorRoles_agrupaPorRolSoloConProcesosActivosDeLaEmpresa")
    void LaneRepository_procesosActivosPorRoles_agrupaPorRolSoloConProcesosActivosDeLaEmpresa() {
        RolProceso analista = rolProcesoRepository.save(rol(empresaA, "Analista"));
        RolProceso auditor = rolProcesoRepository.save(rol(empresaA, "Auditor"));
        RolProceso deB = rolProcesoRepository.save(rol(empresaB, "Analista"));
        laneActivo = laneRepository.save(lane(empresaA, poolActivo, analista, 0));
        laneRepository.save(lane(empresaA, poolActivo, analista, 1)); // misma pareja rol/proceso: una sola fila
        laneInactivo = laneRepository.save(lane(empresaA, poolInactivo, analista, 0));
        laneRepository.save(lane(empresaA, poolInactivo, auditor, 1));
        laneDeB = laneRepository.save(lane(empresaB, poolDeB, deB, 0));

        List<Object[]> filas = laneRepository.procesosActivosPorRoles(
                List.of(analista.getId(), auditor.getId(), deB.getId()), empresaA.getId());

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0)[0]).isEqualTo(analista.getId());
        assertThat(filas.get(0)[1]).isEqualTo(procesoActivo.getId());
        assertThat(filas.get(0)[2]).isEqualTo("Compras");
        assertThat(laneActivo.getId()).isNotNull();
        assertThat(laneInactivo.getId()).isNotNull();
        assertThat(laneDeB.getId()).isNotNull();
    }

    @Test
    void LaneRepository_procesosActivosPorRoles_otraEmpresaNoVeLosRoles() {
        RolProceso analista = rolProcesoRepository.save(rol(empresaA, "Analista"));
        laneRepository.save(lane(empresaA, poolActivo, analista, 0));

        assertThat(laneRepository.procesosActivosPorRoles(List.of(analista.getId()), empresaB.getId())).isEmpty();
    }

    @Test
    void NodoFlujoRepository_buscarActivosPorNombre_ignoraMayusculasInactivosOtroProcesoYOtraEmpresa() {
        RolProceso rol = rolProcesoRepository.save(rol(empresaA, "Analista"));
        Lane lane = laneRepository.save(lane(empresaA, poolActivo, rol, 0));
        Lane laneOtroProceso = laneRepository.save(lane(empresaA, poolInactivo, rol, 0));
        RolProceso rolB = rolProcesoRepository.save(rol(empresaB, "Analista"));
        Lane laneB = laneRepository.save(lane(empresaB, poolDeB, rolB, 0));

        Actividad actividad = nodoFlujoRepository.save(actividad(empresaA, lane, "Orden", true));
        EventoMensaje evento = (EventoMensaje) nodoFlujoRepository.save(evento(empresaA, lane, "ORDEN", true));
        nodoFlujoRepository.save(actividad(empresaA, lane, "orden", false));
        nodoFlujoRepository.save(actividad(empresaA, laneOtroProceso, "Orden", true));
        nodoFlujoRepository.save(actividad(empresaB, laneB, "Orden", true));

        var nodos = nodoFlujoRepository.buscarActivosPorNombre("orden", procesoActivo.getId(), empresaA.getId());

        assertThat(nodos).extracting(n -> n.getId()).containsExactlyInAnyOrder(actividad.getId(), evento.getId());
    }

    @Test
    void MensajeRepository_findActivosPorEvento_devuelveMensajesActivosDeLaEmpresaPorThrowOCatch() {
        RolProceso rol = rolProcesoRepository.save(rol(empresaA, "Analista"));
        Lane lane = laneRepository.save(lane(empresaA, poolActivo, rol, 0));
        Pool otroPool = poolRepository.save(pool(empresaA, procesoActivo, "Cliente"));
        EventoMensaje lanza = (EventoMensaje) nodoFlujoRepository.save(evento(empresaA, lane, "Orden", true));
        EventoMensaje recibe = (EventoMensaje) nodoFlujoRepository.save(evento(empresaA, lane, "Orden recibida", true));

        Mensaje comoThrow = mensajeRepository.save(mensaje(empresaA, "B mensaje", poolActivo, otroPool, lanza, null, true));
        Mensaje comoCatch = mensajeRepository.save(mensaje(empresaA, "A mensaje", otroPool, poolActivo, null, lanza, true));
        mensajeRepository.save(mensaje(empresaA, "Eliminado", poolActivo, otroPool, lanza, null, false));
        mensajeRepository.save(mensaje(empresaA, "Otro evento", poolActivo, otroPool, recibe, null, true));

        assertThat(mensajeRepository.findActivosPorEvento(lanza.getId(), empresaA.getId()))
                .extracting(Mensaje::getId).containsExactly(comoCatch.getId(), comoThrow.getId());
        assertThat(mensajeRepository.findActivosPorEvento(lanza.getId(), empresaB.getId())).isEmpty();
    }

    // ---- fixtures ----

    private static Empresa empresa(String nombre, String nit) {
        Empresa e = new Empresa();
        e.setNombre(nombre);
        e.setNit(nit);
        e.setCorreoContacto("contacto@" + nit + ".com");
        e.setFechaRegistro(LocalDate.now());
        return e;
    }

    private static Proceso proceso(Empresa empresa, String nombre, boolean activo) {
        Proceso p = new Proceso();
        p.setEmpresa(empresa);
        p.setNombre(nombre);
        p.setDescripcion("Descripcion de " + nombre);
        p.setCategoria("Categoria");
        p.setEstado(EstadoProceso.BORRADOR);
        p.setActivo(activo);
        p.setFechaCreacion(LocalDateTime.now());
        p.setFechaModificacion(LocalDateTime.now());
        return p;
    }

    private static Pool pool(Empresa empresa, Proceso proceso, String nombre) {
        Pool p = new Pool();
        p.setEmpresa(empresa);
        p.setProceso(proceso);
        p.setNombre(nombre);
        p.setTipoParticipante(TipoParticipante.EMPRESA);
        p.setOrden(0);
        return p;
    }

    private static RolProceso rol(Empresa empresa, String nombre) {
        RolProceso r = new RolProceso();
        r.setEmpresa(empresa);
        r.setNombre(nombre);
        r.setActivo(true);
        return r;
    }

    private static Lane lane(Empresa empresa, Pool pool, RolProceso rol, int orden) {
        Lane l = new Lane();
        l.setEmpresa(empresa);
        l.setPool(pool);
        l.setRolProceso(rol);
        l.setNombre(rol.getNombre());
        l.setOrden(orden);
        return l;
    }

    private static Actividad actividad(Empresa empresa, Lane lane, String nombre, boolean activo) {
        Actividad a = new Actividad();
        a.setEmpresa(empresa);
        a.setLane(lane);
        a.setNombre(nombre);
        a.setActivo(activo);
        return a;
    }

    private static EventoMensaje evento(Empresa empresa, Lane lane, String nombre, boolean activo) {
        EventoMensaje e = new EventoMensaje();
        e.setEmpresa(empresa);
        e.setLane(lane);
        e.setNombre(nombre);
        e.setTipoEvento(TipoEventoMensaje.THROW);
        e.setContenido("contenido");
        e.setActivo(activo);
        return e;
    }

    private static Mensaje mensaje(Empresa empresa, String nombre, Pool origen, Pool destino, EventoMensaje lanza,
            EventoMensaje recibe, boolean activo) {
        Mensaje m = new Mensaje();
        m.setEmpresa(empresa);
        m.setProceso(origen.getProceso());
        m.setNombre(nombre);
        m.setContenido("contenido");
        m.setPoolOrigen(origen);
        m.setPoolDestino(destino);
        m.setEventoThrow(lanza);
        m.setEventoCatch(recibe);
        m.setActivo(activo);
        return m;
    }
}
