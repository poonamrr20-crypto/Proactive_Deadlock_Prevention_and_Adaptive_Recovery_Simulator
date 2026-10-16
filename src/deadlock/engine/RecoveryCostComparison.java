package deadlock.engine;

/** Compares a plan's hypothetical cost with the committed outcome. */
public final class RecoveryCostComparison {
    private final RecoveryPlanResult predicted;
    private final RecoveryPlanResult actual;

    public RecoveryCostComparison(RecoveryPlanResult predicted,
            RecoveryPlanResult actual) {
        if (predicted == null || actual == null) {
            throw new IllegalArgumentException("Recovery results cannot be null.");
        }
        this.predicted = predicted;
        this.actual = actual;
    }

    public RecoveryPlanResult getPredicted() { return predicted; }
    public RecoveryPlanResult getActual() { return actual; }

    public double getSignedCostError() {
        return actual.getCost() - predicted.getCost();
    }

    public double getAbsoluteCostError() {
        return Math.abs(getSignedCostError());
    }

    public double getRelativeCostError() {
        if (predicted.getCost() == 0.0) return getAbsoluteCostError();
        return getAbsoluteCostError() / Math.abs(predicted.getCost());
    }
}
