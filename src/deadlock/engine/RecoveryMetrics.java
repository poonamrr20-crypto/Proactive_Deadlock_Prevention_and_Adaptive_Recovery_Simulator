package deadlock.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cumulative recovery outcomes used for reporting and adaptation. */
public final class RecoveryMetrics {
    public static final class Outcome {
        private final RecoveryPlanResult result;
        private final RecoveryCostComparison comparison;
        private final long durationNanos;

        private Outcome(RecoveryPlanResult result,
                RecoveryCostComparison comparison, long durationNanos) {
            this.result = result;
            this.comparison = comparison;
            this.durationNanos = durationNanos;
        }

        public RecoveryPlanResult getResult() { return result; }
        public RecoveryCostComparison getComparison() { return comparison; }
        public long getDurationNanos() { return durationNanos; }
    }

    private final List<Outcome> outcomes = new ArrayList<>();
    private final Map<String, Integer> victimSelections = new LinkedHashMap<>();

    public void record(RecoveryPlanResult result, long durationNanos) {
        outcomes.add(new Outcome(result, null, durationNanos));
    }

    public void record(RecoveryPlanResult predicted,
            RecoveryPlanResult actual, long durationNanos) {
        outcomes.add(new Outcome(actual,
                new RecoveryCostComparison(predicted, actual), durationNanos));
    }

    public List<Outcome> getOutcomes() {
        return Collections.unmodifiableList(new ArrayList<>(outcomes));
    }

    public int getRecoveryCount() { return outcomes.size(); }

    public int getSuccessCount() {
        int count = 0;
        for (Outcome outcome : outcomes) {
            if (outcome.result.isDeadlockResolved()) count++;
        }
        return count;
    }

    public double getSuccessRate() {
        return outcomes.isEmpty() ? 0.0 : (double) getSuccessCount() / outcomes.size();
    }

    public double getTotalCost() {
        double total = 0.0;
        for (Outcome outcome : outcomes) total += outcome.result.getCost();
        return total;
    }

    public double getAverageCost() {
        return outcomes.isEmpty() ? 0.0 : getTotalCost() / outcomes.size();
    }

    public double getTotalWorkLost() {
        double total = 0.0;
        for (Outcome outcome : outcomes) total += outcome.result.getWorkLost();
        return total;
    }

    public double getAverageWorkLost() {
        return outcomes.isEmpty() ? 0.0 : getTotalWorkLost() / outcomes.size();
    }

    public int getTotalTerminatedProcesses() {
        int total = 0;
        for (Outcome outcome : outcomes) {
            total += outcome.result.getTerminatedProcesses();
        }
        return total;
    }

    public int getTotalReleasedInstances() {
        int total = 0;
        for (Outcome outcome : outcomes) {
            total += outcome.result.getReleasedInstances();
        }
        return total;
    }

    public double getAverageResourceUtilization() {
        if (outcomes.isEmpty()) return 0.0;
        double total = 0.0;
        for (Outcome outcome : outcomes) {
            total += outcome.result.getResourceUtilization();
        }
        return total / outcomes.size();
    }

    public long getTotalRecoveryTimeNanos() {
        long total = 0;
        for (Outcome outcome : outcomes) total += outcome.durationNanos;
        return total;
    }

    public void recordVictimSelections(Map<String, Integer> selections) {
        for (Map.Entry<String, Integer> entry : selections.entrySet()) {
            victimSelections.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    public void recordVictimSelection(String processId) {
        victimSelections.merge(processId, 1, Integer::sum);
    }

    public Map<String, Integer> getVictimSelections() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(victimSelections));
    }

    public int getRepeatedVictimSelections() {
        int repeated = 0;
        for (int count : victimSelections.values()) {
            repeated += Math.max(0, count - 1);
        }
        return repeated;
    }

    public int getMaximumVictimCount() {
        return victimSelections.isEmpty() ? 0 : Collections.max(victimSelections.values());
    }

    public double getAverageVictimCount() {
        if (victimSelections.isEmpty()) return 0.0;
        int total = 0;
        for (int count : victimSelections.values()) total += count;
        return (double) total / victimSelections.size();
    }

    /** Higher values indicate concentration of recovery on a repeated victim. */
    public double getStarvationIndicator() {
        if (victimSelections.isEmpty()) return 0.0;
        int maximum = Collections.max(victimSelections.values());
        return outcomes.isEmpty() ? maximum : (double) maximum / outcomes.size();
    }

    public double getAverageAbsoluteCostError() {
        double total = 0.0;
        int count = 0;
        for (Outcome outcome : outcomes) {
            if (outcome.comparison != null) {
                total += outcome.comparison.getAbsoluteCostError();
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    public double getAverageSignedCostError() {
        double total = 0.0;
        int count = 0;
        for (Outcome outcome : outcomes) {
            if (outcome.comparison != null) {
                total += outcome.comparison.getSignedCostError();
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    public double getAverageRelativeCostError() {
        double total = 0.0;
        int count = 0;
        for (Outcome outcome : outcomes) {
            if (outcome.comparison != null) {
                total += outcome.comparison.getRelativeCostError();
                count++;
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    public void clear() {
        outcomes.clear();
        victimSelections.clear();
    }
}
