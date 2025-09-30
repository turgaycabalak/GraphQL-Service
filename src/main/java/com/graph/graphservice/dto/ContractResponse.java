package com.graph.graphservice.dto;

import java.util.List;
import java.util.UUID;

import lombok.Builder;

@Builder
public record ContractResponse(
    UUID id,
    String contractName,
    String contractNo,
    Integer renewalNo,
    Integer endorsementNo,

    List<LayerResponse> layers,
    ContractDetailResponse contractDetail
) {
}
