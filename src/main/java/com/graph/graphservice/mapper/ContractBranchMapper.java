package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.graph.graphservice.dto.ContractBranchResponse;
import com.graph.graphservice.entity.ContractBranchEntity;

import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {ContractCoverMapper.class})
public interface ContractBranchMapper {
  ContractBranchMapper INSTANCE = Mappers.getMapper(ContractBranchMapper.class);

  ContractBranchResponse toModel(ContractBranchEntity entity);

  List<ContractBranchResponse> toModels(Collection<ContractBranchEntity> entities);

  @AfterMapping
  default void sort(@MappingTarget List<ContractBranchResponse> responses) {
    if (ObjectUtils.isNotEmpty(responses)) {
      responses.sort(Comparator.comparing(ContractBranchResponse::branchEnum));
    }
  }
}
