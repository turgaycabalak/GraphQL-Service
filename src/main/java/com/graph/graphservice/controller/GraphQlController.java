package com.graph.graphservice.controller;

import java.util.List;
import java.util.UUID;

import com.graph.graphservice.dto.ContractResponse;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.mapper.ContractMapper;
import com.graph.graphservice.repository.ContractRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GraphQlController {
  private final ContractRepository contractRepository;

  @QueryMapping
  public ContractResponse getContract(@Argument("contractId") UUID contractId) {
    ContractEntity contractEntity = contractRepository.findById(contractId)
        .orElseThrow(() -> new IllegalArgumentException("Contract Not Found"));

    return ContractMapper.INSTANCE.toModel(contractEntity);
  }

  @QueryMapping
  public List<ContractResponse> getAllContracts() {
    List<ContractEntity> contracts = contractRepository.findAll();

    return ContractMapper.INSTANCE.toModels(contracts);
  }

}
