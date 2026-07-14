/**
 * Domain events emitted during scenario execution, task lifecycle,
 * assertion results, and orchestrator-to-agent signalling.
 *
 * <h2>Event Categories</h2>
 *
 * <table>
 *   <caption>Sealed interfaces and their permitted records</caption>
 *   <tr><th>Sealed Interface</th><th>Purpose</th><th>Scope</th><th>Records</th></tr>
 *   <tr>
 *     <td>{@link ExecutionEvent}</td>
 *     <td>Scenario execution lifecycle</td>
 *     <td>Orchestrator-local</td>
 *     <td>7 records</td>
 *   </tr>
 *   <tr>
 *     <td>{@link TaskEvent}</td>
 *     <td>Task lifecycle (dispatch, start, complete, fail, retry)</td>
 *     <td>Orchestrator + Agent</td>
 *     <td>7 records</td>
 *   </tr>
 *   <tr>
 *     <td>{@link AssertionEvent}</td>
 *     <td>Assertion results (pass / fail)</td>
 *     <td>Orchestrator + Agent</td>
 *     <td>2 records</td>
 *   </tr>
 *   <tr>
 *     <td>{@link AgentSignal}</td>
 *     <td>Orchestrator-to-agent signals (lifecycle, restart)</td>
 *     <td>Orchestrator -&gt; Agent (transport)</td>
 *     <td>2 records</td>
 *   </tr>
 * </table>
 *
 * <h3>ExecutionEvent (7 records)</h3>
 * <ul>
 *   <li>{@link ScenarioStarted} — a scenario execution begins</li>
 *   <li>{@link ScenarioFinished} — a scenario execution completes normally</li>
 *   <li>{@link ScenarioCancelled} — a scenario execution is cancelled</li>
 *   <li>{@link PhaseStarted} — a DAG phase starts</li>
 *   <li>{@link PhaseCompleted} — a DAG phase completes</li>
 *   <li>{@link ReportGenerated} — a report is generated</li>
 *   <li>{@link ReportPublished} — a report is published</li>
 * </ul>
 * <p>All implement {@code ExecutionEvent} and carry {@code executionId()} + {@code timestamp()}.</p>
 *
 * <h3>TaskEvent (7 records)</h3>
 * <ul>
 *   <li>{@link TaskDispatched} — task sent to an agent</li>
 *   <li>{@link TaskClaimedByAgent} — agent acknowledges the task</li>
 *   <li>{@link TaskStarted} — task execution begins</li>
 *   <li>{@link TaskCompleted} — task execution succeeds</li>
 *   <li>{@link TaskFailed} — task execution fails</li>
 *   <li>{@link TaskRetried} — task is retried after failure</li>
 *   <li>{@link TaskWorkInProgress} — agent reports progress</li>
 * </ul>
 * <p>All implement {@code TaskEvent} and carry {@code executionId()} + {@code taskId()} + {@code timestamp()}.</p>
 *
 * <h3>AssertionEvent (2 records)</h3>
 * <ul>
 *   <li>{@link AssertionPassed} — assertion check passes</li>
 *   <li>{@link AssertionFailed} — assertion check fails</li>
 * </ul>
 * <p>Both implement {@code AssertionEvent} and carry {@code executionId()} + {@code assertionId()} + {@code timestamp()}.</p>
 *
 * <h3>AgentSignal (2 records)</h3>
 * <ul>
 *   <li>{@link ExecutionLifecycleSignal} — START/STOP signal for a task window</li>
 *   <li>{@link ScenarioRestartSignal} — restart one or all scenarios</li>
 * </ul>
 * <p>Both implement {@code AgentSignal} and carry {@code id()} + {@code issuedAt()}.</p>
 * <p>Agent signals are dispatched through the {@code ExecutionTransport} layer to target agents.</p>
 *
 * <h2>Standalone Records</h2>
 *
 * <p>Three records represent agent lifecycle state changes and do not currently implement
 * a sealed event interface:</p>
 * <ul>
 *   <li>{@link AgentRegistered} — agent registers with the orchestrator</li>
 *   <li>{@link AgentLost} — agent heartbeat expires</li>
 *   <li>{@link AgentRecovered} — previously lost agent reconnects</li>
 * </ul>
 *
 * <h2>Helper Types</h2>
 * <ul>
 *   <li>{@link LifecycleAction} — enum: {@code START} or {@code STOP}</li>
 * </ul>
 *
 * <h2>Design Rules</h2>
 *
 * <ul>
 *   <li>Event records implement exactly one sealed interface (or none, for standalone lifecycle records).</li>
 *   <li>The {@code permits} clause of each sealed interface must list every implementing record — enforced at compile time by {@code sealed}.</li>
 *   <li>Every type in this package (except {@link LifecycleAction}) is an immutable Java record.</li>
 *   <li>0 Spring, 0 JPA, 0 Jackson annotations — enforced by {@code DomainArchitectureTest}.</li>
 *   <li>Hierarchy coherence is verified by {@code EventHierarchyArchTest}.</li>
 * </ul>
 *
 * @see ExecutionEvent
 * @see TaskEvent
 * @see AssertionEvent
 * @see AgentSignal
 * @see com.performance.platform.domain.arch.DomainArchitectureTest
 */
package com.performance.platform.domain.event;
