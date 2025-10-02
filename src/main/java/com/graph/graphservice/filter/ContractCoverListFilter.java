package com.graph.graphservice.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ContractCoverListFilter extends BaseFilter {
  private ContractCoverFilter some;
  private ContractCoverFilter every;
  private ContractCoverFilter none;
}
