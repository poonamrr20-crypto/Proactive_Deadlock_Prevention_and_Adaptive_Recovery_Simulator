
package deadlock;

import deadlock.engine.PreventionEngine;
import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

public class LockOrderRollbackTest {
    public static void main(String[] args) {
        SystemState system = new SystemState();

        Process p1 = new Process("P1", 1);

        Resource r1 = new Resource("R1", 1);
        Resource r2 = new Resource("R2", 1);
        Resource r3 = new Resource("R3", 1);

        system.addProcess(p1);
        system.addResource(r1);
        system.addResource(r2);
        system.addResource(r3);

        WaitForGraph wfg = new WaitForGraph();
        LockOrderGraph log = new LockOrderGraph();

        PreventionEngine engine =
                new PreventionEngine(system, wfg, log);

        // P1 acquires two resources.
        System.out.println("STEP 1: P1 acquires R1");
        System.out.println(engine.requestResource("P1", "R1"));

        System.out.println("\nSTEP 2: P1 acquires R2");
        System.out.println(engine.requestResource("P1", "R2"));

        // Add a pre-existing ordering path:
        // R3 -> R1
        // Adding R2 -> R3 will not create a cycle by itself,
        // but R1 -> R3 will create a cycle through R3 -> R1.
        log.addEdge("R3", "R1");

        System.out.println("\nSTEP 3: Initial Lock-order Graph");
        System.out.println(log);

        System.out.println("\nSTEP 4: P1 requests R3");
        System.out.println(engine.requestResource("P1", "R3"));

        System.out.println("\nSTEP 5: Final Lock-order Graph");
        System.out.println(log);

        System.out.println("\nCheck newly proposed edges:");
        System.out.println("R1 -> R3 exists: "
                + log.hasEdge("R1", "R3"));
        System.out.println("R2 -> R3 exists: "
                + log.hasEdge("R2", "R3"));

        System.out.println("\nCheck existing edge preserved:");
        System.out.println("R3 -> R1 exists: "
                + log.hasEdge("R3", "R1"));

        System.out.println("\nP1 holds R3: "
                + system.getAllocatedCount("P1", "R3"));
    }
}
