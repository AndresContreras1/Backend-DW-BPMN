package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.PermisoEstructura;
import com.facimus.procesos.gestion.model.RolAcceso;

public interface PermisoEstructuraRepository extends RepositorioTenant<PermisoEstructura> {

    Optional<PermisoEstructura> findByEmpresaIdAndRolAcceso(Long empresaId, RolAcceso rolAcceso);

    List<PermisoEstructura> findAllByEmpresaIdOrderByRolAccesoAsc(Long empresaId);
}
