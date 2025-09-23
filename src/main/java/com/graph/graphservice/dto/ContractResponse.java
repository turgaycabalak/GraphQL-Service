package com.graph.graphservice.dto;

import java.util.List;
import java.util.UUID;

public record ContractResponse(
    UUID id,
    String contractName,
    String contractNo,
    Integer renewalNo,
    Integer endorsementNo,
    List<LayerResponse> layers
) {
}
