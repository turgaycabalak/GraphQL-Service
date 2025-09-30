package com.graph.graphservice.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@Table(name = "layer")
public class LayerEntity {
  @Id
  @Column(columnDefinition = "UUID default gen_random_uuid()")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id", nullable = false)
  @JsonIgnore
  private ContractEntity contract;

  private int layerOrder;

  private BigDecimal lossLimitAmount;
  private BigDecimal lossLimitAmountRc;
  private BigDecimal deductibleAmount;
  private BigDecimal deductibleAmountRc;
}
