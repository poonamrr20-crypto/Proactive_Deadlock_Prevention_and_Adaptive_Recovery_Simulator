
package deadlock;

import java.util.Scanner;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;

import deadlock.engine.RecoveryExperimentRunner;
import deadlock.engine.PreventionEngine;
import deadlock.engine.PreventionEngine.RequestResult;
import deadlock.engine.RecoveryPlan;
import deadlock.engine.RecoveryPlanResult;
import deadlock.engine.RecoveryMetrics;
import deadlock.engine.SimulationController;
import deadlock.graph.LockOrderGraph;
import deadlock.graph.WaitForGraph;
import deadlock.model.Process;
import deadlock.model.Resource;
import deadlock.model.SystemState;

/**
 * Standalone console interface for the deadlock prevention backend.
 * The frontend can later call the same backend operations.
 */
public class SimulatorCLI {

    private static Scanner scanner = new Scanner(System.in);

    private static SystemState system;
    private static PreventionEngine engine;
    private static WaitForGraph waitForGraph;
    private static LockOrderGraph lockOrderGraph;
    private static SimulationController controller;
    private static List<RecoveryPlanResult> evaluatedPlans = Collections.emptyList();

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println(" DEADLOCK PREVENTION SIMULATOR");
        System.out.println(" Standalone Backend Console");
        System.out.println("==========================================");

        try {
            configureBatchInput(args);
            configureSystem();
            runCommandLoop();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Configuration error: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    /** Keeps interactive input intact while allowing one-line scripted runs. */
    private static void configureBatchInput(String[] args) {
        if (args.length == 0 || !"--batch".equalsIgnoreCase(args[0])) {
            return;
        }
        if (args.length == 1) {
            return;
        }
        String input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String[] entries = input.split("\\s*;\\s*");
        scanner.close();
        scanner = new Scanner(String.join(System.lineSeparator(), entries));
    }

    private static void configureSystem() {
        controller = new SimulationController();
        system = controller.getSystemState();
        waitForGraph = controller.getWaitForGraph();
        lockOrderGraph = controller.getLockOrderGraph();

        System.out.println("\n--- SYSTEM CONFIGURATION ---");

        int processCount = readInt(
                "Number of processes: ", 1, 1000);

        for (int i = 1; i <= processCount; i++) {
            System.out.println("\nProcess " + i);

            String id = readText("Process ID: ");
            int priority = readInt("Priority (1 or higher): ",
                    1, Integer.MAX_VALUE);

            controller.addProcess(new Process(id, priority));
        }

        int resourceCount = readInt(
                "\nNumber of resource types: ", 1, 1000);

        for (int i = 1; i <= resourceCount; i++) {
            System.out.println("\nResource " + i);

            String id = readText("Resource ID: ");
            int instances = readInt(
                    "Total instances (1 or higher): ",
                    1, Integer.MAX_VALUE);
            boolean preemptible = readBoolean("Preemptible (yes/no): ");

            controller.addResource(new Resource(id, instances, preemptible));
        }

        engine = controller.getPreventionEngine();

        System.out.println("\nSystem configured successfully.");
        printFullState();
        printHelp();
    }

    private static void runCommandLoop() {
        while (true) {
            System.out.println();
            System.out.print("simulator> ");

            if (!scanner.hasNextLine()) {
                System.out.println("End of input. Simulator closed.");
                return;
            }
            String line = scanner.nextLine().trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] parts = line.split("\\s+");
            String command = parts[0].toUpperCase();

            switch (command) {
                case "REQUEST":
                    if (requireArguments(parts, 3)) {
                        handleRequest(parts[1], parts[2]);
                    }
                    break;

                case "RELEASE":
                    if (requireArguments(parts, 3)) {
                        handleRelease(parts[1], parts[2]);
                    }
                    break;

                case "STATUS":
                    printFullState();
                    break;

                case "GRAPHS":
                    printGraphs();
                    break;

                case "DETECT":
                    handleDetect();
                    break;

                case "CHECKPOINT":
                    handleCheckpoint(parts);
                    break;

                case "PROGRESS":
                    handleProgress(parts);
                    break;

                case "PLANS":
                    handlePlans();
                    break;

                case "APPLY":
                    handleApply(parts);
                    break;

                case "RECOVER":
                    handleRecover(parts);
                    break;

                case "METRICS":
                    printMetrics();
                    break;

                case "EXPERIMENT":
                    handleExperiment(parts);
                    break;

                case "HELP":
                    printHelp();
                    break;

                case "EXIT":
                case "QUIT":
                    System.out.println("Simulator closed.");
                    return;

                default:
                    System.out.println(
                            "Unknown command. Type HELP to see commands.");
            }
        }
    }

