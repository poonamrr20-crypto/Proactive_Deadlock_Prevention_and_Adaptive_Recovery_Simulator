package deadlock.engine;

import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class SimulationController {
    private final SystemState systemState;
    private final WaitForGraph waitForGraph;
    private final LockOrderGraph lockOrderGraph;
    private final PreventionEngine preventionEngine;
    private final EventLogger eventLogger;
    private final RecoveryEngine recoveryEngine;
    private final CheckpointManager checkpointManager;
    private final RecoveryCostWeights recoveryCostWeights;
    private final Map<String, Integer> victimCounts;
    private final RecoveryPlanEvaluator planEvaluator;
    private final AdaptiveStrategySelector adaptiveSelector;
    private final RecoveryMetrics recoveryMetrics;
    private final List<RecoveryCostComparison> recoveryComparisons;
    private long deadlockSequence;

    public SimulationController() {
        systemState = new SystemState();
        waitForGraph = new WaitForGraph();
        lockOrderGraph = new LockOrderGraph();
        eventLogger = new EventLogger();
        recoveryCostWeights = new RecoveryCostWeights();
        preventionEngine = new PreventionEngine(systemState, waitForGraph,
                lockOrderGraph);
        recoveryEngine = new RecoveryEngine(systemState, waitForGraph,
            eventLogger, recoveryCostWeights);
        checkpointManager = new CheckpointManager();
        victimCounts = new LinkedHashMap<>();
        planEvaluator = new RecoveryPlanEvaluator(checkpointManager,
            recoveryCostWeights, victimCounts);
        adaptiveSelector = new AdaptiveStrategySelector();
        recoveryMetrics = new RecoveryMetrics();
        recoveryComparisons = new ArrayList<>();
        deadlockSequence = 0L;
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
        preventionEngine.refreshWaitForGraph();
        return waitForGraph.hasCycle();
    }

    public List<String> detectDeadlock() {
        preventionEngine.refreshWaitForGraph();
        List<String> cycle = new ArrayList<>(waitForGraph.getCycleNodes());
        if (!cycle.isEmpty()) {
            deadlockSequence++;
            eventLogger.record("DEADLOCK id=" + deadlockSequence
                    + " cycle=" + cycle);
        }
        return Collections.unmodifiableList(cycle);
    }

    public RecoveryEngine.RecoveryResult recoverDeadlock() {
        if (!hasDeadlock()) {
            throw new IllegalStateException("No deadlock detected.");
        }
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

    public void saveCheckpoint(String processId) {
        saveCheckpoint(processId, "default");
    }

    public void saveCheckpoint(String processId, String checkpointId) {
        checkpointManager.save(processId, checkpointId, systemState);
    }

    /** Generates executable candidates without changing the live state. */
    public List<RecoveryPlan> generateRecoveryPlans() {
        preventionEngine.refreshWaitForGraph();
        List<String> candidates = new ArrayList<>(waitForGraph.getCycleNodes());
        if (candidates.isEmpty()) return Collections.emptyList();
        List<RecoveryPlan> plans = new ArrayList<>();
        List<List<RecoveryAction>> actionsByProcess = new ArrayList<>();
        for (String processId : candidates) {
            List<RecoveryAction> processActions = new ArrayList<>();
            RecoveryAction termination = RecoveryAction.terminate(processId);
            processActions.add(termination);
            plans.add(new RecoveryPlan(RecoveryPlan.Strategy.SINGLE_TERMINATION,
                    Collections.singletonList(termination)));
            for (String checkpointId : checkpointManager.getCheckpointIds(processId)) {
                RecoveryAction rollback = RecoveryAction.rollback(processId, checkpointId);
                processActions.add(rollback);
                plans.add(new RecoveryPlan(RecoveryPlan.Strategy.ROLLBACK,
                        Collections.singletonList(rollback)));
            }
            for (Map.Entry<String, Integer> entry
                    : systemState.getHeldResources(processId).entrySet()) {
                Resource resource = systemState.getResource(entry.getKey());
                if (resource != null && resource.isPreemptible()) {
                    RecoveryAction preemption = RecoveryAction.preempt(
                            processId, entry.getKey());
                    processActions.add(preemption);
                    plans.add(new RecoveryPlan(RecoveryPlan.Strategy.PREEMPTION,
                            Collections.singletonList(preemption)));
                        RecoveryAction restart = RecoveryAction.preemptAndRestart(
                            processId, entry.getKey());
                        processActions.add(restart);
                        plans.add(new RecoveryPlan(RecoveryPlan.Strategy.PREEMPTION,
                            Collections.singletonList(restart)));
                }
            }
            actionsByProcess.add(processActions);
        }
        if (candidates.size() > 1) {
            List<RecoveryAction> actions = new ArrayList<>();
            for (String processId : candidates) actions.add(RecoveryAction.terminate(processId));
            plans.add(new RecoveryPlan(RecoveryPlan.Strategy.TERMINATE_ALL, actions));
        }
        addCombinedPlans(actionsByProcess, 0, new ArrayList<>(), plans);
        return Collections.unmodifiableList(plans);
    }

    private void addCombinedPlans(List<List<RecoveryAction>> actionsByProcess,
            int index, List<RecoveryAction> selected, List<RecoveryPlan> plans) {
        if (plans.size() >= recoveryCostWeights.getCombinationLimit()) return;
        if (index == actionsByProcess.size()) {
            if (selected.size() < 2 || isTerminateAll(selected, actionsByProcess.size())) {
                return;
            }
            if (hasDistinctProcesses(selected)) {
                plans.add(new RecoveryPlan(RecoveryPlan.Strategy.COMBINED, selected));
            }
            return;
        }
        addCombinedPlans(actionsByProcess, index + 1, selected, plans);
        for (RecoveryAction action : actionsByProcess.get(index)) {
            selected.add(action);
            addCombinedPlans(actionsByProcess, index + 1, selected, plans);
            selected.remove(selected.size() - 1);
        }
    }

    private boolean isTerminateAll(List<RecoveryAction> actions, int processCount) {
        if (actions.size() != processCount) return false;
        for (RecoveryAction action : actions) {
            if (action.getType() != RecoveryAction.Type.TERMINATE) return false;
        }
        return true;
    }

    private boolean hasDistinctProcesses(List<RecoveryAction> actions) {
        java.util.Set<String> processIds = new java.util.HashSet<>();
        for (RecoveryAction action : actions) {
            if (!processIds.add(action.getProcessId())) return false;
        }
        return true;
    }

    public List<RecoveryPlanResult> evaluateRecoveryPlans() {
        List<RecoveryPlanResult> results = new ArrayList<>();
        for (RecoveryPlan plan : generateRecoveryPlans()) {
            RecoveryPlanResult result = planEvaluator.evaluate(plan, systemState);
            results.add(result);
            eventLogger.record("PLAN_CANDIDATE plan=" + plan
                    + " valid=" + result.isExecutable()
                    + " resolved=" + result.isDeadlockResolved()
                    + " predictedCost=" + result.getCost()
                    + " message=" + result.getMessage());
        }
        return Collections.unmodifiableList(results);
    }

    public RecoveryPlanResult recoverPlan(RecoveryPlan plan) {
        RecoveryContext context = recoveryContext();
        long recoveryDeadlockId = deadlockSequence == 0 ? ++deadlockSequence
            : deadlockSequence;
        RecoveryPlanResult predicted = planEvaluator.evaluate(plan, systemState);
        if (!predicted.isExecutable()) {
            throw new IllegalArgumentException(predicted.getMessage());
        }
        long started = System.nanoTime();
        RecoveryPlanResult actual = planEvaluator.execute(plan, systemState);
        preventionEngine.refreshWaitForGraph();
        resumeWaitingProcesses();
        RecoveryCostComparison comparison = new RecoveryCostComparison(predicted, actual);
        recoveryComparisons.add(comparison);
        recoveryMetrics.record(predicted, actual, System.nanoTime() - started);
        for (deadlock.engine.RecoveryAction action : plan.getActions()) {
            if (action.getType() == deadlock.engine.RecoveryAction.Type.TERMINATE
                    || action.getType() == deadlock.engine.RecoveryAction.Type.PREEMPT
                    || action.getType() == deadlock.engine.RecoveryAction.Type.ROLLBACK) {
                recoveryMetrics.recordVictimSelection(action.getProcessId());
            }
        }
        adaptiveSelector.observe(context, actual);
        eventLogger.record("RECOVERY_CONTEXT id=" + recoveryDeadlockId
            + " " + context);
        eventLogger.record("RECOVERY_PLAN id=" + recoveryDeadlockId
            + " plan=" + plan
                + " predictedCost=" + predicted.getCost()
            + " actualCost=" + actual.getCost()
            + " resolved=" + actual.isDeadlockResolved()
            + " workLost=" + actual.getWorkLost()
            + " predictionError="
            + new RecoveryCostComparison(predicted, actual).getSignedCostError());
        return actual;
    }

    public RecoveryPlanResult recoverAdaptive() {
        List<RecoveryPlanResult> evaluated = evaluateRecoveryPlans();
        RecoveryPlan selected;
        RecoveryContext context = recoveryContext();
        boolean hasHistory = adaptiveSelector.hasHistory(context);
        try {
            selected = adaptiveSelector.select(context, evaluated);
            eventLogger.record((hasHistory ? "ADAPTIVE_SELECTION" : "FALLBACK_SELECTION")
                    + " strategy=" + selected.getStrategy());
        } catch (RuntimeException adaptiveFailure) {
            eventLogger.record("ADAPTIVE_FALLBACK reason="
                    + adaptiveFailure.getMessage());
            try {
                selected = adaptiveSelector.selectFallback(evaluated);
            } catch (RuntimeException noPlan) {
                throw new IllegalStateException(
                        "UNRECOVERABLE DEADLOCK: no valid recovery plan.", noPlan);
            }
        }
        return recoverPlan(selected);
    }

    public RecoveryPlanResult recoverWithStrategy(RecoveryPlan.Strategy strategy) {
        for (RecoveryPlanResult result : evaluateRecoveryPlans()) {
            if (result.getPlan().getStrategy() == strategy && result.isExecutable()) {
                return recoverPlan(result.getPlan());
            }
        }
        throw new IllegalArgumentException("No executable plan for strategy: " + strategy);
    }

    /** Terminates one victim, refreshes detection, and repeats until resolved. */
    public List<RecoveryPlanResult> recoverOneAtATime() {
        List<RecoveryPlanResult> results = new ArrayList<>();
        while (hasDeadlock()) {
            RecoveryPlanResult selected = evaluateRecoveryPlans().stream()
                    .filter(result -> result.isExecutable()
                            && result.getPlan().getStrategy()
                            == RecoveryPlan.Strategy.SINGLE_TERMINATION)
                    .min(java.util.Comparator.comparingDouble(RecoveryPlanResult::getCost))
                        .orElseThrow(() -> new IllegalStateException(
                            "UNRECOVERABLE DEADLOCK: no valid single-victim plan."));
            results.add(recoverPlan(selected.getPlan()));
        }
        return Collections.unmodifiableList(results);
    }

    private RecoveryContext recoveryContext() {
        preventionEngine.refreshWaitForGraph();
        return RecoveryContext.from(systemState, waitForGraph);
    }

    public void reset() {
        systemState.clear();
        waitForGraph.clear();
        lockOrderGraph.clear();
        eventLogger.clear();
        victimCounts.clear();
        deadlockSequence = 0L;
        checkpointManager.clear();
        recoveryMetrics.clear();
        recoveryComparisons.clear();
        adaptiveSelector.clear();
    }

    public SystemState getSystemState() { return systemState; }
    public WaitForGraph getWaitForGraph() { return waitForGraph; }
    public LockOrderGraph getLockOrderGraph() { return lockOrderGraph; }
    public PreventionEngine getPreventionEngine() { return preventionEngine; }
    public RecoveryEngine getRecoveryEngine() { return recoveryEngine; }
    public EventLogger getEventLogger() { return eventLogger; }
    public CheckpointManager getCheckpointManager() { return checkpointManager; }
    public RecoveryCostWeights getRecoveryCostWeights() { return recoveryCostWeights; }
    public RecoveryMetrics getRecoveryMetrics() { return recoveryMetrics; }
    public AdaptiveStrategySelector getAdaptiveStrategySelector() {
        return adaptiveSelector;
    }
    public List<RecoveryCostComparison> getRecoveryComparisons() {
        return Collections.unmodifiableList(new ArrayList<>(recoveryComparisons));
    }
    public Map<String, Integer> getVictimCounts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(victimCounts));
    }
}