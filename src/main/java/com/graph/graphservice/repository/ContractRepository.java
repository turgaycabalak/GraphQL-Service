package com.graph.graphservice.repository;

import java.util.UUID;

import com.graph.graphservice.entity.ContractEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractRepository extends JpaRepository<ContractEntity, UUID> {
}
