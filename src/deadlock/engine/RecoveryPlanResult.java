package deadlock.engine;

/** Predicted or actual outcome of executing a recovery plan. */
public final class RecoveryPlanResult {
    private final RecoveryPlan plan;
    private final boolean executable;
    private final boolean deadlockResolved;
    private final int unblockedProcesses;
    private final int terminatedProcesses;
    private final int releasedInstances;
    private final double workLost;
    private final double resourceUtilization;
    private final double cost;
    private final String message;

    public RecoveryPlanResult(RecoveryPlan plan, boolean executable,
            boolean deadlockResolved, int unblockedProcesses,
            int terminatedProcesses, int releasedInstances, double workLost,
            double resourceUtilization, double cost, String message) {
        this.plan = plan;
        this.executable = executable;
        this.deadlockResolved = deadlockResolved;
        this.unblockedProcesses = unblockedProcesses;
        this.terminatedProcesses = terminatedProcesses;
        this.releasedInstances = releasedInstances;
        this.workLost = workLost;
        this.resourceUtilization = resourceUtilization;
        this.cost = cost;
        this.message = message;
    }

    public RecoveryPlan getPlan() { return plan; }
    public boolean isExecutable() { return executable; }
    public boolean isDeadlockResolved() { return deadlockResolved; }
    public int getUnblockedProcesses() { return unblockedProcesses; }
    public int getTerminatedProcesses() { return terminatedProcesses; }
    public int getReleasedInstances() { return releasedInstances; }
    public double getWorkLost() { return workLost; }
    public double getResourceUtilization() { return resourceUtilization; }
    public double getCost() { return cost; }
    public String getMessage() { return message; }
}
