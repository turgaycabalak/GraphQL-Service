package com.graph.graphservice.mapper;

import java.util.Collection;
import java.util.List;

import com.graph.graphservice.dto.LayerResponse;
import com.graph.graphservice.entity.LayerEntity;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface LayerMapper {
  LayerMapper INSTANCE = Mappers.getMapper(LayerMapper.class);

  LayerResponse toModel(LayerEntity entity);

  List<LayerResponse> toModels(Collection<LayerEntity> entities);
}
