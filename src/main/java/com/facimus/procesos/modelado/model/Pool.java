package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.gestion.model.Proceso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Un participante del proceso: la empresa duena, un cliente, un proveedor o un sistema externo.
 * Eliminacion logica: @SQLDelete cambia el DELETE por un UPDATE y @SQLRestriction oculta las filas
 * inactivas en todas las consultas.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pools")
@SQLDelete(sql = "UPDATE pools SET activo = false WHERE id = ?")
@SQLRestriction("activo = true")
public class Pool extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_participante", nullable = false)
    private TipoParticipante tipoParticipante;

    @Column(name = "caja_negra", nullable = false)
    private boolean cajaNegra = false;

    @Column(nullable = false)
    private int orden;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;
}
