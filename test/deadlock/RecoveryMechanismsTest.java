package deadlock;

import deadlock.engine.RecoveryPlan;
import deadlock.engine.RecoveryPlanResult;
import deadlock.engine.RecoveryAction;
import deadlock.engine.SimulationController;
import deadlock.model.Process;
import deadlock.model.Resource;

public class RecoveryMechanismsTest {
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

        controller.getSystemState().getProcess("P1").setExecutionProgress(25.0);
        controller.saveCheckpoint("P1");
        controller.getSystemState().getProcess("P1").setExecutionProgress(80.0);
        RecoveryPlanResult rollback = controller.evaluateRecoveryPlans().stream()
                .filter(result -> result.getPlan().getStrategy()
                        == RecoveryPlan.Strategy.ROLLBACK)
                .findFirst().orElseThrow();
        if (!rollback.isExecutable() || rollback.getWorkLost() != 55.0) {
            throw new AssertionError("Rollback prediction is incorrect.");
        }

        RecoveryPlanResult preemption = controller.evaluateRecoveryPlans().stream()
                .filter(result -> result.getPlan().getStrategy()
                        == RecoveryPlan.Strategy.PREEMPTION)
                .findFirst().orElseThrow();
        if (!preemption.isExecutable() || !preemption.isDeadlockResolved()) {
            throw new AssertionError("Safe preemption should resolve the cycle.");
        }
        if (controller.getSystemState().getAllocatedCount("P2", "R2") != 1) {
            throw new AssertionError("Planning mutated the live preemptible allocation.");
        }

        controller.getSystemState().getProcess("P2").setExecutionProgress(40.0);
        RecoveryPlan restartPlan = new RecoveryPlan(
            RecoveryPlan.Strategy.PREEMPTION,
            java.util.Collections.singletonList(
                RecoveryAction.preemptAndRestart("P2", "R2")));
        RecoveryPlanResult restart = new deadlock.engine.RecoveryPlanEvaluator(
                controller.getCheckpointManager(),
                controller.getRecoveryCostWeights(),
                new java.util.LinkedHashMap<>())
                .evaluate(restartPlan, controller.getSystemState());
        if (!restart.isExecutable() || restart.getWorkLost() != 40.0
            || controller.getSystemState().getProcess("P2")
                .getExecutionProgress() != 40.0) {
            throw new AssertionError("Restart-after-preemption was not simulated safely.");
        }

        controller.recoverPlan(preemption.getPlan());
        if (controller.hasDeadlock()) {
            throw new AssertionError("Committed preemption left a deadlock.");
        }
        System.out.println("Recovery mechanisms test passed.");
    }
}
