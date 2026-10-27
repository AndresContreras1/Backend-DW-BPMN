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

/** Flujo de mensaje entre pools. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mensajes")
public class Mensaje extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, columnDefinition = "text")
    private String contenido;

    @Column(name = "clave_correlacion")
    private String claveCorrelacion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_origen_id", nullable = false)
    private Pool poolOrigen;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_destino_id", nullable = false)
    private Pool poolDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_throw_id")
    private EventoMensaje eventoThrow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_catch_id")
    private EventoMensaje eventoCatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_destino_externo")
    private TipoDestinoExterno tipoDestinoExterno;

    @Column(name = "destino_externo")
    private String destinoExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_fallo_notificacion")
    private PoliticaFalloNotificacion politicaFalloNotificacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actividad_error_id")
    private Actividad actividadError;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_sin_caso")
    private PoliticaMensajeSinCaso politicaSinCaso = PoliticaMensajeSinCaso.DESCARTAR;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @Column(nullable = false)
    private boolean activo = true;
}
