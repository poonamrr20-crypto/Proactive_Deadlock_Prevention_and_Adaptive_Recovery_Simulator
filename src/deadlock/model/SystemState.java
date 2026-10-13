
package deadlock.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maintains the central state of the deadlock simulator.
 */
public class SystemState {

    private final Map<String, Process> processes;
    private final Map<String, Resource> resources;

    // Process ID -> (Resource ID -> number of instances held)
    private final Map<String, Map<String, Integer>> allocations;

    // Process ID -> Resource ID being requested
    private final Map<String, String> waitingRequests;

    public SystemState() {
        processes = new LinkedHashMap<>();
        resources = new LinkedHashMap<>();
        allocations = new LinkedHashMap<>();
        waitingRequests = new LinkedHashMap<>();
    }

    public void addProcess(Process process) {
        if (process == null) {
            throw new IllegalArgumentException(
                "Process cannot be null."
            );
        }

        String id = process.getProcessId();

        if (processes.containsKey(id)) {
            throw new IllegalArgumentException(
                "Process already exists: " + id
            );
        }

        processes.put(id, process);
        allocations.put(id, new LinkedHashMap<>());
    }

    public void addResource(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException(
                "Resource cannot be null."
            );
        }

        String id = resource.getResourceId();

        if (resources.containsKey(id)) {
            throw new IllegalArgumentException(
                "Resource already exists: " + id
            );
        }

        resources.put(id, resource);
    }

    public Process getProcess(String processId) {
        return processes.get(processId);
    }

    public Resource getResource(String resourceId) {
        return resources.get(resourceId);
    }

    public Collection<Process> getAllProcesses() {
        return Collections.unmodifiableCollection(
            processes.values()
        );
    }

    public Collection<Resource> getAllResources() {
        return Collections.unmodifiableCollection(
            resources.values()
        );
    }

    /**
     * Returns the number of instances of a resource
     * held by a process.
     */
    public int getAllocatedCount(
            String processId, String resourceId) {

        Map<String, Integer> held = allocations.get(processId);

        if (held == null) {
            return 0;
        }

        return held.getOrDefault(resourceId, 0);
    }

    /**
     * Allocates one resource instance to a process.
     * Returns false if the process/resource does not exist
     * or no instance is available.
     */
    public boolean allocateResource(
            String processId, String resourceId) {

        Process process = processes.get(processId);
        Resource resource = resources.get(resourceId);

        if (process == null || resource == null) {
            return false;
        }

        if (!resource.allocateInstance()) {
            return false;
        }

        Map<String, Integer> held = allocations.get(processId);

        held.put(
            resourceId,
            held.getOrDefault(resourceId, 0) + 1
        );

        waitingRequests.remove(processId);
process.setState(Process.State.RUNNING);

        return true;
    }

    /**
     * Releases one resource instance held by a process.
     */
    public boolean releaseResource(
            String processId, String resourceId) {

        Process process = processes.get(processId);
        Resource resource = resources.get(resourceId);

        if (process == null || resource == null) {
            return false;
        }

        Map<String, Integer> held = allocations.get(processId);
        int count = held.getOrDefault(resourceId, 0);

        if (count == 0) {
            return false;
        }

        if (count == 1) {
            held.remove(resourceId);
        } else {
            held.put(resourceId, count - 1);
        }

        resource.releaseInstance();
        return true;
    }

    /**
     * Records that a process is waiting for a resource.
     */
    public boolean setWaitingRequest(
            String processId, String resourceId) {

        if (!processes.containsKey(processId)
                || !resources.containsKey(resourceId)) {
            return false;
        }

        waitingRequests.put(processId, resourceId);
        processes.get(processId).setState(Process.State.WAITING);
        return true;
    }

    /**
     * Returns the resource requested by a waiting process,
     * or null if the process is not waiting.
     */
    public String getWaitingRequest(String processId) {
        return waitingRequests.get(processId);
    }


    
/**
 * Clears a process's waiting request.
 */
public void clearWaitingRequest(String processId) {
    waitingRequests.remove(processId);

    Process process = processes.get(processId);

    if (process != null
            && process.getState() == Process.State.WAITING) {
        process.setState(Process.State.READY);
    }
}

    public Map<String, Integer> getHeldResources(String processId) {
        Map<String, Integer> held = allocations.get(processId);
        if (held == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(held));
    }

    public int releaseAllResources(String processId) {
        if (!processes.containsKey(processId)) {
            return 0;
        }
        int released = 0;
        Map<String, Integer> held = allocations.get(processId);
        for (Map.Entry<String, Integer> entry
                : new LinkedHashMap<>(held).entrySet()) {
            Resource resource = resources.get(entry.getKey());
            if (resource != null) {
                for (int i = 0; i < entry.getValue(); i++) {
                    resource.releaseInstance();
                    released++;
                }
            }
        }
        held.clear();
        clearWaitingRequest(processId);
        return released;
    }

    public boolean terminateProcess(String processId) {
        Process process = processes.get(processId);
        if (process == null) {
            return false;
        }
        releaseAllResources(processId);
        process.setState(Process.State.TERMINATED);
        return true;
    }

    public int getTotalAllocatedCount(String processId) {
        int total = 0;
        for (int count : allocations.getOrDefault(
                processId, Collections.emptyMap()).values()) {
            total += count;
        }
        return total;
    }

    public void clear() {
        processes.clear();
        resources.clear();
        allocations.clear();
        waitingRequests.clear();
    }

    /**
     * Returns a read-only view of the allocation records.
     */
    public Map<String, Map<String, Integer>>
            getAllocations() {

        Map<String, Map<String, Integer>> copy =
                new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Integer>> entry
                : allocations.entrySet()) {
            copy.put(
                entry.getKey(),
                Collections.unmodifiableMap(
                    new LinkedHashMap<>(entry.getValue())
                )
            );
        }

        return Collections.unmodifiableMap(copy);
    }

    @Override
    public String toString() {
        return "SystemState{" +
                "processes=" + processes.values() +
                ", resources=" + resources.values() +
                ", allocations=" + allocations +
                ", waitingRequests=" + waitingRequests +
                '}';
    }
}
