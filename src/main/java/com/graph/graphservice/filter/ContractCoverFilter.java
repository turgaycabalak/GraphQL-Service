package com.graph.graphservice.filter;

import java.math.BigDecimal;

import com.graph.graphservice.entity.CoverEnum;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ContractCoverFilter extends BaseFilter {
  private FieldFilter<CoverEnum> coverEnum;
  private FieldFilter<BigDecimal> premiumAmount;
}
