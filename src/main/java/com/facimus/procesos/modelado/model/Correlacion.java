package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * El criterio que indica a que caso concreto del proceso corresponde un mensaje.
 * Eliminacion logica: @SQLDelete cambia el DELETE por un UPDATE y @SQLRestriction oculta las filas
 * inactivas en todas las consultas.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "correlaciones")
@SQLDelete(sql = "UPDATE correlaciones SET activo = false WHERE id = ?")
@SQLRestriction("activo = true")
public class Correlacion extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private String criterio;

    // ManyToOne y sin unique: las correlaciones eliminadas siguen en la tabla, y un mensaje puede
    // volver a definir la suya. CorrelacionService garantiza que solo haya una activa por mensaje.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "mensaje_id", nullable = false)
    private Mensaje mensaje;
}
