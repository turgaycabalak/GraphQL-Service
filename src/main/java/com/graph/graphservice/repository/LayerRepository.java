package com.graph.graphservice.repository;

import java.util.UUID;

import com.graph.graphservice.entity.LayerEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LayerRepository extends JpaRepository<LayerEntity, UUID> {
}
