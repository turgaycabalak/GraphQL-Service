package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.graph.graphservice.dto.ContractResponse;
import com.graph.graphservice.entity.ContractEntity;

import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {LayerMapper.class})
public interface ContractMapper {
  ContractMapper INSTANCE = Mappers.getMapper(ContractMapper.class);

  ContractResponse toModel(ContractEntity entity);

  List<ContractResponse> toModels(Collection<ContractEntity> entities);

  @AfterMapping
  default void sort(@MappingTarget List<ContractResponse> responses) {
    if (ObjectUtils.isNotEmpty(responses)) {
      responses.sort(
          Comparator.comparing(
                  ContractResponse::contractNo,
                  Comparator.nullsLast(Comparator.comparingInt(this::parseIntOrMax))
              )
              .thenComparing(
                  ContractResponse::renewalNo,
                  Comparator.nullsLast(Integer::compareTo)
              )
              .thenComparing(
                  ContractResponse::endorsementNo,
                  Comparator.nullsLast(Integer::compareTo)
              )
      );
    }
  }

  default int parseIntOrMax(String s) {
    if (s == null) {
      return Integer.MAX_VALUE;
    }
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }
}
