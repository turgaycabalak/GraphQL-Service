package com.graph.graphservice.controller;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.graph.graphservice.dto.ContractResponse;
import com.graph.graphservice.dto.FieldNode;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.mapper.ContractMapper;
import com.graph.graphservice.repository.ContractRepository;
import com.graph.graphservice.repository.DynamicContractRepository;
import com.graph.graphservice.repository.DynamicContractRepositoryV2;
import com.graph.graphservice.utils.FieldNodeUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GraphQlController {
  private final ContractRepository contractRepository;
  private final DynamicContractRepository dynamicContractRepository;
  private final DynamicContractRepositoryV2 dynamicContractRepositoryV2;

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


  @QueryMapping
  public ContractResponse getContractDynamicSql(@Argument("contractId") UUID contractId,
                                                DataFetchingEnvironment env) {
    DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();

    Set<String> contractFields = new HashSet<>();
    Set<String> layerFields = new HashSet<>();

    selectionSet.getFields().forEach(field -> {
      String path = field.getQualifiedName();
      System.out.println(path);

      if (path.startsWith("layers/")) {
        layerFields.add(path.replace("layers/", ""));
      } else if (!"layers".equals(path)) { // root field layers’ı exclude et
        contractFields.add(field.getName());
      }
    });

    System.out.println("Contract fields: " + contractFields);
    System.out.println("Layer fields" + layerFields);

    ContractEntity contractEntity =
        dynamicContractRepository.findContractDynamic(contractId, contractFields, layerFields);

    return ContractMapper.INSTANCE.toModel(contractEntity);
  }

  @QueryMapping
  public ContractResponse getContractDynamicSqlV2(@Argument("contractId") UUID contractId,
                                                  DataFetchingEnvironment env) {
    DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();

    // FieldNode ağacını kur
    FieldNode rootNode = FieldNodeUtil.buildFieldTree(selectionSet, "contract");

    ContractEntity contractEntity = dynamicContractRepositoryV2.findContractDynamic(contractId, rootNode);

    return ContractMapper.INSTANCE.toModel(contractEntity);
  }
}
