package com.facimus.procesos.modelado.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Mensaje;

public interface MensajeRepository extends RepositorioTenant<Mensaje> {

    List<Mensaje> findAllByPoolOrigenIdAndEmpresaId(Long poolOrigenId, Long empresaId);

    List<Mensaje> findAllByPoolDestinoIdAndEmpresaId(Long poolDestinoId, Long empresaId);

    List<Mensaje> findAllByProcesoIdAndEmpresaId(Long procesoId, Long empresaId);

    List<Mensaje> findAllByProcesoIdAndEmpresaIdAndActivoTrue(Long procesoId, Long empresaId);

    boolean existsByPoolOrigenIdAndEmpresaIdAndActivoTrue(Long poolOrigenId, Long empresaId);

    boolean existsByPoolDestinoIdAndEmpresaIdAndActivoTrue(Long poolDestinoId, Long empresaId);

    /** Mensajes activos que usan el evento como Message Throw o como Message Catch. */
    @Query("select m from Mensaje m where m.empresa.id = :empresaId and m.activo = true "
            + "and (m.eventoThrow.id = :eventoId or m.eventoCatch.id = :eventoId) order by m.nombre")
    List<Mensaje> findActivosPorEvento(@Param("eventoId") Long eventoId, @Param("empresaId") Long empresaId);
}