    private static void handleRequest(
            String processId, String resourceId) {

        System.out.println("\n--- RESOURCE REQUEST ---");
        System.out.println(
                "Process " + processId + " requests " + resourceId);

        RequestResult result =
                engine.requestResource(processId, resourceId);

        System.out.println("Result: " + result);
        printFullState();
    }

    private static void handleDetect() {
        List<String> cycle = controller.detectDeadlock();
        System.out.println(cycle.isEmpty()
                ? "No deadlock detected."
                : "Deadlock cycle: " + cycle);
    }

    private static void handleCheckpoint(String[] parts) {
        if (parts.length != 2 && parts.length != 3) {
            System.out.println("Usage: CHECKPOINT <processId> [checkpointId]");
            return;
        }
        String checkpointId = parts.length == 3 ? parts[2] : "default";
        try {
            controller.saveCheckpoint(parts[1], checkpointId);
            System.out.println("Checkpoint saved: " + parts[1]
                    + "/" + checkpointId);
        } catch (IllegalArgumentException e) {
            System.out.println("Checkpoint failed: " + e.getMessage());
        }
    }

    private static void handleProgress(String[] parts) {
        if (parts.length != 3) {
            System.out.println("Usage: PROGRESS <processId> <0-100>");
            return;
        }
        try {
            Process process = system.getProcess(parts[1]);
            if (process == null) throw new IllegalArgumentException("Unknown process.");
            process.setExecutionProgress(Double.parseDouble(parts[2]));
            System.out.println("Progress updated.");
        } catch (IllegalArgumentException e) {
            System.out.println("Progress update failed: " + e.getMessage());
        }
    }

    private static void handlePlans() {
        evaluatedPlans = controller.evaluateRecoveryPlans();
        if (evaluatedPlans.isEmpty()) {
            System.out.println("No recovery plans available.");
            return;
        }
        for (int i = 0; i < evaluatedPlans.size(); i++) {
            RecoveryPlanResult result = evaluatedPlans.get(i);
            System.out.println("[" + i + "] " + result.getPlan());
            System.out.println("    executable=" + result.isExecutable()
                    + " resolved=" + result.isDeadlockResolved()
                    + " unblocked=" + result.getUnblockedProcesses()
                    + " workLost=" + result.getWorkLost()
                    + " cost=" + result.getCost()
                    + " message=" + result.getMessage());
        }
        System.out.println("Use APPLY <index> to execute a plan.");
    }

