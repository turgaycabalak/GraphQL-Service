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

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;

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

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);

    List<Selection<?>> selections = new ArrayList<>();
    Map<Class<?>, Map<Object, Object>> entityMaps = new HashMap<>();

    buildSelectionsRecursively(root, selections, selectedFields, entityMaps, "");

    cq.multiselect(selections)
        .where(cb.equal(root.get("id"), entityId));

    try {
      List<Tuple> result = entityManager.createQuery(cq).getResultList();

      if (result.isEmpty()) {
        return null;
      }

      return mapResultToEntity(result, entityClass, selectedFields, entityMaps);
    } catch (Exception e) {
      log.error("Error executing dynamic query for {}: {}", entityClass.getSimpleName(), e.getMessage(), e);
      throw new RuntimeException("Query execution failed", e);
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
      if (!isRelationshipField(currentClass, field)) {
        try {
          selections.add(from.get(field).alias(prefix + field));
          log.debug("Added simple field: {}{}", prefix, field);
        } catch (IllegalArgumentException e) {
          log.warn("Field '{}' not found in entity: {}", field, currentClass.getSimpleName());
        }
      }
    }

    // İlişkileri işle
    for (String field : fields) {
      if (isRelationshipField(currentClass, field)) {
        try {
          Class<?> targetClass = getTargetClass(currentClass, field);
          String newPrefix = prefix + field + "_";

          Join<?, ?> join = from.join(field, JoinType.LEFT);
          entityMaps.putIfAbsent(targetClass, new HashMap<>());

          log.debug("Processing relationship: {} -> {} with prefix: {}",
              currentClass.getSimpleName(), targetClass.getSimpleName(), newPrefix);

          buildSelectionsRecursively(join, selections, selectedFields, entityMaps, newPrefix);
        } catch (Exception e) {
          log.warn("Error processing relationship field '{}' in {}: {}",
              field, currentClass.getSimpleName(), e.getMessage());
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
      T rootEntity = entityClass.getDeclaredConstructor().newInstance();
      Map<Object, Object> entityCache = new HashMap<>();

      for (Tuple tuple : result) {
        processTuple(tuple, rootEntity, entityClass, selectedFields, entityCache, "");
      }

      return rootEntity;
    } catch (Exception e) {
      throw new RuntimeException("Entity mapping failed for: " + entityClass.getSimpleName(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private void processTuple(Tuple tuple,
                            Object currentEntity,
                            Class<?> entityClass,
                            Map<Class<?>, Set<String>> selectedFields,
                            Map<Object, Object> entityCache,
                            String prefix) throws Exception {

    Set<String> fields = selectedFields.get(entityClass);
    if (fields == null) {
      return;
    }

    // Entity ID'sini al ve cache kontrolü yap
    Object entityId = tuple.get(prefix + "id");
    if (entityId != null && entityCache.containsKey(entityId)) {
      return; // Bu entity zaten işlendi
    }

    if (entityId != null) {
      entityCache.put(entityId, currentEntity);
    }

    // Basit field'ları set et
    for (String field : fields) {
      if (!isRelationshipField(entityClass, field)) {
        Object value = tuple.get(prefix + field);
        if (value != null) {
          setFieldValue(currentEntity, field, value);
        }
      }
    }

    // İlişkileri işle
    for (String field : fields) {
      if (isRelationshipField(entityClass, field)) {
        Class<?> targetClass = getTargetClass(entityClass, field);
        String newPrefix = prefix + field + "_";

        Object relatedEntityId = tuple.get(newPrefix + "id");

        if (relatedEntityId != null) {
          Object relatedEntity = targetClass.getDeclaredConstructor().newInstance();

          // Eğer collection ilişkisi ise
          if (isCollectionField(entityClass, field)) {
            Set<Object> collection = getOrCreateCollection(currentEntity, field);

            // Aynı related entity collection'da var mı kontrol et
            if (!isEntityInCollection(collection, relatedEntityId)) {
              processTuple(tuple, relatedEntity, targetClass, selectedFields, entityCache, newPrefix);
              collection.add(relatedEntity);
              setBackReference(currentEntity, relatedEntity, field, entityClass);
            }
          } else {
            // Single relation
            processTuple(tuple, relatedEntity, targetClass, selectedFields, entityCache, newPrefix);
            setFieldValue(currentEntity, field, relatedEntity);
            setBackReference(currentEntity, relatedEntity, field, entityClass);
          }
        }
      }
    }
  }

  private boolean isRelationshipField(Class<?> entityClass, String fieldName) {
    try {
      Field field = entityClass.getDeclaredField(fieldName);
      Class<?> fieldType = field.getType();

      // Basit type'ları eledik
      if (isSimpleType(fieldType)) {
        return false;
      }

      boolean isEntity = isEntityClass(fieldType);
      boolean isCollection = Collection.class.isAssignableFrom(fieldType);
      boolean isEntityCollection = isCollection && isCollectionOfEntities(field);

      boolean isRelationship = isEntity || isEntityCollection;
      log.debug("Field {} in {} -> isRelationship: {}", fieldName, entityClass.getSimpleName(), isRelationship);

      return isRelationship;
    } catch (NoSuchFieldException e) {
      log.warn("Field '{}' not found in entity: {}", fieldName, entityClass.getSimpleName());
      return false;
    }
  }

  private boolean isSimpleType(Class<?> clazz) {
    return clazz.isPrimitive()
        || clazz == String.class
        || clazz == Integer.class
        || clazz == Long.class
        || clazz == Double.class
        || clazz == Float.class
        || clazz == Boolean.class
        || clazz == BigDecimal.class
        || clazz == UUID.class;
  }

  private boolean isEntityClass(Class<?> clazz) {
    // Package kontrolü - null olabilir
    Package pkg = clazz.getPackage();
    if (pkg == null) {
      log.debug("Class {} has no package, not an entity", clazz.getSimpleName());
      return false;
    }

    String packageName = pkg.getName();
    boolean isEntity = packageName.startsWith(GraphQLFieldCollector.ENTITY_PATH) ||
        clazz.isAnnotationPresent(Entity.class);

    log.debug("Class: {} -> Package: {} -> isEntity: {}",
        clazz.getSimpleName(), packageName, isEntity);

    return isEntity;
  }

  private boolean isCollectionOfEntities(Field field) {
    if (!Collection.class.isAssignableFrom(field.getType())) {
      return false;
    }

    ParameterizedType genericType = (ParameterizedType) field.getGenericType();
    Class<?> collectionType = (Class<?>) genericType.getActualTypeArguments()[0];
    return isEntityClass(collectionType);
  }

  private boolean isCollectionField(Class<?> entityClass, String fieldName) {
    try {
      Field field = entityClass.getDeclaredField(fieldName);
      return Collection.class.isAssignableFrom(field.getType());
    } catch (NoSuchFieldException e) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private Set<Object> getOrCreateCollection(Object entity, String fieldName) throws Exception {
    Field field = entity.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);

    Set<Object> collection = (Set<Object>) field.get(entity);
    if (collection == null) {
      collection = new HashSet<>();
      field.set(entity, collection);
    }
    return collection;
  }

  private boolean isEntityInCollection(Set<Object> collection, Object entityId) {
    return collection.stream()
        .anyMatch(entity -> {
          try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return entityId.equals(idField.get(entity));
          } catch (Exception e) {
            return false;
          }
        });
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

      // Type conversion gerekirse burada yapılabilir
      if (value != null && !field.getType().isAssignableFrom(value.getClass())) {
        value = convertType(value, field.getType());
      }

      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Field set failed: " + fieldName + " in " + target.getClass().getSimpleName(), e);
    }
  }

  private Object convertType(Object value, Class<?> targetType) {
    // Gerekli type conversion'ları burada yap
    if (targetType == BigDecimal.class && value instanceof Number) {
      return BigDecimal.valueOf(((Number) value).doubleValue());
    }
    // Diğer conversion'lar...
    return value;
  }

  private void setBackReference(Object parent, Object child, String fieldName, Class<?> parentClass) {
    // Bidirectional ilişkiler için back reference'ı set et
    // Örnek: child.setParent(parent) gibi
    // Bu kısım entity'lerinize özel olarak genişletilebilir
  }
}