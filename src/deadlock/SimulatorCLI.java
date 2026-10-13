
package deadlock;

import java.util.Scanner;

import deadlock.engine.PreventionEngine;
import deadlock.engine.PreventionEngine.RequestResult;
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

    private static final Scanner scanner = new Scanner(System.in);

    private static SystemState system;
    private static PreventionEngine engine;
    private static WaitForGraph waitForGraph;
    private static LockOrderGraph lockOrderGraph;

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println(" DEADLOCK PREVENTION SIMULATOR");
        System.out.println(" Standalone Backend Console");
        System.out.println("==========================================");

        try {
            configureSystem();
            runCommandLoop();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Configuration error: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    private static void configureSystem() {
        system = new SystemState();
        waitForGraph = new WaitForGraph();
        lockOrderGraph = new LockOrderGraph();

        System.out.println("\n--- SYSTEM CONFIGURATION ---");

        int processCount = readInt(
                "Number of processes: ", 1, 1000);

        for (int i = 1; i <= processCount; i++) {
            System.out.println("\nProcess " + i);

            String id = readText("Process ID: ");
            int priority = readInt("Priority (1 or higher): ",
                    1, Integer.MAX_VALUE);

            system.addProcess(new Process(id, priority));
            waitForGraph.addProcess(id);
        }

        int resourceCount = readInt(
                "\nNumber of resource types: ", 1, 1000);

        for (int i = 1; i <= resourceCount; i++) {
            System.out.println("\nResource " + i);

            String id = readText("Resource ID: ");
            int instances = readInt(
                    "Total instances (1 or higher): ",
                    1, Integer.MAX_VALUE);

            system.addResource(new Resource(id, instances));
            lockOrderGraph.addResource(id);
        }

        engine = new PreventionEngine(
                system, waitForGraph, lockOrderGraph);

        System.out.println("\nSystem configured successfully.");
        printFullState();
        printHelp();
    }

    private static void runCommandLoop() {
        while (true) {
            System.out.println();
            System.out.print("simulator> ");

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
        System.out.println("HELP");
        System.out.println("EXIT");
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
