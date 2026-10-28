package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.config.ModelMapperConfig;
import com.facimus.procesos.gestion.controller.dto.RolProcesoConsultaResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.dto.RolProcesoConsulta;
import com.facimus.procesos.gestion.service.dto.RolProcesoVista;
import com.facimus.procesos.modelado.service.LaneService;

/** Metodos que devuelven Response de RolProcesoService. */
@ExtendWith(MockitoExtension.class)
class RolProcesoServiceResponseTest {

    @Mock
    private RolProcesoRepository rolProcesoRepository;
    @Mock
    private EmpresaService empresaService;
    @Mock
    private LaneService laneService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private HistorialCambioService historialCambioService;

    private RolProcesoServiceImpl service;
    private RolProceso rol;

    @BeforeEach
    void setUp() {
        service = spy(new RolProcesoServiceImpl(rolProcesoRepository, empresaService, laneService, usuarioService,
                historialCambioService, new ModelMapperConfig().modelMapper()));
        rol = new RolProceso();
        rol.setId(2L);
        rol.setNombre("Analista");
        rol.setDescripcion("Analiza");
    }

    @Test
    void RolProcesoServiceImpl_crearRequest_datosValidos_devuelveVistaSinUsos() {
        doReturn(rol).when(service).crear(1L, "Analista", "Analiza");

        RolProcesoVistaResponse respuesta = service.crear(1L, new RolProcesoRequest("Analista", "Analiza"));

        assertThat(respuesta).isEqualTo(new RolProcesoVistaResponse(2L, "Analista", "Analiza", 0, false));
    }

    @Test
    void RolProcesoServiceImpl_editarRequest_rolEnUso_devuelveVistaConUsos() {
        doReturn(rol).when(service).editar(1L, 2L, "Analista", "Analiza");
        doReturn(3L).when(service).contarUsos(1L, 2L);

        RolProcesoVistaResponse respuesta = service.editar(1L, 2L, new RolProcesoRequest("Analista", "Analiza"));

        assertThat(respuesta).isEqualTo(new RolProcesoVistaResponse(2L, "Analista", "Analiza", 3, true));
    }

    @Test
    void RolProcesoServiceImpl_editarRequest_rolSinUso_devuelveEnUsoFalso() {
        doReturn(rol).when(service).editar(1L, 2L, "Analista", "Analiza");
        doReturn(0L).when(service).contarUsos(1L, 2L);

        RolProcesoVistaResponse respuesta = service.editar(1L, 2L, new RolProcesoRequest("Analista", "Analiza"));

        assertThat(respuesta.enUso()).isFalse();
        assertThat(respuesta.procesosQueLoUsan()).isZero();
    }

    @Test
    void RolProcesoServiceImpl_consultarDetalle_rolEnUso_devuelveVistaConUsos() {
        doReturn(rol).when(service).obtener(1L, 2L);
        doReturn(4L).when(service).contarUsos(1L, 2L);

        RolProcesoVistaResponse respuesta = service.consultarDetalle(1L, 2L);

        assertThat(respuesta).isEqualTo(new RolProcesoVistaResponse(2L, "Analista", "Analiza", 4, true));
    }

    @Test
    void RolProcesoServiceImpl_consultarDetalle_rolSinUso_devuelveEnUsoFalso() {
        doReturn(rol).when(service).obtener(1L, 2L);
        doReturn(0L).when(service).contarUsos(1L, 2L);

        assertThat(service.consultarDetalle(1L, 2L).enUso()).isFalse();
    }

    @Test
    void RolProcesoServiceImpl_consultarConUso_hayRoles_devuelveVistaResponses() {
        doReturn(List.of(new RolProcesoVista(rol, 3, true))).when(service).listarConUso(1L);

        List<RolProcesoVistaResponse> respuesta = service.consultarConUso(1L);

        assertThat(respuesta).containsExactly(new RolProcesoVistaResponse(2L, "Analista", "Analiza", 3, true));
    }

    @Test
    void RolProcesoServiceImpl_consultarPagina_hayRoles_devuelvePageResponseDeTamanoDiez() {
        doReturn(new PageImpl<>(List.of(new RolProcesoConsulta(rol, List.of("Compras"))))).when(service)
                .buscarConProcesos(eq(1L), eq("ana"), any(Pageable.class));

        PageResponse<RolProcesoConsultaResponse> respuesta = service.consultarPagina(1L, "ana", 1, null);

        assertThat(respuesta.content()).containsExactly(
                new RolProcesoConsultaResponse(2L, "Analista", "Analiza", List.of("Compras"), true));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).buscarConProcesos(eq(1L), eq("ana"), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getSort().getOrderFor("nombre").isIgnoreCase()).isTrue();
        assertThat(captor.getValue().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void RolProcesoServiceImpl_consultarPagina_conTamano_usaEseTamano() {
        doReturn(new PageImpl<>(List.of(new RolProcesoConsulta(rol, List.of())))).when(service)
                .buscarConProcesos(eq(1L), eq(null), any(Pageable.class));

        service.consultarPagina(1L, null, 0, 25);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).buscarConProcesos(eq(1L), eq(null), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(25);
    }
}
