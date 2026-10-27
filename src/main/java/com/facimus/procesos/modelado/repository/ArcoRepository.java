package com.facimus.procesos.modelado.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.modelado.model.Arco;

public interface ArcoRepository extends RepositorioTenant<Arco> {

    /**
     * origen/destino son LAZY. Se cargan explicitamente aqui porque ArcoService.editar() hace
     * instanceof sobre ellos (NodoFlujo es SINGLE_TABLE): un proxy sin inicializar siempre
     * falla ese instanceof, sin importar la subclase real.
     */
    @Override
    @EntityGraph(attributePaths = { "origen", "destino" })
    Optional<Arco> findByIdAndEmpresaId(Long id, Long empresaId);

    List<Arco> findAllByOrigenIdAndEmpresaId(Long origenId, Long empresaId);

    List<Arco> findAllByDestinoIdAndEmpresaId(Long destinoId, Long empresaId);

    List<Arco> findAllByPoolIdAndEmpresaId(Long poolId, Long empresaId);

    List<Arco> findAllByPool_ProcesoIdAndEmpresaId(Long procesoId, Long empresaId);

    boolean existsByOrigenIdAndDestinoIdAndEmpresaId(Long origenId, Long destinoId, Long empresaId);

    boolean existsByOrigenIdAndDestinoIdAndEmpresaIdAndActivoTrue(Long origenId, Long destinoId, Long empresaId);
}
