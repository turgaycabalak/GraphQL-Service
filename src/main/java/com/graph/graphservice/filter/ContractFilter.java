package com.graph.graphservice.filter;

import com.graph.graphservice.entity.ContractStatusEnum;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ContractFilter extends BaseFilter {
  private FieldFilter<String> contractName;
  private FieldFilter<String> contractNo;
  private FieldFilter<ContractStatusEnum> contractStatus;
  private FieldFilter<Integer> renewalNo;
  private FieldFilter<Integer> endorsementNo;

  private ContractDetailFilter contractDetail;
  private ContractBranchListFilter contractBranches;
  private LayerListFilter layers;
}
