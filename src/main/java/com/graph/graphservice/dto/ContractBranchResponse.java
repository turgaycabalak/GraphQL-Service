package com.graph.graphservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.graph.graphservice.entity.BranchEnum;
import com.graph.graphservice.entity.ContractCoverEntity;

public record ContractBranchResponse(
    UUID id,
    BranchEnum branchEnum,
    BigDecimal premiumAmount,

    List<ContractCoverEntity> contractCovers
) {
}
