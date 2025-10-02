package com.graph.graphservice.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ContractBranchListFilter {
  private ContractBranchFilter some;
  private ContractBranchFilter every;
  private ContractBranchFilter none;
}
