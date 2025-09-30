package com.graph.graphservice.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.javafaker.Faker;
import com.graph.graphservice.entity.ContractDetailEntity;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.entity.LayerEntity;
import com.graph.graphservice.entity.ReinstatementEntity;
import com.graph.graphservice.repository.ContractRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
public class ContractController {
  private final ContractRepository contractRepository;

  @GetMapping
  public List<ContractEntity> saveDummies() {
    Faker faker = new Faker();
    Random random = new Random();

    List<ContractEntity> contracts = IntStream.range(0, 10)
        .mapToObj(i -> {
          // Contract oluştur
          ContractEntity contract = ContractEntity.builder()
              .id(UUID.randomUUID())
              .contractName(faker.company().name())
              .contractNo(faker.number().digits(6))
              .renewalNo(0)
              .endorsementNo(0)
              .build();

          // ContractDetail ekle
          ContractDetailEntity detail = ContractDetailEntity.builder()
              .id(UUID.randomUUID())
              .contract(contract)
              .startDate(LocalDateTime.now().minusDays(random.nextInt(365)))
              .endDate(LocalDateTime.now().plusDays(random.nextInt(365)))
              .build();
          contract.setContractDetail(detail);

          // 1-3 Layer ekle
          Set<LayerEntity> layers = IntStream.range(0, random.nextInt(3) + 1)
              .mapToObj(layerIndex -> {
                LayerEntity layer = LayerEntity.builder()
                    .id(UUID.randomUUID())
                    .contract(contract)
                    .layerOrder(layerIndex + 1)
                    .lossLimitAmount(BigDecimal.valueOf(faker.number().randomDouble(2, 100_000, 1_000_000)))
                    .lossLimitAmountRc(BigDecimal.valueOf(faker.number().randomDouble(2, 100_000, 1_000_000)))
                    .deductibleAmount(BigDecimal.valueOf(faker.number().randomDouble(2, 10_000, 100_000)))
                    .deductibleAmountRc(BigDecimal.valueOf(faker.number().randomDouble(2, 10_000, 100_000)))
                    .build();

                // 0-2 Reinstatement ekle
                Set<ReinstatementEntity> reinstatements = IntStream.range(0, random.nextInt(3))
                    .mapToObj(rIndex -> ReinstatementEntity.builder()
                        .id(UUID.randomUUID())
                        .layer(layer)
                        .contract(contract)
                        .reinstatementOrder(rIndex + 1)
                        .reinstatementRatio(BigDecimal.valueOf(faker.number().randomDouble(2, 1, 100)))
                        .build())
                    .collect(Collectors.toSet());

                layer.setReinstatements(reinstatements);
                return layer;
              })
              .collect(Collectors.toSet());

          contract.setLayers(layers);
          return contract;
        })
        .toList();

    return contractRepository.saveAll(contracts);
  }
}
