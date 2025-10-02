package com.graph.graphservice.filter;

import java.math.BigDecimal;

import com.graph.graphservice.entity.BranchEnum;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ContractBranchFilter extends BaseFilter {
  private FieldFilter<BranchEnum> branchEnum;
  private FieldFilter<BigDecimal> premiumAmount;
  private ContractCoverListFilter contractCovers;
}
