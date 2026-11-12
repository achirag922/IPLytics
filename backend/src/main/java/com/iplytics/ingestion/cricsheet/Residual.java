package com.iplytics.ingestion.cricsheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

/**
 * A working copy of a JSON object from which mapped fields are removed as they are consumed.
 * Whatever remains at the end is the information that has no CSV column, so it can be preserved
 * verbatim instead of being silently dropped.
 */
final class Residual {

    private final ObjectNode root;

    Residual(JsonNode source) {
        if (!(source instanceof ObjectNode object)) {
            throw new IllegalArgumentException("Expected a JSON object but found " + source.getNodeType());
        }
        this.root = object.deepCopy();
    }

    /** Removes and returns the value at {@code path}, or null if absent. */
    JsonNode take(String... path) {
        ObjectNode parent = parentOf(path);
        return parent == null ? null : parent.remove(path[path.length - 1]);
    }

    /** Returns the value at {@code path} without removing it, or null if absent. */
    JsonNode peek(String... path) {
        ObjectNode parent = parentOf(path);
        return parent == null ? null : parent.get(path[path.length - 1]);
    }

    /** What was not consumed, with empty objects and arrays pruned. */
    ObjectNode remaining() {
        prune(root);
        return root;
    }

    private ObjectNode parentOf(String... path) {
        ObjectNode parent = root;
        for (int i = 0; i < path.length - 1; i++) {
            if (!(parent.get(path[i]) instanceof ObjectNode next)) {
                return null;
            }
            parent = next;
        }
        return parent;
    }

    /** Recursively removes empty containers; returns whether {@code node} itself ended up empty. */
    private static boolean prune(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                JsonNode child = fields.next().getValue();
                if (child.isContainerNode() && prune(child)) {
                    fields.remove();
                }
            }
            return object.isEmpty();
        }
        if (node instanceof ArrayNode array) {
            for (int i = array.size() - 1; i >= 0; i--) {
                JsonNode child = array.get(i);
                if (child.isContainerNode() && prune(child)) {
                    array.remove(i);
                }
            }
            return array.isEmpty();
        }
        return false;
    }
}
