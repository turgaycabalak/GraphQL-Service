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

import jakarta.persistence.Entity;

import com.graph.graphservice.aspect.ArtificialRelation;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class GraphQLFieldCollector {
  public static final String ENTITY_PATH = "com.yourpackage.entity";
  private static final Set<String> IGNORED_FIELDS = Set.of("__typename");

  public Map<Class<?>, Set<String>> collectFields(DataFetchingEnvironment env,
                                                  Class<?> rootEntityClass) {
    Map<Class<?>, Set<String>> selectedFields = new HashMap<>();
    DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();

    log.info("=== GraphQL Field Collection ===");
    log.info("Root entity: {}", rootEntityClass.getSimpleName());

    processSelectionSet(selectionSet, rootEntityClass, selectedFields, "");

    // Tüm entity'ler için ID field'ını otomatik ekle
    automaticallyAddIdFields(selectedFields);

    // Artificial relation field'larını işaretle
    markArtificialRelations(selectedFields);

    selectedFields.forEach((entityClass, fields) ->
        log.info("Entity: {} -> Fields: {}", entityClass.getSimpleName(), fields));

    return selectedFields;
  }

  private void markArtificialRelations(Map<Class<?>, Set<String>> selectedFields) {
    for (Map.Entry<Class<?>, Set<String>> entry : selectedFields.entrySet()) {
      Class<?> entityClass = entry.getKey();
      Set<String> fields = entry.getValue();

      for (String field : new HashSet<>(fields)) {
        if (isArtificialRelation(entityClass, field)) {
          // Artificial relation field'larını özel formatta işaretle
          String markedField = field + "::ARTIFICIAL";
          fields.remove(field);
          fields.add(markedField);
          log.debug("Marked artificial relation: {}.{}",
              entityClass.getSimpleName(), field);
        }
      }
    }
  }

  private boolean isArtificialRelation(Class<?> entityClass, String fieldName) {
    try {
      Field field = entityClass.getDeclaredField(fieldName);
      return field.isAnnotationPresent(ArtificialRelation.class);
    } catch (NoSuchFieldException e) {
      return false;
    }
  }

  private void automaticallyAddIdFields(Map<Class<?>, Set<String>> selectedFields) {
    for (Map.Entry<Class<?>, Set<String>> entry : selectedFields.entrySet()) {
      Class<?> entityClass = entry.getKey();
      Set<String> fields = entry.getValue();

      try {
        entityClass.getDeclaredField("id");
        if (!fields.contains("id") && !fields.contains("id::ARTIFICIAL")) {
          fields.add("id");
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

    for (SelectedField field : selectionSet.getFields()) {
      String fieldName = field.getName();

      if (IGNORED_FIELDS.contains(fieldName)) {
        continue;
      }

      String fullFieldPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;

      try {
        Field entityField = currentEntityClass.getDeclaredField(fieldName);

        if (isRelationshipField(entityField)) {
          // Artificial relation kontrolü
          boolean isArtificial = entityField.isAnnotationPresent(ArtificialRelation.class);
          String fieldToAdd = isArtificial ? fieldName + "::ARTIFICIAL" : fieldName;

          currentFields.add(fieldToAdd);

          // Target class'ı bul
          Class<?> targetClass = getTargetClass(entityField);
          DataFetchingFieldSelectionSet subSelection = field.getSelectionSet();

          if (subSelection != null && !subSelection.getFields().isEmpty()) {
            processSelectionSet(subSelection, targetClass, selectedFields, fullFieldPath);
          }
        } else {
          // Basit field - direkt ekle
          currentFields.add(fieldName);
        }

      } catch (NoSuchFieldException e) {
        log.debug("Field '{}' not found in entity: {} (path: {})",
            fieldName, currentEntityClass.getSimpleName(), fullFieldPath);
      }
    }
  }

  private boolean isRelationshipField(Field field) {
    Class<?> fieldType = field.getType();

    if (isSimpleType(fieldType)) {
      return false;
    }

    boolean isEntity = isEntityClass(fieldType);
    boolean isCollection = Collection.class.isAssignableFrom(fieldType);
    boolean isEntityCollection = isCollection && isCollectionOfEntities(field);

    return isEntity || isEntityCollection;
  }

  public boolean isSimpleType(Class<?> clazz) {
    return clazz.isPrimitive()
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
  }

  private boolean isEntityClass(Class<?> clazz) {
    if (clazz.getPackage() == null) {
      return false;
    }
    return clazz.getPackage().getName().startsWith(ENTITY_PATH) ||
        clazz.isAnnotationPresent(Entity.class);
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

  private Class<?> getTargetClass(Field field) {
    if (Collection.class.isAssignableFrom(field.getType())) {
      ParameterizedType genericType = (ParameterizedType) field.getGenericType();
      return (Class<?>) genericType.getActualTypeArguments()[0];
    } else {
      return field.getType();
    }
  }
}