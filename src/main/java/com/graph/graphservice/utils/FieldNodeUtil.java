package com.graph.graphservice.utils;

import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;

import java.util.List;
import java.util.stream.Collectors;

import com.graph.graphservice.dto.FieldNode;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FieldNodeUtil {

  public FieldNode buildFieldTree(DataFetchingFieldSelectionSet selectionSet, String rootName) {
    List<FieldNode> children = selectionSet.getImmediateFields().stream()
        .map(FieldNodeUtil::buildFieldNodeRecursive)
        .collect(Collectors.toList());

    return FieldNode.builder()
        .name(rootName)
        .children(children)
        .build();
  }

  public FieldNode buildFieldNodeRecursive(SelectedField field) {
    List<FieldNode> children = field.getSelectionSet().getFields().stream()
        .map(FieldNodeUtil::buildFieldNodeRecursive)
        .collect(Collectors.toList());

    return FieldNode.builder()
        .name(field.getName())
        .children(children)
        .build();
  }
}
