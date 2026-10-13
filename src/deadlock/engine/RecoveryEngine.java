package deadlock.engine;

import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.SystemState;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public class RecoveryEngine {
    private final SystemState systemState;
    private final WaitForGraph waitForGraph;
    private final EventLogger eventLogger;

    public RecoveryEngine(SystemState systemState, WaitForGraph waitForGraph,
            EventLogger eventLogger) {
        if (systemState == null || waitForGraph == null || eventLogger == null) {
            throw new IllegalArgumentException("Recovery dependencies cannot be null.");
        }
        this.systemState = systemState;
        this.waitForGraph = waitForGraph;
        this.eventLogger = eventLogger;
    }

    /** Lower cost is preferred: 4*priority + 3*lost work + 2*held - 5*benefit. */
    public double calculateRecoveryCost(String processId) {
        Process process = systemState.getProcess(processId);
        if (process == null) {
            throw new IllegalArgumentException("Unknown process: " + processId);
        }
        double lostWork = 100.0 - process.getExecutionProgress();
        double benefit = countUnblockedProcesses(processId) * 10.0;
        return 4.0 * process.getPriority()
                + 3.0 * lostWork
                + 2.0 * systemState.getTotalAllocatedCount(processId)
                - 5.0 * benefit;
    }

    public String selectVictim(Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Recovery candidates cannot be empty.");
        }
        return candidates.stream()
                .filter(id -> systemState.getProcess(id) != null)
                .min(Comparator.comparingDouble(this::calculateRecoveryCost)
                        .thenComparing(id -> id))
                .orElseThrow(() -> new IllegalArgumentException("No valid recovery candidates."));
    }

    public String selectVictim() {
        Set<String> candidates = new LinkedHashSet<>(waitForGraph.getCycleNodes());
        if (candidates.isEmpty()) {
            candidates.addAll(waitForGraph.getProcesses());
        }
        return selectVictim(candidates);
    }

    public RecoveryResult recover(String processId) {
        Process process = systemState.getProcess(processId);
        if (process == null || process.getState() == Process.State.TERMINATED) {
            throw new IllegalArgumentException("Invalid recovery victim: " + processId);
        }
        double cost = calculateRecoveryCost(processId);
        int released = systemState.releaseAllResources(processId);
        process.setState(Process.State.TERMINATED);
        eventLogger.record("RECOVERY victim=" + processId + " cost=" + cost
                + " released=" + released);
        return new RecoveryResult(processId, cost, released);
    }

    private int countUnblockedProcesses(String victimId) {
        int benefit = 0;
        for (String processId : waitForGraph.getProcesses()) {
            if (waitForGraph.getNeighbors(processId).contains(victimId)) {
                benefit++;
            }
        }
        return benefit;
    }

    public static final class RecoveryResult {
        private final String victimId;
        private final double cost;
        private final int releasedInstances;

        public RecoveryResult(String victimId, double cost, int releasedInstances) {
            this.victimId = victimId;
            this.cost = cost;
            this.releasedInstances = releasedInstances;
        }

        public String getVictimId() { return victimId; }
        public double getCost() { return cost; }
        public int getReleasedInstances() { return releasedInstances; }
    }
}