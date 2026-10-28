package com.facimus.procesos.common;

import com.facimus.procesos.gestion.model.Empresa;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Toda entidad que pertenece a una empresa extiende esta clase.
 * La columna empresa_id acota el aislamiento multiempresa en cada consulta.
 *
 * <p><b>Decisiones de mapeo JPA:</b>
 * <ul>
 *   <li><b>{@code @ManyToOne} con {@code fetch = FetchType.LAZY}:</b> el mapeo Entity-&gt;DTO
 *   ocurre en los services, dentro de su {@code @Transactional}, con
 *   {@code spring.jpa.open-in-view=false}. Donde una lectura navega una relacion y conviene
 *   cargarla en la misma consulta, el repositorio declara un {@code @EntityGraph}
 *   (ver {@code LaneRepository}, {@code HistorialCambioRepository} o
 *   {@code ProcesoCompartidoRepository}).</li>
 *   <li><b>Baja logica con campo {@code activo}:</b> las entidades que la soportan
 *   ({@code Proceso}, {@code RolProceso}, {@code Pool}, {@code Lane}, {@code NodoFlujo},
 *   {@code Arco}, {@code Mensaje}, {@code Correlacion}) ponen {@code activo = false} al
 *   "eliminar". {@code Pool}, {@code Lane} y {@code Correlacion} lo hacen ademas con
 *   {@code @SQLDelete} y {@code @SQLRestriction}; en el resto, los repositorios filtran con el
 *   sufijo {@code ActivoTrue} o con predicados explicitos, igual que se filtra por
 *   {@code empresaId} (ver {@link RepositorioTenant}), para que el filtro se vea en cada
 *   consulta.</li>
 * </ul>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadEmpresa {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    protected Empresa empresa;
}
