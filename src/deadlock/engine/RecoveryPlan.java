package deadlock.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable sequence of recovery actions. */
public final class RecoveryPlan {
    public enum Strategy {
        SINGLE_TERMINATION, TERMINATE_ALL, ROLLBACK, PREEMPTION, COMBINED
    }

    private final Strategy strategy;
    private final List<RecoveryAction> actions;

    public RecoveryPlan(Strategy strategy, List<RecoveryAction> actions) {
        if (strategy == null || actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("A recovery plan needs a strategy and actions.");
        }
        this.strategy = strategy;
        this.actions = Collections.unmodifiableList(new ArrayList<>(actions));
    }

    public Strategy getStrategy() { return strategy; }
    public List<RecoveryAction> getActions() { return actions; }

    @Override
    public String toString() {
        return strategy + " " + actions;
    }
}
