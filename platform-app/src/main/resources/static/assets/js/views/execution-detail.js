/**
 * Execution detail view.
 * PDR-029 — ISSUE-128
 *
 * Mounts the detail view for a single execution:
 * - Polling every 3 seconds (setInterval / clearInterval)
 * - Progress bar fed by server-computed {@code progress {total, ok, ko, running}}
 * - Tasks grouped by phase with visual status (ok/ko/running)
 * - errorMessage displayed for KO tasks
 * - Summary view by default; full detail (errorMessage long, timings) via row expansion
 * - Back link to /#/executions; link to report /#/executions/{id}/report
 *
 * Public API:
 *   mount(el, executionId) → { cleanup: Function }
 *
 * The cleanup function clears the polling interval so there is no leak
 * when the router switches to another view.
 */

import { getExecutionStatus, listExecutionTasks } from "/assets/js/api.js";

/** Polling interval in milliseconds. */
const POLL_INTERVAL_MS = 3000;

/** Statuses considered "running" for visual treatment. */
const RUNNING_STATUSES = new Set(["STARTED", "RUNNING"]);

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

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton(executionId);

    const bodyEl = el.querySelector("#exec-detail-body");
    const backLink = el.querySelector("[data-exec-back]");
    const reportLink = el.querySelector("[data-exec-report]");

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
            window.location.hash = "#/executions/" + encodeURIComponent(executionId) + "/report";
        });
    }

    // Delegated click handler on body for row expansion
    bodyEl.addEventListener("click", function (e) {
        var toggleBtn = e.target.closest("[data-task-toggle]");
        if (toggleBtn) {
            e.stopPropagation();
            var taskId = toggleBtn.dataset.taskToggle;
            toggleTaskDetail(bodyEl, taskId);
            return;
        }

        // Click on row header toggles expansion too
        var rowHeader = e.target.closest("[data-task-row]");
        if (rowHeader) {
            var tid = rowHeader.dataset.taskRow;
            toggleTaskDetail(bodyEl, tid);
        }
    });

    /* ---- Fetch & Render loop ---- */

    async function fetchAndRender() {
        try {
            detail = await getExecutionStatus(executionId);
            tasksData = await listExecutionTasks(executionId);
            error = null;
        } catch (e) {
            console.error("execution-detail: fetch failed", e);
            error = e.message || "Failed to load execution detail";
        }
        renderBody(bodyEl, detail, tasksData, error);
    }

    // Initial fetch
    fetchAndRender();

    // Start polling
    intervalId = setInterval(fetchAndRender, POLL_INTERVAL_MS);

    /* ---- Cleanup ---- */
    return {
        cleanup: function () {
            if (intervalId !== null) {
                clearInterval(intervalId);
                intervalId = null;
            }
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
        '      <a href="#/executions/' + escapeAttr(executionId) + '/report" class="btn btn--primary" data-exec-report>View Report</a>' +
        '    </div>' +
        '  </div>' +
        '  <div id="exec-detail-body">' +
        '    <div class="detail-loading">Loading...</div>' +
        '  </div>' +
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

    return '' +
        '<div class="detail-phase">' +
        '  <div class="detail-phase__header">' +
        '    <span class="detail-phase__name">' + escapeHtml(phase) + '</span>' +
        '    <span class="detail-phase__stats">' +
        '      <span class="detail-phase__stat detail-phase__stat--ok">' + okCount + ' ok</span>' +
        '      <span class="detail-phase__stat detail-phase__stat--ko">' + koCount + ' ko</span>' +
        '      <span class="detail-phase__stat detail-phase__stat--running">' + runningCount + ' running</span>' +
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
    var statusClass = taskStatusBadgeClass(task.status);
    var shortMessage = task.errorMessage
        ? truncate(task.errorMessage, 80)
        : (task.status === "SUCCESS" ? "OK" : "—");

    return '' +
        '<tr class="detail-task-row" data-task-row="' + escapeAttr(task.taskId) + '">' +
        '  <td class="detail-tasks-table__expand">' +
        '    <button class="btn btn--sm detail-task-toggle" data-task-toggle="' + escapeAttr(task.taskId) + '" title="Show details">' +
        '      <span class="detail-task-toggle__icon">+</span>' +
        '    </button>' +
        '  </td>' +
        '  <td><span class="text-mono detail-task-name">' + escapeHtml(task.taskName) + '</span></td>' +
        '  <td><span class="status ' + statusClass + '">' + escapeHtml(task.status) + '</span></td>' +
        '  <td class="detail-task-message">' + escapeHtml(shortMessage) + '</td>' +
        '</tr>' +
        '<tr class="detail-task-expanded" id="task-detail-' + escapeAttr(task.taskId) + '" style="display:none">' +
        '  <td></td>' +
        '  <td colspan="3">' +
        '    <div class="detail-task-expanded__content">' +
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Task ID</span>' +
        '        <span class="text-mono">' + escapeHtml(task.taskId) + '</span>' +
        '      </div>' +
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Task Name</span>' +
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
        (task.errorMessage ?
        '      <div class="detail-task-expanded__field">' +
        '        <span class="detail-task-expanded__label">Error Message</span>' +
        '        <pre class="detail-task-expanded__error">' + escapeHtml(task.errorMessage) + '</pre>' +
        '      </div>' : '') +
        '    </div>' +
        '  </td>' +
        '</tr>';
}

/**
 * Toggle expansion of a task detail row.
 * @param {HTMLElement} bodyEl — the detail body element
 * @param {string} taskId
 */
function toggleTaskDetail(bodyEl, taskId) {
    var detailRow = bodyEl.querySelector("#task-detail-" + CSS.escape(taskId));
    var toggleBtn = bodyEl.querySelector("[data-task-toggle=\"" + CSS.escape(taskId) + "\"]");
    if (!detailRow) return;

    var isVisible = detailRow.style.display !== "none";
    if (isVisible) {
        detailRow.style.display = "none";
        if (toggleBtn) {
            toggleBtn.querySelector(".detail-task-toggle__icon").textContent = "+";
        }
    } else {
        detailRow.style.display = "";
        if (toggleBtn) {
            toggleBtn.querySelector(".detail-task-toggle__icon").textContent = "−";
        }
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
