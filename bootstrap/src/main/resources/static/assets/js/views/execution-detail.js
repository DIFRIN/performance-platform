/**
 * Execution detail view.
 * PDR-029 — ISSUE-128
 *
 * Mounts the detail view for a single execution:
 * - Polling every 3 seconds (setInterval / clearInterval); stops once the execution is
 *   terminal, DOM is only rebuilt when data changes and expanded rows survive refreshes
 * - Progress bar fed by server-computed {@code progress {total, ok, ko, running}}
 * - Tasks grouped by phase with visual status (ok/ko/running)
 * - errorMessage displayed for KO tasks
 * - Summary view by default; full detail (errorMessage long, timings) via row expansion
 * - Report navigation stays disabled until execution is terminal
 * - Rerun (terminal only) starts a new execution from the retained YAML
 * - Abort (active only) cancels the execution
 * - Back link to /#/executions; report route /#/executions/{id}/report
 *
 * Public API:
 *   mount(el, executionId) → { cleanup: Function }
 *
 * The cleanup function clears the polling interval so there is no leak
 * when the router switches to another view.
 */

import { getExecutionStatus, listExecutionTasks, getExecutionScenario, cancelExecution, rerunExecution, scheduleScenario } from "/assets/js/api.js";
import {
    toast,
    announce,
    confirmDestructive,
    copyableIdHtml,
    copyText,
    handleCopyClick,
    createPoller,
    refreshStatusText,
    formatDuration,
    formatDateTime,
    shortId
} from "/assets/js/ui.js";
import {
    enhanceScheduleControls,
    isScheduled,
    readSchedule,
    scheduleConfirmation,
    validateScheduleInput,
    scheduleFieldHtml
} from "/assets/js/schedule.js";

/** Polling interval in milliseconds. */
const POLL_INTERVAL_MS = 3000;

/** Statuses considered "running" for visual treatment. */
const RUNNING_STATUSES = new Set(["STARTED", "RUNNING"]);

/** Execution statuses after which polling stops (nothing will change anymore). */
const TERMINAL_STATUSES = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

/**
 * Mount the execution detail view inside the given element.
 *
 * @param {HTMLElement} el — the container element for this view
 * @param {string} executionId — the execution identifier from the route
 * @returns {{cleanup: Function}}
 */
