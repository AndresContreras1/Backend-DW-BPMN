package com.facimus.procesos.gestion.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.PermisoEstructura;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.PermisoEstructuraRepository;

import lombok.RequiredArgsConstructor;

/** HU-24: permisos configurables para modificar pools y lanes. */
@Service
@RequiredArgsConstructor
public class PermisoEstructuraService {

    private final PermisoEstructuraRepository permisoEstructuraRepository;
    private final EmpresaService empresaService;

    @Transactional(readOnly = true)
    public void validar(Long empresaId, RolAcceso rol, OperacionEstructura operacion) {
        PermisoEstructura permiso = obtener(empresaId, rol);
        boolean permitido = switch (operacion) {
            case CREAR_POOL -> permiso.isCrearPool();
            case EDITAR_POOL -> permiso.isEditarPool();
            case ELIMINAR_POOL -> permiso.isEliminarPool();
            case CREAR_LANE -> permiso.isCrearLane();
            case EDITAR_LANE -> permiso.isEditarLane();
            case ELIMINAR_LANE -> permiso.isEliminarLane();
        };
        if (!permitido) {
            throw new ReglaNegocioException("El rol " + rol + " no tiene permiso para " + operacion.name().toLowerCase() + ".");
        }
    }

    @Transactional(readOnly = true)
    public PermisoEstructura obtener(Long empresaId, RolAcceso rol) {
        return permisoEstructuraRepository.findByEmpresaIdAndRolAcceso(empresaId, rol)
                .orElseGet(() -> permisoPorDefecto(empresaId, rol));
    }

    @Transactional(readOnly = true)
    public List<PermisoEstructura> listar(Long empresaId) {
        return Arrays.stream(RolAcceso.values()).map(rol -> obtener(empresaId, rol)).toList();
    }

    @Transactional
    public PermisoEstructura actualizar(Long empresaId, RolAcceso rol, boolean crearPool, boolean editarPool,
            boolean eliminarPool, boolean crearLane, boolean editarLane, boolean eliminarLane) {
        if (rol == RolAcceso.SOLO_LECTURA
                && (crearPool || editarPool || eliminarPool || crearLane || editarLane || eliminarLane)) {
            throw new ReglaNegocioException("El rol SOLO_LECTURA no puede recibir permisos de modificacion.");
        }
        PermisoEstructura permiso = permisoEstructuraRepository.findByEmpresaIdAndRolAcceso(empresaId, rol)
                .orElseGet(() -> permisoPorDefecto(empresaId, rol));
        permiso.setCrearPool(crearPool);
        permiso.setEditarPool(editarPool);
        permiso.setEliminarPool(eliminarPool);
        permiso.setCrearLane(crearLane);
        permiso.setEditarLane(editarLane);
        permiso.setEliminarLane(eliminarLane);
        return permisoEstructuraRepository.save(permiso);
    }

    private PermisoEstructura permisoPorDefecto(Long empresaId, RolAcceso rol) {
        Empresa empresa = empresaService.obtener(empresaId);
        PermisoEstructura permiso = new PermisoEstructura();
        permiso.setEmpresa(empresa);
        permiso.setRolAcceso(rol);

        if (rol == RolAcceso.ADMINISTRADOR) {
            permiso.setCrearPool(true);
            permiso.setEditarPool(true);
            permiso.setEliminarPool(true);
            permiso.setCrearLane(true);
            permiso.setEditarLane(true);
            permiso.setEliminarLane(true);
        } else if (rol == RolAcceso.EDITOR) {
            permiso.setCrearPool(true);
            permiso.setEditarPool(true);
            permiso.setEliminarPool(false);
            permiso.setCrearLane(true);
            permiso.setEditarLane(true);
            permiso.setEliminarLane(false);
        }
        return permiso;
    }
}
