package deadlock.engine;

import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

import java.util.Objects;

/** Immutable context key describing the conditions surrounding a recovery. */
public final class RecoveryContext {
    public enum UtilizationLevel { LOW, MEDIUM, HIGH }

    private final int cycleSize;
    private final int processCount;
    private final int resourceCount;
    private final int waitingCount;
    private final int preemptibleResourceCount;
    private final int utilizationPercent;
    private final UtilizationLevel utilizationLevel;

    private RecoveryContext(int cycleSize, int processCount, int resourceCount,
            int waitingCount, int preemptibleResourceCount,
            int utilizationPercent, UtilizationLevel utilizationLevel) {
        this.cycleSize = cycleSize;
        this.processCount = processCount;
        this.resourceCount = resourceCount;
        this.waitingCount = waitingCount;
        this.preemptibleResourceCount = preemptibleResourceCount;
        this.utilizationPercent = utilizationPercent;
        this.utilizationLevel = utilizationLevel;
    }

    public static RecoveryContext from(SystemState state, WaitForGraph graph) {
        int waiting = 0;
        for (Process process : state.getAllProcesses()) {
            if (state.getWaitingRequest(process.getProcessId()) != null) waiting++;
        }
        int preemptible = 0;
        int totalInstances = 0;
        int availableInstances = 0;
        for (Resource resource : state.getAllResources()) {
            if (resource.isPreemptible()) preemptible++;
            totalInstances += resource.getTotalInstances();
            availableInstances += resource.getAvailableInstances();
        }
        int utilization = totalInstances == 0 ? 0
                : (100 * (totalInstances - availableInstances) / totalInstances);
        UtilizationLevel level = utilization < 34 ? UtilizationLevel.LOW
            : utilization < 67 ? UtilizationLevel.MEDIUM
            : UtilizationLevel.HIGH;
        return new RecoveryContext(graph.getCycleNodes().size(),
                state.getAllProcesses().size(), state.getAllResources().size(),
            waiting, preemptible, utilization, level);
    }

    public int getCycleSize() { return cycleSize; }
    public int getProcessCount() { return processCount; }
    public int getResourceCount() { return resourceCount; }
    public int getWaitingCount() { return waitingCount; }
    public int getPreemptibleResourceCount() { return preemptibleResourceCount; }
    public int getUtilizationPercent() { return utilizationPercent; }
    public UtilizationLevel getUtilizationLevel() { return utilizationLevel; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RecoveryContext)) return false;
        RecoveryContext context = (RecoveryContext) other;
        return cycleSize == context.cycleSize
                && processCount == context.processCount
                && resourceCount == context.resourceCount
                && waitingCount == context.waitingCount
                && preemptibleResourceCount == context.preemptibleResourceCount
                && utilizationLevel == context.utilizationLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cycleSize, processCount, resourceCount, waitingCount,
                preemptibleResourceCount, utilizationLevel);
    }

    @Override
    public String toString() {
        return "RecoveryContext{" + "cycleSize=" + cycleSize
                + ", processCount=" + processCount
                + ", resourceCount=" + resourceCount
                + ", waitingCount=" + waitingCount
                + ", preemptibleResourceCount=" + preemptibleResourceCount
                + ", utilizationLevel=" + utilizationLevel + '}';
    }
}
