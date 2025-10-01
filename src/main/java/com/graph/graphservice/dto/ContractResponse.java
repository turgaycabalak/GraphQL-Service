package com.graph.graphservice.dto;

import java.util.List;
import java.util.UUID;

import com.graph.graphservice.entity.ContractStatusEnum;

import lombok.Builder;

@Builder
public record ContractResponse(
    UUID id,
    String contractName,
    String contractNo,
    Integer renewalNo,
    Integer endorsementNo,
    ContractStatusEnum contractStatus,

    List<ContractBranchResponse> contractBranches,
    List<LayerResponse> layers,
    ContractDetailResponse contractDetail
) {
}
