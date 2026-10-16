package deadlock;

import deadlock.engine.RecoveryExperimentRunner;
import java.util.List;

public class RecoveryExperimentTest {
    public static void main(String[] args) {
        RecoveryExperimentRunner runner = new RecoveryExperimentRunner(42L);
        if (runner.run(4, 3, 2).size() != 3) {
            throw new AssertionError("Expected adaptive and two fixed reports.");
        }
        RecoveryExperimentRunner.WorkloadConfig ring =
            new RecoveryExperimentRunner.WorkloadConfig(4, 4, 2,
                RecoveryExperimentRunner.DeadlockPattern.PROCESS_RING,
                1, 2);
        List<RecoveryExperimentRunner.ExperimentReport> ringReports = runner.run(ring);
        if (ringReports.size() != 3
            || ringReports.get(0).getConfiguration().getPattern()
                != RecoveryExperimentRunner.DeadlockPattern.PROCESS_RING) {
            throw new AssertionError("Ring workload reports were not generated.");
        }
        RecoveryExperimentRunner.WorkloadConfig multiInstance =
            new RecoveryExperimentRunner.WorkloadConfig(3, 2, 1,
                RecoveryExperimentRunner.DeadlockPattern.MULTI_INSTANCE_CYCLE,
                2, 0);
        for (RecoveryExperimentRunner.ExperimentReport report : runner.run(multiInstance)) {
            if (report.getMetrics().getRecoveryCount() == 0
                || report.getMetrics().getTotalCost() < 0.0
                || report.getMetrics().getTotalWorkLost() < 0.0
                || report.getMetrics().getTotalRecoveryTimeNanos() < 0
                || report.getMetrics().getSuccessRate() < 0.0
                || report.getMetrics().getSuccessRate() > 1.0
                || report.getMetrics().getAverageResourceUtilization() < 0.0
                || report.getMetrics().getRepeatedVictimSelections() < 0
                || report.getMetrics().getStarvationIndicator() < 0.0
                || report.getMetrics().getAverageAbsoluteCostError() < 0.0
                || report.getMetrics().getAverageRelativeCostError() < 0.0) {
            throw new AssertionError("Experiment metrics were incomplete.");
            }
        }
        System.out.println("Recovery experiment test passed.");
    }
}