    private static void handleApply(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Usage: APPLY <planIndex>");
            return;
        }
        try {
            int index = Integer.parseInt(parts[1]);
            if (index < 0 || index >= evaluatedPlans.size()) {
                throw new IllegalArgumentException("Unknown plan index.");
            }
            RecoveryPlanResult result = controller.recoverPlan(
                    evaluatedPlans.get(index).getPlan());
            System.out.println("Plan applied: " + result.getMessage());
            printFullState();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Plan application failed: " + e.getMessage());
        }
    }

    private static void handleRecover(String[] parts) {
        if (parts.length != 2) {
            System.out.println("Usage: RECOVER ADAPTIVE|ONE|ALL|ROLLBACK|PREEMPT");
            return;
        }
        try {
            String mode = parts[1].toUpperCase();
            if ("ADAPTIVE".equals(mode)) {
                System.out.println(controller.recoverAdaptive().getMessage());
            } else if ("ONE".equals(mode)) {
                System.out.println("Applied plans: "
                        + controller.recoverOneAtATime().size());
            } else if ("ALL".equals(mode)) {
                System.out.println(controller.recoverWithStrategy(
                        RecoveryPlan.Strategy.TERMINATE_ALL).getMessage());
            } else if ("ROLLBACK".equals(mode)) {
                System.out.println(controller.recoverWithStrategy(
                        RecoveryPlan.Strategy.ROLLBACK).getMessage());
            } else if ("PREEMPT".equals(mode)) {
                System.out.println(controller.recoverWithStrategy(
                        RecoveryPlan.Strategy.PREEMPTION).getMessage());
            } else {
                System.out.println("Unknown recovery mode.");
                return;
            }
            printFullState();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Recovery failed: " + e.getMessage());
        }
    }

    private static void printMetrics() {
        RecoveryMetrics metrics = controller.getRecoveryMetrics();
        System.out.println("\n--- RECOVERY METRICS ---");
        System.out.println("Recoveries: " + metrics.getRecoveryCount());
        System.out.println("Success rate: " + metrics.getSuccessRate());
        System.out.println("Total cost: " + metrics.getTotalCost());
        System.out.println("Terminated processes: "
                + metrics.getTotalTerminatedProcesses());
        System.out.println("Work lost: " + metrics.getTotalWorkLost());
        System.out.println("Recovery time (ns): "
                + metrics.getTotalRecoveryTimeNanos());
        System.out.println("Resource utilization: "
                + metrics.getAverageResourceUtilization());
        System.out.println("Victim selections: " + metrics.getVictimSelections());
        System.out.println("Repeated victims: "
                + metrics.getRepeatedVictimSelections());
        System.out.println("Starvation indicator: "
                + metrics.getStarvationIndicator());
        System.out.println("Average absolute prediction error: "
                + metrics.getAverageAbsoluteCostError());
        System.out.println("Average relative prediction error: "
                + metrics.getAverageRelativeCostError());
    }

    private static void handleExperiment(String[] parts) {
        if (parts.length < 4 || parts.length > 7) {
            System.out.println("Usage: EXPERIMENT <processes> <resources> "
                    + "<iterations> [TWO_PROCESS_CYCLE|PROCESS_RING|MULTI_INSTANCE_CYCLE] "
                    + "[instances] [preemptibleEvery]");
            return;
        }
        try {
            int processes = Integer.parseInt(parts[1]);
            int resources = Integer.parseInt(parts[2]);
            int iterations = Integer.parseInt(parts[3]);
            RecoveryExperimentRunner.DeadlockPattern pattern = parts.length >= 5
                    ? RecoveryExperimentRunner.DeadlockPattern.valueOf(parts[4].toUpperCase())
                    : RecoveryExperimentRunner.DeadlockPattern.TWO_PROCESS_CYCLE;
            int instances = parts.length >= 6 ? Integer.parseInt(parts[5]) : 1;
            int preemptibleEvery = parts.length == 7 ? Integer.parseInt(parts[6]) : 2;
            RecoveryExperimentRunner.WorkloadConfig config =
                    new RecoveryExperimentRunner.WorkloadConfig(processes, resources,
                            iterations, pattern, instances, preemptibleEvery);
            for (RecoveryExperimentRunner.ExperimentReport report
                    : new RecoveryExperimentRunner(42L).run(config)) {
                System.out.println(report.getPolicy() + " success="
                        + report.getMetrics().getSuccessRate()
                        + " cost=" + report.getMetrics().getTotalCost()
                        + " workLost=" + report.getMetrics().getTotalWorkLost()
                        + " timeNs=" + report.getMetrics().getTotalRecoveryTimeNanos());
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Experiment failed: " + e.getMessage());
        }
    }

    private static void handleRelease(
            String processId, String resourceId) {

        System.out.println("\n--- RESOURCE RELEASE ---");
        System.out.println(
                "Process " + processId + " releases " + resourceId);

        int before = system.getAllocatedCount(processId, resourceId);

        engine.releaseResource(processId, resourceId);

        int after = system.getAllocatedCount(processId, resourceId);

        if (before > after) {
            System.out.println("Result: Resource released successfully.");
            System.out.println(
                    "Waiting processes were reconsidered.");
        } else {
            System.out.println(
                    "Result: Release unsuccessful. Check process, resource "
                    + "and ownership.");
        }

        printFullState();
    }

    private static void printFullState() {
        System.out.println("\n========== CURRENT SYSTEM STATE ==========");

        System.out.println("\n[PROCESSES]");
        for (Process process : system.getAllProcesses()) {
            System.out.println(process);
        }

        System.out.println("\n[RESOURCES]");
        for (Resource resource : system.getAllResources()) {
            System.out.println(resource);
        }

        System.out.println("\n[ALLOCATIONS]");
        System.out.println(system.getAllocations());

        System.out.println("\n[WAITING REQUESTS]");
        for (Process process : system.getAllProcesses()) {
            String requested = system.getWaitingRequest(
                    process.getProcessId());

            if (requested != null) {
                System.out.println(
                        process.getProcessId() + " -> " + requested);
            }
        }

        System.out.println("\n[WAIT-FOR GRAPH]");
        System.out.println(waitForGraph);

        System.out.println("\n[LOCK-ORDER GRAPH]");
        System.out.println(lockOrderGraph);

        System.out.println("==========================================");
    }

    private static void printGraphs() {
        System.out.println("\n[WAIT-FOR GRAPH]");
        System.out.println(waitForGraph);

        System.out.println("\n[LOCK-ORDER GRAPH]");
        System.out.println(lockOrderGraph);
    }

    private static void printHelp() {
        System.out.println("\n--- AVAILABLE COMMANDS ---");
        System.out.println("REQUEST <processId> <resourceId>");
        System.out.println("RELEASE <processId> <resourceId>");
        System.out.println("STATUS");
        System.out.println("GRAPHS");
        System.out.println("DETECT");
        System.out.println("CHECKPOINT <processId> [checkpointId]");
        System.out.println("PROGRESS <processId> <0-100>");
        System.out.println("PLANS");
        System.out.println("APPLY <planIndex>");
        System.out.println("RECOVER ADAPTIVE|ONE|ALL|ROLLBACK|PREEMPT");
        System.out.println("METRICS");
        System.out.println("EXPERIMENT <processes> <resources> <iterations> "
            + "[pattern] [instances] [preemptibleEvery]");
        System.out.println("HELP");
        System.out.println("EXIT");
        System.out.println("\nBatch mode:");
        System.out.println("java -cp out deadlock.SimulatorCLI --batch "
            + "\"2;P1;1;P2;2;2;R1;1;no;R2;1;yes;"
            + "REQUEST P1 R1;EXIT\"");
        System.out.println("\nExample:");
        System.out.println("REQUEST P1 R1");
        System.out.println("REQUEST P2 R1");
        System.out.println("RELEASE P1 R1");
    }

    private static boolean requireArguments(
            String[] parts, int expectedLength) {

        if (parts.length != expectedLength) {
            System.out.println("Incorrect arguments. Type HELP.");
            return false;
        }

        return true;
    }

    private static String readText(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();

            if (!value.isEmpty() && !value.matches(".*\\s+.*")) {
                return value;
            }

            System.out.println(
                    "Enter a non-empty ID without spaces.");
        }
    }

    private static boolean readBoolean(String prompt) {
        while (true) {
            String value = readText(prompt).toLowerCase();
            if ("yes".equals(value) || "y".equals(value)) return true;
            if ("no".equals(value) || "n".equals(value)) return false;
            System.out.println("Enter yes or no.");
        }
    }

    private static int readInt(
            String prompt, int min, int max) {

        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            try {
                int value = Integer.parseInt(input);

                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Ask again below.
            }

            System.out.println(
                    "Enter a whole number between " + min
                    + " and " + max + ".");
        }
    }
}
