package deadlock.engine;

import deadlock.model.Process;
import deadlock.model.SystemState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stores process snapshots that can be used by rollback actions. */
public final class CheckpointManager {
    private final Map<String, Map<String, Checkpoint>> checkpoints = new ConcurrentHashMap<>();

    public void save(String processId, String checkpointId, SystemState state) {
        Process process = state.getProcess(processId);
        if (process == null) {
            throw new IllegalArgumentException("Unknown process: " + processId);
        }
        if (process.getState() == Process.State.TERMINATED) {
            throw new IllegalArgumentException("Cannot checkpoint a terminated process.");
        }
        if (checkpointId == null || checkpointId.isBlank()) {
            throw new IllegalArgumentException("Checkpoint ID cannot be empty.");
        }
        checkpoints.computeIfAbsent(processId, ignored -> new ConcurrentHashMap<>())
                .put(checkpointId, new Checkpoint(process.getExecutionProgress(),
                        process.getState(), state.getWaitingRequest(processId),
                        state.getHeldResources(processId)));
    }

    public boolean has(String processId, String checkpointId) {
        return checkpoints.containsKey(processId)
                && checkpoints.get(processId).containsKey(checkpointId);
    }

    public Set<String> getCheckpointIds(String processId) {
        Map<String, Checkpoint> processCheckpoints = checkpoints.get(processId);
        if (processCheckpoints == null) return Collections.emptySet();
        return Collections.unmodifiableSet(
                new java.util.LinkedHashSet<>(processCheckpoints.keySet()));
    }

    public double getProgress(String processId, String checkpointId) {
        Checkpoint checkpoint = checkpoints.containsKey(processId)
                ? checkpoints.get(processId).get(checkpointId) : null;
        if (checkpoint == null) {
            throw new IllegalArgumentException("Unknown checkpoint.");
        }
        return checkpoint.executionProgress;
    }

    public void clear() {
        checkpoints.clear();
    }

    public boolean restore(String processId, String checkpointId, SystemState state) {
        Checkpoint checkpoint = checkpoints.containsKey(processId)
                ? checkpoints.get(processId).get(checkpointId) : null;
        Process process = state.getProcess(processId);
        if (checkpoint == null || process == null
                || process.getState() == Process.State.TERMINATED) {
            return false;
        }
        if (checkpoint.state == Process.State.TERMINATED
                || (checkpoint.state == Process.State.WAITING)
                    != (checkpoint.waitingResource != null)) {
            return false;
        }
        if (checkpoint.waitingResource != null
                && state.getResource(checkpoint.waitingResource) == null) {
            return false;
        }

        for (Map.Entry<String, Integer> entry : checkpoint.heldResources.entrySet()) {
            if (state.getResource(entry.getKey()) == null) return false;
            int current = state.getAllocatedCount(processId, entry.getKey());
            int availableAfterRelease = state.getResource(entry.getKey())
                    .getAvailableInstances() + current;
            if (availableAfterRelease < entry.getValue()) {
                return false;
            }
        }
        state.releaseAllResources(processId);
        for (Map.Entry<String, Integer> entry : checkpoint.heldResources.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                if (!state.allocateResource(processId, entry.getKey())) {
                    throw new IllegalStateException(
                            "Checkpoint validation changed during restoration.");
                }
            }
        }
        process.setExecutionProgress(checkpoint.executionProgress);
        state.clearWaitingRequest(processId);
        if (checkpoint.waitingResource != null
                && !state.setWaitingRequest(processId, checkpoint.waitingResource)) {
            throw new IllegalStateException("Checkpoint waiting request cannot be restored.");
        }
        process.setState(checkpoint.state);
        return true;
    }

    private static final class Checkpoint {
        private final double executionProgress;
        private final Process.State state;
        private final String waitingResource;
        private final Map<String, Integer> heldResources;

        private Checkpoint(double executionProgress, Process.State state,
                String waitingResource, Map<String, Integer> heldResources) {
            this.executionProgress = executionProgress;
            this.state = state;
            this.waitingResource = waitingResource;
            this.heldResources = new LinkedHashMap<>(heldResources);
        }
    }
}
