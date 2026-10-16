package deadlock;

import deadlock.engine.AdaptiveStrategySelector;
import deadlock.engine.RecoveryAction;
import deadlock.engine.RecoveryContext;
import deadlock.engine.RecoveryPlan;
import deadlock.engine.RecoveryPlanEvaluator;
import deadlock.engine.RecoveryPlanResult;
import deadlock.engine.RecoveryCostWeights;
import deadlock.engine.ScenarioManager;
import deadlock.engine.CheckpointManager;
import deadlock.engine.SimulationController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdaptiveRecoveryTest {
    public static void main(String[] args) {
        SimulationController controller = new SimulationController();
        new ScenarioManager(controller).createDeadlockScenario();
        RecoveryContext context = RecoveryContext.from(controller.getSystemState(),
                controller.getWaitForGraph());

        RecoveryPlan termination = new RecoveryPlan(
                RecoveryPlan.Strategy.SINGLE_TERMINATION,
                Arrays.asList(RecoveryAction.terminate("P1")));
        RecoveryPlan preemption = new RecoveryPlan(
                RecoveryPlan.Strategy.PREEMPTION,
                Arrays.asList(RecoveryAction.preempt("P1", "R1")));
        RecoveryPlanResult terminationResult = new RecoveryPlanResult(
                termination, true, false, 0, 1, 1, 10.0, 0.5, 100.0, "failed");
        RecoveryPlanResult preemptionResult = new RecoveryPlanResult(
                preemption, true, true, 1, 0, 1, 1.0, 0.5, 1.0, "success");

        AdaptiveStrategySelector selector = new AdaptiveStrategySelector();
        selector.observe(context, terminationResult);
        selector.observe(context, preemptionResult);
        if (selector.getKnownContextCount() != 1
                || selector.getAttempts(context, RecoveryPlan.Strategy.PREEMPTION) != 1) {
            throw new AssertionError("Adaptive observations were not recorded.");
        }
        RecoveryPlan learned = selector.select(context,
                Arrays.asList(terminationResult, preemptionResult));
        if (learned.getStrategy() != RecoveryPlan.Strategy.PREEMPTION) {
            throw new AssertionError("Learner did not prefer the successful strategy.");
        }
        if (selector.selectFallback(Arrays.asList(terminationResult, preemptionResult))
                .getStrategy() != RecoveryPlan.Strategy.PREEMPTION) {
            throw new AssertionError("Fallback did not select the lowest-cost plan.");
        }

        Map<String, Integer> victimCounts = new LinkedHashMap<>();
        RecoveryPlanEvaluator evaluator = new RecoveryPlanEvaluator(
                new CheckpointManager(), new RecoveryCostWeights(), victimCounts);
        RecoveryPlan victimPlan = new RecoveryPlan(
                RecoveryPlan.Strategy.SINGLE_TERMINATION,
                Arrays.asList(RecoveryAction.terminate("P1")));
        RecoveryPlanResult first = evaluator.evaluate(victimPlan,
                controller.getSystemState());
        evaluator.execute(victimPlan, controller.getSystemState().copy());
        RecoveryPlanResult repeated = evaluator.evaluate(victimPlan,
                controller.getSystemState());
        if (repeated.getCost() <= first.getCost()) {
            throw new AssertionError("Repeated victimization was not penalized.");
        }
        System.out.println("Adaptive recovery test passed.");
    }
}
