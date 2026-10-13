
package deadlock;

import deadlock.engine.PreventionEngine;
import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

public class DeadlockPreventionTest {

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

        // Step 1: P1 acquires R1.
        System.out.println("STEP 1: P1 requests R1");
        System.out.println(engine.requestResource("P1", "R1"));

        // Step 2: P2 acquires R2.
        System.out.println("\nSTEP 2: P2 requests R2");
        System.out.println(engine.requestResource("P2", "R2"));

        // Step 3: P1 requests R2, which P2 holds.
        System.out.println("\nSTEP 3: P1 requests R2");
        System.out.println(engine.requestResource("P1", "R2"));

        System.out.println("Wait-for graph: " + wfg);
        System.out.println("Lock-order graph: " + log);

        // Step 4: P2 requests R1.
        System.out.println("\nSTEP 4: P2 requests R1");
        System.out.println(engine.requestResource("P2", "R1"));

        // Step 5: Inspect the final state.
        System.out.println("\nSTEP 5: Final state");

        System.out.println("P1 holds R1: "
                + system.getAllocatedCount("P1", "R1"));

        System.out.println("P1 holds R2: "
                + system.getAllocatedCount("P1", "R2"));

        System.out.println("P2 holds R1: "
                + system.getAllocatedCount("P2", "R1"));

        System.out.println("P2 holds R2: "
                + system.getAllocatedCount("P2", "R2"));

        System.out.println("P1 waiting for: "
                + system.getWaitingRequest("P1"));

        System.out.println("P2 waiting for: "
                + system.getWaitingRequest("P2"));

        System.out.println("Wait-for graph: " + wfg);
        System.out.println("Lock-order graph: " + log);
    }
}
