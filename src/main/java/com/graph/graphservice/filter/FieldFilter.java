package com.graph.graphservice.filter;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class FieldFilter<T> {
  private FilterOperator operator;
  private T value;
  private List<T> values;
  private Boolean caseSensitive;

  // Range için
  private T start;
  private T end;
}
