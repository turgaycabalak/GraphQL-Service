package com.graph.graphservice.utils;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Entity;

import com.graph.graphservice.aspect.ArtificialRelation;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class GraphQLFieldCollector {
  public static final String ENTITY_PATH = "com.graph.graphservice.entity";
  private static final Set<String> IGNORED_FIELDS = Set.of(
      "__typename", "contracts", "totalCount", "page", "size", "totalPages"
  );

  public Map<Class<?>, Set<String>> collectFields(DataFetchingEnvironment env,
                                                  Class<?> rootEntityClass) {
    Map<Class<?>, Set<String>> selectedFields = new HashMap<>();
    DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();

    log.info("=== GRAPHQL FIELD COLLECTOR START ===");
    log.info("Root entity: {}", rootEntityClass.getSimpleName());
    log.debug("Selection set fields: {}", selectionSet.getFields().stream()
        .map(SelectedField::getName)
        .collect(Collectors.toList()));

    processSelectionSet(selectionSet, rootEntityClass, selectedFields, "");

    // Tüm entity'ler için ID field'ını otomatik ekle
    automaticallyAddIdFields(selectedFields);

    // Artificial relation field'larını işaretle
    markArtificialRelations(selectedFields);

    log.debug("=== FINAL COLLECTED FIELDS ===");
    selectedFields.forEach((entityClass, fields) ->
        log.info("Entity: {} -> Fields: {}", entityClass.getSimpleName(), fields));

    return selectedFields;
  }

  private void markArtificialRelations(Map<Class<?>, Set<String>> selectedFields) {
    log.debug("=== MARKING ARTIFICIAL RELATIONS ===");
    for (Map.Entry<Class<?>, Set<String>> entry : selectedFields.entrySet()) {
      Class<?> entityClass = entry.getKey();
      Set<String> fields = entry.getValue();

      for (String field : new HashSet<>(fields)) {
        if (isArtificialRelation(entityClass, field)) {
          String markedField = field + "::ARTIFICIAL";
          fields.remove(field);
          fields.add(markedField);
          log.debug("Marked artificial relation: {}.{} -> {}",
              entityClass.getSimpleName(), field, markedField);
        }
      }
    }
  }

  private boolean isArtificialRelation(Class<?> entityClass, String fieldName) {
    try {
      Field field = entityClass.getDeclaredField(fieldName);
      boolean isArtificial = field.isAnnotationPresent(ArtificialRelation.class);
      if (isArtificial) {
        log.debug("Found artificial relation: {}.{}", entityClass.getSimpleName(), fieldName);
      }
      return isArtificial;
    } catch (NoSuchFieldException e) {
      log.debug("Field '{}' not found when checking artificial relation in: {}",
          fieldName, entityClass.getSimpleName());
      return false;
    }
  }

  private void automaticallyAddIdFields(Map<Class<?>, Set<String>> selectedFields) {
    log.debug("=== AUTO-ADDING ID FIELDS ===");
    for (Map.Entry<Class<?>, Set<String>> entry : selectedFields.entrySet()) {
      Class<?> entityClass = entry.getKey();
      Set<String> fields = entry.getValue();

      try {
        entityClass.getDeclaredField("id");
        if (!fields.contains("id") && !fields.contains("id::ARTIFICIAL")) {
          fields.add("id");
          log.debug("Auto-added 'id' field for: {}", entityClass.getSimpleName());
        }
      } catch (NoSuchFieldException e) {
        log.debug("Entity {} doesn't have 'id' field, skipping auto-add",
            entityClass.getSimpleName());
      }
    }
  }

  private void processSelectionSet(DataFetchingFieldSelectionSet selectionSet,
                                   Class<?> currentEntityClass,
                                   Map<Class<?>, Set<String>> selectedFields,
                                   String currentPath) {

    Set<String> currentFields = selectedFields.computeIfAbsent(currentEntityClass,
        k -> new HashSet<>());

    log.debug("Processing selection set for: {} (path: '{}')",
        currentEntityClass.getSimpleName(), currentPath);

    for (SelectedField field : selectionSet.getFields()) {
      String fieldName = field.getName();

      if (IGNORED_FIELDS.contains(fieldName)) {
        log.debug("Ignoring field: {}", fieldName);
        continue;
      }

      String fullFieldPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;

      log.debug("Processing field: '{}' (full path: '{}')", fieldName, fullFieldPath);

      try {
        Field entityField = currentEntityClass.getDeclaredField(fieldName);
        log.debug("Found entity field: {} with type: {}",
            fieldName, entityField.getType().getSimpleName());

        if (isRelationshipField(entityField)) {
          boolean isArtificial = entityField.isAnnotationPresent(ArtificialRelation.class);
          String fieldToAdd = isArtificial ? fieldName + "::ARTIFICIAL" : fieldName;

          currentFields.add(fieldToAdd);
          log.debug("Added RELATIONSHIP field: {} (artificial: {})", fieldToAdd, isArtificial);

          Class<?> targetClass = getTargetClass(entityField);
          DataFetchingFieldSelectionSet subSelection = field.getSelectionSet();

          log.debug("Relationship '{}' -> target class: {}", fieldName, targetClass.getSimpleName());
          log.debug("Sub-selection for '{}': {}", fieldName,
              subSelection != null ? subSelection.getFields().stream()
                  .map(SelectedField::getName)
                  .collect(Collectors.toList()) : "null");

          if (subSelection != null && !subSelection.getFields().isEmpty()) {
            log.debug("Recursing into relationship: {} -> {}", fieldName, targetClass.getSimpleName());
            processSelectionSet(subSelection, targetClass, selectedFields, fullFieldPath);
          } else {
            log.debug("No sub-selection for relationship: {}", fieldName);
          }
        } else {
          currentFields.add(fieldName);
          log.debug("Added SIMPLE field: {}", fieldName);
        }

      } catch (NoSuchFieldException e) {
        log.debug("Field '{}' not in {}, but might be in nested entity",
            fieldName, currentEntityClass.getSimpleName());
      }
    }
  }

  private boolean isRelationshipField(Field field) {
    Class<?> fieldType = field.getType();

    if (isSimpleType(fieldType)) {
      log.debug("Field '{}' is SIMPLE type: {}", field.getName(), fieldType.getSimpleName());
      return false;
    }

    boolean isEntity = isEntityClass(fieldType);
    boolean isCollection = Collection.class.isAssignableFrom(fieldType);
    boolean isEntityCollection = isCollection && isCollectionOfEntities(field);

    log.debug("Field '{}' analysis - Type: {}, isEntity: {}, isCollection: {}, isEntityCollection: {}",
        field.getName(), fieldType.getSimpleName(), isEntity, isCollection, isEntityCollection);

    boolean isRelationship = isEntity || isEntityCollection;
    log.debug("Field '{}' is RELATIONSHIP: {}", field.getName(), isRelationship);

    return isRelationship;
  }

  public boolean isSimpleType(Class<?> clazz) {
    boolean isSimple = clazz.isPrimitive()
        || clazz == String.class
        || clazz == Integer.class
        || clazz == Long.class
        || clazz == Double.class
        || clazz == Float.class
        || clazz == Boolean.class
        || clazz == BigDecimal.class
        || clazz == UUID.class
        || clazz == LocalDateTime.class
        || clazz.isEnum();

    log.trace("Type {} is simple: {}", clazz.getSimpleName(), isSimple);
    return isSimple;
  }

  private boolean isEntityClass(Class<?> clazz) {
    if (clazz.getPackage() == null) {
      log.trace("Class {} has no package, not entity", clazz.getSimpleName());
      return false;
    }

    String packageName = clazz.getPackage().getName();
    boolean isEntity = packageName.startsWith(ENTITY_PATH) || clazz.isAnnotationPresent(Entity.class);

    log.debug("Class {} is entity: {} (package: {}, entity path: {})",
        clazz.getSimpleName(), isEntity, packageName, ENTITY_PATH);
    return isEntity;
  }

  private boolean isCollectionOfEntities(Field field) {
    if (!Collection.class.isAssignableFrom(field.getType())) {
      return false;
    }

    try {
      ParameterizedType genericType = (ParameterizedType) field.getGenericType();
      Class<?> collectionType = (Class<?>) genericType.getActualTypeArguments()[0];
      boolean isEntityCollection = isEntityClass(collectionType);

      log.debug("Collection field '{}' of type {} -> isEntityCollection: {}",
          field.getName(), collectionType.getSimpleName(), isEntityCollection);
      return isEntityCollection;
    } catch (Exception e) {
      log.warn("Error checking collection of entities for field '{}': {}",
          field.getName(), e.getMessage());
      return false;
    }
  }

  private Class<?> getTargetClass(Field field) {
    Class<?> targetClass;
    if (Collection.class.isAssignableFrom(field.getType())) {
      ParameterizedType genericType = (ParameterizedType) field.getGenericType();
      targetClass = (Class<?>) genericType.getActualTypeArguments()[0];
      log.debug("Collection field '{}' target class: {}", field.getName(), targetClass.getSimpleName());
    } else {
      targetClass = field.getType();
      log.debug("Single relation field '{}' target class: {}", field.getName(), targetClass.getSimpleName());
    }
    return targetClass;
  }
}