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
    private static final int MAX_COMBINED_PLANS = 4096;
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

    public SimulationController() {
        systemState = new SystemState();
        waitForGraph = new WaitForGraph();
        lockOrderGraph = new LockOrderGraph();
        eventLogger = new EventLogger();
        preventionEngine = new PreventionEngine(systemState, waitForGraph,
                lockOrderGraph);
        recoveryEngine = new RecoveryEngine(systemState, waitForGraph,
                eventLogger);
        checkpointManager = new CheckpointManager();
        recoveryCostWeights = new RecoveryCostWeights();
        victimCounts = new LinkedHashMap<>();
        planEvaluator = new RecoveryPlanEvaluator(checkpointManager,
            recoveryCostWeights, victimCounts);
        adaptiveSelector = new AdaptiveStrategySelector();
        recoveryMetrics = new RecoveryMetrics();
        recoveryComparisons = new ArrayList<>();
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

    public void saveCheckpoint(String processId) {
        saveCheckpoint(processId, "default");
    }

    public void saveCheckpoint(String processId, String checkpointId) {
        checkpointManager.save(processId, checkpointId, systemState);
    }

    /** Generates executable candidates without changing the live state. */
    public List<RecoveryPlan> generateRecoveryPlans() {
        List<String> candidates = new ArrayList<>(waitForGraph.getCycleNodes());
        if (candidates.isEmpty()) candidates.addAll(waitForGraph.getProcesses());
        List<RecoveryPlan> plans = new ArrayList<>();
        List<List<RecoveryAction>> actionsByProcess = new ArrayList<>();
        for (String processId : candidates) {
            List<RecoveryAction> processActions = new ArrayList<>();
            RecoveryAction termination = RecoveryAction.terminate(processId);
            processActions.add(termination);
            plans.add(new RecoveryPlan(RecoveryPlan.Strategy.SINGLE_TERMINATION,
                    Collections.singletonList(termination)));
            if (checkpointManager.has(processId, "default")) {
                RecoveryAction rollback = RecoveryAction.rollback(processId);
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
        if (plans.size() >= MAX_COMBINED_PLANS) return;
        if (index == actionsByProcess.size()) {
            if (selected.size() < 2 || isTerminateAll(selected, actionsByProcess.size())) {
                return;
            }
            plans.add(new RecoveryPlan(RecoveryPlan.Strategy.COMBINED, selected));
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

    public List<RecoveryPlanResult> evaluateRecoveryPlans() {
        List<RecoveryPlanResult> results = new ArrayList<>();
        for (RecoveryPlan plan : generateRecoveryPlans()) {
            results.add(planEvaluator.evaluate(plan, systemState));
        }
        return Collections.unmodifiableList(results);
    }

    public RecoveryPlanResult recoverPlan(RecoveryPlan plan) {
        RecoveryContext context = recoveryContext();
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
            if (action.getType() == deadlock.engine.RecoveryAction.Type.TERMINATE) {
                recoveryMetrics.recordVictimSelection(action.getProcessId());
            }
        }
        adaptiveSelector.observe(context, actual);
        eventLogger.record("RECOVERY_PLAN plan=" + plan
                + " predictedCost=" + predicted.getCost()
                + " actualCost=" + actual.getCost());
        return actual;
    }

    public RecoveryPlanResult recoverAdaptive() {
        List<RecoveryPlanResult> evaluated = evaluateRecoveryPlans();
        RecoveryPlan selected;
        try {
            selected = adaptiveSelector.select(recoveryContext(), evaluated);
        } catch (RuntimeException adaptiveFailure) {
            selected = adaptiveSelector.selectFallback(evaluated);
            eventLogger.record("ADAPTIVE_FALLBACK reason="
                    + adaptiveFailure.getMessage());
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
                    .orElseThrow(() -> new IllegalStateException("No single-victim plan."));
            results.add(recoverPlan(selected.getPlan()));
        }
        return Collections.unmodifiableList(results);
    }

    private RecoveryContext recoveryContext() {
        return RecoveryContext.from(systemState, waitForGraph);
    }

    public void reset() {
        systemState.clear();
        waitForGraph.clear();
        lockOrderGraph.clear();
        eventLogger.clear();
        victimCounts.clear();
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