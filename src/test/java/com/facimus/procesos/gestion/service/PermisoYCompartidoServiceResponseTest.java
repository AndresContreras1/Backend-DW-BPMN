package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.controller.dto.CompartirProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.PermisoEstructuraRequest;
import com.facimus.procesos.gestion.controller.dto.PermisoEstructuraResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoCompartidoResponse;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.PermisoEstructura;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.PermisoEstructuraRepository;
import com.facimus.procesos.gestion.repository.ProcesoCompartidoRepository;
import com.facimus.procesos.gestion.service.dto.ProcesoCompartidoDetalle;

/** Metodos que devuelven Response de PermisoEstructuraService y ProcesoCompartidoService. */
@ExtendWith(MockitoExtension.class)
class PermisoYCompartidoServiceResponseTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 3, 1, 9, 30);

    @Mock
    private PermisoEstructuraRepository permisoEstructuraRepository;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private ProcesoCompartidoRepository procesoCompartidoRepository;
    @Mock
    private ProcesoService procesoService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private ProcesoDiagramaService procesoDiagramaService;
    @Mock
    private HistorialCambioService historialCambioService;

    private PermisoEstructuraServiceImpl permisos;
    private ProcesoCompartidoServiceImpl compartidos;
    private ProcesoCompartido compartido;

    @BeforeEach
    void setUp() {
        var mapper = new ModelMapperConfig().modelMapper();
        permisos = spy(new PermisoEstructuraServiceImpl(permisoEstructuraRepository, empresaService, mapper));
        compartidos = spy(new ProcesoCompartidoServiceImpl(procesoCompartidoRepository, procesoService,
                empresaService, usuarioService, procesoDiagramaService, historialCambioService, mapper));

        Empresa propietaria = new Empresa();
        propietaria.setId(1L);
        propietaria.setNombre("Acme");
        Empresa invitada = new Empresa();
        invitada.setId(2L);
        invitada.setNombre("Aliada");
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        proceso.setEmpresa(propietaria);
        proceso.setNombre("Compras");
        proceso.setDescripcion("Proceso");
        proceso.setCategoria("Operativo");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(FECHA);
        proceso.setFechaModificacion(FECHA);

        compartido = new ProcesoCompartido();
        compartido.setId(7L);
        compartido.setEmpresa(propietaria);
        compartido.setProceso(proceso);
        compartido.setEmpresaInvitada(invitada);
        compartido.setSoloLectura(true);
    }

    private PermisoEstructura permiso(RolAcceso rol, boolean eliminarPool) {
        PermisoEstructura p = new PermisoEstructura();
        p.setRolAcceso(rol);
        p.setCrearPool(true);
        p.setEditarPool(true);
        p.setEliminarPool(eliminarPool);
        p.setCrearLane(true);
        p.setEditarLane(true);
        return p;
    }

    private ProcesoCompartidoResponse compartidoEsperado() {
        return new ProcesoCompartidoResponse(7L, 10L, "Compras", 1L, "Acme", 2L, "Aliada", true);
    }

    // ---- PermisoEstructuraServiceImpl

    @Test
    void PermisoEstructuraServiceImpl_consultarPorEmpresa_hayPermisos_devuelveResponses() {
        doReturn(List.of(permiso(RolAcceso.ADMINISTRADOR, false))).when(permisos).listar(1L);

        List<PermisoEstructuraResponse> respuesta = permisos.consultarPorEmpresa(1L);

        assertThat(respuesta).containsExactly(
                new PermisoEstructuraResponse(RolAcceso.ADMINISTRADOR, true, true, false, true, true, false));
    }

    @Test
    void PermisoEstructuraServiceImpl_actualizarRequest_datosValidos_devuelveResponse() {
        doReturn(permiso(RolAcceso.EDITOR, true)).when(permisos)
                .actualizar(1L, RolAcceso.EDITOR, true, true, true, true, true, false);

        PermisoEstructuraResponse respuesta = permisos.actualizar(1L, RolAcceso.EDITOR,
                new PermisoEstructuraRequest(true, true, true, true, true, false));

        assertThat(respuesta).isEqualTo(
                new PermisoEstructuraResponse(RolAcceso.EDITOR, true, true, true, true, true, false));
    }

    // ---- ProcesoCompartidoServiceImpl

    @Test
    void ProcesoCompartidoServiceImpl_consultarCompartidosPorPropietario_hayCompartidos_devuelveResponses() {
        doReturn(List.of(compartido)).when(compartidos).listarCompartidosPorPropietario(1L, 10L);

        assertThat(compartidos.consultarCompartidosPorPropietario(1L, 10L))
                .containsExactly(compartidoEsperado());
    }

    @Test
    void ProcesoCompartidoServiceImpl_compartirRequest_datosValidos_devuelveResponse() {
        doReturn(compartido).when(compartidos).compartir(1L, 9L, 10L, 2L);

        ProcesoCompartidoResponse respuesta = compartidos.compartir(1L, 9L, 10L, new CompartirProcesoRequest(2L));

        assertThat(respuesta).isEqualTo(compartidoEsperado());
    }

    @Test
    void ProcesoCompartidoServiceImpl_consultarRecibidos_hayRecibidos_devuelveResponses() {
        doReturn(List.of(compartido)).when(compartidos).listarRecibidos(2L);

        assertThat(compartidos.consultarRecibidos(2L)).containsExactly(compartidoEsperado());
    }

    @Test
    void ProcesoCompartidoServiceImpl_consultarCompartido_procesoRecibido_devuelveDetalleResponse() {
        ProcesoCompartidoDetalle detalle = new ProcesoCompartidoDetalle(compartido.getProceso(), compartido,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        doReturn(detalle).when(compartidos).obtenerCompartido(2L, 10L);

        ProcesoCompartidoDetalleResponse respuesta = compartidos.consultarCompartido(2L, 10L);

        assertThat(respuesta.proceso().nombre()).isEqualTo("Compras");
        assertThat(respuesta.empresaPropietaria()).isEqualTo("Acme");
        assertThat(respuesta.soloLectura()).isTrue();
        assertThat(respuesta.pools()).isEmpty();
        assertThat(respuesta.mensajes()).isEmpty();
    }

    @Test
    void ProcesoCompartidoServiceImpl_consultarCompartido_conLane_noExponeElIdDelRolDeLaPropietaria() {
        com.facimus.procesos.modelado.model.Pool pool = new com.facimus.procesos.modelado.model.Pool();
        pool.setId(20L);
        pool.setProceso(compartido.getProceso());
        com.facimus.procesos.gestion.model.RolProceso rol = new com.facimus.procesos.gestion.model.RolProceso();
        rol.setId(99L);
        rol.setNombre("Analista");
        com.facimus.procesos.modelado.model.Lane lane = new com.facimus.procesos.modelado.model.Lane();
        lane.setId(21L);
        lane.setNombre("Analisis");
        lane.setOrden(1);
        lane.setPool(pool);
        lane.setRolProceso(rol);
        ProcesoCompartidoDetalle detalle = new ProcesoCompartidoDetalle(compartido.getProceso(), compartido,
                List.of(pool), List.of(lane), List.of(), List.of(), List.of(), List.of(), List.of());
        doReturn(detalle).when(compartidos).obtenerCompartido(2L, 10L);

        ProcesoCompartidoDetalleResponse respuesta = compartidos.consultarCompartido(2L, 10L);

        assertThat(respuesta.lanes()).containsExactly(
                new com.facimus.procesos.gestion.controller.dto.LaneCompartidaResponse(21L, "Analisis", 1, 20L, "Analista"));
        assertThat(com.facimus.procesos.gestion.controller.dto.LaneCompartidaResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("rolProcesoId");
    }
}
