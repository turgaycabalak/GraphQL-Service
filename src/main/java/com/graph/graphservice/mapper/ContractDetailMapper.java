package com.graph.graphservice.mapper;

import com.graph.graphservice.entity.ContractDetailEntity;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ContractDetailMapper {
  ContractDetailMapper INSTANCE = Mappers.getMapper(ContractDetailMapper.class);

  ContractDetailEntity toModel(ContractDetailEntity entity);
}
