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
 * <p><b>Decisiones de mapeo JPA (deliberadas, no un descuido):</b>
 * <ul>
 *   <li><b>{@code @ManyToOne} con {@code fetch = FetchType.LAZY}:</b> el mapeo Entity-&gt;DTO
 *   sigue ocurriendo en los controllers (via {@code ModelMapper}), fuera del
 *   {@code @Transactional} del service que cargo la entidad, y con
 *   {@code spring.jpa.open-in-view=false} la sesion de Hibernate ya cerro para entonces. Para
 *   que esto no lance {@code LazyInitializationException}, cada repositorio cuyo resultado se
 *   mapea a un DTO que navega una relacion (p. ej. {@code lane.getRolProceso().getNombre()})
 *   declara un {@code @EntityGraph} sobre el metodo correspondiente, cargando esa relacion de
 *   forma explicita en la misma query — ver por ejemplo {@code LaneRepository},
 *   {@code HistorialCambioRepository} o {@code ProcesoCompartidoRepository}. Mover el mapeo a
 *   la capa de servicio tambien evitaria el problema, pero se evaluo y se descarto (la mayoria
 *   de servicios ya se llaman entre si con la entidad real para validar reglas de negocio, y
 *   convertir esos metodos a devolver DTOs rompe esa composicion); por eso la solucion elegida
 *   es declarar explicitamente, por repositorio, que relaciones necesita cada lectura.</li>
 *   <li><b>Baja logica con campo {@code activo} y filtro explicito en cada query:</b>
 *   las entidades que soportan eliminacion logica ({@code Proceso}, {@code Pool}, {@code Lane},
 *   {@code NodoFlujo}, {@code Arco}, {@code Mensaje}, {@code Correlacion}) heredan un campo
 *   {@code activo} que se pone en {@code false} al "eliminar". Los repositorios filtran con
 *   el sufijo {@code ActivoTrue} o con predicados explicitos, de la misma forma en que se filtra
 *   por {@code empresaId} (ver {@link RepositorioTenant}). Se eligio este enfoque manual en vez
 *   de {@code @SQLDelete}/{@code @SQLRestriction} porque un filtro global e implicito de Hibernate
 *   puede interactuar de forma no obvia con el filtrado de tenant y es mas dificil de auditar
 *   por lectura del codigo.</li>
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
