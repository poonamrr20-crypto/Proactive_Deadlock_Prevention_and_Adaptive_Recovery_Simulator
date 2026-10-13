
package deadlock;

import deadlock.engine.PreventionEngine;
import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

public class ResourceReleaseTest {

    public static void main(String[] args) {

        SystemState system = new SystemState();

        Process p1 = new Process("P1", 1);
        Process p2 = new Process("P2", 2);

        Resource r1 = new Resource("R1", 1);

        system.addProcess(p1);
        system.addProcess(p2);
        system.addResource(r1);

        WaitForGraph wfg = new WaitForGraph();
        LockOrderGraph log = new LockOrderGraph();

        PreventionEngine engine =
                new PreventionEngine(system, wfg, log);

        // Step 1: P1 acquires R1.
        System.out.println("STEP 1: P1 requests R1");
        System.out.println(engine.requestResource("P1", "R1"));

        // Step 2: P2 requests R1 and must wait.
        System.out.println("\nSTEP 2: P2 requests R1");
        System.out.println(engine.requestResource("P2", "R1"));
        System.out.println("Wait-for graph: " + wfg);

        // Step 3: P1 releases R1.
        System.out.println("\nSTEP 3: P1 releases R1");
        engine.releaseResource("P1", "R1");

        // Step 4: Verify P2 receives R1.
        System.out.println("\nSTEP 4: Check final state");
        System.out.println("P1 holds R1: "
                + system.getAllocatedCount("P1", "R1"));
        System.out.println("P2 holds R1: "
                + system.getAllocatedCount("P2", "R1"));
        System.out.println("R1 available: "
                + r1.getAvailableInstances());
        System.out.println("P2 waiting for: "
                + system.getWaitingRequest("P2"));
        System.out.println("Wait-for graph: " + wfg);
    }
}
