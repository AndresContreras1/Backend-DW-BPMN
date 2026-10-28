package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoConsultaResponse;
import com.facimus.procesos.gestion.controller.dto.RolProcesoRequest;
import com.facimus.procesos.gestion.controller.dto.RolProcesoVistaResponse;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.dto.RolProcesoConsulta;
import com.facimus.procesos.gestion.service.dto.RolProcesoVista;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.security.ApiPrincipal;

import lombok.RequiredArgsConstructor;

/** HU-17 a HU-20: roles de proceso (funciones, no personas, asignables a Lanes). */
@Service
@RequiredArgsConstructor
public class RolProcesoServiceImpl implements RolProcesoService {

    private static final int TAMANO_PAGINA = 10;

    private final RolProcesoRepository rolProcesoRepository;
    private final EmpresaService empresaService;
    private final LaneService laneService;
    private final UsuarioService usuarioService;
    private final HistorialCambioService historialCambioService;
    private final ModelMapper modelMapper;

    @Transactional
    @Override
    public RolProceso crear(Long empresaId, String nombre, String descripcion) {
        String nombreLimpio = nombre.trim();
        validarNombreUnico(empresaId, nombreLimpio, null);
        Empresa empresa = empresaService.obtener(empresaId);

        RolProceso rol = new RolProceso();
        rol.setEmpresa(empresa);
        rol.setNombre(nombreLimpio);
        rol.setDescripcion(descripcion);
        rol.setActivo(true);
        return rolProcesoRepository.save(rol);
    }

    @Transactional
    @Override
    public RolProceso editar(Long empresaId, Long rolId, String nombre, String descripcion) {
        RolProceso rol = obtener(empresaId, rolId);
        String nombreLimpio = nombre.trim();
        validarNombreUnico(empresaId, nombreLimpio, rolId);
        String nombreAnterior = rol.getNombre();
        rol.setNombre(nombreLimpio);
        rol.setDescripcion(descripcion);
        rol = rolProcesoRepository.save(rol);
        boolean renombrado = !nombreAnterior.equals(nombreLimpio);
        if (renombrado) {
            // La etiqueta de las lanes que usan el rol sigue al nombre del rol.
            laneService.renombrarLanesDeRol(empresaId, rolId, nombreLimpio);
        }
        registrarCambioEnProcesos(empresaId, rolId, renombrado
                ? "Rol de proceso renombrado: \"" + nombreAnterior + "\" -> \"" + nombreLimpio + "\"."
                : "Rol de proceso editado: " + rol.getNombre() + ".");
        return rol;
    }

    @Transactional
    @Override
    public void eliminar(Long empresaId, Long rolId) {
        RolProceso rol = obtener(empresaId, rolId);
        // Solo cuentan los procesos activos: las lanes de procesos dados de baja no bloquean la baja del rol.
        List<String> procesosQueLoUsan = laneService.procesosActivosPorRol(empresaId, List.of(rolId))
                .getOrDefault(rolId, List.of());
        if (!procesosQueLoUsan.isEmpty()) {
            String procesos = String.join(", ", procesosQueLoUsan);
            throw new ReglaNegocioException(
                    "El rol \"" + rol.getNombre() + "\" esta en uso en los procesos: " + procesos
                            + ". No se puede eliminar.");
        }
        rol.setActivo(false);
        rolProcesoRepository.save(rol);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RolProcesoVista> listarConUso(Long empresaId) {
        List<RolProceso> roles = rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId);
        Map<Long, List<String>> procesosPorRol = laneService.procesosActivosPorRol(empresaId,
                roles.stream().map(RolProceso::getId).toList());
        return roles.stream()
                .map(rol -> {
                    long usos = procesosPorRol.getOrDefault(rol.getId(), List.of()).size();
                    return new RolProcesoVista(rol, usos, usos > 0);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Page<RolProcesoConsulta> buscarConProcesos(Long empresaId, String nombre, Pageable pageable) {
        Page<RolProceso> pagina = (nombre == null || nombre.isBlank())
                ? rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId, pageable)
                : rolProcesoRepository.findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(
                        empresaId, nombre.trim(), pageable);
        // Una sola consulta agrupada para toda la pagina (sin una consulta de lanes por rol).
        Map<Long, List<String>> procesosPorRol = laneService.procesosActivosPorRol(empresaId,
                pagina.getContent().stream().map(RolProceso::getId).toList());
        return pagina.map(rol -> new RolProcesoConsulta(rol, procesosPorRol.getOrDefault(rol.getId(), List.of())));
    }

    private void registrarCambioEnProcesos(Long empresaId, Long rolId, String descripcion) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ApiPrincipal principal)
                || !empresaId.equals(principal.empresaId())) {
            return;
        }
        usuarioService.buscar(empresaId, principal.usuarioId()).ifPresent(usuario ->
                laneService.listarPorRolProceso(empresaId, rolId).stream()
                        .map(lane -> lane.getPool().getProceso())
                        .distinct()
                        .forEach(proceso -> historialCambioService.registrar(proceso, usuario, descripcion)));
    }

    @Transactional(readOnly = true)
    @Override
    public long contarUsos(Long empresaId, Long rolId) {
        obtener(empresaId, rolId);
        return laneService.procesosActivosPorRol(empresaId, List.of(rolId)).getOrDefault(rolId, List.of()).size();
    }

    @Transactional(readOnly = true)
    @Override
    public RolProceso obtener(Long empresaId, Long rolId) {
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
    }

    private void validarNombreUnico(Long empresaId, String nombre, Long rolIdActual) {
        boolean existe = rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId).stream()
                .anyMatch(r -> r.getNombre().equalsIgnoreCase(nombre) && !r.getId().equals(rolIdActual));
        if (existe) {
            throw new ReglaNegocioException("Ya existe un rol de proceso con el nombre \"" + nombre + "\" en esta empresa.");
        }
    }

    @Transactional
    @Override
    public RolProcesoVistaResponse crear(Long empresaId, RolProcesoRequest request) {
        RolProceso rol = crear(empresaId, request.nombre(), request.descripcion());
        return new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), 0, false);
    }

    @Transactional
    @Override
    public RolProcesoVistaResponse editar(Long empresaId, Long rolId, RolProcesoRequest request) {
        RolProceso rol = editar(empresaId, rolId, request.nombre(), request.descripcion());
        long usos = contarUsos(empresaId, rolId);
        return new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0);
    }

    @Transactional(readOnly = true)
    @Override
    public RolProcesoVistaResponse consultarDetalle(Long empresaId, Long rolId) {
        RolProceso rol = obtener(empresaId, rolId);
        long usos = contarUsos(empresaId, rolId);
        return new RolProcesoVistaResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), usos, usos > 0);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RolProcesoVistaResponse> consultarConUso(Long empresaId) {
        return listarConUso(empresaId).stream()
                .map(v -> modelMapper.map(v, RolProcesoVistaResponse.class))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<RolProcesoConsultaResponse> consultarPagina(Long empresaId, String nombre, int pagina,
            Integer tamano) {
        int tamanoEfectivo = tamano != null ? tamano : TAMANO_PAGINA;
        Sort orden = Sort.by(Sort.Order.asc("nombre").ignoreCase(), Sort.Order.asc("id"));
        Page<RolProcesoConsultaResponse> page = buscarConProcesos(empresaId, nombre,
                PageRequest.of(pagina, tamanoEfectivo, orden))
                .map(consulta -> modelMapper.map(consulta, RolProcesoConsultaResponse.class));
        return PageResponse.from(page);
    }
}
