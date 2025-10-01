package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.graph.graphservice.dto.ContractCoverResponse;
import com.graph.graphservice.entity.ContractCoverEntity;

import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ContractCoverMapper {
  ContractCoverMapper INSTANCE = Mappers.getMapper(ContractCoverMapper.class);

  ContractCoverResponse toModel(ContractCoverEntity entity);

  List<ContractCoverResponse> toModels(Collection<ContractCoverEntity> entities);

  @AfterMapping
  default void sort(@MappingTarget List<ContractCoverResponse> responses) {
    if (ObjectUtils.isNotEmpty(responses)) {
      responses.sort(Comparator.comparing(ContractCoverResponse::coverEnum));
    }
  }
}
