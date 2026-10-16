package deadlock.engine;

/** Configurable weights for comparing predicted recovery plans. */
public final class RecoveryCostWeights {
    private double lostWork = 3.0;
    private double priority = 4.0;
    private double terminationPenalty = 20.0;
    private double reclaimedResource = -2.0;
    private double rollbackOverhead = 8.0;
    private double starvationRisk = 6.0;

    public double getLostWork() { return lostWork; }
    public void setLostWork(double value) { lostWork = requireFinite(value); }
    public double getPriority() { return priority; }
    public void setPriority(double value) { priority = requireFinite(value); }
    public double getTerminationPenalty() { return terminationPenalty; }
    public void setTerminationPenalty(double value) { terminationPenalty = requireFinite(value); }
    public double getReclaimedResource() { return reclaimedResource; }
    public void setReclaimedResource(double value) { reclaimedResource = requireFinite(value); }
    public double getRollbackOverhead() { return rollbackOverhead; }
    public void setRollbackOverhead(double value) { rollbackOverhead = requireFinite(value); }
    public double getStarvationRisk() { return starvationRisk; }
    public void setStarvationRisk(double value) { starvationRisk = requireFinite(value); }

    private static double requireFinite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Cost weight must be finite.");
        }
        return value;
    }
}
