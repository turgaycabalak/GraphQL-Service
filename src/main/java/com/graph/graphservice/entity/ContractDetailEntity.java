package com.graph.graphservice.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "contract_detail")
public class ContractDetailEntity {
  @Id
  @Column(columnDefinition = "UUID default gen_random_uuid()")
  private UUID id;

  private LocalDateTime startDate;
  private LocalDateTime endDate;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contract_id", nullable = false)
  @JsonIgnore
  private ContractEntity contract;
}
