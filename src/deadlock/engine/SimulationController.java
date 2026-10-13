package deadlock.engine;

import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimulationController {
    private final SystemState systemState;
    private final WaitForGraph waitForGraph;
    private final LockOrderGraph lockOrderGraph;
    private final PreventionEngine preventionEngine;
    private final EventLogger eventLogger;
    private final RecoveryEngine recoveryEngine;

    public SimulationController() {
        systemState = new SystemState();
        waitForGraph = new WaitForGraph();
        lockOrderGraph = new LockOrderGraph();
        eventLogger = new EventLogger();
        preventionEngine = new PreventionEngine(systemState, waitForGraph,
                lockOrderGraph);
        recoveryEngine = new RecoveryEngine(systemState, waitForGraph,
                eventLogger);
    }

    public void addProcess(Process process) {
        systemState.addProcess(process);
        waitForGraph.addProcess(process.getProcessId());
    }

    public void addResource(Resource resource) {
        systemState.addResource(resource);
        lockOrderGraph.addResource(resource.getResourceId());
    }

    public PreventionEngine.RequestResult requestResource(
            String processId, String resourceId) {
        PreventionEngine.RequestResult result = preventionEngine
                .requestResource(processId, resourceId);
        eventLogger.record(result.getStatus() + " process=" + processId
                + " resource=" + resourceId + " message=" + result.getMessage());
        return result;
    }

    public boolean releaseResource(String processId, String resourceId) {
        int before = systemState.getAllocatedCount(processId, resourceId);
        preventionEngine.releaseResource(processId, resourceId);
        boolean released = before > systemState.getAllocatedCount(
                processId, resourceId);
        if (released) {
            eventLogger.record("RELEASE process=" + processId
                    + " resource=" + resourceId);
        }
        return released;
    }

    public boolean hasDeadlock() {
        return waitForGraph.hasCycle();
    }

    public List<String> detectDeadlock() {
        List<String> cycle = new ArrayList<>(waitForGraph.getCycleNodes());
        if (!cycle.isEmpty()) {
            eventLogger.record("DEADLOCK cycle=" + cycle);
        }
        return Collections.unmodifiableList(cycle);
    }

    public RecoveryEngine.RecoveryResult recoverDeadlock() {
        String victim = recoveryEngine.selectVictim();
        RecoveryEngine.RecoveryResult result = recoveryEngine.recover(victim);
        resumeWaitingProcesses();
        return result;
    }

    public void resumeWaitingProcesses() {
        boolean changed;
        do {
            changed = false;
            for (Process process : systemState.getAllProcesses()) {
                String resourceId = systemState.getWaitingRequest(
                        process.getProcessId());
                if (resourceId == null) {
                    continue;
                }
                Resource resource = systemState.getResource(resourceId);
                if (resource != null && resource.hasAvailableInstance()) {
                    systemState.clearWaitingRequest(process.getProcessId());
                    PreventionEngine.RequestResult result = requestResource(
                            process.getProcessId(), resourceId);
                    changed = result.getStatus()
                            == PreventionEngine.RequestStatus.GRANTED;
                }
            }
        } while (changed);
    }

    public void reset() {
        systemState.clear();
        waitForGraph.clear();
        lockOrderGraph.clear();
        eventLogger.clear();
    }

    public SystemState getSystemState() { return systemState; }
    public WaitForGraph getWaitForGraph() { return waitForGraph; }
    public LockOrderGraph getLockOrderGraph() { return lockOrderGraph; }
    public PreventionEngine getPreventionEngine() { return preventionEngine; }
    public RecoveryEngine getRecoveryEngine() { return recoveryEngine; }
    public EventLogger getEventLogger() { return eventLogger; }
}