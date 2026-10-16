package deadlock.engine;

import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

import java.util.Objects;

/** Immutable context key describing the conditions surrounding a recovery. */
public final class RecoveryContext {
    private final int cycleSize;
    private final int processCount;
    private final int resourceCount;
    private final int waitingCount;
    private final int preemptibleResourceCount;
    private final int utilizationPercent;

    private RecoveryContext(int cycleSize, int processCount, int resourceCount,
            int waitingCount, int preemptibleResourceCount,
            int utilizationPercent) {
        this.cycleSize = cycleSize;
        this.processCount = processCount;
        this.resourceCount = resourceCount;
        this.waitingCount = waitingCount;
        this.preemptibleResourceCount = preemptibleResourceCount;
        this.utilizationPercent = utilizationPercent;
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
        return new RecoveryContext(graph.getCycleNodes().size(),
                state.getAllProcesses().size(), state.getAllResources().size(),
                waiting, preemptible, utilization);
    }

    public int getCycleSize() { return cycleSize; }
    public int getProcessCount() { return processCount; }
    public int getResourceCount() { return resourceCount; }
    public int getWaitingCount() { return waitingCount; }
    public int getPreemptibleResourceCount() { return preemptibleResourceCount; }
    public int getUtilizationPercent() { return utilizationPercent; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RecoveryContext)) return false;
        RecoveryContext context = (RecoveryContext) other;
        return cycleSize == context.cycleSize
                && processCount == context.processCount
                && resourceCount == context.resourceCount
                && waitingCount == context.waitingCount
                && preemptibleResourceCount == context.preemptibleResourceCount
                && utilizationPercent == context.utilizationPercent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cycleSize, processCount, resourceCount, waitingCount,
                preemptibleResourceCount, utilizationPercent);
    }

    @Override
    public String toString() {
        return "RecoveryContext{" + "cycleSize=" + cycleSize
                + ", processCount=" + processCount
                + ", resourceCount=" + resourceCount
                + ", waitingCount=" + waitingCount
                + ", preemptibleResourceCount=" + preemptibleResourceCount
                + ", utilizationPercent=" + utilizationPercent + '}';
    }
}
