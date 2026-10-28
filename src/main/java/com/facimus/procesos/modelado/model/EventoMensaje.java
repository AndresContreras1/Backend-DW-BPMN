package com.facimus.procesos.modelado.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Evento BPMN de mensaje: lanzamiento o recepcion. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@DiscriminatorValue("EVENTO_MENSAJE")
public class EventoMensaje extends NodoFlujo {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento_mensaje")
    private TipoEventoMensaje tipoEvento;

    // text y no @Lob: en PostgreSQL @Lob guarda un OID, igual que paso con Proceso y Mensaje (sql/V1, V2).
    @Column(name = "contenido_evento", columnDefinition = "text")
    private String contenido;

    @Column(name = "clave_correlacion_evento")
    private String claveCorrelacion;

    @Column(name = "origen_externo")
    private boolean origenExterno;

    /**
     * HU-27: actividades que utilizan los datos del mensaje recibido. Solo tiene sentido en los
     * eventos CATCH; EventoMensajeService valida que sean del mismo proceso y empresa.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "evento_mensaje_actividades",
            joinColumns = @JoinColumn(name = "evento_id"),
            inverseJoinColumns = @JoinColumn(name = "actividad_id"))
    @BatchSize(size = 50)
    private Set<Actividad> actividades = new HashSet<>();
}
