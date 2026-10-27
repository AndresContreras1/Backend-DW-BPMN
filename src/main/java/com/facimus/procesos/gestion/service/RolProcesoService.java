package com.facimus.procesos.gestion.service;

import java.util.List;
import java.util.stream.Collectors;

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
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.security.ApiPrincipal;

import lombok.RequiredArgsConstructor;

/** HU-17 a HU-20: roles de proceso (funciones, no personas, asignables a Lanes). */
@Service
@RequiredArgsConstructor
public class RolProcesoService {

    private final RolProcesoRepository rolProcesoRepository;
    private final EmpresaService empresaService;
    private final LaneService laneService;
    private final UsuarioService usuarioService;
    private final HistorialCambioService historialCambioService;

    @Transactional
    public RolProceso crear(Long empresaId, String nombre, String descripcion) {
        validarNombreUnico(empresaId, nombre, null);
        Empresa empresa = empresaService.obtener(empresaId);

        RolProceso rol = new RolProceso();
        rol.setEmpresa(empresa);
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        rol.setActivo(true);
        return rolProcesoRepository.save(rol);
    }

    @Transactional
    public RolProceso editar(Long empresaId, Long rolId, String nombre, String descripcion) {
        RolProceso rol = obtener(empresaId, rolId);
        validarNombreUnico(empresaId, nombre, rolId);
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        rol = rolProcesoRepository.save(rol);
        registrarCambioEnProcesos(empresaId, rolId, "Rol de proceso editado: " + rol.getNombre() + ".");
        return rol;
    }

    @Transactional
    public void eliminar(Long empresaId, Long rolId) {
        RolProceso rol = obtener(empresaId, rolId);
        List<Lane> lanesQueLoUsan = laneService.listarPorRolProceso(empresaId, rolId);
        if (!lanesQueLoUsan.isEmpty()) {
            String procesos = lanesQueLoUsan.stream()
                    .map(lane -> lane.getPool().getProceso().getNombre())
                    .distinct()
                    .collect(Collectors.joining(", "));
            throw new ReglaNegocioException(
                    "El rol \"" + rol.getNombre() + "\" esta en uso en los procesos: " + procesos
                            + ". No se puede eliminar.");
        }
        rol.setActivo(false);
        rolProcesoRepository.save(rol);
    }

    @Transactional(readOnly = true)
    public List<RolProcesoVista> listarConUso(Long empresaId) {
        return rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId).stream()
                .map(rol -> {
                    long usos = laneService.contarPorRolProceso(empresaId, rol.getId());
                    return new RolProcesoVista(rol, usos, usos > 0);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<RolProcesoConsulta> buscarConProcesos(Long empresaId, String nombre, Pageable pageable) {
        Page<RolProceso> pagina = (nombre == null || nombre.isBlank())
                ? rolProcesoRepository.findAllByEmpresaIdAndActivoTrue(empresaId, pageable)
                : rolProcesoRepository.findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(
                        empresaId, nombre.trim(), pageable);
        return pagina.map(rol -> {
            List<String> procesos = laneService.listarPorRolProceso(empresaId, rol.getId())
                    .stream()
                    .map(lane -> lane.getPool().getProceso().getNombre())
                    .distinct()
                    .sorted()
                    .toList();
            return new RolProcesoConsulta(rol, procesos);
        });
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
    public long contarUsos(Long empresaId, Long rolId) {
        obtener(empresaId, rolId);
        return laneService.contarPorRolProceso(empresaId, rolId);
    }

    @Transactional(readOnly = true)
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
}
