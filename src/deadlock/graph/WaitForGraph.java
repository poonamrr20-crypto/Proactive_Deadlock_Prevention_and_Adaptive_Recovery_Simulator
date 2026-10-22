
package deadlock.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the Wait-for Graph (WFG).
 *
 * An edge P1 -> P2 means P1 is waiting for a resource
 * currently held by P2.
 */
public class WaitForGraph {

    private final Map<String, Set<String>> adjacencyList;

    public WaitForGraph() {
        adjacencyList = new LinkedHashMap<>();
    }

    /**
     * Adds a process as a node in the graph.
     */
    public void addProcess(String processId) {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException(
                "Process ID cannot be empty."
            );
        }

        adjacencyList.putIfAbsent(
            processId,
            new LinkedHashSet<>()
        );
    }

    /**
     * Adds a waiting dependency: fromProcess -> toProcess.
     */
    public void addEdge(String fromProcess, String toProcess) {
        addProcess(fromProcess);
        addProcess(toProcess);

        adjacencyList.get(fromProcess).add(toProcess);
    }

    /**
     * Removes a waiting dependency.
     */
    public void removeEdge(String fromProcess, String toProcess) {
        Set<String> neighbors = adjacencyList.get(fromProcess);

        if (neighbors != null) {
            neighbors.remove(toProcess);
        }
    }
    
/**
 * Removes every node and edge from the graph.
 */
public void clear() {
    adjacencyList.clear();
}


    /**
     * Returns the processes that the given process is waiting for.
     */
    public Set<String> getNeighbors(String processId) {
        Set<String> neighbors = adjacencyList.get(processId);

        if (neighbors == null) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(neighbors);
    }

    /**
     * Returns all process IDs in the graph.
     */
    public Set<String> getProcesses() {
        return Collections.unmodifiableSet(
            adjacencyList.keySet()
        );
    }

    /**
     * Checks whether the graph contains a directed cycle.
     */
    public boolean hasCycle() {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> recursionStack = new LinkedHashSet<>();

        for (String processId : adjacencyList.keySet()) {
            if (!visited.contains(processId)) {
                if (hasCycleDFS(
                        processId, visited, recursionStack)) {
                    return true;
                }
            }
        }

        return false;
    }

    public Set<String> getCycleNodes() {
        Set<String> visited = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        for (String processId : adjacencyList.keySet()) {
            Set<String> cycle = findCycle(processId, visited, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return Collections.emptySet();
    }

    private Set<String> findCycle(String current, Set<String> visited,
            List<String> path) {
        int cycleStart = path.indexOf(current);
        if (cycleStart >= 0) {
            return new LinkedHashSet<>(path.subList(cycleStart, path.size()));
        }
        if (visited.contains(current)) {
            return Collections.emptySet();
        }
        visited.add(current);
        path.add(current);
        for (String neighbor : adjacencyList.get(current)) {
            Set<String> cycle = findCycle(neighbor, visited, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        return Collections.emptySet();
    }

    /**
     * DFS detects a cycle by checking whether a node is
     * already in the current recursion path.
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

    /**
     * Returns a readable representation of the graph.
     */
    @Override
    public String toString() {
        return adjacencyList.toString();
    }
}
