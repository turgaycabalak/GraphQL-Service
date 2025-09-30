package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.graph.graphservice.dto.LayerResponse;
import com.graph.graphservice.entity.LayerEntity;

import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface LayerMapper {
  LayerMapper INSTANCE = Mappers.getMapper(LayerMapper.class);

  LayerResponse toModel(LayerEntity entity);

  List<LayerResponse> toModels(Collection<LayerEntity> entities);

  @AfterMapping
  default void sortByLayerOrder(@MappingTarget List<LayerResponse> responses) {
    if (ObjectUtils.isNotEmpty(responses)) {
      responses.sort(Comparator.comparingInt(LayerResponse::layerOrder));
    }
  }
}
