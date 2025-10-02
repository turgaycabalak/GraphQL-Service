package com.graph.graphservice.filter;

import java.util.List;

import lombok.Data;

/**
 * Filter Base Class.
 */
@Data
public abstract class BaseFilter {
  private List<BaseFilter> and;
  private List<BaseFilter> or;
  private BaseFilter not;
}
