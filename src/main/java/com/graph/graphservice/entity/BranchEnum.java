package com.graph.graphservice.entity;

import static com.graph.graphservice.entity.CoverEnum.CERTIFICATION;
import static com.graph.graphservice.entity.CoverEnum.CMR;
import static com.graph.graphservice.entity.CoverEnum.FIDELITY;
import static com.graph.graphservice.entity.CoverEnum.FLOOD;
import static com.graph.graphservice.entity.CoverEnum.GLASS;
import static com.graph.graphservice.entity.CoverEnum.GREENHOUSE;
import static com.graph.graphservice.entity.CoverEnum.PERSONAL_ACCIDENT;
import static com.graph.graphservice.entity.CoverEnum.POULTRY;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BranchEnum {
  EARTHQUAKE(Set.of(CERTIFICATION, POULTRY, FLOOD)),
  FIRE(Set.of(GREENHOUSE, GLASS)),
  CARGO(Set.of(PERSONAL_ACCIDENT, CERTIFICATION, CMR, FIDELITY, CoverEnum.CARGO));

  private final Set<CoverEnum> coverEnums;
}
