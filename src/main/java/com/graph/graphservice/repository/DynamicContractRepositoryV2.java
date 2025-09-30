package com.graph.graphservice.repository;

import java.util.UUID;

import jakarta.persistence.EntityManager;

import com.graph.graphservice.dto.FieldNode;
import com.graph.graphservice.entity.ContractEntity;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DynamicContractRepositoryV2 {
  private final EntityManager entityManager;

  public ContractEntity findContractDynamic(UUID contractId, FieldNode rootFields) {

    return null;
  }
}