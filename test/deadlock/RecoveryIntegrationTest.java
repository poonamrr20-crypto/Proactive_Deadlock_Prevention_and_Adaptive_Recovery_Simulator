package deadlock;

import deadlock.engine.RecoveryEngine;
import deadlock.engine.ScenarioManager;
import deadlock.engine.SimulationController;
import deadlock.model.Process;

public class RecoveryIntegrationTest {
    public static void main(String[] args) {
        SimulationController controller = new SimulationController();
        ScenarioManager scenarios = new ScenarioManager(controller);
        scenarios.createDeadlockScenario();

        if (!controller.hasDeadlock()
                || controller.detectDeadlock().size() != 2) {
            throw new AssertionError("Expected a two-process deadlock.");
        }

        RecoveryEngine.RecoveryResult result = controller.recoverDeadlock();
        if (!"P1".equals(result.getVictimId())) {
            throw new AssertionError("Expected the lower-cost victim P1.");
        }
        if (controller.getSystemState().getProcess("P1").getState()
                != Process.State.TERMINATED) {
            throw new AssertionError("Victim was not terminated.");
        }
        if (controller.getSystemState().getAllocatedCount("P2", "R1") != 1) {
            throw new AssertionError("P2 did not resume after recovery.");
        }
        if (controller.getSystemState().getWaitingRequest("P2") != null) {
            throw new AssertionError("P2 still has a stale waiting request.");
        }

        scenarios.reset();
        if (!controller.getSystemState().getAllProcesses().isEmpty()
                || !controller.getWaitForGraph().getProcesses().isEmpty()
                || !controller.getEventLogger().getEvents().isEmpty()) {
            throw new AssertionError("Reset retained stale simulation state.");
        }

        System.out.println("Recovery integration test passed.");
    }
}