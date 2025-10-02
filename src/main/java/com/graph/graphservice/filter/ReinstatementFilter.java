package com.graph.graphservice.filter;

import java.math.BigDecimal;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ReinstatementFilter extends BaseFilter {
  private FieldFilter<Integer> reinstatementOrder;
  private FieldFilter<BigDecimal> reinstatementRatio;
}
