package com.facimus.procesos.modelado.service.dto;

import java.util.List;

public record ImpactoEliminacion(boolean rompeContinuidad, List<String> advertencias) {
}
