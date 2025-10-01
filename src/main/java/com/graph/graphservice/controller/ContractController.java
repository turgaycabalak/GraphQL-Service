package com.graph.graphservice.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.javafaker.Faker;
import com.graph.graphservice.entity.BranchEnum;
import com.graph.graphservice.entity.ContractBranchEntity;
import com.graph.graphservice.entity.ContractCoverEntity;
import com.graph.graphservice.entity.ContractDetailEntity;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.entity.ContractStatusEnum;
import com.graph.graphservice.entity.CoverEnum;
import com.graph.graphservice.entity.LayerEntity;
import com.graph.graphservice.entity.ReinstatementEntity;
import com.graph.graphservice.repository.ContractBranchRepository;
import com.graph.graphservice.repository.ContractRepository;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contract")
@RequiredArgsConstructor
public class ContractController {
  private final ContractRepository contractRepository;
  private final ContractBranchRepository contractBranchRepository;
  private final Random random = new Random();
  private final Faker faker = new Faker();


  @GetMapping
  public List<ContractEntity> saveDummies() {
    List<ContractEntity> contracts = IntStream.range(0, 50)
        .mapToObj(i -> {
          boolean isFinalized = i < 20; // İlk 20 tanesi FINALIZED olsun
          ContractStatusEnum status = isFinalized ? ContractStatusEnum.FINALIZED : ContractStatusEnum.DRAFT;

          // Contract oluştur
          ContractEntity contract = ContractEntity.builder()
              .id(UUID.randomUUID())
              .contractName(faker.company().name())
              .contractNo(faker.number().digits(6))
              .renewalNo(0)
              .endorsementNo(0)
              .contractStatus(status)
              .build();

          if (isFinalized) {
            // ContractDetail ekle
            ContractDetailEntity detail = ContractDetailEntity.builder()
                .id(UUID.randomUUID())
                .contract(contract)
                .startDate(LocalDateTime.now().minusDays(random.nextInt(365)))
                .endDate(LocalDateTime.now().plusDays(random.nextInt(365)))
                .build();
            contract.setContractDetail(detail);

            // 1-3 Coverages ekle (farklı BranchEnum olacak şekilde)
            List<BranchEnum> availableBranches = new ArrayList<>(Arrays.asList(BranchEnum.values()));
            Collections.shuffle(availableBranches);
            int coverageCount = random.nextInt(3) + 1;

            Set<ContractBranchEntity> coverages = IntStream.range(0, coverageCount)
                .mapToObj(idx -> {
                  BranchEnum branch = availableBranches.get(idx);
                  return ContractBranchEntity.builder()
                      .id(UUID.randomUUID())
                      .branchEnum(branch)
                      .premiumAmount(BigDecimal.valueOf(faker.number().randomDouble(2, 10_000, 500_000)))
                      .contract(contract)
                      .build();
                })
                .collect(Collectors.toSet());
            contract.setContractBranches(coverages);

            // 1-8 Layer ekle
            int layerCount = random.nextInt(8) + 1;
            Set<LayerEntity> layers = IntStream.range(0, layerCount)
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

                  // Bazı layer'ların reinstatement'ı olacak
                  if (random.nextBoolean()) {
                    int reinstatementCount = 3 + random.nextInt(3); // 3-5 arası
                    Set<ReinstatementEntity> reinstatements = IntStream.range(0, reinstatementCount)
                        .mapToObj(rIndex -> ReinstatementEntity.builder()
                            .id(UUID.randomUUID())
                            .layer(layer)
                            .contract(contract)
                            .reinstatementOrder(rIndex + 1)
                            .reinstatementRatio(BigDecimal.valueOf(faker.number().randomDouble(2, 1, 100)))
                            .build())
                        .collect(Collectors.toSet());
                    layer.setReinstatements(reinstatements);
                  }
                  return layer;
                })
                .collect(Collectors.toSet());
            contract.setLayers(layers);
          }
          return contract;
        })
        .toList();

    return contractRepository.saveAll(contracts);
  }

  @GetMapping("/set-covers")
  public void setCovers() {
    List<ContractBranchEntity> updatedBranches = contractRepository.findAll().stream()
        .filter(c -> ObjectUtils.isNotEmpty(c.getContractBranches()))
        .flatMap(contract -> contract.getContractBranches().stream())
        .filter(cb -> ObjectUtils.isEmpty(cb.getContractCovers())) // sadece covers olmayan branchler
        .map(cb -> {
          BranchEnum branchEnum = cb.getBranchEnum();
          Set<CoverEnum> possibleCovers = branchEnum.getCoverEnums();

          // 1 .. N adet cover seçelim
          int coverCount = 1 + random.nextInt(possibleCovers.size());
          List<CoverEnum> shuffled = new ArrayList<>(possibleCovers);
          Collections.shuffle(shuffled);

          Set<ContractCoverEntity> covers = IntStream.range(0, coverCount)
              .mapToObj(i -> ContractCoverEntity.builder()
                  .id(UUID.randomUUID())
                  .coverEnum(shuffled.get(i))
                  .premiumAmount(BigDecimal.valueOf(
                      faker.number().randomDouble(2, 10_000, 500_000)
                  ))
                  .contractBranch(cb)
                  .contract(cb.getContract())
                  .build())
              .collect(Collectors.toSet());

          cb.setContractCovers(covers);

          // Branch'in premiumAmount = cover'ların toplamı
          BigDecimal totalPremium = covers.stream()
              .map(ContractCoverEntity::getPremiumAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

          cb.setPremiumAmount(totalPremium);

          return cb;
        })
        .toList();

    contractBranchRepository.saveAll(updatedBranches);
  }
}
