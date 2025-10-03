package com.graph.graphservice.repository;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;

import com.graph.graphservice.aspect.ArtificialRelation;
import com.graph.graphservice.entity.ContractBranchEntity;
import com.graph.graphservice.entity.ContractCoverEntity;
import com.graph.graphservice.entity.ContractDetailEntity;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.entity.LayerEntity;
import com.graph.graphservice.entity.ReinstatementEntity;
import com.graph.graphservice.filter.BaseFilter;
import com.graph.graphservice.filter.ContractBranchFilter;
import com.graph.graphservice.filter.ContractBranchListFilter;
import com.graph.graphservice.filter.ContractCoverFilter;
import com.graph.graphservice.filter.ContractCoverListFilter;
import com.graph.graphservice.filter.ContractDetailFilter;
import com.graph.graphservice.filter.ContractFilter;
import com.graph.graphservice.filter.FieldFilter;
import com.graph.graphservice.filter.FilterOperator;
import com.graph.graphservice.filter.LayerFilter;
import com.graph.graphservice.filter.LayerListFilter;
import com.graph.graphservice.filter.ReinstatementFilter;
import com.graph.graphservice.filter.ReinstatementListFilter;
import com.graph.graphservice.utils.GraphQLFieldCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DynamicFilterRepository {
  private final EntityManager entityManager;


  public <T> List<T> searchEntities(Class<T> entityClass,
                                    BaseFilter filter,
                                    Map<Class<?>, Set<String>> selectedFields,
                                    int page,
                                    int size,
                                    List<String> sortFields) {

    log.debug("=== DYNAMIC FILTER REPOSITORY - SEARCH ENTITIES ===");
    log.debug("Entity Class: {}", entityClass.getSimpleName());
    log.debug("Selected Fields: {}", selectedFields);
    log.debug("Page: {}, Size: {}, Sort: {}", page, size, sortFields);

    if (selectedFields == null || selectedFields.isEmpty()) {
      log.warn("No fields selected for entity: {}", entityClass.getSimpleName());
      return new ArrayList<>();
    }

    // Sadece entity class'larını filtrele
    Map<Class<?>, Set<String>> filteredFields = selectedFields.entrySet().stream()
        .filter(entry -> isEntityClass(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (filteredFields.isEmpty()) {
      log.warn("No entity fields found in selected fields");
      return new ArrayList<>();
    }

    // 1. AŞAMA: Sadece ana entity ID'lerini bul (PostgreSQL uyumlu)
    List<UUID> entityIds = findEntityIds(entityClass, filter, page, size, sortFields);

    if (entityIds.isEmpty()) {
      return new ArrayList<>();
    }

    // 2. AŞAMA: ID'lerle birlikte tüm ilişkileri yükle
    return findEntitiesWithRelationships(entityClass, entityIds, filteredFields, sortFields);
  }

  public <T> Long countEntities(Class<T> entityClass, BaseFilter filter) {
    log.debug("=== DYNAMIC FILTER REPOSITORY - COUNT ENTITIES ===");
    log.debug("Entity Class: {}, Filter: {}", entityClass.getSimpleName(), filter != null ? "present" : "null");

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> cq = cb.createQuery(Long.class);
    Root<T> root = cq.from(entityClass);

    cq.select(cb.countDistinct(root));

    if (filter != null) {
      Predicate predicate = buildPredicateRecursively(cb, root, filter);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    Long count = entityManager.createQuery(cq).getSingleResult();
    log.debug("Count result: {}", count);

    return count;
  }

  /**
   * 1. Aşama: PostgreSQL uyumlu şekilde sadece ana entity ID'lerini bul
   */
  private <T> List<UUID> findEntityIds(Class<T> entityClass,
                                       BaseFilter filter,
                                       int page, int size,
                                       List<String> sortFields) {

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    // PostgreSQL için: Önce ID'leri ve sıralama alanlarını seç, sonra ID'leri al
    CriteriaQuery<Tuple> idQuery = cb.createTupleQuery();
    Root<T> root = idQuery.from(entityClass);

    // SELECT: ID ve sıralama için gerekli alanlar
    List<Selection<?>> selections = new ArrayList<>();
    selections.add(root.get("id").alias("id"));

    // Sıralama alanlarını da seçime ekle (PostgreSQL gereksinimi)
    if (sortFields != null && !sortFields.isEmpty()) {
      for (String sortField : sortFields) {
        String fieldName = sortField.startsWith("-") ? sortField.substring(1) : sortField;
        try {
          selections.add(root.get(fieldName).alias(fieldName));
        } catch (IllegalArgumentException e) {
          log.warn("Sort field '{}' not found in entity {}, skipping",
              fieldName, entityClass.getSimpleName());
        }
      }
    }

    idQuery.multiselect(selections);
    idQuery.distinct(true);

    // WHERE kısmı - filtreleme
    if (filter != null) {
      Predicate predicate = buildPredicateRecursively(cb, root, filter);
      if (predicate != null) {
        idQuery.where(predicate);
      }
    }

    // ORDER BY kısmı - PostgreSQL uyumlu
    if (sortFields != null && !sortFields.isEmpty()) {
      List<Order> orders = buildOrderBy(cb, root, sortFields);
      idQuery.orderBy(orders);
    }

    TypedQuery<Tuple> query = entityManager.createQuery(idQuery);

    // Pagination
    if (page >= 0 && size > 0) {
      query.setFirstResult(page * size);
      query.setMaxResults(size);
    }

    List<Tuple> result = query.getResultList();

    // Tuple'lardan sadece ID'leri al
    List<UUID> ids = result.stream()
        .map(tuple -> tuple.get("id", UUID.class))
        .collect(Collectors.toList());

    log.debug("Found {} entity IDs for pagination", ids.size());
    return ids;
  }

  /**
   * 2. Aşama: ID listesiyle birlikte tüm ilişkileri yükle
   */
  private <T> List<T> findEntitiesWithRelationships(Class<T> entityClass,
                                                    List<UUID> entityIds,
                                                    Map<Class<?>, Set<String>> selectedFields,
                                                    List<String> sortFields) {

    if (entityIds.isEmpty()) {
      return new ArrayList<>();
    }

    // Batch size kontrolü (performance için)
    if (entityIds.size() > 1000) {
      log.warn("Large ID list ({}), consider splitting into batches", entityIds.size());
    }

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);

    // SELECT kısmı - dinamik field seçimi
    List<Selection<?>> selections = new ArrayList<>();
    Map<Class<?>, Map<Object, Object>> entityMaps = new HashMap<>();

    buildSelectionsRecursively(root, selections, selectedFields, entityMaps, "");

    log.debug("Final selections: {}", selections.size());
    selections.forEach(selection ->
        log.debug("Selection: {}", selection.getAlias()));

    cq.multiselect(selections);

    // WHERE: ID'lerle filtrele
    cq.where(root.get("id").in(entityIds));

    // ORDER BY: Orijinal sıralamayı koru (artık güvenli)
    if (sortFields != null && !sortFields.isEmpty()) {
      List<Order> orders = buildOrderBy(cb, root, sortFields);
      cq.orderBy(orders);
    }

    // DISTINCT - artık gerekli değil çünkü ID bazlı sorgu
    cq.distinct(true);

    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    List<Tuple> result = query.getResultList();

    log.debug("Loaded {} tuples for {} entities with relationships", result.size(), entityIds.size());

    // Tuple'dan entity'e dönüşüm
    return mapResultsToEntities(result, entityClass, selectedFields, entityMaps);
  }

  // Filtreleme için recursive predicate builder - TÜM HATALAR DÜZELTİLDİ
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Predicate buildPredicateRecursively(CriteriaBuilder cb, From<?, ?> from, BaseFilter filter) {
    List<Predicate> predicates = new ArrayList<>();

    // Temel field filtreleri - ContractFilter için
    if (filter instanceof ContractFilter) {
      ContractFilter contractFilter = (ContractFilter) filter;
      addFieldPredicate(cb, from, predicates, "contractName", contractFilter.getContractName());
      addFieldPredicate(cb, from, predicates, "contractNo", contractFilter.getContractNo());
      addFieldPredicate(cb, from, predicates, "contractStatus", contractFilter.getContractStatus());
      addFieldPredicate(cb, from, predicates, "renewalNo", contractFilter.getRenewalNo());
      addFieldPredicate(cb, from, predicates, "endorsementNo", contractFilter.getEndorsementNo());

      // Nested ilişki filtreleri
      if (contractFilter.getContractDetail() != null) {
        Join<Object, Object> detailJoin = from.join("contractDetail", JoinType.LEFT);
        Predicate detailPredicate = buildPredicateRecursively(cb, detailJoin, contractFilter.getContractDetail());
        if (detailPredicate != null) {
          predicates.add(detailPredicate);
        }
      }

      // Collection filtreleri - HATA DÜZELTİLDİ
      if (contractFilter.getContractBranches() != null) {
        Predicate branchesPredicate =
            processListFilter(cb, from, "contractBranches", contractFilter.getContractBranches(),
                ContractBranchEntity.class);
        if (branchesPredicate != null) {
          predicates.add(branchesPredicate);
        }
      }

      if (contractFilter.getLayers() != null) {
        Predicate layersPredicate =
            processListFilter(cb, from, "layers", contractFilter.getLayers(), LayerEntity.class);
        if (layersPredicate != null) {
          predicates.add(layersPredicate);
        }
      }
    }
    // ContractBranchFilter için
    else if (filter instanceof ContractBranchFilter) {
      ContractBranchFilter branchFilter = (ContractBranchFilter) filter;
      addFieldPredicate(cb, from, predicates, "branchEnum", branchFilter.getBranchEnum());
      addFieldPredicate(cb, from, predicates, "premiumAmount", branchFilter.getPremiumAmount());

      if (branchFilter.getContractCovers() != null) {
        Predicate coversPredicate =
            processListFilter(cb, from, "contractCovers", branchFilter.getContractCovers(), ContractCoverEntity.class);
        if (coversPredicate != null) {
          predicates.add(coversPredicate);
        }
      }
    }
    // ContractCoverFilter için
    else if (filter instanceof ContractCoverFilter) {
      ContractCoverFilter coverFilter = (ContractCoverFilter) filter;
      addFieldPredicate(cb, from, predicates, "coverEnum", coverFilter.getCoverEnum());
      addFieldPredicate(cb, from, predicates, "premiumAmount", coverFilter.getPremiumAmount());
    }
    // ContractDetailFilter için
    else if (filter instanceof ContractDetailFilter) {
      ContractDetailFilter detailFilter = (ContractDetailFilter) filter;
      addFieldPredicate(cb, from, predicates, "startDate", detailFilter.getStartDate());
      addFieldPredicate(cb, from, predicates, "endDate", detailFilter.getEndDate());
    }
    // LayerFilter için
    else if (filter instanceof LayerFilter) {
      LayerFilter layerFilter = (LayerFilter) filter;
      addFieldPredicate(cb, from, predicates, "layerOrder", layerFilter.getLayerOrder());
      addFieldPredicate(cb, from, predicates, "lossLimitAmount", layerFilter.getLossLimitAmount());
      addFieldPredicate(cb, from, predicates, "lossLimitAmountRc", layerFilter.getLossLimitAmountRc());
      addFieldPredicate(cb, from, predicates, "deductibleAmount", layerFilter.getDeductibleAmount());
      addFieldPredicate(cb, from, predicates, "deductibleAmountRc", layerFilter.getDeductibleAmountRc());

      if (layerFilter.getReinstatements() != null) {
        Predicate reinstatementsPredicate =
            processListFilter(cb, from, "reinstatements", layerFilter.getReinstatements(), ReinstatementEntity.class);
        if (reinstatementsPredicate != null) {
          predicates.add(reinstatementsPredicate);
        }
      }
    }
    // ReinstatementFilter için
    else if (filter instanceof ReinstatementFilter) {
      ReinstatementFilter reinstatementFilter = (ReinstatementFilter) filter;
      addFieldPredicate(cb, from, predicates, "reinstatementOrder", reinstatementFilter.getReinstatementOrder());
      addFieldPredicate(cb, from, predicates, "reinstatementRatio", reinstatementFilter.getReinstatementRatio());
    }

    // Logical operatörler (AND, OR, NOT)
    if (filter.getAnd() != null && !filter.getAnd().isEmpty()) {
      List<Predicate> andPredicates = filter.getAnd().stream()
          .map(f -> buildPredicateRecursively(cb, from, f))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      if (!andPredicates.isEmpty()) {
        predicates.add(cb.and(andPredicates.toArray(new Predicate[0])));
      }
    }

    if (filter.getOr() != null && !filter.getOr().isEmpty()) {
      List<Predicate> orPredicates = filter.getOr().stream()
          .map(f -> buildPredicateRecursively(cb, from, f))
          .filter(Objects::nonNull)
          .collect(Collectors.toList());
      if (!orPredicates.isEmpty()) {
        predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
      }
    }

    if (filter.getNot() != null) {
      Predicate notPredicate = buildPredicateRecursively(cb, from, filter.getNot());
      if (notPredicate != null) {
        predicates.add(cb.not(notPredicate));
      }
    }

    return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
  }

  // Collection filtreleri için (some, every, none) - TİP GÜVENLİĞİ SAĞLANDI
  private <T> Predicate processListFilter(CriteriaBuilder cb, From<?, ?> from, String collectionName,
                                          Object listFilter, Class<T> targetEntityClass) {

    try {
      List<Predicate> collectionPredicates = new ArrayList<>();

      // Reflection yerine doğrudan field erişimi - TİP GÜVENLİĞİ
      if (listFilter instanceof ContractBranchListFilter) {
        ContractBranchListFilter branchListFilter = (ContractBranchListFilter) listFilter;
        if (branchListFilter.getSome() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, branchListFilter.getSome(), targetEntityClass);
          collectionPredicates.add(cb.exists(subquery));
        }
        if (branchListFilter.getEvery() != null) {
          // Tüm elemanlar filtreyi sağlamalı: NOT EXISTS (eleman filtreyi sağlamayan)
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, branchListFilter.getEvery(), targetEntityClass, true);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
        if (branchListFilter.getNone() != null) {
          // Hiçbir eleman filtreyi sağlamamalı
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, branchListFilter.getNone(), targetEntityClass);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
      } else if (listFilter instanceof ContractCoverListFilter) {
        ContractCoverListFilter coverListFilter = (ContractCoverListFilter) listFilter;
        if (coverListFilter.getSome() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, coverListFilter.getSome(), targetEntityClass);
          collectionPredicates.add(cb.exists(subquery));
        }
        if (coverListFilter.getEvery() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, coverListFilter.getEvery(), targetEntityClass, true);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
        if (coverListFilter.getNone() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, coverListFilter.getNone(), targetEntityClass);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
      } else if (listFilter instanceof LayerListFilter) {
        LayerListFilter layerListFilter = (LayerListFilter) listFilter;
        if (layerListFilter.getSome() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, layerListFilter.getSome(), targetEntityClass);
          collectionPredicates.add(cb.exists(subquery));
        }
        if (layerListFilter.getEvery() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, layerListFilter.getEvery(), targetEntityClass, true);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
        if (layerListFilter.getNone() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, layerListFilter.getNone(), targetEntityClass);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
      } else if (listFilter instanceof ReinstatementListFilter) {
        ReinstatementListFilter reinstatementListFilter = (ReinstatementListFilter) listFilter;
        if (reinstatementListFilter.getSome() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, reinstatementListFilter.getSome(), targetEntityClass);
          collectionPredicates.add(cb.exists(subquery));
        }
        if (reinstatementListFilter.getEvery() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, reinstatementListFilter.getEvery(), targetEntityClass, true);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
        if (reinstatementListFilter.getNone() != null) {
          Subquery<Long> subquery =
              createSubquery(cb, from, collectionName, reinstatementListFilter.getNone(), targetEntityClass);
          collectionPredicates.add(cb.not(cb.exists(subquery)));
        }
      }

      return collectionPredicates.isEmpty() ? null : cb.and(collectionPredicates.toArray(new Predicate[0]));

    } catch (Exception e) {
      log.error("Error processing list filter for {}: {}", collectionName, e.getMessage(), e);
      return null;
    }
  }

  // Subquery oluşturma - FROM.GETQUERY() HATASI DÜZELTİLDİ
  private <T> Subquery<Long> createSubquery(CriteriaBuilder cb, From<?, ?> from, String collectionName,
                                            BaseFilter filter, Class<T> targetEntityClass) {
    return createSubquery(cb, from, collectionName, filter, targetEntityClass, false);
  }

  private <T> Subquery<Long> createSubquery(CriteriaBuilder cb, From<?, ?> from, String collectionName,
                                            BaseFilter filter, Class<T> targetEntityClass, boolean negateFilter) {

    // Ana CriteriaQuery'yi from'dan almak yerine yeni bir query oluştur
    CriteriaQuery<?> mainQuery = cb.createQuery();
    Subquery<Long> subquery = mainQuery.subquery(Long.class);
    Root<T> subRoot = subquery.from(targetEntityClass);
    subquery.select(cb.literal(1L));

    // Ana entity ile ilişki kur
    String backReferenceField = getBackReferenceField(from.getJavaType(), targetEntityClass);
    Predicate correlationPredicate = cb.equal(subRoot.get(backReferenceField), from);

    // Filtre predicate'ini oluştur
    Predicate filterPredicate = buildPredicateRecursively(cb, subRoot, filter);

    if (filterPredicate != null) {
      if (negateFilter) {
        // every için: filtreyi sağlamayan elemanları bul
        filterPredicate = cb.not(filterPredicate);
      }
      subquery.where(cb.and(correlationPredicate, filterPredicate));
    } else {
      subquery.where(correlationPredicate);
    }

    return subquery;
  }

  // Back reference field mapping - GELİŞTİRİLMİŞ
  private String getBackReferenceField(Class<?> parentClass, Class<?> childClass) {
    Map<Class<?>, Map<Class<?>, String>> backReferenceMap = Map.of(
        ContractEntity.class, Map.of(
            ContractBranchEntity.class, "contract",
            LayerEntity.class, "contract",
            ContractDetailEntity.class, "contract"
        ),
        ContractBranchEntity.class, Map.of(
            ContractCoverEntity.class, "contractBranch"
        ),
        LayerEntity.class, Map.of(
            ReinstatementEntity.class, "layer"
        )
    );

    Map<Class<?>, String> childMap = backReferenceMap.get(parentClass);
    if (childMap != null) {
      String field = childMap.get(childClass);
      if (field != null) {
        return field;
      }
    }

    // Fallback: artificial relation kontrolü
    if (hasArtificialRelation(childClass, parentClass)) {
      return "contract";
    }

    log.warn("No back reference mapping found for {} -> {}, using default 'contract'",
        parentClass.getSimpleName(), childClass.getSimpleName());
    return "contract";
  }

  private boolean hasArtificialRelation(Class<?> entityClass, Class<?> targetClass) {
    try {
      Field[] fields = entityClass.getDeclaredFields();
      for (Field field : fields) {
        if (field.getType().equals(targetClass) &&
            field.isAnnotationPresent(ArtificialRelation.class)) {
          return true;
        }
      }
    } catch (Exception e) {
      log.debug("Error checking artificial relation: {}", e.getMessage());
    }
    return false;
  }

  // Field predicate ekleme yardımcı metodu
  private <T> void addFieldPredicate(CriteriaBuilder cb, From<?, ?> from, List<Predicate> predicates,
                                     String fieldName, FieldFilter<T> fieldFilter) {
    if (fieldFilter == null || fieldFilter.getOperator() == null) {
      return;
    }

    try {
      Path<T> path = from.get(fieldName);
      Predicate fieldPredicate = createFieldPredicate(cb, path, fieldFilter);

      if (fieldPredicate != null) {
        predicates.add(fieldPredicate);
      }
    } catch (IllegalArgumentException e) {
      log.warn("Field '{}' not found in entity: {}", fieldName, from.getJavaType().getSimpleName());
    }
  }

  // Operatörlere göre predicate oluşturma - TİP DÖNÜŞÜMLERİ EKLENDİ
  @SuppressWarnings("unchecked")
  private <T> Predicate createFieldPredicate(CriteriaBuilder cb, Path<T> path, FieldFilter<T> fieldFilter) {
    FilterOperator operator = fieldFilter.getOperator();
    T value = fieldFilter.getValue();
    List<T> values = fieldFilter.getValues();
    Boolean caseSensitive = fieldFilter.getCaseSensitive() != null ? fieldFilter.getCaseSensitive() : false;

    // Null kontrolü
    if (value == null && (operator != FilterOperator.IS_NULL && operator != FilterOperator.IS_NOT_NULL)) {
      return null;
    }

    try {
      switch (operator) {
        case EQ:
          return cb.equal(path, value);
        case NEQ:
          return cb.notEqual(path, value);
        case GT:
          return cb.greaterThan((Path<Comparable>) path, (Comparable) value);
        case GTE:
          return cb.greaterThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
        case LT:
          return cb.lessThan((Path<Comparable>) path, (Comparable) value);
        case LTE:
          return cb.lessThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
        case IN:
          if (values != null && !values.isEmpty()) {
            return path.in(values);
          } else if (value != null) {
            return path.in(value);
          }
          break;
        case NOT_IN:
          if (values != null && !values.isEmpty()) {
            return cb.not(path.in(values));
          } else if (value != null) {
            return cb.not(path.in(value));
          }
          break;
        case CONTAINS:
          if (path.getJavaType() == String.class && value instanceof String) {
            String stringValue = (String) value;
            if (caseSensitive) {
              return cb.like((Path<String>) path, "%" + stringValue + "%");
            } else {
              return cb.like(cb.lower((Path<String>) path), "%" + stringValue.toLowerCase() + "%");
            }
          }
          break;
        case STARTS_WITH:
          if (path.getJavaType() == String.class && value instanceof String) {
            String stringValue = (String) value;
            if (caseSensitive) {
              return cb.like((Path<String>) path, stringValue + "%");
            } else {
              return cb.like(cb.lower((Path<String>) path), stringValue.toLowerCase() + "%");
            }
          }
          break;
        case ENDS_WITH:
          if (path.getJavaType() == String.class && value instanceof String) {
            String stringValue = (String) value;
            if (caseSensitive) {
              return cb.like((Path<String>) path, "%" + stringValue);
            } else {
              return cb.like(cb.lower((Path<String>) path), "%" + stringValue.toLowerCase());
            }
          }
          break;
        case BETWEEN:
          if (fieldFilter.getStart() != null && fieldFilter.getEnd() != null) {
            return cb.between((Path<Comparable>) path,
                (Comparable) fieldFilter.getStart(),
                (Comparable) fieldFilter.getEnd());
          }
          break;
        case IS_NULL:
          return cb.isNull(path);
        case IS_NOT_NULL:
          return cb.isNotNull(path);
      }
    } catch (Exception e) {
      log.error("Error creating predicate for field {} with operator {}: {}",
          path.getAlias(), operator, e.getMessage());
    }

    return null;
  }

  // ORDER BY builder
  private List<Order> buildOrderBy(CriteriaBuilder cb, Root<?> root, List<String> sortFields) {
    return sortFields.stream()
        .map(field -> {
          String fieldName = field.startsWith("-") ? field.substring(1) : field;
          try {
            if (field.startsWith("-")) {
              return cb.desc(root.get(fieldName));
            } else {
              return cb.asc(root.get(fieldName));
            }
          } catch (IllegalArgumentException e) {
            log.warn("Sort field '{}' not found in entity: {}", fieldName, root.getJavaType().getSimpleName());
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  // Selections builder
  private void buildSelectionsRecursively(From<?, ?> from,
                                          List<Selection<?>> selections,
                                          Map<Class<?>, Set<String>> selectedFields,
                                          Map<Class<?>, Map<Object, Object>> entityMaps,
                                          String prefix) {

    Class<?> currentClass = from.getJavaType();
    Set<String> fields = selectedFields.get(currentClass);

    log.debug("=== BUILDING SELECTIONS FOR: {} ===", currentClass.getSimpleName());
    log.debug("Fields from GraphQL collector: {}", fields);

    if (fields == null || fields.isEmpty()) {
      log.warn("NO FIELDS SELECTED for: {}", currentClass.getSimpleName());

      // Hiç field seçilmemişse sadece ID'yi ekle**
      try {
        Selection<?> idSelection = from.get("id").alias(prefix + "id");
        selections.add(idSelection);
        log.debug("Added only ID field for: {}", currentClass.getSimpleName());
      } catch (IllegalArgumentException e) {
        log.warn("Entity {} doesn't have 'id' field", currentClass.getSimpleName());
      }
      return;
    }

    // Basit field'ları ekle
    for (String field : fields) {
      String cleanFieldName = cleanFieldName(field);
      boolean isArtificial = isArtificialRelationField(field);

      log.debug("Processing field: '{}' (clean: '{}', artificial: {})",
          field, cleanFieldName, isArtificial);

      if (!isRelationshipField(currentClass, cleanFieldName)) {
        try {
          Selection<?> selection = from.get(cleanFieldName).alias(prefix + cleanFieldName);
          selections.add(selection);
          log.debug("Added SIMPLE field selection: {}{}", prefix, cleanFieldName);
        } catch (IllegalArgumentException e) {
          log.warn("Field '{}' not found in entity: {}", cleanFieldName, currentClass.getSimpleName());
        }
      } else {
        log.debug("Field '{}' is RELATIONSHIP, will create join", cleanFieldName);
      }
    }

    // İlişkiler için JOIN oluştur
    for (String field : fields) {
      String cleanFieldName = cleanFieldName(field);
      if (isRelationshipField(currentClass, cleanFieldName)) {
        try {
          Class<?> targetClass = getTargetClass(currentClass, cleanFieldName);
          entityMaps.putIfAbsent(targetClass, new HashMap<>());

          if (selectedFields.containsKey(targetClass)) {
            String newPrefix = prefix + cleanFieldName + "_";
            log.debug("Creating JOIN for: {} -> {} with prefix: {}",
                currentClass.getSimpleName(), targetClass.getSimpleName(), newPrefix);

            Join<?, ?> join = from.join(cleanFieldName, JoinType.LEFT);
            buildSelectionsRecursively(join, selections, selectedFields, entityMaps, newPrefix);
          }
        } catch (Exception e) {
          log.error("Join creation failed for {}.{}: {}",
              currentClass.getSimpleName(), cleanFieldName, e.getMessage());
        }
      }
    }
  }

  private <T> List<T> mapResultsToEntities(List<Tuple> result,
                                           Class<T> entityClass,
                                           Map<Class<?>, Set<String>> selectedFields,
                                           Map<Class<?>, Map<Object, Object>> entityMaps) {
    try {
      Map<String, Object> entityCache = new HashMap<>();
      List<T> entities = new ArrayList<>();

      log.debug("Starting to map {} tuples to entities", result.size());

      for (Tuple tuple : result) {
        T entity = processTupleAndBuildEntities(tuple, entityClass, selectedFields, entityCache, "");
        if (entity != null && !containsEntity(entities, entity)) {
          entities.add(entity);
          log.debug("Added entity to result list: {}", getEntityId(entity));
        }
      }

      log.debug("Successfully mapped {} entities", entities.size());
      return entities;
    } catch (Exception e) {
      log.error("Error mapping results to entities: {}", e.getMessage(), e);
      return new ArrayList<>();
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T processTupleAndBuildEntities(Tuple tuple,
                                             Class<T> entityClass,
                                             Map<Class<?>, Set<String>> selectedFields,
                                             Map<String, Object> entityCache,
                                             String prefix) throws Exception {

    // Entity ID'sini al
    Object entityId = getValueSafely(tuple, prefix + "id");
    if (entityId == null) {
      log.debug("Entity ID is null for {} with prefix: {}", entityClass.getSimpleName(), prefix);
      return null;
    }

    String entityKey = createCacheKey(entityId, prefix, entityClass);

    log.debug("Processing entity: {}, ID: {}, Prefix: {}",
        entityClass.getSimpleName(), entityId, prefix);

    // Cache'te varsa kullan, yoksa oluştur
    T entity;
    if (entityCache.containsKey(entityKey)) {
      entity = (T) entityCache.get(entityKey);
      log.debug("Using cached entity: {}", entityKey);
    } else {
      entity = entityClass.getDeclaredConstructor().newInstance();
      entityCache.put(entityKey, entity);
      log.debug("Created new entity: {}", entityKey);

      // Basit field'ları set etmeden ÖNCE collection'ları initialize et
      Set<String> fields = selectedFields.get(entityClass);
      if (fields != null) {
        // Önce tüm collection field'larını initialize et (empty olarak)
        for (String field : fields) {
          String cleanFieldName = cleanFieldName(field);
          if (isCollectionField(entityClass, cleanFieldName)) {
            try {
              Collection<Object> collection = getOrCreateCollection(entity, cleanFieldName);
              log.debug("Pre-initialized collection for {}.{}",
                  entityClass.getSimpleName(), cleanFieldName);
            } catch (Exception e) {
              log.warn("Failed to initialize collection {}.{}: {}",
                  entityClass.getSimpleName(), cleanFieldName, e.getMessage());
            }
          }
        }

        // Sonra basit field'ları set et
        for (String field : fields) {
          String cleanFieldName = cleanFieldName(field);
          boolean isArtificial = isArtificialRelationField(field);

          if (!isRelationshipField(entityClass, cleanFieldName)) {
            Object value = getValueSafely(tuple, prefix + cleanFieldName);
            if (value != null) {
              setFieldValue(entity, cleanFieldName, value);
              log.debug("Set field {}{} to: {}", prefix, cleanFieldName, value);
            }
          }
        }
      }
    }

    // İlişkileri işle - ARTIK COLLECTION'LAR BOŞ DA OLSA HAZIR
    Set<String> fields = selectedFields.get(entityClass);
    if (fields != null) {
      for (String field : fields) {
        String cleanFieldName = cleanFieldName(field);
        boolean isArtificial = isArtificialRelationField(field);

        if (isRelationshipField(entityClass, cleanFieldName)) {
          Class<?> targetClass = getTargetClass(entityClass, cleanFieldName);
          String newPrefix = prefix + cleanFieldName + "_";

          Object relatedEntityId = getValueSafely(tuple, newPrefix + "id");

          log.debug("Processing relationship - Field: {}, Target: {}, Related ID: {}, Artificial: {}",
              cleanFieldName, targetClass.getSimpleName(), relatedEntityId, isArtificial);

          if (relatedEntityId != null) {
            Object relatedEntity =
                processTupleAndBuildEntities(tuple, targetClass, selectedFields, entityCache, newPrefix);

            if (relatedEntity != null) {
              // Collection ilişkisi
              if (isCollectionField(entityClass, cleanFieldName)) {
                Collection<Object> collection = getOrCreateCollection(entity, cleanFieldName);

                // Collection'da bu entity var mı kontrol et
                if (!isEntityInCollection(collection, relatedEntityId)) {
                  collection.add(relatedEntity);

                  // Artificial relation değilse back reference set et
                  if (!isArtificial) {
                    setBackReference(entity, relatedEntity, cleanFieldName, entityClass);
                  }
                  log.debug("Added to collection: {} (Artificial: {})",
                      createCacheKey(relatedEntityId, newPrefix, targetClass), isArtificial);
                }
              } else {
                // Single relation
                setFieldValue(entity, cleanFieldName, relatedEntity);

                // Artificial relation değilse back reference set et
                if (!isArtificial) {
                  setBackReference(entity, relatedEntity, cleanFieldName, entityClass);
                }
                log.debug("Set single relation: {} (Artificial: {})",
                    createCacheKey(relatedEntityId, newPrefix, targetClass), isArtificial);
              }
            }
          } else {
            // İlişki ID'si null ise, collection'ı boş bırak (zaten initialize edilmiş)
            log.debug("No related entity found for {}.{}", entityClass.getSimpleName(), cleanFieldName);
          }
        }
      }
    }

    return entity;
  }

  // Yardımcı metodlar
  private String cleanFieldName(String field) {
    return field.replace("::ARTIFICIAL", "");
  }

  private boolean isArtificialRelationField(String field) {
    return field.contains("::ARTIFICIAL");
  }

  private Object getValueSafely(Tuple tuple, String alias) {
    try {
      Object value = tuple.get(alias);
      log.trace("Getting value for alias '{}': {}", alias, value);
      return value;
    } catch (IllegalArgumentException e) {
      log.trace("Alias '{}' not found in tuple", alias);
      return null;
    }
  }

  private String createCacheKey(Object entityId, String prefix, Class<?> entityClass) {
    return entityClass.getSimpleName() + "_" + prefix + "_" + (entityId != null ? entityId.toString() : "null");
  }

  private boolean isRelationshipField(Class<?> entityClass, String fieldName) {
    try {
      Field field = entityClass.getDeclaredField(fieldName);
      Class<?> fieldType = field.getType();

      if (GraphQLFieldCollector.isSimpleType(fieldType) || fieldType.isEnum()) {
        return false;
      }

      boolean isEntity = isEntityClass(fieldType);
      boolean isCollection = Collection.class.isAssignableFrom(fieldType);
      boolean isEntityCollection = isCollection && isCollectionOfEntities(field);

      return isEntity || isEntityCollection;
    } catch (NoSuchFieldException e) {
      log.warn("Field '{}' not found in entity: {}", fieldName, entityClass.getSimpleName());
      return false;
    }
  }

  private boolean isEntityClass(Class<?> clazz) {
    Package pkg = clazz.getPackage();
    if (pkg == null) {
      return false;
    }

    String packageName = pkg.getName();
    boolean isEntity = packageName.startsWith(GraphQLFieldCollector.ENTITY_PATH) ||
        clazz.isAnnotationPresent(Entity.class);

    return isEntity;
  }

  private boolean isCollectionOfEntities(Field field) {
    if (!Collection.class.isAssignableFrom(field.getType())) {
      return false;
    }

    try {
      ParameterizedType genericType = (ParameterizedType) field.getGenericType();
      Class<?> collectionType = (Class<?>) genericType.getActualTypeArguments()[0];
      return isEntityClass(collectionType);
    } catch (Exception e) {
      return false;
    }
  }

  private boolean isCollectionField(Class<?> entityClass, String fieldName) {
    try {
      Field field = entityClass.getDeclaredField(fieldName);
      return Collection.class.isAssignableFrom(field.getType());
    } catch (NoSuchFieldException e) {
      return false;
    }
  }

  private Class<?> getTargetClass(Class<?> entityClass, String fieldName) {
    try {
      Field field = entityClass.getDeclaredField(fieldName);
      if (Collection.class.isAssignableFrom(field.getType())) {
        ParameterizedType genericType = (ParameterizedType) field.getGenericType();
        return (Class<?>) genericType.getActualTypeArguments()[0];
      }
      return field.getType();
    } catch (NoSuchFieldException e) {
      throw new RuntimeException("Field not found: " + fieldName + " in " + entityClass.getSimpleName(), e);
    }
  }

  private void setFieldValue(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);

      if (value != null && !field.getType().isAssignableFrom(value.getClass())) {
        value = convertType(value, field.getType());
      }

      if (value != null) {
        field.set(target, value);
        log.trace("Set field {} to value: {}", fieldName, value);
      }
    } catch (Exception e) {
      log.error("Field set failed: {} in {}. Value: {}. Error: {}",
          fieldName, target.getClass().getSimpleName(), value, e.getMessage());
      throw new RuntimeException("Field set failed: " + fieldName + " in " + target.getClass().getSimpleName(), e);
    }
  }

  private Object convertType(Object value, Class<?> targetType) {
    // Enum conversion
    if (targetType.isEnum() && value instanceof String) {
      try {
        return Enum.valueOf((Class<Enum>) targetType, (String) value);
      } catch (Exception e) {
        log.warn("Cannot convert {} to enum {}", value, targetType.getSimpleName());
        return null;
      }
    }

    // BigDecimal conversion
    if (targetType == BigDecimal.class) {
      if (value instanceof Number) {
        return BigDecimal.valueOf(((Number) value).doubleValue());
      } else if (value instanceof String) {
        try {
          return new BigDecimal((String) value);
        } catch (Exception e) {
          log.warn("Cannot convert {} to BigDecimal: {}", value, e.getMessage());
          return null;
        }
      }
    }

    // UUID conversion
    if (targetType == UUID.class && value instanceof String) {
      try {
        return UUID.fromString((String) value);
      } catch (Exception e) {
        log.warn("Cannot convert {} to UUID: {}", value, e.getMessage());
        return null;
      }
    }

    // Integer conversion
    if (targetType == Integer.class && value instanceof Number) {
      return ((Number) value).intValue();
    }

    // Long conversion
    if (targetType == Long.class && value instanceof Number) {
      return ((Number) value).longValue();
    }

    // Double conversion
    if (targetType == Double.class && value instanceof Number) {
      return ((Number) value).doubleValue();
    }

    // Float conversion
    if (targetType == Float.class && value instanceof Number) {
      return ((Number) value).floatValue();
    }

    return value;
  }

  @SuppressWarnings("unchecked")
  private Collection<Object> getOrCreateCollection(Object entity, String fieldName) throws Exception {
    Field field = entity.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);

    Collection<Object> collection = (Collection<Object>) field.get(entity);
    if (collection == null) {
      if (Set.class.isAssignableFrom(field.getType())) {
        collection = new HashSet<>();
      } else if (List.class.isAssignableFrom(field.getType())) {
        collection = new ArrayList<>();
      } else {
        collection = new HashSet<>();
      }
      field.set(entity, collection);
      log.debug("Created new collection for field: {}", fieldName);
    }
    return collection;
  }

  private boolean isEntityInCollection(Collection<Object> collection, Object entityId) {
    if (collection == null || entityId == null) {
      return false;
    }

    return collection.stream()
        .anyMatch(entity -> {
          try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object existingId = idField.get(entity);
            return entityId.equals(existingId);
          } catch (Exception e) {
            return false;
          }
        });
  }

  private void setBackReference(Object parent, Object child, String fieldName, Class<?> parentClass) {
    try {
      Field[] childFields = child.getClass().getDeclaredFields();
      for (Field childField : childFields) {
        if (childField.getType().equals(parentClass) &&
            !childField.isAnnotationPresent(ArtificialRelation.class)) {
          childField.setAccessible(true);
          childField.set(child, parent);
          log.debug("Set back reference: {}.{} -> {}",
              child.getClass().getSimpleName(), childField.getName(), parentClass.getSimpleName());
          break;
        }
      }
    } catch (Exception e) {
      log.debug("Could not set back reference for {} -> {}: {}",
          parentClass.getSimpleName(), child.getClass().getSimpleName(), e.getMessage());
    }
  }

  private <T> boolean containsEntity(List<T> entities, T entity) {
    try {
      Field idField = entity.getClass().getDeclaredField("id");
      idField.setAccessible(true);
      Object entityId = idField.get(entity);

      for (T existing : entities) {
        Object existingId = idField.get(existing);
        if (existingId.equals(entityId)) {
          return true;
        }
      }
    } catch (Exception e) {
      // Fallback: referans karşılaştırması
      return entities.contains(entity);
    }
    return false;
  }

  private Object getEntityId(Object entity) {
    try {
      Field idField = entity.getClass().getDeclaredField("id");
      idField.setAccessible(true);
      return idField.get(entity);
    } catch (Exception e) {
      return "unknown";
    }
  }
}