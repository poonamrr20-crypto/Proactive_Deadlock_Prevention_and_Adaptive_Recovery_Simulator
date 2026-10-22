package deadlock.engine;

import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Executes recovery actions against a supplied state, normally a copy. */
public final class RecoveryPlanEvaluator {
    private final CheckpointManager checkpoints;
    private final RecoveryCostWeights weights;
    private final Map<String, Integer> victimCounts;

    public RecoveryPlanEvaluator(CheckpointManager checkpoints,
            RecoveryCostWeights weights, Map<String, Integer> victimCounts) {
        this.checkpoints = checkpoints;
        this.weights = weights;
        this.victimCounts = victimCounts;
    }

    public RecoveryPlanResult evaluate(RecoveryPlan plan, SystemState original) {
        try {
            return execute(plan, original.copy(), false);
        } catch (RuntimeException failure) {
            return invalid(plan, failure.getMessage() == null
                    ? "Candidate simulation failed." : failure.getMessage());
        }
    }

    public RecoveryPlanResult execute(RecoveryPlan plan, SystemState state) {
        return execute(plan, state, true);
    }

    private RecoveryPlanResult execute(RecoveryPlan plan, SystemState state,
            boolean actual) {
        WaitForGraph graph = buildWaitForGraph(state);
        Set<String> cycleProcesses = graph.getCycleNodes();
        Set<String> initiallyBlocked = blockedProcesses(state);
        int terminated = 0;
        int rollbackActions = 0;
        int released = 0;
        double lostWork = 0.0;
        double priorityImpact = 0.0;
        double affectedWork = 0.0;

        for (RecoveryAction action : plan.getActions()) {
            Process process = state.getProcess(action.getProcessId());
            if (process == null || process.getState() == Process.State.TERMINATED) {
                return invalid(plan, "Unknown or terminated process.");
            }
            switch (action.getType()) {
                case TERMINATE:
                lostWork += process.getCompletedWork();
                affectedWork += process.getTotalWork();
                priorityImpact += process.getCompletedWork()
                    * priorityMultiplier(process);
                    released += state.releaseAllResources(action.getProcessId());
                    process.setState(Process.State.TERMINATED);
                process.incrementRecoveryVictimCount();
                    terminated++;
                    break;
                case ROLLBACK:
                double workBeforeRollback = process.getCompletedWork();
                    double checkpointProgress = checkpoints.getProgress(
                            action.getProcessId(), action.getCheckpointId());
                    if (!checkpoints.restore(action.getProcessId(),
                            action.getCheckpointId(), state)) {
                        return invalid(plan, "Checkpoint cannot be restored safely.");
                    }
                lostWork += Math.max(0.0, workBeforeRollback
                    - process.getTotalWork() * checkpointProgress / 100.0);
                affectedWork += process.getTotalWork();
                priorityImpact += Math.max(0.0, workBeforeRollback
                    - process.getTotalWork() * checkpointProgress / 100.0)
                    * priorityMultiplier(process);
                rollbackActions++;
                    process.incrementRecoveryVictimCount();
                    break;
                case PREEMPT:
                    if (!canPreemptSafely(state, action.getProcessId(),
                            action.getResourceId())) {
                        return invalid(plan, "Resource cannot be safely preempted.");
                    }
                    if (action.isRestartAfterPreemption()
                            || weights.getPreemptionMode()
                                == RecoveryCostWeights.PreemptionMode.RESTART) {
                        double workBeforeRestart = process.getCompletedWork();
                        lostWork += workBeforeRestart;
                        affectedWork += process.getTotalWork();
                        priorityImpact += workBeforeRestart * priorityMultiplier(process);
                        process.setExecutionProgress(0.0);
                    }
                    if (!state.releaseResource(action.getProcessId(),
                            action.getResourceId())) {
                        return invalid(plan, "Resource preemption failed.");
                    }
                    released++;
                        process.setState(state.getWaitingRequest(process.getProcessId()) == null
                            ? Process.State.READY : Process.State.WAITING);
                    process.incrementRecoveryVictimCount();
                    break;
                default:
                    return invalid(plan, "Unsupported recovery action.");
            }
        }

        resumeWaitingProcesses(state);
        graph = buildWaitForGraph(state);
        Set<String> remainingBlocked = blockedProcesses(state);
        int unblocked = initiallyBlocked.size() - remainingBlocked.size();
        boolean resolved = !graph.hasCycle();
        boolean success = resolved
            && (unblocked > 0 || terminated >= cycleProcesses.size());
        double totalResources = 0.0;
        for (Resource resource : state.getAllResources()) {
            totalResources += resource.getTotalInstances();
        }
        double lostWorkCost = clamp(affectedWork == 0.0 ? 0.0 : lostWork / affectedWork);
        double priorityCost = clamp(affectedWork == 0.0 ? 0.0
            : priorityImpact / (affectedWork * weights.getMaximumPriorityMultiplier()));
        double terminationCost = clamp(cycleProcesses.isEmpty() ? 0.0
            : (double) terminated / cycleProcesses.size());
        double rollbackCost = clamp((double) rollbackActions
            / Math.max(1, cycleProcesses.size()));
        double starvationCost = clamp(starvationCost(plan));
        double resourceBenefit = clamp(totalResources == 0.0 ? 0.0
            : released / totalResources);
        double cost = weights.getLostWork() * lostWorkCost
            + weights.getPriority() * priorityCost
            + weights.getTerminationPenalty() * terminationCost
            + weights.getRollbackOverhead() * rollbackCost
            + weights.getStarvationRisk() * starvationCost
            + (success ? 0.0 : weights.getUnresolvedDeadlock())
            - weights.getResourceBenefit() * resourceBenefit;
        cost = Math.max(0.0, cost);
        if (actual) {
            for (RecoveryAction action : plan.getActions()) {
                if (action.getType() == RecoveryAction.Type.TERMINATE
                    || action.getType() == RecoveryAction.Type.PREEMPT
                    || action.getType() == RecoveryAction.Type.ROLLBACK) {
                    victimCounts.merge(action.getProcessId(), 1, Integer::sum);
                }
            }
        }
        return new RecoveryPlanResult(plan, true, success, unblocked,
            terminated, released, lostWork, resourceUtilization(state), cost,
            success ? "Plan executed successfully." : "Plan left a deadlock.");
    }

