package com.graph.graphservice.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
public class DynamicContractRepositoryV2 {
  private final EntityManager entityManager;

  public ContractEntity findContractDynamic(UUID contractId,
                                            Collection<String> contractFields,
                                            Collection<String> layerFields,
                                            Collection<String> reinstatementFields,
                                            Collection<String> contractDetailFields) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<ContractEntity> contractRoot = cq.from(ContractEntity.class);

    List<Selection<?>> selections = new ArrayList<>();

    // Contract alanlarını ekle
    for (String field : contractFields) {
      selections.add(contractRoot.get(field).alias(field));
    }

    // ContractDetail join ve field'ları
    Join<ContractEntity, ContractDetailEntity> contractDetailJoin = null;
    if (ObjectUtils.isNotEmpty(contractDetailFields)) {
      contractDetailJoin = contractRoot.join("contractDetail", JoinType.LEFT);
      for (String field : contractDetailFields) {
        selections.add(contractDetailJoin.get(field).alias("contractDetail_" + field));
      }
    }

    // Layer join ve field'ları
    Join<ContractEntity, LayerEntity> layerJoin = null;
    Map<UUID, LayerEntity> layerMap = new HashMap<>();

    if (ObjectUtils.isNotEmpty(layerFields) || ObjectUtils.isNotEmpty(reinstatementFields)) {
      layerJoin = contractRoot.join("layers", JoinType.LEFT);

      // Layer field'ları
      for (String field : layerFields) {
        selections.add(layerJoin.get(field).alias("layer_" + field));
      }

      // Reinstatement join ve field'ları
      if (ObjectUtils.isNotEmpty(reinstatementFields)) {
        Join<LayerEntity, ReinstatementEntity> reinstatementJoin =
            layerJoin.join("reinstatements", JoinType.LEFT);

        for (String field : reinstatementFields) {
          selections.add(reinstatementJoin.get(field).alias("reinstatement_" + field));
        }
      }
    }

    cq.multiselect(selections)
        .where(cb.equal(contractRoot.get("id"), contractId));

    List<Tuple> result = entityManager.createQuery(cq).getResultList();

    if (result.isEmpty()) {
      return null;
    }

    return mapResultToContract(result, contractFields, layerFields,
        reinstatementFields, contractDetailFields, layerMap);
  }

  private ContractEntity mapResultToContract(List<Tuple> result,
                                             Collection<String> contractFields,
                                             Collection<String> layerFields,
                                             Collection<String> reinstatementFields,
                                             Collection<String> contractDetailFields,
                                             Map<UUID, LayerEntity> layerMap) {

    ContractEntity contract = new ContractEntity();
    Set<LayerEntity> layers = new HashSet<>();
    ContractDetailEntity contractDetail = null;
    Map<UUID, Set<ReinstatementEntity>> reinstatementMap = new HashMap<>();

    for (Tuple tuple : result) {
      // Contract alanları
      for (String field : contractFields) {
        setContractField(contract, field, tuple.get(field));
      }

      // ContractDetail alanları
      if (ObjectUtils.isNotEmpty(contractDetailFields)) {
        if (contractDetail == null) {
          contractDetail = new ContractDetailEntity();
        }
        for (String field : contractDetailFields) {
          setContractDetailField(contractDetail, field, tuple.get("contractDetail_" + field));
        }
        contractDetail.setContract(contract);
      }

      // Layer alanları
      if (ObjectUtils.isNotEmpty(layerFields) || ObjectUtils.isNotEmpty(reinstatementFields)) {
        UUID layerId = (UUID) tuple.get("layer_id");

        if (layerId != null) {
          LayerEntity layer = layerMap.get(layerId);
          if (layer == null) {
            layer = new LayerEntity();
            for (String field : layerFields) {
              setLayerField(layer, field, tuple.get("layer_" + field));
            }
            layer.setContract(contract);
            layerMap.put(layerId, layer);
            layers.add(layer);
            reinstatementMap.put(layerId, new HashSet<>());
          }

          // Reinstatement alanları
          if (ObjectUtils.isNotEmpty(reinstatementFields)) {
            UUID reinstatementId = (UUID) tuple.get("reinstatement_id");
            if (reinstatementId != null) {
              Set<ReinstatementEntity> reinstatements = reinstatementMap.get(layerId);
              ReinstatementEntity reinstatement = new ReinstatementEntity();

              for (String field : reinstatementFields) {
                setReinstatementField(reinstatement, field,
                    tuple.get("reinstatement_" + field));
              }

              reinstatement.setLayer(layer);
              reinstatement.setContract(contract);
              reinstatements.add(reinstatement);
            }
          }
        }
      }
    }

    // Reinstatement'ları layer'lara set et
    for (LayerEntity layer : layers) {
      Set<ReinstatementEntity> reinstatements = reinstatementMap.get(layer.getId());
      if (reinstatements != null) {
        layer.setReinstatements(reinstatements);
      }
    }

    contract.setLayers(layers);
    contract.setContractDetail(contractDetail);

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

  private void setReinstatementField(ReinstatementEntity reinstatement, String field, Object value) {
    switch (field) {
      case "id" -> reinstatement.setId((UUID) value);
      case "reinstatementOrder" -> reinstatement.setReinstatementOrder((Integer) value);
      case "reinstatementRatio" -> reinstatement.setReinstatementRatio((BigDecimal) value);
    }
  }

  private void setContractDetailField(ContractDetailEntity contractDetail, String field, Object value) {
    switch (field) {
      case "id" -> contractDetail.setId((UUID) value);
      case "startDate" -> contractDetail.setStartDate((LocalDateTime) value);
      case "endDate" -> contractDetail.setEndDate((LocalDateTime) value);
    }
  }
}