
package deadlock.model;

/**
 * Represents a virtual process in the deadlock simulator.
 */
public class Process {

    public enum State {
        NEW,
        READY,
        RUNNING,
        WAITING,
        TERMINATED
    }

    private final String processId;
    private final double totalWork;
    private int priority;
    private double executionProgress;
    private State state;
    private int recoveryVictimCount;
    private Runnable terminationHandler;

    public Process(String processId, int priority) {
        this(processId, priority, 100.0);
    }

    public Process(String processId, int priority, double totalWork) {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException(
                "Process ID cannot be empty."
            );
        }

        if (priority < 1 || priority > 4) {
            throw new IllegalArgumentException(
                "Priority must be between 1 and 4."
            );
        }

        if (Double.isNaN(totalWork) || Double.isInfinite(totalWork)
                || totalWork <= 0.0) {
            throw new IllegalArgumentException(
                "Total work must be finite and greater than zero."
            );
        }

        this.processId = processId;
        this.totalWork = totalWork;
        this.priority = priority;
        this.executionProgress = 0.0;
        this.state = State.NEW;
        this.recoveryVictimCount = 0;
        this.terminationHandler = null;
    }

    public String getProcessId() {
        return processId;
    }

    public int getPriority() {
        return priority;
    }

    public double getTotalWork() {
        return totalWork;
    }

    public double getCompletedWork() {
        return totalWork * executionProgress / 100.0;
    }

    public double getRemainingWork() {
        return totalWork - getCompletedWork();
    }

    public void setPriority(int priority) {
        if (priority < 1 || priority > 4) {
            throw new IllegalArgumentException(
                "Priority must be between 1 and 4."
            );
        }

        this.priority = priority;
    }

    public double getExecutionProgress() {
        return executionProgress;
    }

    public void setExecutionProgress(double executionProgress) {
        if (executionProgress < 0.0
                || executionProgress > 100.0) {
            throw new IllegalArgumentException(
                "Execution progress must be between 0 and 100."
            );
        }

        this.executionProgress = executionProgress;
    }

    public State getState() {
        return state;
    }

    public int getRecoveryVictimCount() {
        return recoveryVictimCount;
    }

    public void incrementRecoveryVictimCount() {
        recoveryVictimCount++;
    }

    public void setState(State state) {
        if (state == null) {
            throw new IllegalArgumentException(
                "Process state cannot be null."
            );
        }

        if (this.state == State.TERMINATED && state != State.TERMINATED) {
            throw new IllegalStateException(
                "A terminated process cannot change state."
            );
        }

        if (state == State.TERMINATED && this.state != State.TERMINATED
                && terminationHandler != null) {
            terminationHandler.run();
        }

        this.state = state;
    }

    public void setTerminationHandler(Runnable terminationHandler) {
        this.terminationHandler = terminationHandler;
    }

    @Override
    public String toString() {
        return "Process{" +
                "processId='" + processId + '\'' +
                ", priority=" + priority +
                ", executionProgress=" + executionProgress +
                ", state=" + state +
                '}';
    }
}
