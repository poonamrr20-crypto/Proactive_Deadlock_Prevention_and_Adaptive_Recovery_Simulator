package deadlock.engine;

/** Configurable weights for comparing predicted recovery plans. */
public final class RecoveryCostWeights {
    public enum PreemptionMode { PRESERVE_PROGRESS, RESTART }
    public enum CostProfile {
        BALANCED, WORK_PRESERVATION, PROCESS_PRESERVATION,
        PRIORITY_SENSITIVE, FAIRNESS_SENSITIVE
    }

    // Lower numeric priority classes represent more important processes.
    private final double[] priorityMultipliers = {0.0, 3.0, 2.0, 1.5, 1.0};
    private double lostWork = 0.35;
    private double priority = 0.15;
    private double terminationPenalty = 0.15;
    private double rollbackOverhead = 0.10;
    private double starvationRisk = 0.10;
    private double unresolvedDeadlock = 1000.0;
    private double resourceBenefit = 0.15;
    private int starvationThreshold = 3;
    private int combinationLimit = 4096;
    private PreemptionMode preemptionMode = PreemptionMode.PRESERVE_PROGRESS;

    public double getLostWork() { return lostWork; }
    public void setLostWork(double value) { lostWork = requireWeight(value); }
    public double getPriority() { return priority; }
    public void setPriority(double value) { priority = requireWeight(value); }
    public double getTerminationPenalty() { return terminationPenalty; }
    public void setTerminationPenalty(double value) { terminationPenalty = requireWeight(value); }
    public double getRollbackOverhead() { return rollbackOverhead; }
    public void setRollbackOverhead(double value) { rollbackOverhead = requireWeight(value); }
    public double getStarvationRisk() { return starvationRisk; }
    public void setStarvationRisk(double value) { starvationRisk = requireWeight(value); }
    public double getUnresolvedDeadlock() { return unresolvedDeadlock; }
    public void setUnresolvedDeadlock(double value) { unresolvedDeadlock = requireWeight(value); }
    public double getResourceBenefit() { return resourceBenefit; }
    public void setResourceBenefit(double value) { resourceBenefit = requireWeight(value); }

    public double getReclaimedResource() { return -resourceBenefit; }
    public void setReclaimedResource(double value) {
        resourceBenefit = requireWeight(Math.abs(value));
    }

    public double getRollbackWeight() { return rollbackOverhead; }
    public int getStarvationThreshold() { return starvationThreshold; }
    public void setStarvationThreshold(int value) {
        if (value < 1) throw new IllegalArgumentException("Starvation threshold must be positive.");
        starvationThreshold = value;
    }

    public int getCombinationLimit() { return combinationLimit; }
    public void setCombinationLimit(int value) {
        if (value < 1) throw new IllegalArgumentException("Combination limit must be positive.");
        combinationLimit = value;
    }

    public PreemptionMode getPreemptionMode() { return preemptionMode; }
    public void setPreemptionMode(PreemptionMode value) {
        if (value == null) throw new IllegalArgumentException("Preemption mode cannot be null.");
        preemptionMode = value;
    }

    public double getPriorityMultiplier(int priorityClass) {
        if (priorityClass < 1 || priorityClass > 4) {
            throw new IllegalArgumentException("Priority must be between 1 and 4.");
        }
        return priorityMultipliers[priorityClass];
    }

    public double getMaximumPriorityMultiplier() {
        double maximum = priorityMultipliers[1];
        for (int priority = 2; priority <= 4; priority++) {
            maximum = Math.max(maximum, priorityMultipliers[priority]);
        }
        return maximum;
    }

    public RecoveryCostWeights copy() {
        RecoveryCostWeights copy = new RecoveryCostWeights();
        copy.lostWork = lostWork;
        copy.priority = priority;
        copy.terminationPenalty = terminationPenalty;
        copy.rollbackOverhead = rollbackOverhead;
        copy.starvationRisk = starvationRisk;
        copy.unresolvedDeadlock = unresolvedDeadlock;
        copy.resourceBenefit = resourceBenefit;
        copy.starvationThreshold = starvationThreshold;
        copy.combinationLimit = combinationLimit;
        copy.preemptionMode = preemptionMode;
        for (int priorityClass = 1; priorityClass <= 4; priorityClass++) {
            copy.priorityMultipliers[priorityClass] = priorityMultipliers[priorityClass];
        }
        return copy;
    }

    public static RecoveryCostWeights forProfile(CostProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Cost profile cannot be null.");
        RecoveryCostWeights weights = new RecoveryCostWeights();
        switch (profile) {
            case WORK_PRESERVATION:
                weights.setLostWork(0.60);
                weights.setTerminationPenalty(0.10);
                break;
            case PROCESS_PRESERVATION:
                weights.setLostWork(0.15);
                weights.setTerminationPenalty(0.60);
                break;
            case PRIORITY_SENSITIVE:
                weights.setPriority(0.50);
                break;
            case FAIRNESS_SENSITIVE:
                weights.setStarvationRisk(0.50);
                break;
            case BALANCED:
                break;
            default:
                throw new IllegalArgumentException("Unsupported cost profile.");
        }
        return weights;
    }

    public void setPriorityMultiplier(int priorityClass, double value) {
        if (priorityClass < 1 || priorityClass > 4) {
            throw new IllegalArgumentException("Priority must be between 1 and 4.");
        }
        priorityMultipliers[priorityClass] = requireWeight(value);
    }

    @Override
    public String toString() {
        return "RecoveryCostWeights{lostWork=" + lostWork
                + ", priority=" + priority + ", termination=" + terminationPenalty
                + ", rollback=" + rollbackOverhead + ", starvation=" + starvationRisk
                + ", unresolved=" + unresolvedDeadlock + ", resourceBenefit="
                + resourceBenefit + ", starvationThreshold=" + starvationThreshold
                + ", combinationLimit=" + combinationLimit + ", preemptionMode="
                + preemptionMode + '}';
    }

    private static double requireWeight(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Cost weight must be finite and non-negative.");
        }
        return value;
    }
}
