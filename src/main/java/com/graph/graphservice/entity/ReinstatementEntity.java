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
@Table(name = "reinstatement")
public class ReinstatementEntity {
  @Id
  @Column(columnDefinition = "UUID default gen_random_uuid()")
  private UUID id;

  private int reinstatementOrder;
  private BigDecimal reinstatementRatio;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "layer_id", columnDefinition = "uuid default null", nullable = false)
  @JsonIgnore
  private LayerEntity layer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id", columnDefinition = "uuid default null", nullable = false)
  @JsonIgnore
  private ContractEntity contract;
}
