package com.facimus.procesos.gestion.model;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Acceso de solo lectura concedido por la empresa propietaria a otra empresa. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "procesos_compartidos",
        uniqueConstraints = @UniqueConstraint(columnNames = { "proceso_id", "empresa_invitada_id" }))
public class ProcesoCompartido extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_invitada_id", nullable = false)
    private Empresa empresaInvitada;

    @Column(name = "solo_lectura", nullable = false)
    private boolean soloLectura = true;

    @Column(nullable = false)
    private boolean activo = true;
}
