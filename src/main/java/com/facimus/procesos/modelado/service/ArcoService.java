package com.facimus.procesos.modelado.service;

import com.facimus.procesos.modelado.controller.dto.ArcoRequest;
import com.facimus.procesos.modelado.controller.dto.EditarArcoRequest;
import com.facimus.procesos.modelado.controller.dto.ArcoResponse;
import com.facimus.procesos.modelado.controller.dto.ImpactoEliminacionResponse;
import java.util.List;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.service.dto.ImpactoEliminacion;

public interface ArcoService {

    Arco crear(Long empresaId, Long origenId, Long destinoId, String etiqueta, String condicion);

    Arco editar(Long empresaId, Long arcoId, String etiqueta, String condicion, Long origenId, Long destinoId);

    ImpactoEliminacion evaluarImpactoEliminacion(Long empresaId, Long arcoId);

    void eliminar(Long empresaId, Long arcoId);

    Arco obtener(Long empresaId, Long arcoId);

    List<Arco> listarActivosPorProceso(Long empresaId, Long procesoId);

    List<Arco> listarPorPool(Long empresaId, Long poolId);

    List<Arco> listarPorOrigen(Long empresaId, Long origenId);

    List<Arco> listarPorDestino(Long empresaId, Long destinoId);

    void desactivarPorNodo(Long empresaId, Long nodoId);

    Arco guardar(Arco arco);

    ArcoResponse crear(Long empresaId, ArcoRequest request);

    ArcoResponse editar(Long empresaId, Long arcoId, EditarArcoRequest request);

    ArcoResponse consultarDetalle(Long empresaId, Long arcoId);

    List<ArcoResponse> consultarPorPool(Long empresaId, Long poolId);

    ImpactoEliminacionResponse consultarImpactoEliminacion(Long empresaId, Long arcoId);
}
