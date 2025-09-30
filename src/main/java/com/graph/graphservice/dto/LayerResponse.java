package com.graph.graphservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.Builder;

@Builder
public record LayerResponse(
    UUID id,
    int layerOrder,
    BigDecimal lossLimitAmount,
    BigDecimal lossLimitAmountRc,
    BigDecimal deductibleAmount,
    BigDecimal deductibleAmountRc,

    List<ReinstatementResponse> reinstatements
) {
}
