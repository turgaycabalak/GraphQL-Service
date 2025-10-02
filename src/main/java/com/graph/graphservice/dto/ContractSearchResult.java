package com.graph.graphservice.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record ContractSearchResult(
    List<ContractResponse> contracts,
    int totalCount,
    int page,
    int size,
    int totalPages
) {
}