    public boolean canPreemptSafely(SystemState state, String processId,
            String resourceId) {
        Process process = state.getProcess(processId);
        Resource resource = state.getResource(resourceId);
        return process != null
                && process.getState() != Process.State.TERMINATED
                && resource != null
                && resource.isPreemptible()
                && state.getAllocatedCount(processId, resourceId) > 0;
    }

    private double starvationCost(RecoveryPlan plan) {
        double cost = 0.0;
        for (RecoveryAction action : plan.getActions()) {
            cost = Math.max(cost, (double) victimCounts.getOrDefault(
                    action.getProcessId(), 0) / weights.getStarvationThreshold());
        }
        return cost;
    }

    private double priorityMultiplier(Process process) {
        return weights.getPriorityMultiplier(process.getPriority());
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private RecoveryPlanResult invalid(RecoveryPlan plan, String message) {
        return new RecoveryPlanResult(plan, false, false, 0, 0, 0, 0.0,
                0.0, Double.POSITIVE_INFINITY, message);
    }

    private static double resourceUtilization(SystemState state) {
        int total = 0;
        int available = 0;
        for (Resource resource : state.getAllResources()) {
            total += resource.getTotalInstances();
            available += resource.getAvailableInstances();
        }
        return total == 0 ? 0.0 : (double) (total - available) / total;
    }

    private static void resumeWaitingProcesses(SystemState state) {
        boolean changed;
        do {
            changed = false;
            for (Process process : state.getAllProcesses()) {
                String resourceId = state.getWaitingRequest(process.getProcessId());
                Resource resource = resourceId == null
                        ? null : state.getResource(resourceId);
                if (resource != null && resource.hasAvailableInstance()) {
                    state.clearWaitingRequest(process.getProcessId());
                    changed = state.allocateResource(process.getProcessId(), resourceId)
                            || changed;
                }
            }
        } while (changed);
    }

    private static Set<String> blockedProcesses(SystemState state) {
        Set<String> blocked = new LinkedHashSet<>();
        for (Process process : state.getAllProcesses()) {
            if (state.getWaitingRequest(process.getProcessId()) != null) {
                blocked.add(process.getProcessId());
            }
        }
        return blocked;
    }

    private static WaitForGraph buildWaitForGraph(SystemState state) {
        WaitForGraph graph = new WaitForGraph();
        for (Process process : state.getAllProcesses()) {
            graph.addProcess(process.getProcessId());
        }
        for (Process waiting : state.getAllProcesses()) {
            String requested = state.getWaitingRequest(waiting.getProcessId());
            if (requested == null) {
                continue;
            }
            Resource requestedResource = state.getResource(requested);
            if (requestedResource == null || requestedResource.hasAvailableInstance()) {
                continue;
            }
            for (Process holder : state.getAllProcesses()) {
                if (!waiting.getProcessId().equals(holder.getProcessId())
                        && state.getAllocatedCount(holder.getProcessId(), requested) > 0) {
                    graph.addEdge(waiting.getProcessId(), holder.getProcessId());
                }
            }
        }
        return graph;
    }
}
