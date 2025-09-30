package com.graph.graphservice.repository;

import java.math.BigDecimal;
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
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.entity.LayerEntity;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DynamicContractRepository {
  private final EntityManager entityManager;

  public ContractEntity findContractDynamic(UUID contractId,
                                            Collection<String> contractFields,
                                            Collection<String> layerFields) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<ContractEntity> contractRoot = cq.from(ContractEntity.class);

    // Seçilecek alanlar
    List<Selection<?>> selections = new ArrayList<>();

    // Contract alanlarını ekle
    for (String field : contractFields) {
      Path<Object> objectPath = contractRoot.get(field);
      Selection<Object> alias = objectPath.alias(field);
      selections.add(alias);
    }

    Join<ContractEntity, LayerEntity> layerJoin = null;
    boolean layerFieldsNotEmpty = ObjectUtils.isNotEmpty(layerFields);
    if (layerFieldsNotEmpty) {
      layerJoin = contractRoot.join("layers", JoinType.LEFT);

      for (String field : layerFields) {
        Path<Object> objectPath = layerJoin.get(field);
        Selection<Object> alias = objectPath.alias("layer_" + field);
        selections.add(alias);
      }
    }

    cq.multiselect(selections)
        .where(cb.equal(contractRoot.get("id"), contractId));

    List<Tuple> result = entityManager.createQuery(cq).getResultList();

    boolean isThereAnyResultFromQuery = result.isEmpty();
    if (isThereAnyResultFromQuery) {
      return null;
    }

    // ContractEntity'yi manuel map et
    ContractEntity contract = new ContractEntity();
    Set<LayerEntity> layers = new HashSet<>();

    for (Tuple tuple : result) {
      // Contract alanları
      for (String field : contractFields) {
        Object value = tuple.get(field);
        setContractField(contract, field, value);
      }

      // Layer alanları
      if (layerJoin != null) {
        LayerEntity layer = new LayerEntity();
        for (String field : layerFields) {
          Object value = tuple.get("layer_" + field);
          setLayerField(layer, field, value);
        }
        layer.setContract(contract);
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
}
