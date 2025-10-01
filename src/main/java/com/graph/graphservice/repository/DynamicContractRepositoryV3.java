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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

import com.graph.graphservice.aspect.ArtificialRelation;
import com.graph.graphservice.entity.ContractEntity;
import com.graph.graphservice.entity.LayerEntity;
import com.graph.graphservice.entity.ReinstatementEntity;
import com.graph.graphservice.utils.GraphQLFieldCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamicContractRepositoryV3 {
  private final EntityManager entityManager;

  public <T> T findEntityDynamic(UUID entityId,
                                 Class<T> entityClass,
                                 Map<Class<?>, Set<String>> selectedFields) {

    if (selectedFields == null || selectedFields.isEmpty()) {
      log.warn("No fields selected for entity: {}", entityClass.getSimpleName());
      return null;
    }

    Map<Class<?>, Set<String>> filteredFields = selectedFields.entrySet().stream()
        .filter(entry -> isEntityClass(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);

    List<Selection<?>> selections = new ArrayList<>();
    Map<Class<?>, Map<Object, Object>> entityMaps = new HashMap<>();

    buildSelectionsRecursively(root, selections, filteredFields, entityMaps, "");

    cq.multiselect(selections)
        .where(cb.equal(root.get("id"), entityId))
        .distinct(true);

    try {
      List<Tuple> result = entityManager.createQuery(cq).getResultList();

      if (result.isEmpty()) {
        return null;
      }

      logTupleContents(result);
      T mappedEntity = mapResultToEntity(result, entityClass, filteredFields, entityMaps);

      //logMappedEntity(mappedEntity);

      return mappedEntity;
    } catch (Exception e) {
      log.error("Error executing dynamic query for {}: {}", entityClass.getSimpleName(), e.getMessage(), e);
      throw new RuntimeException("Query execution failed", e);
    }
  }

//  private void logMappedEntity(Object entity) {
//    if (entity != null && log.isDebugEnabled()) {
//      log.debug("=== Mapped Entity Contents ===");
//      log.debug("Entity type: {}", entity.getClass().getSimpleName());
//
//      try {
//        if (entity instanceof ContractEntity) {
//          ContractEntity contract = (ContractEntity) entity;
//          log.debug("Contract ID: {}", contract.getId());
//          log.debug("Contract Name: {}", contract.getContractName());
//          log.debug("Contract No: {}", contract.getContractNo());
//
//          if (contract.getLayers() != null) {
//            log.debug("Layers count: {}", contract.getLayers().size());
//            for (LayerEntity layer : contract.getLayers()) {
//              log.debug("Layer ID: {}, Order: {}", layer.getId(), layer.getLayerOrder());
//              if (layer.getReinstatements() != null) {
//                log.debug("  Reinstatements count: {}", layer.getReinstatements().size());
//                for (ReinstatementEntity reinst : layer.getReinstatements()) {
//                  log.debug("    Reinstatement ID: {}, Order: {}, Ratio: {}",
//                      reinst.getId(), reinst.getReinstatementOrder(), reinst.getReinstatementRatio());
//                }
//              } else {
//                log.debug("  Reinstatements: null");
//              }
//            }
//          } else {
//            log.debug("Layers: null");
//          }
//        }
//      } catch (Exception e) {
//        log.debug("Error logging mapped entity: {}", e.getMessage());
//      }
//    }
//  }

  private void logTupleContents(List<Tuple> result) {
    if (!result.isEmpty() && log.isDebugEnabled()) {
      log.debug("=== Tuple Contents ===");
      for (int i = 0; i < result.size(); i++) {
        log.debug("--- Tuple {} ---", i);
        Tuple tuple = result.get(i);
        for (TupleElement<?> element : tuple.getElements()) {
          String alias = element.getAlias();
          Object value = tuple.get(alias);
          log.debug("Alias: {} -> Value: {} (Type: {})",
              alias, value, value != null ? value.getClass().getSimpleName() : "null");
        }
      }
    }
  }

  private void buildSelectionsRecursively(From<?, ?> from,
                                          List<Selection<?>> selections,
                                          Map<Class<?>, Set<String>> selectedFields,
                                          Map<Class<?>, Map<Object, Object>> entityMaps,
                                          String prefix) {

    Class<?> currentClass = from.getJavaType();
    Set<String> fields = selectedFields.get(currentClass);

    if (fields == null || fields.isEmpty()) {
      log.debug("No fields selected for: {}", currentClass.getSimpleName());
      return;
    }

    log.debug("Building selections for: {} with fields: {}", currentClass.getSimpleName(), fields);

    // Mevcut entity'nin field'larını ekle
    for (String field : fields) {
      String cleanFieldName = cleanFieldName(field);

      if (!isRelationshipField(currentClass, cleanFieldName)) {
        try {
          selections.add(from.get(cleanFieldName).alias(prefix + cleanFieldName));
          log.debug("Added simple field: {}{}", prefix, cleanFieldName);
        } catch (IllegalArgumentException e) {
          log.warn("Field '{}' not found in entity: {}", cleanFieldName, currentClass.getSimpleName());
        }
      }
    }

    // İlişkileri işle
    for (String field : fields) {
      String cleanFieldName = cleanFieldName(field);
      boolean isArtificial = isArtificialRelationField(field);

      if (isRelationshipField(currentClass, cleanFieldName)) {
        try {
          Class<?> targetClass = getTargetClass(currentClass, cleanFieldName);
          String newPrefix = prefix + cleanFieldName + "_";

          Join<?, ?> join = from.join(cleanFieldName, JoinType.LEFT);
          entityMaps.putIfAbsent(targetClass, new HashMap<>());

          log.debug("Processing relationship: {} -> {} with prefix: {} (Artificial: {})",
              currentClass.getSimpleName(), targetClass.getSimpleName(), newPrefix, isArtificial);

          buildSelectionsRecursively(join, selections, selectedFields, entityMaps, newPrefix);
        } catch (Exception e) {
          log.warn("Error processing relationship field '{}' in {}: {}",
              cleanFieldName, currentClass.getSimpleName(), e.getMessage());
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T mapResultToEntity(List<Tuple> result,
                                  Class<T> entityClass,
                                  Map<Class<?>, Set<String>> selectedFields,
                                  Map<Class<?>, Map<Object, Object>> entityMaps) {

    try {
      // Tüm entity'leri toplamak için yeni bir yaklaşım
      Map<String, Object> entityCache = new HashMap<>();
      T rootEntity = null;

      for (Tuple tuple : result) {
        rootEntity = processTupleAndBuildEntities(tuple, entityClass, selectedFields, entityCache, "");
      }

      return rootEntity;
    } catch (Exception e) {
      log.error("Error mapping result to entity {}: {}", entityClass.getSimpleName(), e.getMessage(), e);
      throw new RuntimeException("Entity mapping failed for: " + entityClass.getSimpleName(), e);
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

      // Basit field'ları set et
      Set<String> fields = selectedFields.get(entityClass);
      if (fields != null) {
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

    // İlişkileri işle - SADECE BASİT FIELDLAR SET EDİLDİKTEN SONRA
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
      return value;
    } catch (IllegalArgumentException e) {
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
}