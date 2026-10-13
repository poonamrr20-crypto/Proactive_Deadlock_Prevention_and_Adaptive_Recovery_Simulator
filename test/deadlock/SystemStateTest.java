
package deadlock;

import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

public class SystemStateTest {

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

        // Test 1: Allocate R1 to P1
        System.out.println("TEST 1: Allocate resource R1 to P1");

        System.out.println(
            "Allocation successful: "
            + system.allocateResource("P1", "R1")
        );

        System.out.println(
            "R1 held by P1: "
            + system.getAllocatedCount("P1", "R1")
        );

        System.out.println("State: " + system);

        // Test 2: R1 is already allocated
        System.out.println("\nTEST 2: Allocate unavailable R1 to P2");

        System.out.println(
            "Allocation successful: "
            + system.allocateResource("P2", "R1")
        );

        // Test 3: P2 waits for R1
        System.out.println("\nTEST 3: P2 waits for R1");

        System.out.println(
            "Waiting request recorded: "
            + system.setWaitingRequest("P2", "R1")
        );

        System.out.println(
            "P2 waiting for: "
            + system.getWaitingRequest("P2")
        );

        // Test 4: Release R1 from P1
        System.out.println("\nTEST 4: Release R1 from P1");

        System.out.println(
            "Release successful: "
            + system.releaseResource("P1", "R1")
        );

        System.out.println(
            "R1 available instances: "
            + r1.getAvailableInstances()
        );

        System.out.println("\nFinal system state:");
        System.out.println(system);
    }
}
