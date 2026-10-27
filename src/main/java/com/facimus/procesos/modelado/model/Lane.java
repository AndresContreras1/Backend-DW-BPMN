package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.gestion.model.RolProceso;

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
 * Division interna de un Pool que agrupa las actividades de un mismo rol responsable.
 * Eliminacion logica: @SQLDelete cambia el DELETE por un UPDATE y @SQLRestriction oculta las filas
 * inactivas en todas las consultas.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "lanes")
@SQLDelete(sql = "UPDATE lanes SET activo = false WHERE id = ?")
@SQLRestriction("activo = true")
public class Lane extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private int orden;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_proceso_id", nullable = false)
    private RolProceso rolProceso;
}
