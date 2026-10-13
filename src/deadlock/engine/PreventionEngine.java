
package deadlock.engine;

import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

import java.util.Map;

/**
 * Handles proactive deadlock prevention and resource requests.
 */
public class PreventionEngine {

    public enum RequestStatus {
        GRANTED,
        BLOCKED,
        DENIED
    }

    public static class RequestResult {
        private final RequestStatus status;
        private final String message;

        public RequestResult(RequestStatus status, String message) {
            this.status = status;
            this.message = message;
        }

        public RequestStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return status + ": " + message;
        }
    }

    private final SystemState systemState;
    private final WaitForGraph waitForGraph;
    private final LockOrderGraph lockOrderGraph;

    public PreventionEngine(
            SystemState systemState,
            WaitForGraph waitForGraph,
            LockOrderGraph lockOrderGraph) {

        if (systemState == null
                || waitForGraph == null
                || lockOrderGraph == null) {
            throw new IllegalArgumentException(
                "System state and graphs cannot be null."
            );
        }

        this.systemState = systemState;
        this.waitForGraph = waitForGraph;
        this.lockOrderGraph = lockOrderGraph;
    }

    /**
     * Processes a request for one instance of a resource.
     */
    public RequestResult requestResource(
            String processId, String resourceId) {

        Process process = systemState.getProcess(processId);
        Resource resource = systemState.getResource(resourceId);

        if (process == null || resource == null) {
            return new RequestResult(
                RequestStatus.DENIED,
                "Unknown process or resource."
            );
        }

        if (process.getState() == Process.State.TERMINATED) {
            return new RequestResult(
                RequestStatus.DENIED,
                "Terminated processes cannot request resources."
            );
        }

        
String existingRequest =
        systemState.getWaitingRequest(processId);

if (existingRequest != null) {
    if (existingRequest.equals(resourceId)) {
        return new RequestResult(
            RequestStatus.BLOCKED,
            "Process is already waiting for this resource."
        );
    }

    rebuildWaitForGraph();
    return new RequestResult(
        RequestStatus.DENIED,
        "Process must resolve its existing waiting request first."
    );
    
}

        
// Prevent a process from waiting for a resource
// when it already holds all instances of that resource.
if (systemState.getAllocatedCount(processId, resourceId)
        >= resource.getTotalInstances()) {

    return new RequestResult(
        RequestStatus.DENIED,
        "Process already holds all instances of this resource."
    );
}

        // Proposed resource-order edges from resources
        // already held by this process to the requested resource.
        Map<String, Map<String, Integer>> allocations =
                systemState.getAllocations();

        Map<String, Integer> held = allocations.get(processId);

        if (held == null) {
            return new RequestResult(
                RequestStatus.DENIED,
                "Process allocation record is missing."
            );
        }

        // Check proposed edges before permanently adding them.
        
java.util.List<String[]> addedEdges = new java.util.ArrayList<>();

for (Map.Entry<String, Integer> entry : held.entrySet()) {
    if (entry.getValue() <= 0) {
        continue;
    }

    String heldResource = entry.getKey();

    if (heldResource.equals(resourceId)) {
        continue;
    }

    lockOrderGraph.addResource(heldResource);
    lockOrderGraph.addResource(resourceId);

    // Preserve edges that existed before this request.
    boolean edgeAlreadyExisted =
            lockOrderGraph.hasEdge(heldResource, resourceId);

    lockOrderGraph.addEdge(heldResource, resourceId);

    // Track only newly created edges.
    if (!edgeAlreadyExisted) {
        addedEdges.add(new String[] {
            heldResource, resourceId
        });
    }

    if (lockOrderGraph.hasCycle()) {
        // Remove all edges newly added by this request.
        for (String[] edge : addedEdges) {
            lockOrderGraph.removeEdge(edge[0], edge[1]);
        }

        return new RequestResult(
            RequestStatus.DENIED,
            "Request denied: Lock-order Graph cycle detected."
        );
    }
}


        // If no instance is available, record the waiting request.
        if (!resource.hasAvailableInstance()) {
            systemState.setWaitingRequest(processId, resourceId);

            // Build WFG edges to processes currently holding
            // the requested resource.
            for (Process other : systemState.getAllProcesses()) {
                if (!other.getProcessId().equals(processId)
                        && systemState.getAllocatedCount(
                            other.getProcessId(), resourceId) > 0) {

                    waitForGraph.addEdge(
                        processId, other.getProcessId()
                    );
                }
            }

            return new RequestResult(
                RequestStatus.BLOCKED,
                "Resource unavailable; process is waiting."
            );
        }

        // Commit allocation only after prevention checks pass.
        boolean allocated = systemState.allocateResource(
            processId, resourceId
        );

        if (!allocated) {
            for (String[] edge : addedEdges) {
        lockOrderGraph.removeEdge(edge[0], edge[1]);
    }

    rebuildWaitForGraph();

    return new RequestResult(
        RequestStatus.BLOCKED,
        "Allocation could not be completed."
    );
        }

        // Register graph nodes for the known entities.
        waitForGraph.addProcess(processId);
        lockOrderGraph.addResource(resourceId);

        rebuildWaitForGraph();
        return new RequestResult(
            RequestStatus.GRANTED,
            "Resource allocated successfully."
        );
    }

    public WaitForGraph getWaitForGraph() {
        return waitForGraph;
    }

    public LockOrderGraph getLockOrderGraph() {
        return lockOrderGraph;
    }


    
/**
 * Rebuilds the Wait-for Graph from current waiting requests
 * and current resource allocations.
 */
private void rebuildWaitForGraph() {

    // Remove all existing process nodes and edges by rebuilding
    // the graph object through a helper method.
    waitForGraph.clear();

    for (Process process : systemState.getAllProcesses()) {
        waitForGraph.addProcess(process.getProcessId());
    }

    for (Process waitingProcess : systemState.getAllProcesses()) {
        String requestedResource = systemState.getWaitingRequest(
            waitingProcess.getProcessId()
        );

        if (requestedResource == null) {
            continue;
        }

        for (Process holder : systemState.getAllProcesses()) {
            if (!holder.getProcessId().equals(
                    waitingProcess.getProcessId())
                    && systemState.getAllocatedCount(
                        holder.getProcessId(), requestedResource) > 0) {

                waitForGraph.addEdge(
                    waitingProcess.getProcessId(),
                    holder.getProcessId()
                );
            }
        }
    }
}

/**
 * Releases one resource instance and reconsiders waiting processes.
 */
public void releaseResource(
        String processId, String resourceId) {

    boolean released = systemState.releaseResource(
        processId, resourceId
    );

    if (!released) {
        return;
    }

    // Reconsider processes waiting for the released resource.
    for (Process process : systemState.getAllProcesses()) {
        String requestedResource = systemState.getWaitingRequest(
            process.getProcessId()
        );

        if (resourceId.equals(requestedResource)) {
            Resource resource = systemState.getResource(resourceId);

            if (resource != null && resource.hasAvailableInstance()) {
                systemState.clearWaitingRequest(process.getProcessId());

                RequestResult result = requestResource(
                    process.getProcessId(), resourceId
                );

                // If the request is blocked or denied, its result
                // is reflected by requestResource().
            }
        }
    }

    rebuildWaitForGraph();
}

}
