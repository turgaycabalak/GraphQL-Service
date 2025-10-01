package com.graph.graphservice.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.graph.graphservice.aspect.ArtificialRelation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@Table(name = "contract_cover")
public class ContractCoverEntity {
  @Id
  @Column(columnDefinition = "UUID default gen_random_uuid()")
  private UUID id;

  @Enumerated(EnumType.STRING)
  private CoverEnum coverEnum;
  private BigDecimal premiumAmount;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_branch_id", nullable = false)
  @JsonIgnore
  private ContractBranchEntity contractBranch;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id", nullable = false)
  @JsonIgnore
  @ArtificialRelation(description = "ContractCover directly linked to the contract (special requirement)")
  private ContractEntity contract;
}
