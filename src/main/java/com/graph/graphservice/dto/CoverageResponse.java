package com.graph.graphservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.graph.graphservice.entity.BranchEnum;

public record CoverageResponse(
    UUID id,
    BranchEnum branchEnum,
    BigDecimal premiumAmount
) {
}
