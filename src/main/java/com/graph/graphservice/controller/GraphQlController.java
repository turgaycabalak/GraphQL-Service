package com.graph.graphservice.controller;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graph.graphservice.dto.ContractResponse;
import com.graph.graphservice.dto.ContractSearchResult;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.filter.ContractFilter;
import com.graph.graphservice.mapper.ContractMapper;
import com.graph.graphservice.repository.ContractRepository;
import com.graph.graphservice.repository.DynamicContractRepository;
import com.graph.graphservice.repository.DynamicContractRepositoryV2;
import com.graph.graphservice.repository.DynamicContractRepositoryV3;
import com.graph.graphservice.repository.DynamicFilterRepository;
import com.graph.graphservice.utils.GraphQLFieldCollector;

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
  private final DynamicContractRepositoryV3 dynamicContractRepositoryV3;
  private final DynamicFilterRepository dynamicFilterRepository;

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
    Set<String> detailFields = new HashSet<>();
    Set<String> layerFields = new HashSet<>();
    Set<String> reinFields = new HashSet<>();

    selectionSet.getFields().forEach(field -> {
      String path = field.getQualifiedName(); // örn: "layers/reinstatements/reinstatementOrder"

      if (path.startsWith("layers/reinstatements/")) {
        reinFields.add(path.replace("layers/reinstatements/", ""));
      } else if (path.startsWith("layers/")) {
        layerFields.add(path.replace("layers/", ""));
      } else if (path.startsWith("contractDetail/")) {
        detailFields.add(path.replace("contractDetail/", ""));
      } else if (!"layers".equals(path) && !"contractDetail".equals(path)) {
        contractFields.add(field.getName());
      }
    });

    ContractEntity contractEntity = dynamicContractRepository.findContractDynamic(
        contractId, contractFields, detailFields, layerFields, reinFields
    );

    return ContractMapper.INSTANCE.toModel(contractEntity);
  }

  @QueryMapping
  public ContractResponse getContractDynamicSqlV2(@Argument("contractId") UUID contractId,
                                                  DataFetchingEnvironment env) {
    DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();

    Set<String> contractFields = new HashSet<>();
    Set<String> layerFields = new HashSet<>();
    Set<String> reinstatementFields = new HashSet<>();
    Set<String> contractDetailFields = new HashSet<>();

    selectionSet.getFields().forEach(field -> {
      String path = field.getQualifiedName();

      if (path.startsWith("layers/")) {
        String remainingPath = path.replace("layers/", "");
        if (remainingPath.startsWith("reinstatements/")) {
          reinstatementFields.add(remainingPath.replace("reinstatements/", ""));
        } else {
          layerFields.add(remainingPath);
        }
      } else if (path.startsWith("contractDetail/")) {
        contractDetailFields.add(path.replace("contractDetail/", ""));
      } else if (!"layers".equals(path) && !"contractDetail".equals(path)) {
        contractFields.add(field.getName());
      }
    });

    System.out.println("Contract fields: " + contractFields);
    System.out.println("Layer fields: " + layerFields);
    System.out.println("Reinstatement fields: " + reinstatementFields);
    System.out.println("ContractDetail fields: " + contractDetailFields);

    ContractEntity contractEntity = dynamicContractRepositoryV2.findContractDynamic(
        contractId, contractFields, layerFields, reinstatementFields, contractDetailFields);

    return ContractMapper.INSTANCE.toModel(contractEntity);
  }

  @QueryMapping
  public ContractResponse getContractDynamicSqlV3(@Argument("contractId") UUID contractId,
                                                  DataFetchingEnvironment env) {
    // GraphQLFieldCollector kullanılarak seçilen field'lar toplanıyor
    Map<Class<?>, Set<String>> selectedFields = GraphQLFieldCollector.collectFields(env, ContractEntity.class);

    // Dynamic repository ile sadece istenen field'lar ve ilişkiler yükleniyor
    ContractEntity contractEntity = dynamicContractRepositoryV3.findEntityDynamic(
        contractId, ContractEntity.class, selectedFields);

    return ContractMapper.INSTANCE.toModel(contractEntity);
  }

  @QueryMapping
  public ContractSearchResult searchContracts(@Argument("filter") Map<String, Object> filterInput,
                                              @Argument("page") Integer page,
                                              @Argument("size") Integer size,
                                              @Argument("sort") List<String> sort,
                                              DataFetchingEnvironment env) {

    // Filter input'unu Java objesine çevir
    ContractFilter filter = convertFilterInput(filterInput);

    // Seçilen field'ları topla
    Map<Class<?>, Set<String>> selectedFields = GraphQLFieldCollector.collectFields(env, ContractEntity.class);

    // Sayfalama
    int pageNum = page != null ? page : 0;
    int pageSize = size != null ? size : 20;
    List<String> sortFields = sort != null ? sort : List.of("contractNo");

    // Sorguyu çalıştır
    List<ContractEntity> contracts = dynamicFilterRepository.searchEntities(
        ContractEntity.class, filter, selectedFields, pageNum, pageSize, sortFields);

    // Toplam sayıyı al (pagination için)
    Long totalCount = dynamicFilterRepository.countEntities(ContractEntity.class, filter);

    return ContractSearchResult.builder()
        .contracts(contracts.stream()
            .map(ContractMapper.INSTANCE::toModel)
            .collect(Collectors.toList()))
        .totalCount(totalCount.intValue())
        .page(pageNum)
        .size(pageSize)
        .totalPages((int) Math.ceil((double) totalCount / pageSize))
        .build();
  }

  private ContractFilter convertFilterInput(Map<String, Object> filterInput) {
    // Map'ten Java objesine dönüşüm
    // Bu kısım Jackson ObjectMapper veya manuel mapping ile yapılabilir
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(filterInput, ContractFilter.class);
  }
}
