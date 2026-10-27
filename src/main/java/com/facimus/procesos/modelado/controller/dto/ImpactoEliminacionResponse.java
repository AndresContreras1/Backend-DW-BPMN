package com.facimus.procesos.modelado.controller.dto;

import java.util.List;

public record ImpactoEliminacionResponse(
        boolean requiereConfirmacion,
        boolean rompeContinuidad,
        List<String> advertencias) {
}
