
package deadlock.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents the Lock-order Graph (LOG).
 *
 * An edge R1 -> R2 means a process acquired R1
 * before requesting R2.
 */
public class LockOrderGraph {

    private final Map<String, Set<String>> adjacencyList;

    public LockOrderGraph() {
        adjacencyList = new LinkedHashMap<>();
    }

    /**
     * Adds a resource as a node in the graph.
     */
    public void addResource(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException(
                "Resource ID cannot be empty."
            );
        }

        adjacencyList.putIfAbsent(
            resourceId,
            new LinkedHashSet<>()
        );
    }

    /**
     * Records an ordering dependency: fromResource -> toResource.
     */
    public void addEdge(String fromResource, String toResource) {
        addResource(fromResource);
        addResource(toResource);

        adjacencyList.get(fromResource).add(toResource);
    }

    /**
     * Removes an ordering dependency.
     */
    public void removeEdge(String fromResource, String toResource) {
        Set<String> neighbors = adjacencyList.get(fromResource);

        if (neighbors != null) {
            neighbors.remove(toResource);
        }
    }

    /**
     * Returns the resources that follow the given resource.
     */
    public Set<String> getNeighbors(String resourceId) {
        Set<String> neighbors = adjacencyList.get(resourceId);

        if (neighbors == null) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(neighbors);
    }

    /**
     * Checks whether the resource-order graph contains a cycle.
     */
    public boolean hasCycle() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> recursionStack = new LinkedHashSet<>();

        for (String resourceId : adjacencyList.keySet()) {
            if (!visited.contains(resourceId)) {
                if (hasCycleDFS(
                        resourceId, visited, recursionStack)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Uses Depth-First Search to detect a directed cycle.
     */
    private boolean hasCycleDFS(
            String current,
            Set<String> visited,
            Set<String> recursionStack) {

        visited.add(current);
        recursionStack.add(current);

        for (String neighbor : adjacencyList.get(current)) {

            if (!visited.contains(neighbor)) {
                if (hasCycleDFS(
                        neighbor, visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                return true;
            }
        }

        recursionStack.remove(current);
        return false;
    }

    @Override
    public String toString() {
        return adjacencyList.toString();
    }

    public boolean hasEdge(String from, String to) {
    return adjacencyList.containsKey(from)
            && adjacencyList.get(from).contains(to);
}
}
