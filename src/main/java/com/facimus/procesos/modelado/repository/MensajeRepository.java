package com.facimus.procesos.modelado.repository;

import java.util.List;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Mensaje;

public interface MensajeRepository extends RepositorioTenant<Mensaje> {

    List<Mensaje> findAllByPoolOrigenIdAndEmpresaId(Long poolOrigenId, Long empresaId);

    List<Mensaje> findAllByPoolDestinoIdAndEmpresaId(Long poolDestinoId, Long empresaId);

    List<Mensaje> findAllByProcesoIdAndEmpresaId(Long procesoId, Long empresaId);

    List<Mensaje> findAllByProcesoIdAndEmpresaIdAndActivoTrue(Long procesoId, Long empresaId);

    boolean existsByPoolOrigenIdAndEmpresaIdAndActivoTrue(Long poolOrigenId, Long empresaId);

    boolean existsByPoolDestinoIdAndEmpresaIdAndActivoTrue(Long poolDestinoId, Long empresaId);
}
