package com.graph.graphservice.repository;

import java.util.UUID;

public record ContractDto(
    UUID id,
    String contractName,
    String contractNo,
    Integer renewalNo,
    Integer endorsementNo
) {
}
