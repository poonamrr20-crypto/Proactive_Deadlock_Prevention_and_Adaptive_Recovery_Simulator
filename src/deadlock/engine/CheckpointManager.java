package deadlock.engine;

import deadlock.model.Process;
import deadlock.model.SystemState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stores process snapshots that can be used by rollback actions. */
public final class CheckpointManager {
    private final Map<String, Map<String, Checkpoint>> checkpoints = new ConcurrentHashMap<>();

    public void save(String processId, String checkpointId, SystemState state) {
        Process process = state.getProcess(processId);
        if (process == null) {
            throw new IllegalArgumentException("Unknown process: " + processId);
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
                    return false;
                }
            }
        }
        process.setExecutionProgress(checkpoint.executionProgress);
        if (checkpoint.waitingResource != null) {
            if (!state.setWaitingRequest(processId, checkpoint.waitingResource)) {
                return false;
            }
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
