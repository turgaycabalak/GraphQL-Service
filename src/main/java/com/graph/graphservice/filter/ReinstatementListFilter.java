package com.graph.graphservice.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ReinstatementListFilter extends BaseFilter {
  private ReinstatementFilter some;
  private ReinstatementFilter every;
  private ReinstatementFilter none;
}
