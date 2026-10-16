package deadlock;

import deadlock.engine.RecoveryPlan;
import deadlock.engine.RecoveryPlanResult;
import deadlock.engine.SimulationController;
import deadlock.model.Process;
import deadlock.model.Resource;

public class RecoveryPlanCombinationTest {
    public static void main(String[] args) {
        SimulationController controller = new SimulationController();
        controller.addProcess(new Process("P1", 1));
        controller.addProcess(new Process("P2", 2));
        controller.addResource(new Resource("R1", 1, false));
        controller.addResource(new Resource("R2", 1, true));
        controller.requestResource("P1", "R1");
        controller.requestResource("P2", "R2");
        controller.getSystemState().setWaitingRequest("P1", "R2");
        controller.getSystemState().setWaitingRequest("P2", "R1");
        controller.getWaitForGraph().clear();
        controller.getWaitForGraph().addEdge("P1", "P2");
        controller.getWaitForGraph().addEdge("P2", "P1");

        boolean foundCombined = false;
        boolean foundResolvedCombined = false;
        for (RecoveryPlanResult result : controller.evaluateRecoveryPlans()) {
            if (result.getPlan().getStrategy() == RecoveryPlan.Strategy.COMBINED) {
                foundCombined = true;
                foundResolvedCombined |= result.isExecutable()
                        && result.isDeadlockResolved();
            }
        }
        if (!foundCombined || !foundResolvedCombined) {
            throw new AssertionError("Expected an executable mixed recovery plan.");
        }
        if (controller.getSystemState().getAllocatedCount("P2", "R2") != 1) {
            throw new AssertionError("Plan evaluation mutated live state.");
        }

        RecoveryPlan invalid = new RecoveryPlan(RecoveryPlan.Strategy.COMBINED,
                java.util.Collections.singletonList(
                        deadlock.engine.RecoveryAction.preempt("P1", "R1")));
        boolean rejected = false;
        try {
            controller.recoverPlan(invalid);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        if (!rejected || controller.getSystemState().getAllocatedCount("P1", "R1") != 1) {
            throw new AssertionError("Unsafe preemption plan was not rejected safely.");
        }
        System.out.println("Recovery plan combination test passed.");
    }
}
