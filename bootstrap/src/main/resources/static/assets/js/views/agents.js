/**
 * Agents dashboard view.
 * PDR-029 — ISSUE-129
 *
 * Mounts the agents dashboard with:
 * - Polling every 3 seconds (setInterval / clearInterval)
 * - Table columns: agentId, state, supportedTasks, lastHeartbeat
 * - Visible only when ORCHESTRATOR role (nav link hidden otherwise)
 *
 * Public API:
 *   mount(el) → { cleanup: Function }
 *
 * The cleanup function clears the polling interval so there is no leak
 * when the router switches to another view.
 */

import { listAgents } from "/assets/js/api.js";

/** Polling interval in milliseconds. */
const POLL_INTERVAL_MS = 3000;

/**
 * Mount the agents dashboard view inside the given element.
 *
 * @param {HTMLElement} el — the container element for this view
 * @returns {{cleanup: Function}}
 */
export function mount(el) {
    /* ---- State ---- */
    let intervalId = null;
    let agents = [];
    let error = null;

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton();

    const bodyEl = el.querySelector("#agents-list-body");

    /* ---- Fetch & Render loop ---- */

    async function fetchAndRender() {
        try {
            agents = await listAgents();
            error = null;
        } catch (e) {
            console.error("agents: fetch failed", e);
            error = e.message || "Failed to load agents";
            // Keep previous agents so the table does not blank
        }
        renderBody(bodyEl, agents, error);
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
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Build the static skeleton HTML.
 * @returns {string}
 */
function buildSkeleton() {
    return '' +
        '<div class="card">' +
        '  <div class="flex-between mb-2">' +
        '    <div class="card__title">Agents</div>' +
        '    <span class="text-muted" style="font-size:0.8125rem" id="agents-count"></span>' +
        '  </div>' +
        '  <div id="agents-list-body"></div>' +
        '</div>';
}

/**
 * Render the body area: table or empty state.
 * @param {HTMLElement} bodyEl — the #agents-list-body div
 * @param {Array} agents — raw list from API
 * @param {string|null} error — fetch error message, if any
 */
function renderBody(bodyEl, agents, error) {
    // Update count in header
    const countEl = document.getElementById("agents-count");
    if (countEl) {
        countEl.textContent = agents.length + " agent" + (agents.length !== 1 ? "s" : "");
    }

    // Initial load failure (no data yet)
    if (error && agents.length === 0) {
        bodyEl.innerHTML = '' +
            '<div class="empty-state">' +
            '  <div class="empty-state__text exec-error">' + escapeHtml(error) + '</div>' +
            '</div>';
        return;
    }

    // Error banner above table on transient failures
    var errorBanner = error
        ? '<div class="exec-error-banner">' + escapeHtml(error) + '</div>'
        : '';

    // Empty state with explicit message
    if (agents.length === 0) {
        bodyEl.innerHTML = '' +
            errorBanner +
            '<div class="empty-state">' +
            '  <div class="empty-state__text">No agents registered</div>' +
            '  <p class="text-muted" style="font-size:0.8125rem;margin-top:0.5rem">' +
            'Payload agents only appear when running in ORCHESTRATOR mode.</p>' +
            '</div>';
        return;
    }

    // Build table
    bodyEl.innerHTML = '' +
        errorBanner +
        '<table class="table agents-table">' +
        '  <thead>' +
        '    <tr>' +
        '      <th>Agent ID</th>' +
        '      <th>State</th>' +
        '      <th>Supported Tasks</th>' +
        '      <th>Last Heartbeat</th>' +
        '    </tr>' +
        '  </thead>' +
        '  <tbody>' +
        agents.map(renderRow).join("") +
        '  </tbody>' +
        '</table>';
}

/**
 * Render a single agent table row.
 * @param {Object} agent — AgentResponse from API
 * @returns {string} HTML string for the <tr>
 */
function renderRow(agent) {
    var shortId = agent.agentId ? agent.agentId.substring(0, 8) : "—";
    var stateClass = agentStateBadgeClass(agent.state);
    var tasks = (agent.supportedTasks && agent.supportedTasks.length > 0)
        ? agent.supportedTasks.join(", ")
        : "—";
    var heartbeat = formatTimestamp(agent.lastHeartbeatAt);

    return '' +
        '<tr>' +
        '  <td><span class="text-mono">' + escapeHtml(shortId) + '</span></td>' +
        '  <td><span class="status ' + stateClass + '">' + escapeHtml(agent.state || "UNKNOWN") + '</span></td>' +
        '  <td><span class="agents-table__tasks">' + escapeHtml(tasks) + '</span></td>' +
        '  <td class="agents-table__heartbeat">' + escapeHtml(heartbeat) + '</td>' +
        '</tr>';
}

/**
 * Map an agent lifecycle state to a CSS badge class.
 * @param {string} state — e.g. "IDLE", "EXECUTING", "OFFLINE"
 * @returns {string} CSS class name
 */
function agentStateBadgeClass(state) {
    switch (state) {
        case "IDLE":
            return "status--ok";
        case "EXECUTING":
            return "status--running";
        case "REGISTERING":
        case "DRAINING":
            return "status--pending";
        case "OFFLINE":
            return "status--ko";
        default:
            return "status--pending";
    }
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
