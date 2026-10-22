package deadlock;

import deadlock.engine.RecoveryPlanResult;
import deadlock.engine.SimulationController;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;

public class SpecificationCoverageTest {
    public static void main(String[] args) {
        testCycleMembers();
        testMultiInstanceRecoveryAndIsolation();
        testTerminatedProcessFinality();
        testCostConfiguration();
        System.out.println("Specification coverage test passed.");
    }

    private static void testCycleMembers() {
        WaitForGraph graph = new WaitForGraph();
        graph.addEdge("P1", "P2");
        graph.addEdge("P2", "P3");
        graph.addEdge("P3", "P2");
        if (!graph.getCycleNodes().toString().equals("[P2, P3]")) {
            throw new AssertionError("Cycle detection returned non-cycle nodes.");
        }
    }

    private static void testMultiInstanceRecoveryAndIsolation() {
        SimulationController controller = new SimulationController();
        controller.addProcess(new Process("P1", 1));
        controller.addProcess(new Process("P2", 2));
        controller.addResource(new Resource("R1", 2));
        controller.addResource(new Resource("R2", 2));
        controller.getSystemState().allocateResource("P1", "R1");
        controller.getSystemState().allocateResource("P1", "R1");
        controller.getSystemState().allocateResource("P2", "R2");
        controller.getSystemState().allocateResource("P2", "R2");
        controller.getSystemState().setWaitingRequest("P1", "R2");
        controller.getSystemState().setWaitingRequest("P2", "R1");
        controller.getPreventionEngine().refreshWaitForGraph();

        if (!controller.hasDeadlock() || controller.detectDeadlock().size() != 2) {
            throw new AssertionError("Multi-instance cycle was not detected.");
        }
        int r1Before = controller.getSystemState().getResource("R1")
                .getAvailableInstances();
        for (RecoveryPlanResult result : controller.evaluateRecoveryPlans()) {
            if (result.isExecutable() && result.getPlan().getActions().isEmpty()) {
                throw new AssertionError("Generated an empty recovery plan.");
            }
        }
        if (controller.getSystemState().getResource("R1")
                .getAvailableInstances() != r1Before) {
            throw new AssertionError("Plan evaluation mutated live state.");
        }
        RecoveryPlanResult result = controller.recoverWithStrategy(
                deadlock.engine.RecoveryPlan.Strategy.TERMINATE_ALL);
        if (!result.isDeadlockResolved() || result.getReleasedInstances() != 4) {
            throw new AssertionError("Multi-instance termination did not recover the cycle.");
        }
    }

    private static void testTerminatedProcessFinality() {
        Process process = new Process("P1", 1);
        process.setState(Process.State.TERMINATED);
        try {
            process.setState(Process.State.READY);
            throw new AssertionError("Terminated process was revived.");
        } catch (IllegalStateException expected) {
            // Expected lifecycle rejection.
        }

        SimulationController controller = new SimulationController();
        controller.addProcess(new Process("P2", 1));
        controller.addResource(new Resource("R1", 1));
        controller.getSystemState().allocateResource("P2", "R1");
        controller.getSystemState().getProcess("P2").setState(Process.State.TERMINATED);
        if (controller.getSystemState().getResource("R1").getAvailableInstances() != 1
                || controller.getSystemState().getTotalAllocatedCount("P2") != 0) {
            throw new AssertionError("Direct termination retained resources.");
        }
    }

    private static void testCostConfiguration() {
        SimulationController controller = new SimulationController();
        if (controller.getRecoveryCostWeights().getPriorityMultiplier(1) != 3.0
                || controller.getRecoveryCostWeights().getPriorityMultiplier(4) != 1.0
                || controller.getRecoveryCostWeights().getCombinationLimit() != 4096) {
            throw new AssertionError("Default cost configuration is incorrect.");
        }
    }
}
