package deadlock;

import deadlock.engine.RecoveryPlan;
import deadlock.engine.RecoveryPlanResult;
import deadlock.engine.ScenarioManager;
import deadlock.engine.SimulationController;

import java.util.List;

public class RecoveryPlanningTest {
    public static void main(String[] args) {
        SimulationController controller = new SimulationController();
        new ScenarioManager(controller).createDeadlockScenario();

        int p1Before = controller.getSystemState().getAllocatedCount("P1", "R1");
        int p2Before = controller.getSystemState().getAllocatedCount("P2", "R2");
        List<RecoveryPlanResult> predictions = controller.evaluateRecoveryPlans();

        if (predictions.isEmpty() || !controller.hasDeadlock()) {
            throw new AssertionError("Expected recovery candidates for the deadlock.");
        }
        if (controller.getSystemState().getAllocatedCount("P1", "R1") != p1Before
                || controller.getSystemState().getAllocatedCount("P2", "R2") != p2Before) {
            throw new AssertionError("Hypothetical recovery mutated live allocations.");
        }

        RecoveryPlanResult single = predictions.stream()
                .filter(result -> result.getPlan().getStrategy()
                        == RecoveryPlan.Strategy.SINGLE_TERMINATION)
                .findFirst().orElseThrow();
        if (!single.isExecutable() || !single.isDeadlockResolved()) {
            throw new AssertionError("Single termination should resolve the scenario.");
        }
        if (single.getUnblockedProcesses() == 0) {
            throw new AssertionError("Prediction did not simulate waiter resumption.");
        }

        double originalCost = single.getCost();
        controller.getRecoveryCostWeights().setTerminationPenalty(200.0);
        double weightedCost = controller.evaluateRecoveryPlans().stream()
                .filter(result -> result.getPlan().getStrategy()
                        == RecoveryPlan.Strategy.SINGLE_TERMINATION)
                .findFirst().orElseThrow().getCost();
        if (weightedCost <= originalCost) {
            throw new AssertionError("Configurable termination weight was ignored.");
        }

        RecoveryPlanResult actual = controller.recoverPlan(single.getPlan());
        if (!actual.isDeadlockResolved() || controller.hasDeadlock()) {
            throw new AssertionError("Selected recovery plan did not resolve deadlock.");
        }
        if (controller.getRecoveryMetrics().getRecoveryCount() != 1) {
            throw new AssertionError("Recovery outcome was not recorded.");
        }
        if (controller.getRecoveryComparisons().size() != 1
                || controller.getRecoveryMetrics().getAverageAbsoluteCostError() < 0.0) {
            throw new AssertionError("Predicted-versus-actual cost was not recorded.");
        }
        System.out.println("Recovery planning test passed.");
    }
}
