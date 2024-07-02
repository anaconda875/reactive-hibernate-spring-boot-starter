package com.htech.data.jpa.reactive.repository.query;

import jakarta.persistence.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.reactive.stage.Stage;
import org.springframework.data.jpa.repository.query.JpaEntityGraph;
import org.springframework.data.jpa.repository.support.MutableQueryHints;
import org.springframework.data.jpa.repository.support.QueryHints;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

/**
 * @author Bao.Ngo
 */
public class Jpa21Utils {

  private static final @Nullable Method GET_ENTITY_GRAPH_METHOD;
  private static final boolean JPA21_AVAILABLE =
      ClassUtils.isPresent(
          "jakarta.persistence.NamedEntityGraph",
          org.springframework.data.jpa.repository.query.Jpa21Utils.class.getClassLoader());

  static {
    if (JPA21_AVAILABLE) {
      GET_ENTITY_GRAPH_METHOD =
          ReflectionUtils.findMethod(EntityManager.class, "getEntityGraph", String.class);
    } else {
      GET_ENTITY_GRAPH_METHOD = null;
    }
  }

  private Jpa21Utils() {}

  public static QueryHints getFetchGraphHint(
      Stage.Session em, @Nullable JpaEntityGraph entityGraph, Class<?> entityType) {
    MutableQueryHints result = new MutableQueryHints();

    if (entityGraph == null) {
      return result;
    }

    EntityGraph<?> graph = tryGetFetchGraph(em, entityGraph, entityType);
    if (graph == null) {
      return result;
    }

    result.add(entityGraph.getType().getKey(), graph);
    return result;
  }

  @Nullable
  private static EntityGraph<?> tryGetFetchGraph(
      Stage.Session session, JpaEntityGraph jpaEntityGraph, Class<?> entityType) {
    Assert.notNull(session, "EntityManager must not be null");
    Assert.notNull(jpaEntityGraph, "EntityGraph must not be null");
    Assert.notNull(entityType, "EntityType must not be null");

    Assert.isTrue(
        JPA21_AVAILABLE,
        "The EntityGraph-Feature requires at least a JPA 2.1 persistence provider");
    Assert.isTrue(
        GET_ENTITY_GRAPH_METHOD != null,
        "It seems that you have the JPA 2.1 API but a JPA 2.0 implementation on the classpath");

    try {
      // first check whether an entityGraph with that name is already registered.
      return session.getEntityGraph(entityType, jpaEntityGraph.getName());
    } catch (Exception ex) {
      // try to create and dynamically register the entityGraph
      return createDynamicEntityGraph(session, jpaEntityGraph, entityType);
    }
  }

  private static EntityGraph<?> createDynamicEntityGraph(
      Stage.Session em, JpaEntityGraph jpaEntityGraph, Class<?> entityType) {
    Assert.notNull(em, "EntityManager must not be null");
    Assert.notNull(jpaEntityGraph, "JpaEntityGraph must not be null");
    Assert.notNull(entityType, "Entity type must not be null");
    Assert.isTrue(
        jpaEntityGraph.isAdHocEntityGraph(), "The given " + jpaEntityGraph + " is not dynamic");

    EntityGraph<?> entityGraph = em.createEntityGraph(entityType);
    configureFetchGraphFrom(jpaEntityGraph, entityGraph);

    return entityGraph;
  }

  static void configureFetchGraphFrom(JpaEntityGraph jpaEntityGraph, EntityGraph<?> entityGraph) {
    List<String> attributePaths = new ArrayList<>(jpaEntityGraph.getAttributePaths());

    // Sort to ensure that the intermediate entity subgraphs are created accordingly.
    Collections.sort(attributePaths);

    for (String path : attributePaths) {
      String[] pathComponents = StringUtils.delimitedListToStringArray(path, ".");
      createGraph(pathComponents, 0, entityGraph, null);
    }
  }

  private static void createGraph(
      String[] pathComponents, int offset, EntityGraph<?> root, @Nullable Subgraph<?> parent) {
    String attributeName = pathComponents[offset];

    // we found our leaf property, now let's see if it already exists and add it if not
    if (pathComponents.length - 1 == offset) {
      if (parent == null && !exists(attributeName, root.getAttributeNodes())) {
        root.addAttributeNodes(attributeName);
      } else if (parent != null && !exists(attributeName, parent.getAttributeNodes())) {
        parent.addAttributeNodes(attributeName);
      }

      return;
    }

    AttributeNode<?> node = findAttributeNode(attributeName, root, parent);
    if (node != null) {
      Subgraph<?> subgraph = getSubgraph(node);
      if (subgraph == null) {
        subgraph =
            parent != null ? parent.addSubgraph(attributeName) : root.addSubgraph(attributeName);
      }

      createGraph(pathComponents, offset + 1, root, subgraph);
      return;
    }

    if (parent == null) {
      createGraph(pathComponents, offset + 1, root, root.addSubgraph(attributeName));
    } else {
      createGraph(pathComponents, offset + 1, root, parent.addSubgraph(attributeName));
    }
  }

  private static boolean exists(String attributeNodeName, List<AttributeNode<?>> nodes) {
    return findAttributeNode(attributeNodeName, nodes) != null;
  }

  @Nullable
  private static AttributeNode<?> findAttributeNode(
      String attributeNodeName, EntityGraph<?> entityGraph, @Nullable Subgraph<?> parent) {
    return findAttributeNode(
        attributeNodeName,
        parent != null ? parent.getAttributeNodes() : entityGraph.getAttributeNodes());
  }

  @Nullable
  private static AttributeNode<?> findAttributeNode(
      String attributeNodeName, List<AttributeNode<?>> nodes) {
    for (AttributeNode<?> node : nodes) {
      if (ObjectUtils.nullSafeEquals(node.getAttributeName(), attributeNodeName)) {
        return node;
      }
    }

    return null;
  }

  @Nullable
  private static Subgraph<?> getSubgraph(AttributeNode<?> node) {
    return node.getSubgraphs().isEmpty() ? null : node.getSubgraphs().values().iterator().next();
  }
}
