package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.security.ApiPrincipal;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.service.LaneService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolProcesoServiceTest {

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

    @InjectMocks
    private RolProcesoService rolProcesoService;

    private Empresa empresa;
    private RolProceso rol;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);

        rol = new RolProceso();
        rol.setId(5L);
        rol.setEmpresa(empresa);
        rol.setNombre("Analista");
        rol.setActivo(true);
    }


    @Test
    void crear_nombre_duplicado_falla() {
        RolProceso existente = new RolProceso();
        existente.setId(9L);
        existente.setNombre("Analista");
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(1L)).thenReturn(List.of(existente));

        assertThrows(ReglaNegocioException.class,
                () -> rolProcesoService.crear(1L, "analista", "Duplicado"));
    }

    @Test
    void editar_actualiza_y_registra_historial_con_usuario_autenticado() {
        Proceso proceso = new Proceso();
        proceso.setId(10L);
        proceso.setNombre("Compras");
        Pool pool = new Pool();
        pool.setProceso(proceso);
        Lane lane = new Lane();
        lane.setPool(pool);

        Usuario usuario = new Usuario();
        usuario.setId(7L);

        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(1L)).thenReturn(List.of(rol));
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioService.buscar(1L, 7L)).thenReturn(Optional.of(usuario));
        when(laneService.listarPorRolProceso(1L, 5L)).thenReturn(List.of(lane));

        ApiPrincipal principal = new ApiPrincipal(7L, 1L, RolAcceso.ADMINISTRADOR, "admin@demo.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
        try {
            RolProceso resultado = rolProcesoService.editar(1L, 5L, "Analista Sr", "Senior");
            assertEquals("Analista Sr", resultado.getNombre());
            verify(historialCambioService).registrar(eq(proceso), eq(usuario), contains("editado"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void listar_con_uso_mapea_conteo_y_bandera() {
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(1L)).thenReturn(List.of(rol));
        when(laneService.contarPorRolProceso(1L, 5L)).thenReturn(2L);

        var resultado = rolProcesoService.listarConUso(1L);

        assertEquals(1, resultado.size());
        assertEquals(2L, resultado.get(0).procesosQueLoUsan());
        assertTrue(resultado.get(0).enUso());
    }

    @Test
    void buscar_sin_nombre_usa_paginacion_y_lista_procesos() {
        Proceso proceso = new Proceso();
        proceso.setNombre("Compras");
        Pool pool = new Pool();
        pool.setProceso(proceso);
        Lane lane = new Lane();
        lane.setPool(pool);

        var pageable = PageRequest.of(0, 10);
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(rol)));
        when(laneService.listarPorRolProceso(1L, 5L)).thenReturn(List.of(lane));

        var resultado = rolProcesoService.buscarConProcesos(1L, " ", pageable);

        assertEquals("Compras", resultado.getContent().get(0).procesos().get(0));
    }

    @Test
    void buscar_por_nombre_recorta_filtro() {
        var pageable = PageRequest.of(0, 10);
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(
                1L, "Ana", pageable)).thenReturn(new PageImpl<>(List.of(rol)));
        when(laneService.listarPorRolProceso(1L, 5L)).thenReturn(List.of());

        var resultado = rolProcesoService.buscarConProcesos(1L, " Ana ", pageable);

        assertEquals(1, resultado.getTotalElements());
    }

    @Test
    void contar_usos_valida_rol_y_retorna_conteo() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(laneService.contarPorRolProceso(1L, 5L)).thenReturn(3L);

        assertEquals(3L, rolProcesoService.contarUsos(1L, 5L));
    }

    @Test
    void obtener_inexistente_falla() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(com.facimus.procesos.common.RecursoNoEncontradoException.class,
                () -> rolProcesoService.obtener(1L, 99L));
    }

    @Test
    @DisplayName("HU-17: crear rol con nombre unico")
    void crear_exitoso() {
        when(rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(1L)).thenReturn(Collections.emptyList());
        when(empresaService.obtener(1L)).thenReturn(empresa);
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> inv.getArgument(0));

        RolProceso result = rolProcesoService.crear(1L, "Auditor", "Revisa procesos");

        assertEquals("Auditor", result.getNombre());
        assertTrue(result.isActivo());
    }

    @Test
    @DisplayName("HU-19: eliminar rol en uso lanza excepcion (regla 13)")
    void eliminar_rol_en_uso() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        Proceso proceso = new Proceso();
        proceso.setNombre("Compras");
        Pool pool = new Pool();
        pool.setProceso(proceso);
        Lane lane = new Lane();
        lane.setPool(pool);
        when(laneService.listarPorRolProceso(1L, 5L)).thenReturn(List.of(lane));

        assertThrows(ReglaNegocioException.class,
                () -> rolProcesoService.eliminar(1L, 5L));

        verify(rolProcesoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("HU-19: eliminar rol sin uso desactiva correctamente")
    void eliminar_rol_sin_uso() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(laneService.listarPorRolProceso(1L, 5L)).thenReturn(Collections.emptyList());
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> inv.getArgument(0));

        rolProcesoService.eliminar(1L, 5L);

        assertFalse(rol.isActivo());
        verify(rolProcesoRepository).save(rol);
    }
}
