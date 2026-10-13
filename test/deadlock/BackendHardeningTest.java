
package deadlock;

import deadlock.engine.PreventionEngine;
import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

public class BackendHardeningTest {

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

        // Test 1: First request succeeds.
        System.out.println("TEST 1: P1 requests R1");
        System.out.println(engine.requestResource("P1", "R1"));

        // Test 2: Repeated request for an already-held resource.
        System.out.println("\nTEST 2: P1 requests R1 again");
        System.out.println(engine.requestResource("P1", "R1"));

        System.out.println("P1 holds R1: "
                + system.getAllocatedCount("P1", "R1"));

        // Test 3: Invalid release by P2.
        System.out.println("\nTEST 3: P2 tries to release R1");

        System.out.println("Release successful: "
                + system.releaseResource("P2", "R1"));

        System.out.println("R1 available: "
                + r1.getAvailableInstances());

        // Test 4: Unknown resource request.
        System.out.println("\nTEST 4: P1 requests unknown resource");

        System.out.println(
            engine.requestResource("P1", "R99")
        );

        // Test 5: Confirm final allocation state.
        System.out.println("\nTEST 5: Final state");
        System.out.println(system);
        System.out.println("Wait-for graph: " + wfg);
        System.out.println("Lock-order graph: " + log);
    }
}
