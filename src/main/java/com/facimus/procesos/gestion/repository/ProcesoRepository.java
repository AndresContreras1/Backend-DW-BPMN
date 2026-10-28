package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;

public interface ProcesoRepository extends RepositorioTenant<Proceso> {

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(Long empresaId, String nombre);

    Optional<Proceso> findByIdAndEmpresaIdAndActivoTrue(Long id, Long empresaId);

    /**
     * Busqueda de HU-07: siempre acotada a la empresa y al valor de {@code activo}; el nombre se
     * busca por fragmento y la categoria por igualdad, ambos sin distinguir mayusculas. Los filtros
     * opcionales llegan como un indicador booleano mas un valor de relleno (nunca null), porque
     * PostgreSQL no infiere el tipo de un parametro null dentro de la consulta.
     *
     * @param patronNombre fragmento ya en minusculas y con %, _ y ! escapados con '!', rodeado de %
     * @param categoria    categoria en minusculas; solo se compara si {@code filtrarCategoria} es true
     */
    @Query("""
            SELECT p FROM Proceso p
            WHERE p.empresa.id = :empresaId
              AND p.activo = :activo
              AND LOWER(p.nombre) LIKE :patronNombre ESCAPE '!'
              AND (:filtrarEstado = false OR p.estado = :estado)
              AND (:filtrarCategoria = false OR LOWER(p.categoria) = :categoria)
            """)
    Page<Proceso> buscarConFiltros(@Param("empresaId") Long empresaId, @Param("activo") boolean activo,
            @Param("patronNombre") String patronNombre, @Param("filtrarEstado") boolean filtrarEstado,
            @Param("estado") EstadoProceso estado, @Param("filtrarCategoria") boolean filtrarCategoria,
            @Param("categoria") String categoria, Pageable pageable);
}
