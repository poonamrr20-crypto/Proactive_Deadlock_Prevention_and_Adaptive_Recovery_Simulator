package deadlock.engine;

/** One operation in a hypothetical or executable recovery plan. */
public final class RecoveryAction {
    public enum Type { TERMINATE, ROLLBACK, PREEMPT }

    private final Type type;
    private final String processId;
    private final String resourceId;
    private final String checkpointId;
    private final boolean restartAfterPreemption;

    private RecoveryAction(Type type, String processId, String resourceId,
            String checkpointId, boolean restartAfterPreemption) {
        this.type = type;
        this.processId = processId;
        this.resourceId = resourceId;
        this.checkpointId = checkpointId;
        this.restartAfterPreemption = restartAfterPreemption;
    }

    public static RecoveryAction terminate(String processId) {
        return new RecoveryAction(Type.TERMINATE, processId, null, null, false);
    }

    public static RecoveryAction rollback(String processId) {
        return rollback(processId, "default");
    }

    public static RecoveryAction rollback(String processId, String checkpointId) {
        return new RecoveryAction(Type.ROLLBACK, processId, null, checkpointId, false);
    }

    public static RecoveryAction preempt(String processId, String resourceId) {
        return new RecoveryAction(Type.PREEMPT, processId, resourceId, null, false);
    }

    public static RecoveryAction preemptAndRestart(String processId,
            String resourceId) {
        return new RecoveryAction(Type.PREEMPT, processId, resourceId, null, true);
    }

    public Type getType() { return type; }
    public String getProcessId() { return processId; }
    public String getResourceId() { return resourceId; }
    public String getCheckpointId() { return checkpointId; }
    public boolean isRestartAfterPreemption() { return restartAfterPreemption; }

    @Override
    public String toString() {
        return type + "(" + processId
            + (resourceId == null ? "" : "," + resourceId)
            + (restartAfterPreemption ? ",restart" : "") + ")";
    }
}
