package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.controller.dto.CambiarEstadoProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.EditarProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.HistorialCambioResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoDiagramaResponse;
import com.facimus.procesos.gestion.controller.dto.ProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.ProcesoResponse;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.dto.ProcesoDiagrama;
import com.facimus.procesos.modelado.controller.dto.PoolResponse;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.NodoFlujoService;
import com.facimus.procesos.modelado.service.PoolService;

/** Metodos que devuelven Response de ProcesoService, ProcesoOrquestadorService y ProcesoDiagramaService. */
@ExtendWith(MockitoExtension.class)
class ProcesoServicesResponseTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 3, 1, 9, 30);

    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PoolService poolService;
    @Mock
    private LaneService laneService;
    @Mock
    private NodoFlujoService nodoFlujoService;
    @Mock
    private ArcoService arcoService;
    @Mock
    private MensajeService mensajeService;
    @Mock
    private ProcesoService procesoServiceMock;

    private ProcesoServiceImpl procesoService;
    private ProcesoOrquestadorServiceImpl orquestador;
    private ProcesoDiagramaServiceImpl diagramaService;
    private Proceso proceso;

    @BeforeEach
    void setUp() {
        var mapper = new ModelMapperConfig().modelMapper();
        procesoService = spy(new ProcesoServiceImpl(procesoRepository, empresaService, usuarioService,
                historialCambioService, eventPublisher, mapper));
        orquestador = spy(new ProcesoOrquestadorServiceImpl(procesoServiceMock, poolService, mapper));
        diagramaService = spy(new ProcesoDiagramaServiceImpl(procesoServiceMock, poolService, laneService,
                nodoFlujoService, arcoService, mensajeService, mapper));

        Empresa empresa = new Empresa();
        empresa.setId(1L);
        proceso = new Proceso();
        proceso.setId(5L);
        proceso.setEmpresa(empresa);
        proceso.setNombre("Ventas");
        proceso.setDescripcion("Desc");
        proceso.setCategoria("Operativo");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(FECHA);
        proceso.setFechaModificacion(FECHA);
    }

    private ProcesoResponse respuestaEsperada(EstadoProceso estado, String nombre) {
        return new ProcesoResponse(5L, nombre, "Desc", "Operativo", estado, true, FECHA, FECHA);
    }

    private HistorialCambio historial(Long id, String descripcion) {
        Usuario autor = new Usuario();
        autor.setNombre("Juan");
        HistorialCambio h = new HistorialCambio();
        h.setId(id);
        h.setAutor(autor);
        h.setFechaCambio(FECHA);
        h.setDescripcionCambio(descripcion);
        return h;
    }

    // ---- ProcesoServiceImpl

    @Test
    void ProcesoServiceImpl_editarDatosRequest_datosValidos_devuelveProcesoResponse() {
        proceso.setNombre("Ventas v2");
        doReturn(proceso).when(procesoService).editarDatos(1L, 5L, 9L, "Ventas v2", "Desc", "Operativo");

        ProcesoResponse respuesta = procesoService.editarDatos(1L, 5L, 9L,
                new EditarProcesoRequest("Ventas v2", "Desc", "Operativo"));

        assertThat(respuesta).isEqualTo(respuestaEsperada(EstadoProceso.BORRADOR, "Ventas v2"));
    }

    @Test
    void ProcesoServiceImpl_cambiarEstadoRequest_publicar_devuelveProcesoResponse() {
        proceso.setEstado(EstadoProceso.PUBLICADO);
        doReturn(proceso).when(procesoService).cambiarEstado(1L, 5L, 9L, EstadoProceso.PUBLICADO);

        ProcesoResponse respuesta = procesoService.cambiarEstado(1L, 5L, 9L,
                new CambiarEstadoProcesoRequest(EstadoProceso.PUBLICADO));

        assertThat(respuesta).isEqualTo(respuestaEsperada(EstadoProceso.PUBLICADO, "Ventas"));
    }

    @Test
    void ProcesoServiceImpl_consultarPagina_hayResultados_devuelvePageResponseOrdenadaPorFechaModificacion() {
        doReturn(new PageImpl<>(List.of(proceso))).when(procesoService)
                .buscar(any(), any(), any(), any(), any(), any(Pageable.class));

        PageResponse<ProcesoResponse> respuesta = procesoService.consultarPagina(1L, "Ven",
                EstadoProceso.BORRADOR, "Operativo", true, 2);

        assertThat(respuesta.content()).containsExactly(respuestaEsperada(EstadoProceso.BORRADOR, "Ventas"));
        assertThat(respuesta.totalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(procesoService).buscar(eq(1L), eq("Ven"),
                eq(EstadoProceso.BORRADOR),
                eq("Operativo"), eq(true),
                captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort().getOrderFor("fechaModificacion").isDescending()).isTrue();
    }

    @Test
    void ProcesoServiceImpl_consultarDetalle_procesoExiste_devuelveProcesoEHistorial() {
        doReturn(proceso).when(procesoService).obtener(1L, 5L);
        given(historialCambioService.listarPorProceso(1L, 5L)).willReturn(List.of(historial(3L, "Creado")));

        ProcesoDetalleResponse respuesta = procesoService.consultarDetalle(1L, 5L);

        assertThat(respuesta.proceso()).isEqualTo(respuestaEsperada(EstadoProceso.BORRADOR, "Ventas"));
        assertThat(respuesta.historial()).containsExactly(new HistorialCambioResponse(3L, FECHA, "Creado", "Juan"));
    }

    @Test
    void ProcesoServiceImpl_consultarDetalle_procesoNoExiste_lanzaExcepcionSinConsultarHistorial() {
        doThrow(new RecursoNoEncontradoException("Proceso no encontrado.")).when(procesoService).obtener(1L, 99L);

        assertThatThrownBy(() -> procesoService.consultarDetalle(1L, 99L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(historialCambioService, never()).listarPorProceso(any(), any());
    }

    @Test
    void ProcesoServiceImpl_consultarHistorial_procesoExiste_devuelveHistorialResponse() {
        doReturn(proceso).when(procesoService).obtener(1L, 5L);
        given(historialCambioService.listarPorProceso(1L, 5L))
                .willReturn(List.of(historial(3L, "Creado"), historial(4L, "Editado")));

        List<HistorialCambioResponse> respuesta = procesoService.consultarHistorial(1L, 5L);

        assertThat(respuesta).extracting(HistorialCambioResponse::descripcionCambio)
                .containsExactly("Creado", "Editado");
    }

    @Test
    void ProcesoServiceImpl_consultarHistorial_procesoNoExiste_lanzaExcepcion() {
        doThrow(new RecursoNoEncontradoException("Proceso no encontrado.")).when(procesoService).obtener(1L, 99L);

        assertThatThrownBy(() -> procesoService.consultarHistorial(1L, 99L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(historialCambioService, never()).listarPorProceso(any(), any());
    }

    // ---- ProcesoOrquestadorServiceImpl

    @Test
    void ProcesoOrquestadorServiceImpl_crearConPoolInicialRequest_datosValidos_devuelveProcesoResponse() {
        doReturn(proceso).when(orquestador).crearConPoolInicial(1L, 9L, "Ventas", "Desc", "Operativo");

        ProcesoResponse respuesta = orquestador.crearConPoolInicial(1L, 9L,
                new ProcesoRequest("Ventas", "Desc", "Operativo"));

        assertThat(respuesta).isEqualTo(respuestaEsperada(EstadoProceso.BORRADOR, "Ventas"));
    }

    // ---- ProcesoDiagramaServiceImpl

    @Test
    void ProcesoDiagramaServiceImpl_consultarDiagrama_procesoConPool_devuelveDiagramaResponse() {
        Pool pool = new Pool();
        pool.setId(2L);
        pool.setNombre("Acme");
        pool.setTipoParticipante(TipoParticipante.EMPRESA);
        pool.setOrden(1);
        pool.setProceso(proceso);
        doReturn(new ProcesoDiagrama(proceso, List.of(pool), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of())).when(diagramaService).obtener(1L, 5L);

        ProcesoDiagramaResponse respuesta = diagramaService.consultarDiagrama(1L, 5L);

        assertThat(respuesta.proceso()).isEqualTo(respuestaEsperada(EstadoProceso.BORRADOR, "Ventas"));
        assertThat(respuesta.pools()).containsExactly(
                new PoolResponse(2L, "Acme", TipoParticipante.EMPRESA, false, 1, 5L));
        assertThat(respuesta.lanes()).isEmpty();
        assertThat(respuesta.actividades()).isEmpty();
        assertThat(respuesta.gateways()).isEmpty();
        assertThat(respuesta.eventos()).isEmpty();
        assertThat(respuesta.arcos()).isEmpty();
        assertThat(respuesta.mensajes()).isEmpty();
    }
}
