package com.graph.graphservice.filter;

import java.math.BigDecimal;

import com.graph.graphservice.entity.ContractStatusEnum;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class LayerFilter extends BaseFilter {
  private FieldFilter<Integer> layerOrder;
  private FieldFilter<BigDecimal> lossLimitAmount;
  private FieldFilter<BigDecimal> lossLimitAmountRc;
  private FieldFilter<BigDecimal> deductibleAmount;
  private FieldFilter<BigDecimal> deductibleAmountRc;
  private ReinstatementListFilter reinstatements;
}
