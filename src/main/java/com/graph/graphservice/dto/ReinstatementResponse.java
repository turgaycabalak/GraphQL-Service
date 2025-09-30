package com.graph.graphservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;

@Builder
public record ReinstatementResponse(
    UUID id,
    //int layerOrder, // coming from LayerEntity
    int reinstatementOrder,
    BigDecimal reinstatementRatio
) {
}
