package com.graph.graphservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.graph.graphservice.entity.BranchEnum;
import com.graph.graphservice.entity.CoverEnum;

public record ContractCoverResponse(
    UUID id,
    CoverEnum coverEnum,
    BigDecimal premiumAmount
) {
}
