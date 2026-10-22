# Adaptive Deadlock Recovery Simulator

This project is a deterministic Java simulator for comparing deadlock recovery plans. It models processes, resource counts, pending requests, checkpoints, recovery actions, normalized simulated recovery cost, and contextual adaptive selection.

## Model

A process has an identifier, priority class, total abstract work, completed progress, lifecycle state, held allocations, and recovery victim history. Progress is a percentage from 0 to 100:

`completedWork = totalWork * progress / 100`

`remainingWork = totalWork - completedWork`

A work unit is an abstract unit of simulated computation. It does not represent a CPU instruction, second, or real operating-system operation. Priority is an importance class, not an amount of work; lower numeric values mean higher actual priority. The default configurable multipliers are 3.0, 2.0, 1.5, and 1.0 for classes 1 through 4. Changing them changes the simulator's decision model.

Resources preserve `allocated + available = total` for every resource type. Allocation, release, termination, rollback, and preemption operate on individual instances. Preemption requires a live process, ownership, and a preemptible resource.

## Detection and recovery

The wait-for graph is rebuilt from current waiting requests and current allocations. An edge `P1 -> P2` means P1 is waiting for an instance held by P2. A directed cycle is a deadlock; ordinary waiting is not. Multi-instance resources are supported by counting all current holders.

Recovery plans are generated before selection and simulated on independent state copies. Supported actions are `TERMINATE`, `TERMINATE_ALL`, `ROLLBACK`, and `PREEMPT`. Combined plans are applied and validated sequentially. Invalid actions, invalid checkpoints, conflicting actions, and accounting failures are rejected without changing the live state. Candidate generation is capped at 4096 by default.

Preemption has explicit modes: `PRESERVE_PROGRESS` (default) and `RESTART`. The restart mode resets progress and records the discarded completed work.

## Normalized decision cost

The cost is a configurable simulated recovery cost, not monetary cost and not an operating-system fact. Each component is normalized to approximately `[0, 1]` before weighting:

`lostWorkCost = sum(workLost) / sum(totalWork of affected processes)`

`priorityImpact = sum(workLost * priorityMultiplier) / (3 * sum(affected total work))`

`terminationCost = terminated cycle processes / cycle size`

`rollbackCost = rollback actions / cycle size`

`starvationCost = max(previous victim count / starvation threshold)`

`resourceBenefit = released instances / total resource instances`

`cost = wLost * lostWorkCost + wPriority * priorityImpact + wTermination * terminationCost + wRollback * rollbackCost + wStarvation * starvationCost + wUnresolved * unresolved - wResource * resourceBenefit`

An unresolved deadlock has `unresolved = 1` and the default unresolved weight is deliberately dominant. The default weight object also contains the priority multipliers, starvation threshold, preemption mode, and combination limit; its `toString()` output is shown with recovery metrics.

Named cost profiles are available for balanced, work-preservation, process-preservation, priority-sensitive, and fairness-sensitive experiments. Their numeric values are simulator assumptions and should be justified before being treated as final project parameters.

## Adaptive selection

The hypothetical evaluator chooses current-state outcomes and predicted costs. The adaptive selector separately remembers attempts, successes, and actual costs by discretized recovery context and strategy. Context includes cycle size, process/resource counts, waiting count, preemptible-resource count, and utilization level (`LOW`, `MEDIUM`, or `HIGH`). Invalid plans are never learned.

With no usable history, unseen strategies, or learner failure, selection falls back deterministically to the lowest executable predicted cost, then lower work loss, fewer terminations, and action text ordering. After execution, actual results update metrics, victim history, prediction errors, and adaptive history.

## Experiments and assumptions

`RecoveryExperimentRunner` creates one deterministic workload per iteration from `seed + iteration`; each policy receives an equivalent freshly copied workload generated from the same seed. Reports include recovery count, success rate, simulated cost, work loss, terminated processes, released instances, utilization, victim concentration, starvation, and prediction error. The CLI prints the seed and active cost configuration when metrics are requested; interactive `HISTORY` displays detection, candidate, selection, and actual-recovery events.

The simulator's cost weights, priority multipliers, preemption mode, workload distribution, and tie-break rules are configurable model assumptions. They should be justified as project parameters, not presented as universal operating-system behavior.

Run the project without a build system with:

```text
javac -d out src/deadlock/**/*.java test/deadlock/**/*.java
java -cp out deadlock.SimulatorCLI --scenario EXPERIMENT
```

On PowerShell, compiling all sources is more reliable with:

```powershell
$sources = Get-ChildItem src,test -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $sources
```