export function mount(el, executionId) {
    /* ---- State ---- */
    let poller = null;
    let detail = null;       // ExecutionStatusResponse from getExecutionStatus
    let tasksData = null;    // TaskListResponse from listExecutionTasks
    let error = null;
    let lastSnapshot = null; // serialized last rendered state — skip DOM rebuild when unchanged
    let inFlight = false;
    let disposed = false;
    let lastStatus = null;
    const expandedTaskIds = new Set(); // survives re-renders so polling never collapses open rows

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton(executionId);
    enhanceScheduleControls(el);

    const bodyEl = el.querySelector("#exec-detail-body");
    const backLink = el.querySelector("[data-exec-back]");
    const reportLink = el.querySelector("[data-exec-report]");
    const rerunBtn = el.querySelector("[data-exec-rerun]");
    const scheduleInput = el.querySelector("[data-schedule-input]");
    const abortBtn = el.querySelector("[data-exec-abort]");
    const scenarioPanel = el.querySelector("[data-scenario-source]");
    const scenarioCode = el.querySelector("[data-scenario-code]");
    const scenarioCopy = el.querySelector("[data-scenario-copy]");
    const scenarioDownload = el.querySelector("[data-scenario-download]");
    const refreshStateEl = el.querySelector("[data-refresh-state]");
    let sourceLoaded = false;
    let sourceLoading = false;
    let sourceYaml = null;
    let actionInFlight = false;

    scenarioPanel.addEventListener("toggle", async function () {
        if (!scenarioPanel.open || sourceLoaded || sourceLoading) return;
        sourceLoading = true;
        scenarioCode.textContent = "Loading...";
        try {
            const source = await getExecutionScenario(executionId);
            if (disposed) return;
            sourceYaml = source.yaml;
            scenarioCode.textContent = source.yaml == null
                ? "Original YAML is unavailable for this execution."
                : source.yaml;
            sourceLoaded = source.yaml != null;
            if (scenarioCopy) scenarioCopy.disabled = !sourceLoaded;
            if (scenarioDownload) scenarioDownload.disabled = !sourceLoaded;
        } catch (e) {
            console.error("execution-detail: scenario source fetch failed", e);
            if (!disposed) scenarioCode.textContent = "Failed to load YAML: " + e.message +
                ". Close and reopen this panel to retry.";
        } finally {
            sourceLoading = false;
        }
    });

    if (scenarioCopy) {
        scenarioCopy.addEventListener("click", async function () {
            if (!sourceYaml) return;
            const ok = await copyText(sourceYaml);
            toast(ok ? "Scenario YAML copied" : "Copy failed", { variant: ok ? "success" : "error" });
        });
    }

    if (scenarioDownload && scenarioDownload.tagName === "BUTTON") {
        scenarioDownload.addEventListener("click", function () {
            downloadSourceYaml();
        });
    } else if (scenarioDownload) {
        scenarioDownload.addEventListener("click", async function (e) {
            e.preventDefault();
            if (!sourceLoaded) {
                try {
                    const source = await getExecutionScenario(executionId);
                    sourceYaml = source.yaml;
                } catch (err) {
                    toast("Download failed: " + err.message, { variant: "error" });
                    return;
                }
            }
            downloadSourceYaml();
        });
    }

    function downloadSourceYaml() {
        if (!sourceYaml) {
            toast("Scenario YAML is unavailable", { variant: "warning" });
            return;
        }
        const blob = new Blob([sourceYaml], { type: "text/yaml" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "scenario-" + shortId(executionId) + ".yaml";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    /* ---- Event handlers ---- */

    // Back link
    if (backLink) {
        backLink.addEventListener("click", function (e) {
            e.preventDefault();
            window.location.hash = "#/executions";
        });
    }

    // Report link
    if (reportLink) {
        reportLink.addEventListener("click", function (e) {
            e.preventDefault();
            if (reportLink.disabled) return;
            window.location.hash = "#/executions/" + encodeURIComponent(executionId) + "/report";
        });
    }

    // Rerun this execution from its retained source YAML
    if (rerunBtn) {
        rerunBtn.addEventListener("click", async function () {
            if (rerunBtn.disabled || actionInFlight) return;
            actionInFlight = true;
            rerunBtn.disabled = true;
            rerunBtn.textContent = "Starting...";
            try {
                const schedule = readSchedule(scheduleInput);
                let result;
                if (isScheduled(schedule)) {
                    const check = validateScheduleInput(scheduleInput);
                    if (!check.valid) return;
                    const source = await getExecutionScenario(executionId);
                    if (!source || !source.yaml) {
                        throw new Error("The original scenario YAML is unavailable");
                    }
                    result = await scheduleScenario(source.yaml, schedule);
                    if (!result.executionId) {
                        toast(scheduleConfirmation(result.nextRunAt), { variant: "success" });
                        return;
                    }
                } else {
                    result = await rerunExecution(executionId);
                }
                window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
            } catch (e) {
                toast("Rerun failed: " + (e.message || "Unknown error"), { variant: "error" });
            } finally {
                actionInFlight = false;
                if (!disposed) {
                    rerunBtn.textContent = "Rerun";
                    rerunBtn.disabled = false;
                }
            }
        });
    }

    // Abort (cancel) an active execution
    if (abortBtn) {
        abortBtn.addEventListener("click", async function () {
            if (abortBtn.disabled || actionInFlight) return;
            if (!confirmDestructive("Abort execution " + executionId + "? This cannot be undone.")) return;
            actionInFlight = true;
            abortBtn.disabled = true;
            abortBtn.textContent = "Aborting...";
            try {
                await cancelExecution(executionId);
                toast("Execution abort requested", { variant: "success" });
                announce("Execution abort requested");
            } catch (e) {
                toast("Abort failed: " + (e.message || "Unknown error"), { variant: "error" });
            } finally {
                actionInFlight = false;
                if (!disposed) {
                    abortBtn.textContent = "Abort";
                }
                fetchAndRender();
            }
        });
    }

    // Delegated click handler on body for row expansion
    bodyEl.addEventListener("click", function (e) {
        if (handleCopyClick(e)) return;

        var toggleBtn = e.target.closest("[data-task-toggle]");
        if (toggleBtn) {
            e.stopPropagation();
            var taskId = toggleBtn.dataset.taskToggle;
            toggleTaskDetail(bodyEl, taskId, expandedTaskIds);
            return;
        }

        // Don't collapse the row when the user is selecting text inside it
        if (window.getSelection && String(window.getSelection()).length > 0) {
            return;
        }

        // Click on row header toggles expansion too
        var rowHeader = e.target.closest("[data-task-row]");
        if (rowHeader) {
            var tid = rowHeader.dataset.taskRow;
            toggleTaskDetail(bodyEl, tid, expandedTaskIds);
        }
    });

    /* ---- Fetch & Render loop ---- */

    function stopPolling() {
        if (poller) poller.pause();
    }

    /**
     * Enable/disable Rerun and Abort according to the current execution status.
     * @param {Object|null} current
     */
    function updateActionButtons(current) {
        const terminal = !!current && TERMINAL_STATUSES.has(current.status);
        const active = !!current && RUNNING_STATUSES.has(current.status);

        if (rerunBtn) {
            rerunBtn.disabled = actionInFlight || !terminal;
            rerunBtn.title = terminal
                ? "Start a new execution from the same scenario"
                : "Available when execution has finished";
        }
        if (abortBtn) {
            abortBtn.disabled = actionInFlight || !active;
            abortBtn.title = active
                ? "Cancel this execution"
                : "Available while execution is running";
        }
    }

    async function fetchAndRender() {
        if (inFlight || disposed) return;
        inFlight = true;
        try {
            try {
                detail = await getExecutionStatus(executionId);
                tasksData = await listExecutionTasks(executionId);
                error = null;
            } catch (e) {
                console.error("execution-detail: fetch failed", e);
                error = e.message || "Failed to load execution detail";
            }
            if (disposed) return;

            reportLink.disabled = !detail || !TERMINAL_STATUSES.has(detail.status);
            reportLink.title = reportLink.disabled
                ? "Available when execution has finished"
                : "View execution report";
            updateActionButtons(detail);

            if (detail && detail.status !== lastStatus) {
                if (lastStatus !== null) {
                    announce("Execution is now " + detail.status);
                }
                lastStatus = detail.status;
            }

            var snapshot = JSON.stringify([detail, tasksData, error]);
            if (snapshot !== lastSnapshot) {
                lastSnapshot = snapshot;
                renderBody(bodyEl, detail, tasksData, error);
                restoreExpandedRows(bodyEl, expandedTaskIds, tasksData ? tasksData.tasks : []);
            }

            if (!error && detail && TERMINAL_STATUSES.has(detail.status)) {
                stopPolling();
            }
        } finally {
            inFlight = false;
        }
    }

    poller = createPoller({
        run: fetchAndRender,
        intervalMs: POLL_INTERVAL_MS,
        onState: function (next) {
            if (refreshStateEl) refreshStateEl.textContent = refreshStatusText(next);
        }
    });
    poller.start();

    /* ---- Cleanup ---- */
    return {
        cleanup: function () {
            disposed = true;
            if (poller) poller.stop();
        }
    };
}

// ---------------------------------------------------------------------------
// Internal helpers — skeleton
// ---------------------------------------------------------------------------

/**
 * Build the static skeleton HTML.
 * @param {string} executionId
 * @returns {string}
 */
function buildSkeleton(executionId) {
    return '' +
        '<div class="detail-card">' +
        '  <div class="detail-card__header">' +
        '    <div class="detail-header__left">' +
        '      <a href="#/executions" class="detail-back-link" data-exec-back>&larr; Back to Executions</a>' +
        '      <h1 class="detail-header__title" data-page-heading>Execution ' +
        '        ' + copyableIdHtml(executionId, { label: executionId, title: "Copy execution ID" }) +
        '      </h1>' +
        '      <span class="refresh-state" data-refresh-state aria-live="polite">Waiting for data\u2026</span>' +
        '    </div>' +
        '    <div class="detail-header__right">' +
        '      ' + scheduleFieldHtml("exec-detail", { compact: true, label: "Schedule rerun" }) +
        '      <button type="button" class="btn" data-exec-rerun disabled title="Available when execution has finished">Rerun</button>' +
        '      <button type="button" class="btn btn--warning" data-exec-abort disabled title="Available while execution is running">Abort</button>' +
        '      <button type="button" class="btn btn--primary" data-exec-report disabled title="Available when execution has finished">View Report</button>' +
        '    </div>' +
        '  </div>' +
        '  <div id="exec-detail-body">' +
        '    <div class="detail-loading">Loading...</div>' +
        '  </div>' +
        '  <details class="card detail-scenario-source" data-scenario-source>' +
        '    <summary class="card__title">Scenario YAML (original source)</summary>' +
        '    <div class="code-toolbar">' +
        '      <button type="button" class="btn btn--sm" data-scenario-copy disabled>Copy</button>' +
        '      <button type="button" class="btn btn--sm" data-scenario-download disabled>Download .yaml</button>' +
        '    </div>' +
        '    <pre class="detail-technical-value"><code data-scenario-code></code></pre>' +
        '  </details>' +
        '</div>';
}

// ---------------------------------------------------------------------------
// Internal helpers — body rendering
// ---------------------------------------------------------------------------

/**
 * Render the body area: info, progress bar, phase sections, tasks table, or error.
 * @param {HTMLElement} bodyEl — the #exec-detail-body div
 * @param {Object|null} detail — ExecutionStatusResponse from API
 * @param {Object|null} tasksData — TaskListResponse from API
 * @param {string|null} error — fetch error message
 */
function renderBody(bodyEl, detail, tasksData, error) {
    if (error && !detail) {
        bodyEl.innerHTML = '' +
            '<div class="empty-state">' +
            '  <div class="empty-state__text exec-error">' + escapeHtml(error) + '</div>' +
            '</div>';
        return;
    }

    if (!detail) {
        bodyEl.innerHTML = '<div class="detail-loading">Loading...</div>';
        return;
    }

    var errorBanner = error
        ? '<div class="exec-error-banner">' + escapeHtml(error) + '</div>'
        : '';

    var infoHtml = renderInfoSection(detail);
    var progressHtml = renderDetailProgress(detail.progress);
    var timelineHtml = renderTimeline(detail, tasksData);
    var phasesHtml = renderPhaseSections(tasksData);

    bodyEl.innerHTML = '' +
        errorBanner +
        infoHtml +
        progressHtml +
        timelineHtml +
        phasesHtml;
}

/**
 * Render the execution info section (scenario ID, status, timestamps, phase statuses).
 * @param {Object} detail
 * @returns {string} HTML
 */
function renderInfoSection(detail) {
    var statusClass = statusBadgeClass(detail.status);
    var started = formatTimestamp(detail.startedAt);
    var updated = formatTimestamp(detail.updatedAt);

    return '' +
        '<div class="card mb-2">' +
        '  <div class="detail-info-grid">' +
        '    <div class="detail-info-label">Scenario ID</div>' +
        '    <div class="detail-info-value">' + scenarioLink(detail.scenarioId) + '</div>' +
        '    <div class="detail-info-label">Status</div>' +
        '    <div class="detail-info-value"><span class="status ' + statusClass + '">' + escapeHtml(detail.status || "UNKNOWN") + '</span></div>' +
        '    <div class="detail-info-label">Started</div>' +
        '    <div class="detail-info-value">' + escapeHtml(started) + '</div>' +
        '    <div class="detail-info-label">Updated</div>' +
        '    <div class="detail-info-value">' + escapeHtml(updated) + '</div>' +
        '  </div>' +
        '  ' + renderPhaseStatusBadges(detail.phaseStatuses) +
        '</div>';
}

/**
 * Render the scenario id as a link to the catalog view filtered on it.
 * @param {string|null} scenarioId
 * @returns {string} HTML
 */
function scenarioLink(scenarioId) {
    if (!scenarioId) {
        return '<span class="text-muted">—</span>';
    }
    return '<a class="scenario-link text-mono" href="#/scenarios/' +
        encodeURIComponent(scenarioId) + '" title="Open this scenario in the catalog">' +
        escapeHtml(scenarioId) + '</a>';
}

/**
 * Render phase status badges below the info grid.
 * @param {Object<string,string>|null} phaseStatuses — e.g. {"PREPARATION": "COMPLETED", "INJECTION": "RUNNING"}
 * @returns {string} HTML
 */
function renderPhaseStatusBadges(phaseStatuses) {
    if (!phaseStatuses || Object.keys(phaseStatuses).length === 0) {
        return '';
    }

    var badges = Object.entries(phaseStatuses).map(function (entry) {
        var phase = entry[0];
        var status = entry[1];
        var cls = phaseStatusBadgeClass(status);
        return '<span class="status ' + cls + ' phase-badge">' + escapeHtml(phase) + ': ' + escapeHtml(status) + '</span>';
    }).join(" ");

    return '<div class="detail-phase-badges">' + badges + '</div>';
}

/**
 * Render the full-width progress bar for the detail view.
 * @param {{total: number, ok: number, ko: number, running: number}|null} progress
 * @returns {string} HTML
 */
function renderDetailProgress(progress) {
    if (!progress) return '';

    var total = progress.total || 0;
    if (total === 0) return '';

    var ok = progress.ok || 0;
    var ko = progress.ko || 0;
    var running = progress.running || 0;
    var pending = total - ok - ko - running;
    if (pending < 0) pending = 0;

    var okPct = (ok / total * 100).toFixed(1);
    var koPct = (ko / total * 100).toFixed(1);
    var runningPct = (running / total * 100).toFixed(1);
    var pendingPct = (pending / total * 100).toFixed(1);

    var segments = [];
    if (ok > 0) {
        segments.push('<div class="detail-progress__seg detail-progress__seg--ok"' +
            ' style="width:' + okPct + '%" title="' + ok + ' ok"></div>');
    }
    if (ko > 0) {
        segments.push('<div class="detail-progress__seg detail-progress__seg--ko"' +
            ' style="width:' + koPct + '%" title="' + ko + ' ko"></div>');
    }
    if (running > 0) {
        segments.push('<div class="detail-progress__seg detail-progress__seg--running"' +
            ' style="width:' + runningPct + '%" title="' + running + ' running"></div>');
    }
    if (pending > 0) {
        segments.push('<div class="detail-progress__seg detail-progress__seg--pending"' +
            ' style="width:' + pendingPct + '%" title="' + pending + ' pending"></div>');
    }

    var pct = total > 0 ? Math.round((ok + ko) / total * 100) : 0;
    var counts = ok + '/' + total + ' ok';
    if (ko > 0) counts += ', ' + ko + ' ko';
    if (running > 0) counts += ', ' + running + ' running';

    return '' +
        '<div class="card mb-2">' +
        '  <div class="detail-progress">' +
        '    <div class="flex-between mb-1">' +
        '      <span class="detail-progress__label">Progress</span>' +
        '      <span class="detail-progress__pct">' + pct + '%</span>' +
        '    </div>' +
        '    <div class="detail-progress__bar">' +
        segments.join("") +
        '    </div>' +
        '    <div class="detail-progress__counts">' + escapeHtml(counts) + '</div>' +
        '  </div>' +
        '</div>';
}

// ---------------------------------------------------------------------------
// Internal helpers — task timeline
// ---------------------------------------------------------------------------

/**
 * Render a horizontal timeline of tasks with their durations.
 * Task start is derived from completion time minus duration; the longest task
 * is highlighted as the dominant (critical-path) task.
 *
 * @param {Object} detail — ExecutionStatusResponse
 * @param {Object|null} tasksData — TaskListResponse
 * @returns {string} HTML
 */
function renderTimeline(detail, tasksData) {
    var tasks = tasksData && tasksData.tasks ? tasksData.tasks : [];
    if (tasks.length === 0) return '';

    var items = [];
    tasks.forEach(function (task) {
        if (task.durationMillis === null || task.durationMillis === undefined) return;
        var duration = Number(task.durationMillis);
        var end = task.completedAt ? new Date(task.completedAt).getTime() : null;
        if (end === null || isNaN(end)) return;
        items.push({
            task: task,
            start: end - duration,
            end: end,
            duration: duration
        });
    });

    if (items.length === 0) return '';

    items.sort(function (a, b) { return a.start - b.start; });

    var rangeStart = Math.min.apply(null, items.map(function (i) { return i.start; }));
    var rangeEnd = Math.max.apply(null, items.map(function (i) { return i.end; }));
    var span = Math.max(1, rangeEnd - rangeStart);

    var slowestId = items.reduce(function (best, item) {
        return item.duration > best.duration ? item : best;
    }, items[0]);

    var rows = items.map(function (item) {
        var left = (item.start - rangeStart) / span * 100;
        var width = Math.max(0.8, item.duration / span * 100);
        var kind = taskBarKind(item.task.status);
        var critical = item.task === slowestId.task ? " timeline__bar--critical" : "";
        return '<div class="timeline__row">' +
            '<span class="timeline__label" title="' + escapeAttr(item.task.taskId) + '">' +
                escapeHtml(item.task.taskName || item.task.taskId) +
                '<span class="timeline__phase">' + escapeHtml(item.task.phase || "") + '</span>' +
            '</span>' +
            '<span class="timeline__track">' +
                '<span class="timeline__bar timeline__bar--' + kind + critical + '"' +
                ' style="left:' + left.toFixed(2) + '%;width:' + width.toFixed(2) + '%"' +
                ' title="' + escapeAttr(item.task.taskName + " \u00b7 " + formatDuration(item.duration)) + '"></span>' +
            '</span>' +
            '<span class="timeline__value">' + escapeHtml(formatDuration(item.duration)) + '</span>' +
            '</div>';
    }).join("");

    return '<div class="card mb-2">' +
        '<div class="flex-between mb-1">' +
        '  <span class="card__title">Timeline</span>' +
        '  <span class="text-muted">Longest task: ' +
             escapeHtml(slowestId.task.taskName || slowestId.task.taskId) + ' (' +
             escapeHtml(formatDuration(slowestId.duration)) + ')</span>' +
        '</div>' +
        '<div class="timeline">' + rows + '</div>' +
        '<p class="timeline__hint text-muted">Bars are positioned from task completion time and duration.</p>' +
        '</div>';
}

function taskBarKind(status) {
    switch (status) {
        case "SUCCESS": return "ok";
        case "FAILED":
        case "TIMEOUT":
        case "SKIPPED": return "ko";
        case "RUNNING":
        case "STARTED": return "running";
        default: return "pending";
    }
}

// ---------------------------------------------------------------------------
// Internal helpers — tasks table grouped by phase
// ---------------------------------------------------------------------------

/**
 * Render tasks grouped by phase.
 * Tasks with null/undefined phase are grouped under "Tasks".
 * @param {Object|null} tasksData — TaskListResponse from API
 * @returns {string} HTML
 */
function renderPhaseSections(tasksData) {
    if (!tasksData || !tasksData.tasks || tasksData.tasks.length === 0) {
        return '' +
            '<div class="card">' +
            '  <div class="card__title">Tasks</div>' +
            '  <p class="text-muted">No tasks recorded yet.</p>' +
            '</div>';
    }

    // Group tasks by phase (null → "Tasks")
    var groups = {};
    tasksData.tasks.forEach(function (t) {
        var phase = t.phase || "Tasks";
        if (!groups[phase]) groups[phase] = [];
        groups[phase].push(t);
    });

    var total = tasksData.total || tasksData.tasks.length;

    var html = '<div class="card">' +
        '<div class="card__title">Tasks (' + total + ')</div>';

    Object.keys(groups).sort().forEach(function (phase) {
        html += renderPhaseGroup(phase, groups[phase]);
    });

    html += '</div>';
    return html;
}

/**
 * Render a single phase group with a sub-header and tasks table.
 * @param {string} phase — phase name
 * @param {Array} tasks — list of TaskSummaryResponse
 * @returns {string} HTML
 */
function renderPhaseGroup(phase, tasks) {
    var okCount = tasks.filter(function (t) { return t.status === "SUCCESS"; }).length;
    var koCount = tasks.filter(function (t) {
        return t.status === "FAILED" || t.status === "TIMEOUT" || t.status === "SKIPPED";
    }).length;
    var runningCount = tasks.filter(function (t) {
        return t.status === "RUNNING" || t.status === "STARTED";
    }).length;
    var pendingCount = tasks.filter(function (t) { return t.status === "PENDING"; }).length;

    return '' +
        '<div class="detail-phase">' +
        '  <div class="detail-phase__header">' +
        '    <span class="detail-phase__name">' + escapeHtml(phase) + '</span>' +
        '    <span class="detail-phase__stats">' +
        '      <span class="detail-phase__stat detail-phase__stat--ok">' + okCount + ' ok</span>' +
        '      <span class="detail-phase__stat detail-phase__stat--ko">' + koCount + ' ko</span>' +
        '      <span class="detail-phase__stat detail-phase__stat--running">' + runningCount + ' running</span>' +
        '      <span class="detail-phase__stat">' + pendingCount + ' pending</span>' +
        '    </span>' +
        '  </div>' +
        renderTasksTable(tasks) +
        '</div>';
}

/**
 * Render the tasks table for a phase.
 * @param {Array} tasks
 * @returns {string} HTML
 */
function renderTasksTable(tasks) {
    var rows = tasks.map(renderTaskRow).join("");

    return '' +
        '<table class="table detail-tasks-table">' +
        '  <thead>' +
        '    <tr>' +
        '      <th class="detail-tasks-table__expand"></th>' +
        '      <th>Task Name</th>' +
        '      <th>Task Type</th>' +
        '      <th>Status</th>' +
        '      <th>Duration</th>' +
        '      <th>Message</th>' +
        '    </tr>' +
        '  </thead>' +
        '  <tbody>' +
        rows +
        '  </tbody>' +
        '</table>';
}

/**
 * Render a single task row (summary line + hidden detail).
 * @param {Object} task — TaskSummaryResponse
 * @returns {string} HTML
 */
function renderTaskRow(task) {
    var rowKey = taskRowKey(task);
    var statusClass = taskStatusBadgeClass(task.status);
    var shortMessage = task.errorMessage
        ? truncate(task.errorMessage, 80)
        : (task.status === "SUCCESS" ? "OK" : "—");

    return '' +
        '<tr class="detail-task-row" data-task-row="' + escapeAttr(rowKey) + '">' +
        '  <td class="detail-tasks-table__expand">' +
        '    <button class="btn btn--sm detail-task-toggle" data-task-toggle="' + escapeAttr(rowKey) + '" title="Show details" aria-expanded="false">' +
        '      <span class="detail-task-toggle__icon">+</span>' +
        '    </button>' +
        '  </td>' +
        '  <td><span class="text-mono detail-task-name">' + escapeHtml(task.taskId) + '</span></td>' +
        '  <td>' + escapeHtml(task.taskName) + '</td>' +
        '  <td><span class="status ' + statusClass + '">' + escapeHtml(task.status) + '</span></td>' +
        '  <td class="text-mono">' + escapeHtml(formatDuration(task.durationMillis)) + '</td>' +
        '  <td class="detail-task-message">' + escapeHtml(shortMessage) + '</td>' +
        '</tr>' +
        '<tr class="detail-task-expanded" id="task-detail-' + escapeAttr(rowKey) + '" style="display:none">' +
        '  <td></td>' +
        '  <td colspan="5">' +
        '    <div class="detail-task-expanded__content">' +
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Task Name</span>' +
        '        <span class="text-mono">' + escapeHtml(task.taskId) + '</span>' +
        '      </div>' +
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Task Type</span>' +
        '        <span>' + escapeHtml(task.taskName) + '</span>' +
        '      </div>' +
        (task.phase ?
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Phase</span>' +
        '        <span>' + escapeHtml(task.phase) + '</span>' +
        '      </div>' : '') +
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Status</span>' +
        '        <span class="status ' + statusClass + '">' + escapeHtml(task.status) + '</span>' +
        '      </div>' +
        renderTechnicalField("Agent", task.agentId) +
        renderTechnicalField("Duration (ms)", task.durationMillis) +
        renderTechnicalField("Completed at", task.completedAt ? formatTimestamp(task.completedAt) : null) +
        renderTechnicalSection("Step configuration", task.configuration) +
        renderTechnicalSection("Execution outputs / injection metrics", task.outputs) +
        (task.errorMessage ?
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Error Message</span>' +
        '        <pre class="detail-task-expanded__error">' + escapeHtml(task.errorMessage) + '</pre>' +
        '      </div>' : '') +
        '    </div>' +
        '  </td>' +
        '</tr>';
}

function renderTechnicalField(label, value) {
    if (value === null || value === undefined) return "";
    var text = typeof value === "object" ? JSON.stringify(value, null, 2) : String(value);
    return '<div class="detail-task-expanded__field">' +
        '<span class="detail-task-expanded__label">' + escapeHtml(label) + '</span>' +
        '<pre class="detail-technical-value">' + escapeHtml(text) + '</pre></div>';
}

function renderTechnicalSection(title, values) {
    if (!values || Object.keys(values).length === 0) return "";
    return '<section class="detail-technical-section"><h3>' + escapeHtml(title) + '</h3>' +
        Object.entries(values).map(function ([key, value]) {
            return renderTechnicalField(key, value);
        }).join("") + '</section>';
}

function taskRowKey(task) {
    return JSON.stringify([task.taskId, task.agentId || null]);
}

/**
 * Toggle expansion of a task detail row and remember the choice.
 * @param {HTMLElement} bodyEl — the detail body element
 * @param {string} taskId
 * @param {Set<string>} expandedTaskIds — persistent expansion state
 */
function toggleTaskDetail(bodyEl, taskId, expandedTaskIds) {
    var expand = !expandedTaskIds.has(taskId);
    if (expand) {
        expandedTaskIds.add(taskId);
    } else {
        expandedTaskIds.delete(taskId);
    }
    setTaskExpanded(bodyEl, taskId, expand);
}

/**
 * Re-apply expansion state after the body has been re-rendered.
 * @param {HTMLElement} bodyEl
 * @param {Set<string>} expandedTaskIds
 */
function restoreExpandedRows(bodyEl, expandedTaskIds, tasks) {
    const assignedTasks = new Set();
    (tasks || []).forEach(function (task) {
        const plannedKey = taskRowKey({ taskId: task.taskId });
        if (task.agentId && expandedTaskIds.has(plannedKey)) {
            expandedTaskIds.add(taskRowKey(task));
            assignedTasks.add(plannedKey);
        }
    });
    assignedTasks.forEach(function (key) { expandedTaskIds.delete(key); });
    expandedTaskIds.forEach(function (taskId) {
        setTaskExpanded(bodyEl, taskId, true);
    });
}

function cssEscape(value) {
    if (window.CSS && typeof window.CSS.escape === "function") return window.CSS.escape(value);
    return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
}

/**
 * @param {HTMLElement} bodyEl
 * @param {string} taskId
 * @param {boolean} expanded
 */
function setTaskExpanded(bodyEl, taskId, expanded) {
    var detailRow = bodyEl.querySelector("#task-detail-" + cssEscape(taskId));
    var toggleBtn = bodyEl.querySelector("[data-task-toggle=\"" + cssEscape(taskId) + "\"]");
    if (!detailRow) return;

    detailRow.style.display = expanded ? "" : "none";
    if (toggleBtn) {
        toggleBtn.setAttribute("aria-expanded", String(expanded));
        toggleBtn.querySelector(".detail-task-toggle__icon").textContent = expanded ? "−" : "+";
    }
}

// ---------------------------------------------------------------------------
// Internal helpers — status classes
// ---------------------------------------------------------------------------

/**
 * Map execution status to CSS badge class.
 * @param {string} status
 * @returns {string}
 */
function statusBadgeClass(status) {
    switch (status) {
        case "STARTED":
        case "RUNNING":
            return "status--running";
        case "COMPLETED":
            return "status--ok";
        case "FAILED":
        case "CANCELLED":
            return "status--ko";
        default:
            return "status--pending";
    }
}

/**
 * Map task status to CSS badge class.
 * @param {string} status
 * @returns {string}
 */
function taskStatusBadgeClass(status) {
    switch (status) {
        case "SUCCESS":
            return "status--ok";
        case "FAILED":
        case "TIMEOUT":
        case "SKIPPED":
            return "status--ko";
        case "RUNNING":
        case "STARTED":
            return "status--running";
        default:
            return "status--pending";
    }
}

/**
 * Map phase status to CSS badge class.
 * @param {string} status
 * @returns {string}
 */
function phaseStatusBadgeClass(status) {
    switch (status) {
        case "COMPLETED":
            return "status--ok";
        case "FAILED":
        case "ABORTED":
            return "status--ko";
        case "RUNNING":
        case "STARTED":
            return "status--running";
        default:
            return "status--pending";
    }
}

// ---------------------------------------------------------------------------
// Internal helpers — formatting & utilities
// ---------------------------------------------------------------------------

/**
 * Format an ISO-8601 timestamp into a short locale string.
 * @param {string|null} isoString
 * @returns {string}
 */
function formatTimestamp(isoString) {
    if (!isoString) return "—";
    try {
        var d = new Date(isoString);
        if (isNaN(d.getTime())) return isoString;
        return d.toLocaleString();
    } catch (e) {
        return isoString;
    }
}

/**
 * Truncate a string to a max length with ellipsis.
 * @param {string} text
 * @param {number} maxLen
 * @returns {string}
 */
function truncate(text, maxLen) {
    if (!text) return "";
    if (text.length <= maxLen) return text;
    return text.substring(0, maxLen) + "...";
}

// ---- HTML escaping utilities ----

/**
 * Escape text for safe inclusion in HTML element content.
 * @param {string|null} text
 * @returns {string}
 */
function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

/**
 * Escape text for safe inclusion in an HTML attribute value.
 * @param {string|null} text
 * @returns {string}
 */
function escapeAttr(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}
