package deadlock.engine;

import deadlock.model.Process;
import deadlock.model.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Runs repeatable deadlock workloads against adaptive and fixed policies. */
public final class RecoveryExperimentRunner {
    public enum Policy { ADAPTIVE, SINGLE_TERMINATION, TERMINATE_ALL }
    public enum DeadlockPattern { TWO_PROCESS_CYCLE, PROCESS_RING, MULTI_INSTANCE_CYCLE }

    public static final class WorkloadConfig {
        private final int processCount;
        private final int resourceCount;
        private final int iterations;
        private final DeadlockPattern pattern;
        private final int resourceInstances;
        private final int preemptibleEvery;

        public WorkloadConfig(int processCount, int resourceCount, int iterations,
                DeadlockPattern pattern, int resourceInstances,
                int preemptibleEvery) {
            if (processCount < 2 || resourceCount < 2 || iterations < 1
                    || resourceInstances < 1 || preemptibleEvery < 0
                    || pattern == null) {
                throw new IllegalArgumentException("Invalid workload configuration.");
            }
            this.processCount = processCount;
            this.resourceCount = resourceCount;
            this.iterations = iterations;
            this.pattern = pattern;
            this.resourceInstances = resourceInstances;
            this.preemptibleEvery = preemptibleEvery;
        }

        public int getProcessCount() { return processCount; }
        public int getResourceCount() { return resourceCount; }
        public int getIterations() { return iterations; }
        public DeadlockPattern getPattern() { return pattern; }
        public int getResourceInstances() { return resourceInstances; }
        public int getPreemptibleEvery() { return preemptibleEvery; }
    }

    public static final class ExperimentReport {
        private final Policy policy;
        private final WorkloadConfig configuration;
        private final RecoveryMetrics metrics;

        private ExperimentReport(Policy policy, WorkloadConfig configuration,
                RecoveryMetrics metrics) {
            this.policy = policy;
            this.configuration = configuration;
            this.metrics = metrics;
        }

        public Policy getPolicy() { return policy; }
        public WorkloadConfig getConfiguration() { return configuration; }
        public RecoveryMetrics getMetrics() { return metrics; }
    }

    private final long seed;

    public RecoveryExperimentRunner(long seed) {
        this.seed = seed;
    }

    public List<ExperimentReport> run(int processCount, int resourceCount,
            int iterations) {
        return run(new WorkloadConfig(processCount, resourceCount, iterations,
                DeadlockPattern.TWO_PROCESS_CYCLE, 1, 2));
    }

    public List<ExperimentReport> run(WorkloadConfig configuration) {
        List<ExperimentReport> reports = new ArrayList<>();
        for (Policy policy : Policy.values()) {
            RecoveryMetrics aggregate = new RecoveryMetrics();
            for (int iteration = 0; iteration < configuration.getIterations(); iteration++) {
                SimulationController controller = createWorkload(configuration, iteration);
                while (controller.hasDeadlock()) {
                    if (policy == Policy.ADAPTIVE) {
                        controller.recoverAdaptive();
                    } else if (policy == Policy.SINGLE_TERMINATION) {
                        controller.recoverOneAtATime();
                    } else {
                        controller.recoverWithStrategy(RecoveryPlan.Strategy.TERMINATE_ALL);
                    }
                }
                for (RecoveryMetrics.Outcome outcome
                        : controller.getRecoveryMetrics().getOutcomes()) {
                    if (outcome.getComparison() == null) {
                        aggregate.record(outcome.getResult(), outcome.getDurationNanos());
                    } else {
                        aggregate.record(outcome.getComparison().getPredicted(),
                                outcome.getComparison().getActual(),
                                outcome.getDurationNanos());
                    }
                }
                aggregate.recordVictimSelections(controller.getVictimCounts());
            }
            reports.add(new ExperimentReport(policy, configuration, aggregate));
        }
        return Collections.unmodifiableList(reports);
    }

    private SimulationController createWorkload(WorkloadConfig configuration,
            int iteration) {
        Random random = new Random(seed + iteration);
        SimulationController controller = new SimulationController();
        for (int i = 1; i <= configuration.getProcessCount(); i++) {
            controller.addProcess(new Process("P" + i, 1 + random.nextInt(5)));
        }
        for (int i = 1; i <= configuration.getResourceCount(); i++) {
            boolean preemptible = configuration.getPreemptibleEvery() > 0
                    && i % configuration.getPreemptibleEvery() == 0;
            controller.addResource(new Resource("R" + i,
                    configuration.getResourceInstances(), preemptible));
        }
        int cycleSize = configuration.getPattern() == DeadlockPattern.PROCESS_RING
                ? Math.min(configuration.getProcessCount(),
                        configuration.getResourceCount()) : 2;
        for (int i = 0; i < cycleSize; i++) {
            String processId = "P" + (i + 1);
            String heldResource = "R" + (i + 1);
            String requestedResource = "R" + ((i + 1) % cycleSize + 1);
            for (int instance = 0; instance < configuration.getResourceInstances(); instance++) {
                if (!controller.getSystemState().allocateResource(processId, heldResource)) {
                    throw new IllegalStateException("Unable to create workload allocation.");
                }
            }
            controller.getSystemState().setWaitingRequest(processId, requestedResource);
        }
        controller.getWaitForGraph().clear();
        for (Process process : controller.getSystemState().getAllProcesses()) {
            controller.getWaitForGraph().addProcess(process.getProcessId());
        }
        for (int i = 0; i < cycleSize; i++) {
            controller.getWaitForGraph().addEdge("P" + (i + 1),
                    "P" + ((i + 1) % cycleSize + 1));
        }
        return controller;
    }
}
