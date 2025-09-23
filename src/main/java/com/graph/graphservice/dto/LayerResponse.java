package com.graph.graphservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LayerResponse(
    UUID id,
    int layerOrder,
    BigDecimal lossLimitAmount,
    BigDecimal lossLimitAmountRc,
    BigDecimal deductibleAmount,
    BigDecimal deductibleAmountRc
) {
}
