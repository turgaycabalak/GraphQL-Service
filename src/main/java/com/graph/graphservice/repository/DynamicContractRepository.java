package com.graph.graphservice.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import com.graph.graphservice.entity.ContractDetailEntity;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.entity.LayerEntity;
import com.graph.graphservice.entity.ReinstatementEntity;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DynamicContractRepository {
  private final EntityManager entityManager;

  public ContractEntity findContractDynamic(UUID contractId,
                                            Collection<String> contractFields,
                                            Collection<String> contractDetailFields,
                                            Collection<String> layerFields,
                                            Collection<String> reinstatementFields) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<ContractEntity> contractRoot = cq.from(ContractEntity.class);

    List<Selection<?>> selections = new ArrayList<>();

    // Contract fields
    for (String field : contractFields) {
      selections.add(contractRoot.get(field).alias(field));
    }

    // ContractDetail join
    Join<ContractEntity, ContractDetailEntity> detailJoin = null;
    if (ObjectUtils.isNotEmpty(contractDetailFields)) {
      detailJoin = contractRoot.join("contractDetail", JoinType.LEFT);
      for (String field : contractDetailFields) {
        selections.add(detailJoin.get(field).alias("detail_" + field));
      }
    }

    // Layer join
    Join<ContractEntity, LayerEntity> layerJoin = null;
    if (ObjectUtils.isNotEmpty(layerFields) || ObjectUtils.isNotEmpty(reinstatementFields)) {
      layerJoin = contractRoot.join("layers", JoinType.LEFT);
      for (String field : layerFields) {
        selections.add(layerJoin.get(field).alias("layer_" + field));
      }
    }

    // Reinstatement join
    Join<LayerEntity, ReinstatementEntity> reinJoin = null;
    if (ObjectUtils.isNotEmpty(reinstatementFields)) {
      reinJoin = layerJoin.join("reinstatements", JoinType.LEFT);
      for (String field : reinstatementFields) {
        selections.add(reinJoin.get(field).alias("rein_" + field));
      }
    }

    cq.multiselect(selections)
        .where(cb.equal(contractRoot.get("id"), contractId));

    List<Tuple> result = entityManager.createQuery(cq).getResultList();
    if (result.isEmpty()) {
      return null;
    }

    // Manuel map
    ContractEntity contract = new ContractEntity();
    Set<LayerEntity> layers = new HashSet<>();
    ContractDetailEntity detail = new ContractDetailEntity();

    for (Tuple tuple : result) {
      // Contract fields
      for (String field : contractFields) {
        setContractField(contract, field, tuple.get(field));
      }

      // ContractDetail fields
      if (detailJoin != null) {
        for (String field : contractDetailFields) {
          setContractDetailField(detail, field, tuple.get("detail_" + field));
        }
        detail.setContract(contract);
        contract.setContractDetail(detail);
      }

      // Layer fields
      if (layerJoin != null) {
        LayerEntity layer = new LayerEntity();
        for (String field : layerFields) {
          setLayerField(layer, field, tuple.get("layer_" + field));
        }
        layer.setContract(contract);

        // Reinstatement fields
        if (reinJoin != null) {
          ReinstatementEntity rein = new ReinstatementEntity();
          for (String field : reinstatementFields) {
            setReinstatementField(rein, field, tuple.get("rein_" + field));
          }
          rein.setContract(contract);
          rein.setLayer(layer);
          layer.setReinstatements(Set.of(rein));
        }

        layers.add(layer);
      }
    }

    contract.setLayers(layers);
    return contract;
  }

  private void setContractField(ContractEntity contract, String field, Object value) {
    switch (field) {
      case "id" -> contract.setId((UUID) value);
      case "contractName" -> contract.setContractName((String) value);
      case "contractNo" -> contract.setContractNo((String) value);
      case "renewalNo" -> contract.setRenewalNo((Integer) value);
      case "endorsementNo" -> contract.setEndorsementNo((Integer) value);
    }
  }

  private void setContractDetailField(ContractDetailEntity detail, String field, Object value) {
    switch (field) {
      case "id" -> detail.setId((UUID) value);
      case "startDate" -> detail.setStartDate((LocalDateTime) value);
      case "endDate" -> detail.setEndDate((LocalDateTime) value);
    }
  }

  private void setLayerField(LayerEntity layer, String field, Object value) {
    switch (field) {
      case "id" -> layer.setId((UUID) value);
      case "layerOrder" -> layer.setLayerOrder((Integer) value);
      case "lossLimitAmount" -> layer.setLossLimitAmount((BigDecimal) value);
      case "lossLimitAmountRc" -> layer.setLossLimitAmountRc((BigDecimal) value);
      case "deductibleAmount" -> layer.setDeductibleAmount((BigDecimal) value);
      case "deductibleAmountRc" -> layer.setDeductibleAmountRc((BigDecimal) value);
    }
  }

  private void setReinstatementField(ReinstatementEntity rein, String field, Object value) {
    switch (field) {
      case "id" -> rein.setId((UUID) value);
      case "reinstatementOrder" -> rein.setReinstatementOrder((Integer) value);
      case "reinstatementRatio" -> rein.setReinstatementRatio((BigDecimal) value);
    }
  }
}
