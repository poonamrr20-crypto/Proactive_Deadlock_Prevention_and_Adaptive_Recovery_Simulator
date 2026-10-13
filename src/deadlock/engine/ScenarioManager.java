package deadlock.engine;

import deadlock.model.Process;
import deadlock.model.Resource;

public class ScenarioManager {
    private final SimulationController controller;

    public ScenarioManager(SimulationController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("Controller cannot be null.");
        }
        this.controller = controller;
    }

    public void reset() {
        controller.reset();
    }

    /** Creates P1->R1 and P2->R2, then makes both wait for the other. */
    public SimulationController createDeadlockScenario() {
        reset();
        controller.addProcess(new Process("P1", 1));
        controller.addProcess(new Process("P2", 2));
        controller.addResource(new Resource("R1", 1));
        controller.addResource(new Resource("R2", 1));
        controller.requestResource("P1", "R1");
        controller.requestResource("P2", "R2");
        controller.getSystemState().setWaitingRequest("P1", "R2");
        controller.getSystemState().setWaitingRequest("P2", "R1");
        controller.getLockOrderGraph().clear();
        controller.getWaitForGraph().clear();
        controller.getWaitForGraph().addProcess("P1");
        controller.getWaitForGraph().addProcess("P2");
        controller.getWaitForGraph().addEdge("P1", "P2");
        controller.getWaitForGraph().addEdge("P2", "P1");
        return controller;
    }
}