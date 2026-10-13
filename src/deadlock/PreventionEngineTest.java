
package deadlock;

import deadlock.engine.PreventionEngine;
import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

public class PreventionEngineTest {

    public static void main(String[] args) {

        SystemState system = new SystemState();

        Process p1 = new Process("P1", 1);
        Process p2 = new Process("P2", 2);

        Resource r1 = new Resource("R1", 1);
        Resource r2 = new Resource("R2", 1);

        system.addProcess(p1);
        system.addProcess(p2);
        system.addResource(r1);
        system.addResource(r2);

        WaitForGraph wfg = new WaitForGraph();
        LockOrderGraph log = new LockOrderGraph();

        PreventionEngine engine =
                new PreventionEngine(system, wfg, log);

        // Test 1: P1 requests available R1.
        System.out.println("TEST 1: Grant request");

        System.out.println(
            engine.requestResource("P1", "R1")
        );

        System.out.println(
            "P1 holds R1: "
            + system.getAllocatedCount("P1", "R1")
        );

        // Test 2: P2 requests R1 while P1 holds it.
        System.out.println("\nTEST 2: Block request");

        System.out.println(
            engine.requestResource("P2", "R1")
        );

        System.out.println(
            "P2 waiting for: "
            + system.getWaitingRequest("P2")
        );

        System.out.println("Wait-for graph: " + wfg);

        // Test 3: Unknown process.
        System.out.println("\nTEST 3: Reject invalid request");

        System.out.println(
            engine.requestResource("P99", "R1")
        );

        // Test 4: Create an ordering conflict.
        System.out.println("\nTEST 4: Detect ordering cycle");

        // P1 already holds R1; record a path R2 -> R1.
        log.addEdge("R2", "R1");

        // P1's request for R2 proposes R1 -> R2.
        System.out.println(
            engine.requestResource("P1", "R2")
        );

        System.out.println("Lock-order graph: " + log);

        System.out.println(
            "P1 holds R2: "
            + system.getAllocatedCount("P1", "R2")
        );
    }
}
