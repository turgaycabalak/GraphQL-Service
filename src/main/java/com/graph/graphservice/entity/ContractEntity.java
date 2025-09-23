package com.graph.graphservice.entity;

import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@Table(name = "contract")
public class ContractEntity {
  @Id
  @Column(columnDefinition = "UUID default gen_random_uuid()")
  private UUID id;

  private String contractName;
  private String contractNo;
  private Integer renewalNo;
  private Integer endorsementNo;

  @OneToMany(mappedBy = "contract", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  private Set<LayerEntity> layers;
}
