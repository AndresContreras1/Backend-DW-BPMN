package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.ProcesoCompartido;

public interface ProcesoCompartidoRepository extends RepositorioTenant<ProcesoCompartido> {

    /** proceso/empresa/empresaInvitada son LAZY y se mapean a DTO en el controller. */
    @EntityGraph(attributePaths = { "proceso", "empresa", "empresaInvitada" })
    List<ProcesoCompartido> findAllByProcesoIdAndEmpresaIdAndActivoTrue(Long procesoId, Long empresaId);

    @EntityGraph(attributePaths = { "proceso", "empresa", "empresaInvitada" })
    List<ProcesoCompartido> findAllByEmpresaInvitadaIdAndActivoTrue(Long empresaInvitadaId);

    @EntityGraph(attributePaths = { "proceso", "empresa" })
    Optional<ProcesoCompartido> findByProcesoIdAndEmpresaInvitadaIdAndActivoTrue(Long procesoId,
            Long empresaInvitadaId);

    Optional<ProcesoCompartido> findByProcesoIdAndEmpresaIdAndEmpresaInvitadaId(Long procesoId,
            Long empresaId, Long empresaInvitadaId);

    Optional<ProcesoCompartido> findByProcesoIdAndEmpresaIdAndEmpresaInvitadaIdAndActivoTrue(Long procesoId,
            Long empresaId, Long empresaInvitadaId);
}
