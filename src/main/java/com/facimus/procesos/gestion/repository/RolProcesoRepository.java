package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.RolProceso;

public interface RolProcesoRepository extends RepositorioTenant<RolProceso> {

    List<RolProceso> findAllByEmpresaIdAndActivoTrue(Long empresaId);

    Optional<RolProceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    Page<RolProceso> findAllByEmpresaIdAndActivoTrue(Long empresaId, Pageable pageable);

    Page<RolProceso> findAllByEmpresaIdAndActivoTrueAndNombreContainingIgnoreCase(
            Long empresaId, String nombre, Pageable pageable);
}
