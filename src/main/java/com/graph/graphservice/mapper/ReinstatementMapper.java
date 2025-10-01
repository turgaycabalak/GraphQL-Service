package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.graph.graphservice.dto.ReinstatementResponse;
import com.graph.graphservice.entity.ReinstatementEntity;

import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ReinstatementMapper {
  ReinstatementMapper INSTANCE = Mappers.getMapper(ReinstatementMapper.class);

  //@Mapping(target = "layerOrder", source = "layer.layerOrder")
  ReinstatementResponse toModel(ReinstatementEntity entity);

  List<ReinstatementResponse> toModels(Collection<ReinstatementEntity> entities);

  @AfterMapping
  default void sort(@MappingTarget List<ReinstatementResponse> responses) {
    if (ObjectUtils.isNotEmpty(responses)) {
      //responses.sort(Comparator.comparingInt(ReinstatementResponse::layerOrder)
      //    .thenComparingInt(ReinstatementResponse::reinstatementOrder));

      responses.sort(Comparator.comparingInt(ReinstatementResponse::reinstatementOrder));
    }
  }
}
