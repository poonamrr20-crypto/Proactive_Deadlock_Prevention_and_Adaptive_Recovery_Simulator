
package deadlock;

import deadlock.graph.WaitForGraph;

public class WaitForGraphTest {

    public static void main(String[] args) {

        WaitForGraph graph = new WaitForGraph();

        // Test 1: A graph without a cycle
        System.out.println("TEST 1: No cycle");

        graph.addEdge("P1", "P2");
        graph.addEdge("P2", "P3");

        System.out.println("Graph: " + graph);
        System.out.println("Cycle detected: " + graph.hasCycle());

        // Test 2: Introduce a cycle
        System.out.println("\nTEST 2: Cycle exists");

        graph.addEdge("P3", "P1");

        System.out.println("Graph: " + graph);
        System.out.println("Cycle detected: " + graph.hasCycle());

        // Test 3: Remove an edge to break the cycle
        System.out.println("\nTEST 3: Remove cycle");

        graph.removeEdge("P3", "P1");

        System.out.println("Graph: " + graph);
        System.out.println("Cycle detected: " + graph.hasCycle());
    }
}