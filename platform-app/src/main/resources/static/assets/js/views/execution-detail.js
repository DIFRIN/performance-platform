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
 * - Back link to /#/executions; report route /#/executions/{id}/report
 *
 * Public API:
 *   mount(el, executionId) → { cleanup: Function }
 *
 * The cleanup function clears the polling interval so there is no leak
 * when the router switches to another view.
 */

import { getExecutionStatus, listExecutionTasks, getExecutionScenario } from "/assets/js/api.js";

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
    let intervalId = null;
    let detail = null;       // ExecutionStatusResponse from getExecutionStatus
    let tasksData = null;    // TaskListResponse from listExecutionTasks
    let error = null;
    let lastSnapshot = null; // serialized last rendered state — skip DOM rebuild when unchanged
    let inFlight = false;
    let disposed = false;
    const expandedTaskIds = new Set(); // survives re-renders so polling never collapses open rows

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton(executionId);

    const bodyEl = el.querySelector("#exec-detail-body");
    const backLink = el.querySelector("[data-exec-back]");
    const reportLink = el.querySelector("[data-exec-report]");
    const scenarioPanel = el.querySelector("[data-scenario-source]");
    const scenarioCode = el.querySelector("[data-scenario-code]");
    let sourceLoaded = false;
    let sourceLoading = false;

    scenarioPanel.addEventListener("toggle", async function () {
        if (!scenarioPanel.open || sourceLoaded || sourceLoading) return;
        sourceLoading = true;
        scenarioCode.textContent = "Loading...";
        try {
            const source = await getExecutionScenario(executionId);
            if (disposed) return;
            scenarioCode.textContent = source.yaml == null
                ? "Original YAML is unavailable for this execution."
                : source.yaml;
            sourceLoaded = true;
        } catch (e) {
            console.error("execution-detail: scenario source fetch failed", e);
            if (!disposed) scenarioCode.textContent = "Failed to load YAML: " + e.message +
                ". Close and reopen this panel to retry.";
        } finally {
            sourceLoading = false;
        }
    });

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

    // Delegated click handler on body for row expansion
    bodyEl.addEventListener("click", function (e) {
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
        if (intervalId !== null) {
            clearInterval(intervalId);
            intervalId = null;
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

    // Initial fetch
    fetchAndRender();

    // Start polling
    intervalId = setInterval(fetchAndRender, POLL_INTERVAL_MS);

    /* ---- Cleanup ---- */
    return {
        cleanup: function () {
            disposed = true;
            stopPolling();
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
        '      <h1 class="detail-header__title">Execution <span class="text-mono">' + escapeHtml(executionId) + '</span></h1>' +
        '    </div>' +
        '    <div class="detail-header__right">' +
        '      <button type="button" class="btn btn--primary" data-exec-report disabled title="Available when execution has finished">View Report</button>' +
        '    </div>' +
        '  </div>' +
        '  <div id="exec-detail-body">' +
        '    <div class="detail-loading">Loading...</div>' +
        '  </div>' +
        '  <details class="card detail-scenario-source" data-scenario-source>' +
        '    <summary class="card__title">Scenario YAML (original source)</summary>' +
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
    var phasesHtml = renderPhaseSections(tasksData);

    bodyEl.innerHTML = '' +
        errorBanner +
        infoHtml +
        progressHtml +
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
        '    <div class="detail-info-value"><span class="text-mono">' + escapeHtml(detail.scenarioId || "—") + '</span></div>' +
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
        '  <td class="detail-task-message">' + escapeHtml(shortMessage) + '</td>' +
        '</tr>' +
        '<tr class="detail-task-expanded" id="task-detail-' + escapeAttr(rowKey) + '" style="display:none">' +
        '  <td></td>' +
        '  <td colspan="4">' +
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

/**
 * @param {HTMLElement} bodyEl
 * @param {string} taskId
 * @param {boolean} expanded
 */
function setTaskExpanded(bodyEl, taskId, expanded) {
    var detailRow = bodyEl.querySelector("#task-detail-" + CSS.escape(taskId));
    var toggleBtn = bodyEl.querySelector("[data-task-toggle=\"" + CSS.escape(taskId) + "\"]");
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
