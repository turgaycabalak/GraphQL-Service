package com.graph.graphservice.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LayerListFilter extends BaseFilter {
  private LayerFilter some;
  private LayerFilter every;
  private LayerFilter none;
}
