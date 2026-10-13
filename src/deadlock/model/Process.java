
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
    private int priority;
    private double executionProgress;
    private State state;

    public Process(String processId, int priority) {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException(
                "Process ID cannot be empty."
            );
        }

        if (priority < 1) {
            throw new IllegalArgumentException(
                "Priority must be at least 1."
            );
        }

        this.processId = processId;
        this.priority = priority;
        this.executionProgress = 0.0;
        this.state = State.NEW;
    }

    public String getProcessId() {
        return processId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        if (priority < 1) {
            throw new IllegalArgumentException(
                "Priority must be at least 1."
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

    public void setState(State state) {
        if (state == null) {
            throw new IllegalArgumentException(
                "Process state cannot be null."
            );
        }

        this.state = state;
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
