
package deadlock.model;

/**
 * Represents a resource type in the deadlock simulator.
 */
public class Resource {

    private final String resourceId;
    private final int totalInstances;
    private final boolean preemptible;
    private int availableInstances;

    public Resource(String resourceId, int totalInstances) {
        this(resourceId, totalInstances, false);
    }

    public Resource(String resourceId, int totalInstances, boolean preemptible) {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException(
                "Resource ID cannot be empty."
            );
        }

        if (totalInstances < 1) {
            throw new IllegalArgumentException(
                "A resource must have at least one instance."
            );
        }

        this.resourceId = resourceId;
        this.totalInstances = totalInstances;
        this.preemptible = preemptible;
        this.availableInstances = totalInstances;
    }

    public String getResourceId() {
        return resourceId;
    }

    public int getTotalInstances() {
        return totalInstances;
    }

    public int getAvailableInstances() {
        return availableInstances;
    }

    public boolean isPreemptible() {
        return preemptible;
    }

    public boolean hasAvailableInstance() {
        return availableInstances > 0;
    }

    /**
     * Allocates one instance if one is available.
     *
     * @return true if allocation succeeds, false otherwise.
     */
    public boolean allocateInstance() {
        if (availableInstances == 0) {
            return false;
        }

        availableInstances--;
        return true;
    }

    /**
     * Releases one previously allocated instance.
     */
    public void releaseInstance() {
        if (availableInstances >= totalInstances) {
            throw new IllegalStateException(
                "Cannot release: no instance is currently allocated."
            );
        }

        availableInstances++;
    }

    @Override
    public String toString() {
        return "Resource{" +
                "resourceId='" + resourceId + '\'' +
                ", totalInstances=" + totalInstances +
                ", availableInstances=" + availableInstances +
                '}';
    }
}
