package deadlock.engine;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Small contextual learner that chooses the best observed strategy per context. */
public final class AdaptiveStrategySelector {
    private static final class Statistics {
        int attempts;
        int successes;
        double totalCost;
    }

    private final Map<RecoveryContext, EnumMap<RecoveryPlan.Strategy, Statistics>> history
            = new java.util.HashMap<>();

    public RecoveryPlan select(RecoveryContext context,
            List<RecoveryPlanResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No recovery plans available.");
        }
        RecoveryPlanResult fallback = selectFallbackResult(candidates);
        EnumMap<RecoveryPlan.Strategy, Statistics> stats = history.get(context);
        if (stats == null) return fallback.getPlan();

        RecoveryPlanResult best = fallback;
        double bestScore = score(fallback, stats.get(fallback.getPlan().getStrategy()));
        for (RecoveryPlanResult candidate : candidates) {
            if (!candidate.isExecutable()) continue;
            double score = score(candidate, stats.get(candidate.getPlan().getStrategy()));
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best.getPlan();
    }

    /** Deterministic policy used when learning data is unavailable or fails. */
    public RecoveryPlan selectFallback(List<RecoveryPlanResult> candidates) {
        return selectFallbackResult(candidates).getPlan();
    }

    public void observe(RecoveryContext context, RecoveryPlanResult result) {
        EnumMap<RecoveryPlan.Strategy, Statistics> stats = history.computeIfAbsent(
                context, ignored -> new EnumMap<>(RecoveryPlan.Strategy.class));
        Statistics strategy = stats.computeIfAbsent(result.getPlan().getStrategy(),
                ignored -> new Statistics());
        strategy.attempts++;
        if (result.isDeadlockResolved()) strategy.successes++;
        strategy.totalCost += result.getCost();
    }

    public int getAttempts(RecoveryContext context, RecoveryPlan.Strategy strategy) {
        EnumMap<RecoveryPlan.Strategy, Statistics> stats = history.get(context);
        Statistics value = stats == null ? null : stats.get(strategy);
        return value == null ? 0 : value.attempts;
    }

    public int getSuccesses(RecoveryContext context, RecoveryPlan.Strategy strategy) {
        EnumMap<RecoveryPlan.Strategy, Statistics> stats = history.get(context);
        Statistics value = stats == null ? null : stats.get(strategy);
        return value == null ? 0 : value.successes;
    }

    public int getKnownContextCount() { return history.size(); }

    public void clear() { history.clear(); }

    private RecoveryPlanResult selectFallbackResult(List<RecoveryPlanResult> candidates) {
        return candidates.stream()
                .filter(RecoveryPlanResult::isExecutable)
                .min(java.util.Comparator
                        .comparingDouble(RecoveryPlanResult::getCost)
                        .thenComparing(result -> result.getPlan().toString()))
                .orElseThrow(() -> new IllegalArgumentException("No executable recovery plans."));
    }

    private double score(RecoveryPlanResult result, Statistics statistics) {
        if (statistics == null || statistics.attempts == 0) return result.getCost();
        double successRate = (double) statistics.successes / statistics.attempts;
        double averageCost = statistics.totalCost / statistics.attempts;
        return averageCost - successRate * 100.0;
    }
}
