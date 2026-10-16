package deadlock;

import deadlock.engine.RecoveryAction;
import deadlock.engine.RecoveryPlan;
import deadlock.engine.RecoveryPlanResult;
import deadlock.engine.ScenarioManager;
import deadlock.engine.SimulationController;
import deadlock.model.Process;
import deadlock.model.Resource;

import java.util.LinkedHashMap;
import java.util.Map;

public class RecoveryCorrectnessTest {
    public static void main(String[] args) {
        testResourceAndProcessInvariants();
        testRollbackIndependently();
        testPreemptionIndependently();
        testHypotheticalRecoveryIsolation();
        testCompleteDeadlockCycle();
        System.out.println("Recovery correctness test passed.");
    }

    private static void testResourceAndProcessInvariants() {
        SimulationController controller = new SimulationController();
        controller.addProcess(new Process("P1", 1));
        controller.addProcess(new Process("P2", 2));
        controller.addResource(new Resource("R1", 1));
        controller.requestResource("P1", "R1");
        controller.requestResource("P2", "R1");

        assertAccounting(controller);
        assertState(controller, "P1", Process.State.RUNNING);
        assertState(controller, "P2", Process.State.WAITING);

        if (!controller.releaseResource("P1", "R1")) {
            throw new AssertionError("Expected release to succeed.");
        }
        assertAccounting(controller);
        if (controller.getSystemState().getAllocatedCount("P2", "R1") != 1
                || controller.getSystemState().getWaitingRequest("P2") != null) {
            throw new AssertionError("Waiting process was not resumed correctly.");
        }
    }

    private static void testRollbackIndependently() {
        SimulationController controller = new SimulationController();
        controller.addProcess(new Process("P1", 1));
        controller.addResource(new Resource("R1", 1));
        controller.requestResource("P1", "R1");
        controller.getSystemState().getProcess("P1").setExecutionProgress(20.0);
        controller.saveCheckpoint("P1");
        controller.getSystemState().getProcess("P1").setExecutionProgress(70.0);

        RecoveryPlan plan = new RecoveryPlan(RecoveryPlan.Strategy.ROLLBACK,
                java.util.Collections.singletonList(RecoveryAction.rollback("P1")));
        RecoveryPlanResult result = controller.recoverPlan(plan);
        if (result.getWorkLost() != 50.0
                || controller.getSystemState().getProcess("P1")
                        .getExecutionProgress() != 20.0
                || controller.getSystemState().getAllocatedCount("P1", "R1") != 1) {
            throw new AssertionError("Rollback did not restore the checkpoint state.");
        }
        assertAccounting(controller);
    }

    private static void testPreemptionIndependently() {
        SimulationController controller = new SimulationController();
        controller.addProcess(new Process("P1", 1));
        controller.addResource(new Resource("R1", 1, true));
        controller.requestResource("P1", "R1");
        controller.getSystemState().getProcess("P1").setExecutionProgress(45.0);

        RecoveryPlan plan = new RecoveryPlan(RecoveryPlan.Strategy.PREEMPTION,
                java.util.Collections.singletonList(
                        RecoveryAction.preemptAndRestart("P1", "R1")));
        RecoveryPlanResult result = controller.recoverPlan(plan);
        if (result.getWorkLost() != 45.0
                || controller.getSystemState().getResource("R1")
                        .getAvailableInstances() != 1
                || controller.getSystemState().getProcess("P1")
                        .getExecutionProgress() != 0.0
                || controller.getSystemState().getProcess("P1").getState()
                        != Process.State.READY) {
            throw new AssertionError("Preemption restart did not restore process state.");
        }
        assertAccounting(controller);
    }

    private static void testHypotheticalRecoveryIsolation() {
        SimulationController controller = new SimulationController();
        new ScenarioManager(controller).createDeadlockScenario();
        Map<String, Map<String, Integer>> allocations = controller.getSystemState().getAllocations();
        Map<String, Integer> available = availableResources(controller);
        Map<String, Process.State> states = processStates(controller);
        Map<String, String> waiting = waitingRequests(controller);

        for (RecoveryPlanResult result : controller.evaluateRecoveryPlans()) {
            if (!result.isExecutable() && result.getMessage() == null) {
                throw new AssertionError("Invalid plan had no rejection reason.");
            }
        }
        if (!allocations.equals(controller.getSystemState().getAllocations())
                || !available.equals(availableResources(controller))
                || !states.equals(processStates(controller))
                || !waiting.equals(waitingRequests(controller))) {
            throw new AssertionError("Hypothetical recovery mutated live state.");
        }
    }

    private static void testCompleteDeadlockCycle() {
        SimulationController controller = new SimulationController();
        new ScenarioManager(controller).createDeadlockScenario();
        if (!controller.hasDeadlock() || controller.detectDeadlock().size() != 2) {
            throw new AssertionError("Deadlock detection missed the complete cycle.");
        }
        controller.recoverOneAtATime();
        if (controller.hasDeadlock()) {
            throw new AssertionError("Complete recovery cycle left a deadlock.");
        }
        assertAccounting(controller);
        for (Process process : controller.getSystemState().getAllProcesses()) {
            if (controller.getSystemState().getWaitingRequest(process.getProcessId()) != null) {
                throw new AssertionError("Recovery left a stale waiting request.");
            }
        }
    }

    private static void assertAccounting(SimulationController controller) {
        for (Resource resource : controller.getSystemState().getAllResources()) {
            int allocated = 0;
            for (Process process : controller.getSystemState().getAllProcesses()) {
                allocated += controller.getSystemState().getAllocatedCount(
                        process.getProcessId(), resource.getResourceId());
            }
            if (allocated + resource.getAvailableInstances()
                    != resource.getTotalInstances()) {
                throw new AssertionError("Resource accounting invariant failed.");
            }
        }
    }

    private static void assertState(SimulationController controller,
            String processId, Process.State expected) {
        if (controller.getSystemState().getProcess(processId).getState() != expected) {
            throw new AssertionError("Unexpected state for " + processId);
        }
    }

    private static Map<String, Integer> availableResources(
            SimulationController controller) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Resource resource : controller.getSystemState().getAllResources()) {
            result.put(resource.getResourceId(), resource.getAvailableInstances());
        }
        return result;
    }

    private static Map<String, Process.State> processStates(
            SimulationController controller) {
        Map<String, Process.State> result = new LinkedHashMap<>();
        for (Process process : controller.getSystemState().getAllProcesses()) {
            result.put(process.getProcessId(), process.getState());
        }
        return result;
    }

    private static Map<String, String> waitingRequests(
            SimulationController controller) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Process process : controller.getSystemState().getAllProcesses()) {
            result.put(process.getProcessId(), controller.getSystemState()
                    .getWaitingRequest(process.getProcessId()));
        }
        return result;
    }
}
