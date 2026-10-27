package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Politica de edicion de pools y lanes por rol de acceso. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "permisos_estructura",
        uniqueConstraints = @UniqueConstraint(columnNames = { "empresa_id", "rol_acceso" }))
public class PermisoEstructura extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_acceso", nullable = false)
    private RolAcceso rolAcceso;

    @Column(name = "crear_pool", nullable = false)
    private boolean crearPool;

    @Column(name = "editar_pool", nullable = false)
    private boolean editarPool;

    @Column(name = "eliminar_pool", nullable = false)
    private boolean eliminarPool;

    @Column(name = "crear_lane", nullable = false)
    private boolean crearLane;

    @Column(name = "editar_lane", nullable = false)
    private boolean editarLane;

    @Column(name = "eliminar_lane", nullable = false)
    private boolean eliminarLane;
}
