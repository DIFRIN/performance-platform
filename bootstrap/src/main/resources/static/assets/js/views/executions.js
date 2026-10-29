/**
 * Executions list view.
 * PDR-029 — ISSUE-127
 *
 * Mounts the executions list with:
 * - Polling every 3 seconds (setInterval / clearInterval)
 * - Client-side status filter: active (STARTED|RUNNING) vs completed (COMPLETED|FAILED|CANCELLED) vs all
 * - Progress bar per row from server-computed {@code progress {total, ok, ko, running}}
 * - Cancel button for active executions (POST /cancel → 202)
 * - Delete button for completed executions (DELETE → 204, with confirmation)
 * - Row click → hash navigation to /#/executions/{id}
 *
 * Public API:
 *   mount(el) → { cleanup: Function }
 *
 * The cleanup function clears the polling interval so there is no leak
 * when the router switches to another view.
 */

import { listExecutions, cancelExecution, deleteExecution } from "/assets/js/api.js";

/** Polling interval in milliseconds. */
const POLL_INTERVAL_MS = 3000;

/** Execution statuses considered "active" for the default filter. */
const ACTIVE_STATUSES = new Set(["STARTED", "RUNNING"]);

/** Execution statuses considered "completed" for the filter. */
const COMPLETED_STATUSES = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

/**
 * Mount the executions list view inside the given element.
 *
 * The function renders the skeleton once (card + filter buttons + table area),
 * then starts a fetch-and-render loop every 3 seconds.
 *
 * @param {HTMLElement} el — the container element for this view
 * @returns {{cleanup: Function}}
 */
