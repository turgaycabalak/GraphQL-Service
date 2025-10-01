package com.graph.graphservice.utils;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Entity;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class GraphQLFieldCollector {
  public static final String ENTITY_PATH = "com.graph.graphservice.entity";
  private static final Set<String> IGNORED_FIELDS = Set.of("__typename");


  public Map<Class<?>, Set<String>> collectFields(DataFetchingEnvironment env,
                                                  Class<?> rootEntityClass) {
    Map<Class<?>, Set<String>> selectedFields = new HashMap<>();
    DataFetchingFieldSelectionSet selectionSet = env.getSelectionSet();

    log.info("=== GraphQL Field Collection ===");
    log.info("Root entity: {}", rootEntityClass.getSimpleName());

    processSelectionSet(selectionSet, rootEntityClass, selectedFields, new HashSet<>());

    selectedFields.forEach((entityClass, fields) ->
        log.info("Entity: {} -> Fields: {}", entityClass.getSimpleName(), fields));

    return selectedFields;
  }

  private void processSelectionSet(DataFetchingFieldSelectionSet selectionSet,
                                   Class<?> currentEntityClass,
                                   Map<Class<?>, Set<String>> selectedFields,
                                   Set<Class<?>> processedClasses) {

    // Circular reference önleme
    if (processedClasses.contains(currentEntityClass)) {
      return;
    }
    processedClasses.add(currentEntityClass);

    Set<String> currentFields = selectedFields.computeIfAbsent(currentEntityClass, k -> new HashSet<>());

    for (SelectedField field : selectionSet.getFields()) {
      String fieldName = field.getName();

      if (IGNORED_FIELDS.contains(fieldName)) {
        continue;
      }

      try {
        // Field mevcut entity'de var mı kontrol et
        Field entityField = currentEntityClass.getDeclaredField(fieldName);

        if (isRelationshipField(entityField)) {
          // Relationship field - sadece field adını ekle
          currentFields.add(fieldName);

          // Target class'ı bul
          Class<?> targetClass = getTargetClass(entityField);
          DataFetchingFieldSelectionSet subSelection = field.getSelectionSet();

          if (subSelection != null && !subSelection.getFields().isEmpty()) {
            // Alt field'ları target entity için process et
            processSelectionSet(subSelection, targetClass, selectedFields, new HashSet<>(processedClasses));
          }
        } else {
          // Basit field - direkt ekle
          currentFields.add(fieldName);
        }

      } catch (NoSuchFieldException e) {
        log.warn("Field '{}' not found in entity: {}", fieldName, currentEntityClass.getSimpleName());
      }
    }
  }

  private boolean isRelationshipField(Field field) {
    Class<?> fieldType = field.getType();

    // Önce basit type'ları eledik
    if (isSimpleType(fieldType)) {
      return false;
    }

    // Entity kontrolü
    boolean isEntity = isEntityClass(fieldType);

    // Collection kontrolü
    boolean isCollection = Collection.class.isAssignableFrom(fieldType);
    boolean isEntityCollection = isCollection && isCollectionOfEntities(field);

    return isEntity || isEntityCollection;
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
