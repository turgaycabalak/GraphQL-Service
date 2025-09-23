package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.List;

import com.graph.graphservice.dto.ContractResponse;
import com.graph.graphservice.entity.ContractEntity;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {LayerMapper.class})
public interface ContractMapper {
  ContractMapper INSTANCE = Mappers.getMapper(ContractMapper.class);

  ContractResponse toModel(ContractEntity entity);

  List<ContractResponse> toModels(Collection<ContractEntity> entities);

}
