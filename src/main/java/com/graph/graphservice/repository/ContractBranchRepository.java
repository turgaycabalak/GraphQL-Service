package com.graph.graphservice.repository;

import java.util.UUID;

import com.graph.graphservice.entity.ContractBranchEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractBranchRepository extends JpaRepository<ContractBranchEntity, UUID> {
}
