package com.graph.graphservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;

@Builder
public record ContractDetailResponse(
    UUID id,
    LocalDateTime startDate,
    LocalDateTime endDate
) {
}
