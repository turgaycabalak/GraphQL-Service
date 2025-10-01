package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.graph.graphservice.dto.CoverageResponse;
import com.graph.graphservice.entity.ContractBranchEntity;

import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CoverageMapper {
  CoverageMapper INSTANCE = Mappers.getMapper(CoverageMapper.class);

  CoverageResponse toModel(ContractBranchEntity entity);

  List<CoverageResponse> toModels(Collection<ContractBranchEntity> entities);

  @AfterMapping
  default void sort(@MappingTarget List<CoverageResponse> responses) {
    if (ObjectUtils.isNotEmpty(responses)) {
      responses.sort(Comparator.comparing(CoverageResponse::branchEnum));
    }
  }
}
