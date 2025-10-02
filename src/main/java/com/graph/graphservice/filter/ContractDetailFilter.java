package com.graph.graphservice.filter;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Entity-specific Filter.
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ContractDetailFilter extends BaseFilter {
  private FieldFilter<String> startDate; // DateTime as String
  private FieldFilter<String> endDate;
}