export function mount(el) {
    /* ---- State ---- */
    let filter = "active";
    let intervalId = null;
    let executions = [];
    let error = null;

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton();

    const bodyEl = el.querySelector("#exec-list-body");
    const filterBtns = el.querySelectorAll("[data-exec-filter]");

    /* ---- Event handlers ---- */

    // Filter button clicks
    filterBtns.forEach(function (btn) {
        btn.addEventListener("click", function () {
            filter = btn.dataset.execFilter;
            highlightFilterButtons(el, filter);
            renderBody(bodyEl, executions, filter, error);
        });
    });

    // Delegated click handler on body for row navigation and action buttons
    bodyEl.addEventListener("click", function (e) {
        // Action buttons (cancel / delete)
        var actionBtn = e.target.closest("[data-exec-action]");
        if (actionBtn) {
            e.stopPropagation();
            var actionId = actionBtn.dataset.execId;
            var action = actionBtn.dataset.execAction;
            handleAction(action, actionId);
            return;
        }

        // Row click → navigate to execution detail
        var row = e.target.closest("[data-exec-id]");
        if (row) {
            window.location.hash = "#/executions/" + row.dataset.execId;
        }
    });

    /* ---- Fetch & Render loop ---- */

    /**
     * Fetch executions from the server, update local state, and re-render.
     * On fetch error, preserves the previous executions array so the UI
     * does not blank out during a transient failure.
     */
    async function fetchAndRender() {
        try {
            executions = await listExecutions(50);
            error = null;
        } catch (e) {
            console.error("executions: fetch failed", e);
            error = e.message || "Failed to load executions";
        }
        renderBody(bodyEl, executions, filter, error);
    }

    // Initial fetch (no await — the promise settles and triggers render)
    fetchAndRender();

    // Start polling
    intervalId = setInterval(fetchAndRender, POLL_INTERVAL_MS);

    // Listen for immediate-refresh events (dispatched after cancel/delete)
    window.addEventListener("exec-refresh", fetchAndRender);

    /* ---- Cleanup ---- */
    return {
        cleanup: function () {
            if (intervalId !== null) {
                clearInterval(intervalId);
                intervalId = null;
            }
            window.removeEventListener("exec-refresh", fetchAndRender);
        }
    };
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Build the static skeleton HTML (card, header, filter buttons, body slot).
 * @returns {string}
 */
function buildSkeleton() {
    return '' +
        '<div class="card">' +
        '  <div class="flex-between mb-2">' +
        '    <div class="card__title">Executions</div>' +
        '    <div class="exec-filter-group">' +
        '      <button class="btn btn--sm btn--primary" data-exec-filter="active">Active</button>' +
        '      <button class="btn btn--sm" data-exec-filter="completed">Completed</button>' +
        '      <button class="btn btn--sm" data-exec-filter="all">All</button>' +
        '    </div>' +
        '  </div>' +
        '  <div id="exec-list-body"></div>' +
        '</div>';
}

/**
 * Highlight the active filter button by toggling btn--primary.
 * @param {HTMLElement} el — the view container
 * @param {string} activeFilter — "active" | "completed" | "all"
 */
function highlightFilterButtons(el, activeFilter) {
    el.querySelectorAll("[data-exec-filter]").forEach(function (btn) {
        if (btn.dataset.execFilter === activeFilter) {
            btn.classList.add("btn--primary");
        } else {
            btn.classList.remove("btn--primary");
        }
    });
}

/**
 * Apply the client-side status filter.
 * @param {Array} executions
 * @param {string} filter — "active" | "completed" | "all"
 * @returns {Array}
 */
function filterExecutions(executions, filter) {
    if (filter === "all") return executions;
    var statusSet = filter === "active" ? ACTIVE_STATUSES : COMPLETED_STATUSES;
    return executions.filter(function (e) {
        return statusSet.has(e.status);
    });
}

/**
 * Render the body area: table or empty state.
 * @param {HTMLElement} bodyEl — the #exec-list-body div
 * @param {Array} executions — raw list from API
 * @param {string} filter — current filter value
 * @param {string|null} error — fetch error message, if any
 */
function renderBody(bodyEl, executions, filter, error) {
    // Initial load failure (no data yet)
    if (error && executions.length === 0) {
        bodyEl.innerHTML = '' +
            '<div class="empty-state">' +
            '  <div class="empty-state__text exec-error">' + escapeHtml(error) + '</div>' +
            '</div>';
        return;
    }

    var filtered = filterExecutions(executions, filter);

    if (filtered.length === 0) {
        bodyEl.innerHTML = '' +
            '<div class="empty-state">' +
            '  <div class="empty-state__text">No executions found</div>' +
            '</div>';
        return;
    }

    // Build table — error banner shown above table on transient failures
    var errorBanner = error
        ? '<div class="exec-error-banner">' + escapeHtml(error) + '</div>'
        : '';

    bodyEl.innerHTML = '' +
        errorBanner +
        '<table class="table executions-table">' +
        '  <thead>' +
        '    <tr>' +
        '      <th>Execution ID</th>' +
        '      <th>Scenario ID</th>' +
        '      <th>Status</th>' +
        '      <th>Progress</th>' +
        '      <th>Started</th>' +
        '      <th class="executions-table__actions-col">Actions</th>' +
        '    </tr>' +
        '  </thead>' +
        '  <tbody>' +
        filtered.map(renderRow).join("") +
        '  </tbody>' +
        '</table>';
}

/**
 * Render a single table row.
 * @param {Object} exec — execution summary from the API
 * @returns {string} HTML string for the <tr>
 */
function renderRow(exec) {
    var shortId = exec.executionId ? exec.executionId.substring(0, 8) : "—";
    var statusClass = statusBadgeClass(exec.status);
    var progressHtml = renderProgress(exec.progress);
    var actionsHtml = renderActions(exec);
    var started = formatTimestamp(exec.startedAt);

    return '' +
        '<tr class="executions-table__row" data-exec-id="' + escapeAttr(exec.executionId) + '">' +
        '  <td><span class="text-mono">' + escapeHtml(shortId) + '</span></td>' +
        '  <td><span class="text-mono">' + escapeHtml(exec.scenarioId) + '</span></td>' +
        '  <td><span class="status ' + statusClass + '">' + escapeHtml(exec.status) + '</span></td>' +
        '  <td>' + progressHtml + '</td>' +
        '  <td class="executions-table__started">' + escapeHtml(started) + '</td>' +
        '  <td>' + actionsHtml + '</td>' +
        '</tr>';
}

/**
 * Map an execution status to a CSS badge class.
 * @param {string} status — e.g. "RUNNING", "COMPLETED", "FAILED"
 * @returns {string} CSS class name
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
 * Render the mini progress bar + counts for a single execution.
 * @param {{total: number, ok: number, ko: number, running: number}|null} progress
 * @returns {string} HTML string
 */
function renderProgress(progress) {
    if (!progress || progress.total === 0) {
        return '<span class="text-muted">—</span>';
    }

    var total = progress.total || 0;
    var ok = progress.ok || 0;
    var ko = progress.ko || 0;
    var running = progress.running || 0;
    var pending = total - ok - ko - running;
    if (pending < 0) pending = 0;

    var okPct = total > 0 ? (ok / total * 100).toFixed(1) : 0;
    var koPct = total > 0 ? (ko / total * 100).toFixed(1) : 0;
    var runningPct = total > 0 ? (running / total * 100).toFixed(1) : 0;
    var pendingPct = total > 0 ? (pending / total * 100).toFixed(1) : 0;

    var segments = [];
    if (ok > 0) {
        segments.push('<div class="exec-progress__seg exec-progress__seg--ok"' +
            ' style="width:' + okPct + '%" title="' + ok + ' ok"></div>');
    }
    if (ko > 0) {
        segments.push('<div class="exec-progress__seg exec-progress__seg--ko"' +
            ' style="width:' + koPct + '%" title="' + ko + ' ko"></div>');
    }
    if (running > 0) {
        segments.push('<div class="exec-progress__seg exec-progress__seg--running"' +
            ' style="width:' + runningPct + '%" title="' + running + ' running"></div>');
    }
    if (pending > 0) {
        segments.push('<div class="exec-progress__seg exec-progress__seg--pending"' +
            ' style="width:' + pendingPct + '%" title="' + pending + ' pending"></div>');
    }

    var counts = ok + '/' + total + ' ok';
    if (ko > 0) counts += ', ' + ko + ' ko';
    if (running > 0) counts += ', ' + running + ' running';

    return '' +
        '<div class="exec-progress">' +
        '  <div class="exec-progress__bar">' +
        segments.join("") +
        '  </div>' +
        '  <span class="exec-progress__counts">' + escapeHtml(counts) + '</span>' +
        '</div>';
}

/**
 * Render action buttons for a row (cancel for active, delete for completed).
 * @param {Object} exec
 * @returns {string} HTML string
 */
function renderActions(exec) {
    var buttons = [];
    if (ACTIVE_STATUSES.has(exec.status)) {
        buttons.push(
            '<button class="btn btn--sm btn--warning" data-exec-action="cancel"' +
            ' data-exec-id="' + escapeAttr(exec.executionId) + '">Cancel</button>'
        );
    }
    if (COMPLETED_STATUSES.has(exec.status)) {
        buttons.push(
            '<button class="btn btn--sm btn--danger" data-exec-action="delete"' +
            ' data-exec-id="' + escapeAttr(exec.executionId) + '">Delete</button>'
        );
    }
    return buttons.join(" ");
}

/**
 * Handle a cancel or delete action: call the API, then re-fetch the list.
 * Delete shows a confirmation dialog before proceeding.
 *
 * @param {string} action — "cancel" or "delete"
 * @param {string} id — execution identifier
 */
async function handleAction(action, id) {
    try {
        if (action === "delete") {
            if (!confirm("Delete execution " + id + "? This action cannot be undone.")) {
                return;
            }
            await deleteExecution(id);
        } else if (action === "cancel") {
            await cancelExecution(id);
        }
    } catch (e) {
        alert("Action failed: " + (e.message || "Unknown error"));
        return;
    }
    // Trigger an immediate refresh by dispatching a custom event.
    // The polling loop will also pick up the change on the next tick,
    // but we want instant feedback.
    window.dispatchEvent(new CustomEvent("exec-refresh"));
}

/**
 * Format an ISO-8601 timestamp into a short locale string.
 * Falls back to the raw value on parsing failure.
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
