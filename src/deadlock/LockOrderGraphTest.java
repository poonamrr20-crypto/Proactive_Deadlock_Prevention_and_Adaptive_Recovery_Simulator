
package deadlock;

import deadlock.graph.LockOrderGraph;

public class LockOrderGraphTest {

    public static void main(String[] args) {

        LockOrderGraph graph = new LockOrderGraph();

        // Test 1: No cycle
        System.out.println("TEST 1: No cycle");

        graph.addEdge("R1", "R2");

        System.out.println("Graph: " + graph);
        System.out.println("Cycle detected: " + graph.hasCycle());

        // Test 2: Introduce a cycle
        System.out.println("\nTEST 2: Cycle exists");

        graph.addEdge("R2", "R1");

        System.out.println("Graph: " + graph);
        System.out.println("Cycle detected: " + graph.hasCycle());

        // Test 3: Remove the cycle
        System.out.println("\nTEST 3: Remove cycle");

        graph.removeEdge("R2", "R1");

        System.out.println("Graph: " + graph);
        System.out.println("Cycle detected: " + graph.hasCycle());
    }
}
