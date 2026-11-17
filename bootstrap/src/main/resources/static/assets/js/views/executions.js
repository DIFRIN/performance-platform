/**
 * Executions list view.
 *
 * - Visibility-aware polling (pauses while the tab is hidden) with a
 *   "last updated / refreshing" indicator
 * - Server-bounded window ("Load more" grows the limit) plus client-side
 *   status / scenario / full-ID filters, all reflected in the URL
 * - Sortable columns and a sticky table header
 * - Copy-to-clipboard execution and schedule ids
 * - Non-blocking feedback: successes become toasts, action errors are shown
 *   inline next to the row, confirmations are reserved for delete
 * - Scheduled runs: reschedule, run now, cancel, explicit timezone
 *
 * Public API:
 *   mount(el) -> { cleanup: Function }
 */

import {
    listExecutions,
    cancelExecution,
    deleteExecution,
    rerunExecution,
    listScheduledRuns,
    cancelScheduledRun,
    rescheduleScheduledRun,
    runScheduledRunNow,
    scheduleScenario,
    getExecutionScenario
} from "/assets/js/api.js";
import {
    toast,
    announce,
    confirmDestructive,
    copyableIdHtml,
    handleCopyClick,
    createPoller,
    refreshStatusText,
    formatDateTime,
    formatRelative,
    formatDuration,
    elapsedMs,
    shortId,
    escapeHtml,
    escapeAttr,
    timeZoneLabel
} from "/assets/js/ui.js";
import { verdictSlotHtml, hydrateVerdictBadges } from "/assets/js/verdict.js";
import { readQueryParams, updateQueryParams } from "/assets/js/router.js";
import {
    enhanceScheduleControls,
    isScheduled,
    readSchedule,
    scheduleConfirmation,
    validateScheduleInput,
    scheduleFieldHtml
} from "/assets/js/schedule.js";

const POLL_INTERVAL_MS = 3000;
const DEFAULT_LIMIT = 50;
const LIMIT_STEP = 50;

const ACTIVE_STATUSES = new Set(["STARTED", "RUNNING"]);
const COMPLETED_STATUSES = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

const SORTABLE_COLUMNS = {
    executionId: "Execution ID",
    scenarioId: "Scenario ID",
    status: "Status",
    progress: "Progress",
    startedAt: "Started",
    duration: "Elapsed"
};

/**
 * Mount the executions list view.
 *
 * @param {HTMLElement} el
 * @returns {{cleanup: Function}}
 */
export function mount(el) {
    const params = readQueryParams();
    const state = {
        filter: params.get("status") || "all",
        scenarioFilter: params.get("scenario") || "all",
        search: params.get("q") || "",
        sortKey: SORTABLE_COLUMNS[params.get("sort")] ? params.get("sort") : "startedAt",
        sortDir: params.get("dir") === "asc" ? "asc" : "desc",
        limit: parseLimit(params.get("limit")),
        executions: [],
        scheduledRuns: [],
        error: null,
        scheduleError: null,
        loaded: false,
        lastSnapshot: null,
        rowErrors: new Map(),
        rowSchedules: new Map(),
        openReschedules: new Set(),
        scheduleErrors: new Map(),
        scheduleSortKey: "nextRunAt",
        scheduleSortDir: "asc"
    };

    el.innerHTML = buildSkeleton();

    const bodyEl = el.querySelector("#exec-list-body");
    const scheduleBodyEl = el.querySelector("#schedule-list-body");
    const scenarioFilterEl = el.querySelector("[data-exec-scenario-filter]");
    const searchEl = el.querySelector("[data-exec-search]");
    const filterBtns = el.querySelectorAll("[data-exec-filter]");
    const refreshStateEl = el.querySelector("[data-refresh-state]");
    const loadMoreBtn = el.querySelector("[data-load-more]");
    const scheduleRefreshEl = el.querySelector("[data-schedule-refresh-state]");

    searchEl.value = state.search;
    highlightFilterButtons(el, state.filter);

    let refreshState = null;

    function updateRefreshIndicator(next) {
        refreshState = next;
        refreshStateEl.textContent = refreshStatusText(next);
    }

    function persistQuery() {
        updateQueryParams({
            status: state.filter === "all" ? null : state.filter,
            scenario: state.scenarioFilter === "all" ? null : state.scenarioFilter,
            q: state.search || null,
            sort: state.sortKey === "startedAt" ? null : state.sortKey,
            dir: state.sortDir === "desc" ? null : state.sortDir,
            limit: state.limit === DEFAULT_LIMIT ? null : state.limit
        });
    }

    filterBtns.forEach(function (btn) {
        btn.addEventListener("click", function () {
            state.filter = btn.dataset.execFilter;
            highlightFilterButtons(el, state.filter);
            persistQuery();
            render();
        });
    });

    scenarioFilterEl.addEventListener("change", function () {
        state.scenarioFilter = scenarioFilterEl.value;
        persistQuery();
        render();
    });

    let searchTimer = null;
    searchEl.addEventListener("input", function () {
        if (searchTimer !== null) clearTimeout(searchTimer);
        searchTimer = setTimeout(function () {
            state.search = searchEl.value.trim();
            persistQuery();
            render();
        }, 180);
    });

    loadMoreBtn.addEventListener("click", function () {
        state.limit += LIMIT_STEP;
        persistQuery();
        loadMoreBtn.disabled = true;
        loadMoreBtn.textContent = "Loading\u2026";
        poller.refresh();
    });

    bodyEl.addEventListener("click", function (e) {
        if (handleCopyClick(e)) return;

        const sortBtn = e.target.closest("[data-sort]");
        if (sortBtn) {
            const key = sortBtn.dataset.sort;
            if (state.sortKey === key) {
                state.sortDir = state.sortDir === "asc" ? "desc" : "asc";
            } else {
                state.sortKey = key;
                state.sortDir = key === "startedAt" || key === "duration" || key === "progress" ? "desc" : "asc";
            }
            persistQuery();
            render();
            return;
        }

        if (e.target.closest("[data-exec-retry]")) {
            poller.refresh();
            return;
        }

        if (e.target.closest("[data-exec-clear]")) {
            state.filter = "all";
            state.scenarioFilter = "all";
            state.search = "";
            searchEl.value = "";
            scenarioFilterEl.value = "all";
            highlightFilterButtons(el, state.filter);
            persistQuery();
            render();
            return;
        }

        const actionBtn = e.target.closest("[data-exec-action]");
        if (actionBtn) {
            e.stopPropagation();
            const row = actionBtn.closest("tr[data-exec-id]");
            handleAction(actionBtn.dataset.execAction, actionBtn.dataset.execId, row);
            return;
        }

        if (e.target.closest("[data-scenario-link]")) return;
        if (e.target.closest("[data-schedule-control], select, input, button")) return;

        const rowEl = e.target.closest("tr[data-exec-id]");
        if (rowEl) {
            window.location.hash = "#/executions/" + encodeURIComponent(rowEl.dataset.execId);
        }
    });

    bodyEl.addEventListener("input", function (e) {
        const input = e.target.closest("[data-row-schedule]");
        if (input) {
            state.rowSchedules.set(input.dataset.rowSchedule, input.value);
        }
    });

    scheduleBodyEl.addEventListener("click", function (e) {
        if (handleCopyClick(e)) return;

        const sortBtn = e.target.closest("[data-schedule-sort]");
        if (sortBtn) {
            const key = sortBtn.dataset.scheduleSort;
            if (state.scheduleSortKey === key) {
                state.scheduleSortDir = state.scheduleSortDir === "asc" ? "desc" : "asc";
            } else {
                state.scheduleSortKey = key;
                state.scheduleSortDir = "asc";
            }
            renderScheduled(scheduleBodyEl, state);
            return;
        }

        const actionEl = e.target.closest("[data-schedule-action]");
        if (!actionEl) return;
        e.stopPropagation();
        const row = actionEl.closest("tr[data-schedule-row]");
        handleScheduleAction(actionEl.dataset.scheduleAction, actionEl.dataset.scheduleId, row);
    });

    scheduleBodyEl.addEventListener("input", function (e) {
        const input = e.target.closest("[data-reschedule-input]");
        if (input) {
            state.rowSchedules.set("reschedule:" + input.dataset.rescheduleInput, input.value);
        }
    });
    /* ---- Fetch & render ---- */

    /**
     * True while the user edits a control inside a table body. Polling must not
     * rebuild those rows (it would steal focus and caret position); header
     * controls such as the search box are unaffected.
     */
    function isEditing() {
        const active = document.activeElement;
        if (!active || !active.matches || !active.matches("input, select, textarea")) return false;
        return bodyEl.contains(active) || scheduleBodyEl.contains(active);
    }

    async function fetchAndRender() {
        try {
            state.executions = await listExecutions(state.limit);
            state.error = null;
        } catch (e) {
            console.error("executions: fetch failed", e);
            state.error = e.message || "Failed to load executions";
        }
        try {
            state.scheduledRuns = await listScheduledRuns(50);
            state.scheduleError = null;
        } catch (e) {
            console.error("executions: scheduled runs fetch failed", e);
            state.scheduleError = e.message || "Failed to load scheduled runs";
        }
        state.loaded = true;
        render();
    }

    function render() {
        state.scenarioFilter = renderScenarioOptions(scenarioFilterEl, state.executions, state.scenarioFilter);
        if (!isEditing()) {
            renderScheduled(scheduleBodyEl, state);
            const snapshot = JSON.stringify([
                state.executions, state.error, state.filter, state.scenarioFilter,
                state.search, state.sortKey, state.sortDir, state.limit, state.loaded,
                Array.from(state.rowErrors.entries())
            ]);
            if (snapshot !== state.lastSnapshot) {
                state.lastSnapshot = snapshot;
                renderBody(bodyEl, state);
            }
        }
        updateLoadMore(loadMoreBtn, state);
        updateScheduleIndicator(scheduleRefreshEl, refreshState);
    }

    const poller = createPoller({
        run: fetchAndRender,
        intervalMs: POLL_INTERVAL_MS,
        onState: function (next) {
            updateRefreshIndicator(next);
            updateScheduleIndicator(scheduleRefreshEl, next);
        }
    });

    function refreshNow() {
        poller.refresh();
    }

    /* ---- Actions ---- */

    function clearRowError(id) {
        if (state.rowErrors.delete(id)) {
            state.lastSnapshot = null;
            render();
        }
    }

    function setRowError(id, message) {
        state.rowErrors.set(id, message);
        state.lastSnapshot = null;
        render();
    }

    async function handleAction(action, id, row) {
        clearRowError(id);
        const input = row ? row.querySelector("[data-row-schedule]") : null;
        const schedule = readSchedule(input);

        try {
            if (action === "delete") {
                if (!confirmDestructive("Delete execution " + id + "? This cannot be undone.")) return;
                await deleteExecution(id);
                toast("Execution deleted", { variant: "success" });
                refreshNow();
                return;
            }

            if (action === "cancel") {
                await cancelExecution(id);
                toast("Execution cancelled", { variant: "success" });
                announce("Execution cancelled");
                refreshNow();
                return;
            }

            if (action === "rerun") {
                if (isScheduled(schedule)) {
                    const check = input ? validateScheduleInput(input) : { valid: true };
                    if (!check.valid) return;
                    const source = await getExecutionScenario(id);
                    if (!source || !source.yaml) {
                        throw new Error("The original scenario YAML is unavailable");
                    }
                    const result = await scheduleScenario(source.yaml, schedule);
                    if (result.executionId) {
                        window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
                    } else {
                        toast(scheduleConfirmation(result.nextRunAt), { variant: "success" });
                        refreshNow();
                    }
                    return;
                }
                const result = await rerunExecution(id);
                window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
            }
        } catch (e) {
            const message = e.message || "Action failed";
            setRowError(id, message);
            toast("Action failed: " + message, { variant: "error" });
        }
    }

    async function handleScheduleAction(action, scheduleId, row) {
        if (action === "reschedule") {
            if (state.openReschedules.has(scheduleId)) state.openReschedules.delete(scheduleId);
            else state.openReschedules.add(scheduleId);
            renderScheduled(scheduleBodyEl, state);
            const editor = scheduleBodyEl.querySelector('[data-reschedule-input="' + cssEscape(scheduleId) + '"]');
            if (editor) editor.focus();
            return;
        }

        if (action === "cancel-reschedule") {
            state.openReschedules.delete(scheduleId);
            state.scheduleErrors.delete(scheduleId);
            renderScheduled(scheduleBodyEl, state);
            return;
        }

        if (action === "save-reschedule") {
            const editorRow = row ? row.nextElementSibling : null;
            const input = editorRow ? editorRow.querySelector("[data-reschedule-input]") : null;
            const check = input ? validateScheduleInput(input) : { valid: false };
            if (!check.valid) return;
            const expression = readSchedule(input);
            if (!isScheduled(expression)) {
                state.scheduleErrors.set(scheduleId,
                    "Choose a deferred schedule (preset or expression), or use Run now.");
                renderScheduled(scheduleBodyEl, state);
                return;
            }
            try {
                const result = await rescheduleScheduledRun(scheduleId, expression);
                state.openReschedules.delete(scheduleId);
                state.scheduleErrors.delete(scheduleId);
                toast(scheduleConfirmation(result && result.nextRunAt), { variant: "success" });
                refreshNow();
            } catch (e) {
                state.scheduleErrors.set(scheduleId, e.message || "Reschedule failed");
                renderScheduled(scheduleBodyEl, state);
            }
            return;
        }

        try {
            if (action === "cancel") {
                await cancelScheduledRun(scheduleId);
                toast("Scheduled run cancelled", { variant: "success" });
                refreshNow();
            } else if (action === "run-now") {
                const result = await runScheduledRunNow(scheduleId);
                if (result && result.executionId) {
                    toast("Execution started", { variant: "success" });
                    window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
                } else {
                    toast("Run triggered", { variant: "success" });
                    refreshNow();
                }
            }
        } catch (e) {
            state.scheduleErrors.set(scheduleId, e.message || "Action failed");
            toast("Action failed: " + (e.message || "Unknown error"), { variant: "error" });
            renderScheduled(scheduleBodyEl, state);
        }
    }

    poller.start();

    return {
        cleanup: function () {
            poller.stop();
        }
    };
}

// ---------------------------------------------------------------------------
// Skeleton
// ---------------------------------------------------------------------------

function buildSkeleton() {
    return '' +
        '<div class="page-header">' +
        '  <h1 class="page-title" data-page-heading>Executions</h1>' +
        '  <span class="refresh-state" data-refresh-state aria-live="polite">Waiting for data\u2026</span>' +
        '</div>' +
        '<div class="card mb-3">' +
        '  <div class="flex-between mb-2">' +
        '    <div class="card__title">Scheduled runs</div>' +
        '    <span class="text-muted schedule-tz" data-schedule-refresh-state>' +
        '      Times shown in ' + escapeHtml(timeZoneLabel()) + '</span>' +
        '  </div>' +
        '  <div id="schedule-list-body">' + skeletonRows(3, 7) + '</div>' +
        '</div>' +
        '<div class="card">' +
        '  <div class="executions-header mb-2">' +
        '    <div class="card__title">Executions</div>' +
        '    <div class="executions-controls">' +
        '      <input type="search" class="scenarios-search" data-exec-search' +
        '             placeholder="Search by full execution ID\u2026"' +
        '             aria-label="Search by full execution ID">' +
        '      <select class="executions-filter" data-exec-scenario-filter aria-label="Filter by scenario">' +
        '        <option value="all">All scenarios</option>' +
        '      </select>' +
        '      <div class="segmented" role="group" aria-label="Filter by status">' +
        '        <button type="button" class="segmented__btn is-active" data-exec-filter="all">All</button>' +
        '        <button type="button" class="segmented__btn" data-exec-filter="active">Active</button>' +
        '        <button type="button" class="segmented__btn" data-exec-filter="completed">Completed</button>' +
        '      </div>' +
        '    </div>' +
        '  </div>' +
        '  <div id="exec-list-body">' + skeletonRows(5, 7) + '</div>' +
        '  <div class="table-footer">' +
        '    <button type="button" class="btn btn--sm" data-load-more hidden>Load more</button>' +
        '  </div>' +
        '</div>';
}

function skeletonRows(count, columns) {
    let html = '<div class="skeleton-table" aria-hidden="true">';
    for (let i = 0; i < count; i++) {
        html += '<div class="skeleton-row">';
        for (let c = 0; c < columns; c++) {
            html += '<span class="skeleton-bar"></span>';
        }
        html += '</div>';
    }
    return html + '</div>';
}

function highlightFilterButtons(el, activeFilter) {
    el.querySelectorAll("[data-exec-filter]").forEach(function (btn) {
        btn.classList.toggle("is-active", btn.dataset.execFilter === activeFilter);
    });
}

function parseLimit(value) {
    const parsed = parseInt(value || "", 10);
    if (isNaN(parsed) || parsed < DEFAULT_LIMIT) return DEFAULT_LIMIT;
    return Math.min(parsed, 1000);
}

// ---------------------------------------------------------------------------
// Filtering & sorting
// ---------------------------------------------------------------------------

function filterExecutions(executions, state) {
    let result = executions;

    if (state.filter !== "all") {
        const statusSet = state.filter === "active" ? ACTIVE_STATUSES : COMPLETED_STATUSES;
        result = result.filter(function (e) { return statusSet.has(e.status); });
    }
    if (state.scenarioFilter && state.scenarioFilter !== "all") {
        result = result.filter(function (e) { return e.scenarioId === state.scenarioFilter; });
    }
    if (state.search) {
        const needle = state.search.toLowerCase();
        result = result.filter(function (e) {
            return String(e.executionId || "").toLowerCase().indexOf(needle) !== -1;
        });
    }
    return sortExecutions(result, state.sortKey, state.sortDir);
}

function sortValue(exec, key) {
    switch (key) {
        case "executionId":
            return String(exec.executionId || "");
        case "scenarioId":
            return String(exec.scenarioId || "");
        case "status":
            return String(exec.status || "");
        case "progress": {
            const p = exec.progress || {};
            const total = p.total || 0;
            return total === 0 ? 0 : ((p.ok || 0) + (p.ko || 0)) / total;
        }
        case "duration":
            return elapsedMs(exec.startedAt, COMPLETED_STATUSES.has(exec.status) ? exec.updatedAt : null) || 0;
        case "startedAt":
        default: {
            const date = new Date(exec.startedAt || 0);
            return isNaN(date.getTime()) ? 0 : date.getTime();
        }
    }
}

function sortExecutions(executions, key, dir) {
    const factor = dir === "asc" ? 1 : -1;
    return executions.slice().sort(function (a, b) {
        const va = sortValue(a, key);
        const vb = sortValue(b, key);
        if (va < vb) return -1 * factor;
        if (va > vb) return factor;
        return 0;
    });
}

function renderScenarioOptions(selectEl, executions, selected) {
    if (!selectEl) return selected;
    const ids = [];
    executions.forEach(function (e) {
        if (e.scenarioId && ids.indexOf(e.scenarioId) === -1) ids.push(e.scenarioId);
    });
    ids.sort();
    const value = ids.indexOf(selected) !== -1 ? selected : "all";
    const options = '<option value="all">All scenarios</option>' + ids.map(function (id) {
        return '<option value="' + escapeAttr(id) + '">' + escapeHtml(id) + '</option>';
    }).join("");
    if (selectEl.innerHTML !== options) selectEl.innerHTML = options;
    selectEl.value = value;
    return value;
}

// ---------------------------------------------------------------------------
// Executions rendering
// ---------------------------------------------------------------------------

function renderBody(bodyEl, state) {
    if (!state.loaded) return;

    if (state.error && state.executions.length === 0) {
        bodyEl.innerHTML = '<div class="empty-state">' +
            '<div class="empty-state__text exec-error">' + escapeHtml(state.error) + '</div>' +
            '<button type="button" class="btn btn--sm" data-exec-retry>Retry</button>' +
            '</div>';
        return;
    }

    if (state.executions.length === 0) {
        bodyEl.innerHTML = '<div class="empty-state">' +
            '<div class="empty-state__text">No executions yet</div>' +
            '<p class="empty-state__hint text-muted">Run a scenario to see its progress here.</p>' +
            '<div class="empty-state__actions">' +
            '<a class="btn btn--sm btn--primary" href="#/scenarios">Open scenarios</a>' +
            '<a class="btn btn--sm" href="#/scenarios?upload=1">Upload a scenario</a>' +
            '</div></div>';
        return;
    }

    const filtered = filterExecutions(state.executions, state);

    if (filtered.length === 0) {
        bodyEl.innerHTML = '<div class="empty-state">' +
            '<div class="empty-state__text">No executions match the current filters</div>' +
            '<p class="empty-state__hint text-muted">' +
                escapeHtml(state.executions.length + " loaded \u00b7 " + filterSummary(state)) +
            '</p>' +
            '<button type="button" class="btn btn--sm" data-exec-clear>Clear filters</button>' +
            '</div>';
        announce("No executions match the current filters");
        return;
    }

    const errorBanner = state.error
        ? '<div class="exec-error-banner">' + escapeHtml(state.error) + '</div>'
        : '';

    bodyEl.innerHTML = errorBanner +
        '<div class="table-scroll">' +
        '<table class="table executions-table">' +
        '  <thead><tr>' +
        sortableHeader("executionId", "Execution ID", state) +
        sortableHeader("scenarioId", "Scenario ID", state) +
        sortableHeader("status", "Status", state) +
        sortableHeader("progress", "Progress", state) +
        sortableHeader("startedAt", "Started", state) +
        sortableHeader("duration", "Elapsed", state) +
        '      <th class="executions-table__actions-col">Actions</th>' +
        '  </tr></thead>' +
        '  <tbody>' +
        filtered.map(function (exec) { return renderRow(exec, state); }).join("") +
        '  </tbody>' +
        '</table></div>';

    enhanceScheduleControls(bodyEl);
    hydrateVerdictBadges(bodyEl);
}

function sortableHeader(key, label, state) {
    const active = state.sortKey === key;
    const arrow = active ? (state.sortDir === "asc" ? "\u25b2" : "\u25bc") : "";
    return '<th aria-sort="' + (active ? (state.sortDir === "asc" ? "ascending" : "descending") : "none") + '">' +
        '<button type="button" class="sort-btn' + (active ? " sort-btn--active" : "") + '" data-sort="' + key + '">' +
        escapeHtml(label) +
        '<span class="sort-btn__arrow" aria-hidden="true">' + arrow + '</span>' +
        '</button></th>';
}

function filterSummary(state) {
    const parts = [];
    if (state.filter !== "all") parts.push("status=" + state.filter);
    if (state.scenarioFilter !== "all") parts.push("scenario=" + state.scenarioFilter);
    if (state.search) parts.push("id~" + state.search);
    return parts.length ? parts.join(", ") : "no filter";
}

function renderRow(exec, state) {
    const statusClass = statusBadgeClass(exec.status);
    const scenarioLink = exec.scenarioId
        ? '<a class="scenario-link text-mono" data-scenario-link' +
          ' href="#/scenarios/' + encodeURIComponent(exec.scenarioId) + '"' +
          ' title="Open this scenario in the catalog">' +
          escapeHtml(exec.scenarioId) + '</a>'
        : '<span class="text-muted">\u2014</span>';

    const rowError = state.rowErrors.get(exec.executionId);

    return '' +
        '<tr class="executions-table__row" data-exec-id="' + escapeAttr(exec.executionId) + '">' +
        '  <td>' + copyableIdHtml(exec.executionId) + '</td>' +
        '  <td>' + scenarioLink + '</td>' +
        '  <td><span class="status ' + statusClass + '">' + escapeHtml(exec.status) + '</span>' + verdictSlotHtml(exec.executionId, exec.status) + '</td>' +
        '  <td>' + renderProgress(exec.progress) + '</td>' +
        '  <td class="executions-table__started" title="' + escapeAttr(formatDateTime(exec.startedAt)) + '">' +
             escapeHtml(formatDateTime(exec.startedAt)) +
             '<span class="cell-subtext">' + escapeHtml(formatRelative(exec.startedAt)) + '</span></td>' +
        '  <td class="executions-table__duration">' + renderDuration(exec) + '</td>' +
        '  <td>' + renderActions(exec, state) + '</td>' +
        '</tr>' +
        (rowError
            ? '<tr class="row-error"><td colspan="7"><span class="row-error__text">' +
              escapeHtml(rowError) + '</span></td></tr>'
            : '');
}

function renderDuration(exec) {
    const terminal = COMPLETED_STATUSES.has(exec.status);
    const ms = elapsedMs(exec.startedAt, terminal ? exec.updatedAt : null);
    if (ms === null) return '<span class="text-muted">\u2014</span>';
    if (terminal) return escapeHtml(formatDuration(ms));
    return '<span class="text-muted">' + escapeHtml(formatDuration(ms)) + ' elapsed</span>';
}

function statusBadgeClass(status) {
    switch (status) {
        case "STARTED":
        case "RUNNING":
            return "status--running";
        case "COMPLETED":
            return "status--ok";
        case "FAILED":
            return "status--ko";
        case "CANCELLED":
            return "status--cancelled";
        default:
            return "status--pending";
    }
}

function renderProgress(progress) {
    if (!progress || progress.total === 0) return '<span class="text-muted">\u2014</span>';

    const total = progress.total || 0;
    const ok = progress.ok || 0;
    const ko = progress.ko || 0;
    const running = progress.running || 0;
    const pending = Math.max(0, total - ok - ko - running);

    const segments = [];
    if (ok > 0) segments.push(segment("ok", ok / total * 100, ok + " ok"));
    if (ko > 0) segments.push(segment("ko", ko / total * 100, ko + " ko"));
    if (running > 0) segments.push(segment("running", running / total * 100, running + " running"));
    if (pending > 0) segments.push(segment("pending", pending / total * 100, pending + " pending"));

    let counts = ok + "/" + total + " ok";
    if (ko > 0) counts += ", " + ko + " ko";
    if (running > 0) counts += ", " + running + " running";

    return '<div class="exec-progress">' +
        '<div class="exec-progress__bar">' + segments.join("") + '</div>' +
        '<span class="exec-progress__counts">' + escapeHtml(counts) + '</span>' +
        '</div>';
}

function segment(kind, pct, title) {
    return '<div class="exec-progress__seg exec-progress__seg--' + kind + '"' +
        ' style="width:' + pct.toFixed(1) + '%" title="' + escapeAttr(title) + '"></div>';
}

function renderActions(exec, state) {
    const buttons = [];
    if (ACTIVE_STATUSES.has(exec.status)) {
        buttons.push('<button type="button" class="btn btn--sm btn--warning" data-exec-action="cancel"' +
            ' data-exec-id="' + escapeAttr(exec.executionId) + '">Cancel</button>');
    }
    if (COMPLETED_STATUSES.has(exec.status)) {
        const value = state.rowSchedules.get(exec.executionId) || "";
        buttons.push(
            '<div class="schedule-control schedule-control--compact schedule-control--inline" data-schedule-control>' +
            '<div class="schedule-control__row">' +
            '<select class="schedule-control__preset" data-schedule-preset' +
            ' aria-label="Schedule for rerun of ' + escapeAttr(shortId(exec.executionId)) + '">' +
            inlinePresets() + '</select>' +
            '<input type="text" class="schedule-control__input" data-schedule-input' +
            ' data-row-schedule="' + escapeAttr(exec.executionId) + '"' +
            ' autocomplete="off" spellcheck="false" hidden' +
            ' placeholder="in 10m, cron:\u2026"' +
            ' aria-label="Schedule for rerun of ' + escapeAttr(exec.executionId) + '"' +
            ' value="' + escapeAttr(value) + '">' +
            '</div>' +
            '<div class="schedule-control__hint" data-schedule-hint aria-live="polite"></div>' +
            '</div>'
        );
        buttons.push('<button type="button" class="btn btn--sm" data-exec-action="rerun"' +
            ' data-exec-id="' + escapeAttr(exec.executionId) + '"' +
            ' title="Re-run now or schedule a new execution from the same scenario">Rerun</button>');
        buttons.push('<button type="button" class="btn btn--sm btn--danger" data-exec-action="delete"' +
            ' data-exec-id="' + escapeAttr(exec.executionId) + '">Delete</button>');
    }
    return '<div class="table-actions table-actions--schedule">' + buttons.join("") + '</div>';
}

function inlinePresets() {
    return '<option value="">Now</option>' +
        '<option value="in 15m">In 15 min</option>' +
        '<option value="in 1h">In 1 hour</option>' +
        '<option value="__custom__">Custom\u2026</option>';
}

function updateLoadMore(button, state) {
    if (!button) return;
    const more = state.executions.length >= state.limit;
    button.hidden = !more;
    button.disabled = false;
    button.textContent = more
        ? "Load more (loaded " + state.executions.length + ")"
        : "All loaded";
}

// ---------------------------------------------------------------------------
// Scheduled runs rendering
// ---------------------------------------------------------------------------

function updateScheduleIndicator(el, refreshState) {
    if (!el) return;
    if (!refreshState || !refreshState.lastUpdated) {
        el.textContent = "Times shown in " + timeZoneLabel();
        return;
    }
    el.textContent = "Times shown in " + timeZoneLabel() + " \u00b7 " + refreshStatusText(refreshState);
}

function renderScheduled(bodyEl, state) {
    if (!bodyEl) return;

    if (state.scheduleError && state.scheduledRuns.length === 0) {
        bodyEl.innerHTML = '<div class="empty-state">' +
            '<div class="empty-state__text exec-error">' + escapeHtml(state.scheduleError) + '</div>' +
            '</div>';
        return;
    }

    if (!state.scheduledRuns || state.scheduledRuns.length === 0) {
        bodyEl.innerHTML = '<div class="empty-state">' +
            '<div class="empty-state__text">No scheduled runs</div>' +
            '<p class="empty-state__hint text-muted">Pick a preset in a scenario or execution row, ' +
            'then use Run or Rerun to defer an execution.</p>' +
            '</div>';
        return;
    }

    const runs = sortScheduledRuns(state.scheduledRuns, state.scheduleSortKey, state.scheduleSortDir);

    bodyEl.innerHTML = '<div class="table-scroll">' +
        '<table class="table scheduled-table">' +
        '  <thead><tr>' +
        scheduleHeader("scheduleId", "Schedule ID", state) +
        scheduleHeader("scenarioId", "Scenario", state) +
        '      <th>Schedule</th>' +
        scheduleHeader("nextRunAt", "Next run", state) +
        scheduleHeader("status", "Status", state) +
        '      <th>Last execution</th>' +
        '      <th class="executions-table__actions-col">Actions</th>' +
        '  </tr></thead>' +
        '  <tbody>' +
        runs.map(function (run) { return renderScheduledRow(run, state); }).join("") +
        '  </tbody>' +
        '</table></div>';

    enhanceScheduleControls(bodyEl);
}

function renderScheduledRow(run, state) {
    const schedule = run.schedule || run.kind || "\u2014";
    const pending = run.status === "PENDING";

    const scenario = run.scenarioId
        ? '<a class="scenario-link text-mono" href="#/scenarios/' + encodeURIComponent(run.scenarioId) + '"' +
          ' title="Open this scenario in the catalog">' + escapeHtml(run.scenarioId) + '</a>'
        : '<span class="text-muted">\u2014</span>';

    const lastExecution = run.lastExecutionId
        ? '<a class="text-mono" href="#/executions/' + encodeURIComponent(run.lastExecutionId) + '"' +
          ' title="Open the produced execution">' + escapeHtml(shortId(run.lastExecutionId)) + '</a>'
        : '<span class="text-muted">\u2014</span>';

    const actions = pending
        ? '<button type="button" class="btn btn--sm" data-schedule-action="run-now"' +
          ' data-schedule-id="' + escapeAttr(run.scheduleId) + '">Run now</button>' +
          '<button type="button" class="btn btn--sm" data-schedule-action="reschedule"' +
          ' data-schedule-id="' + escapeAttr(run.scheduleId) + '">Reschedule</button>' +
          '<button type="button" class="btn btn--sm btn--warning" data-schedule-action="cancel"' +
          ' data-schedule-id="' + escapeAttr(run.scheduleId) + '">Cancel</button>'
        : '';

    const rowError = state.scheduleErrors.get(run.scheduleId);

    const mainRow = '<tr class="scheduled-table__row" data-schedule-row="' + escapeAttr(run.scheduleId) + '">' +
        '  <td>' + copyableIdHtml(run.scheduleId) + '</td>' +
        '  <td>' + scenario + '</td>' +
        '  <td><span class="text-mono">' + escapeHtml(schedule) + '</span>' +
             '<span class="text-muted"> \u00b7 ' + escapeHtml(run.kind) + '</span></td>' +
        '  <td>' + escapeHtml(formatDateTime(run.nextRunAt)) +
             '<span class="cell-subtext">' + escapeHtml(formatRelative(run.nextRunAt)) + '</span></td>' +
        '  <td><span class="status ' + scheduleStatusBadgeClass(run.status) + '">' +
             escapeHtml(run.status) + '</span></td>' +
        '  <td>' + lastExecution + '</td>' +
        '  <td class="executions-table__actions-col"><span class="table-actions">' + actions + '</span></td>' +
        '</tr>';

    const editorRow = state.openReschedules.has(run.scheduleId)
        ? '<tr class="schedule-editor-row"><td colspan="7">' +
          '<div class="schedule-editor">' +
          scheduleFieldHtml("resched-" + run.scheduleId, {
              compact: true,
              label: "New schedule",
              value: state.rowSchedules.get("reschedule:" + run.scheduleId) || ""
          }) +
          '<div class="schedule-editor__actions">' +
          '<button type="button" class="btn btn--sm" data-schedule-action="cancel-reschedule"' +
          ' data-schedule-id="' + escapeAttr(run.scheduleId) + '">Cancel</button>' +
          '<button type="button" class="btn btn--sm btn--primary" data-schedule-action="save-reschedule"' +
          ' data-schedule-id="' + escapeAttr(run.scheduleId) + '">Save</button>' +
          '</div></div></td></tr>'
        : '';

    const errorRow = rowError
        ? '<tr class="row-error"><td colspan="7"><span class="row-error__text">' +
          escapeHtml(rowError) + '</span></td></tr>'
        : '';

    return mainRow + editorRow + errorRow;
}

function scheduleHeader(key, label, state) {
    const active = state.scheduleSortKey === key;
    const arrow = active ? (state.scheduleSortDir === "asc" ? "\u25b2" : "\u25bc") : "";
    return '<th aria-sort="' + (active ? (state.scheduleSortDir === "asc" ? "ascending" : "descending") : "none") + '">' +
        '<button type="button" class="sort-btn' + (active ? " sort-btn--active" : "") + '"' +
        ' data-schedule-sort="' + key + '">' + escapeHtml(label) +
        '<span class="sort-btn__arrow" aria-hidden="true">' + arrow + '</span></button></th>';
}

function sortScheduledRuns(runs, key, dir) {
    const factor = dir === "asc" ? 1 : -1;
    const value = function (run) {
        switch (key) {
            case "scheduleId": return String(run.scheduleId || "");
            case "scenarioId": return String(run.scenarioId || "");
            case "status": return String(run.status || "");
            case "nextRunAt":
            default: {
                const date = new Date(run.nextRunAt || 0);
                return isNaN(date.getTime()) ? 0 : date.getTime();
            }
        }
    };
    return runs.slice().sort(function (a, b) {
        const va = value(a);
        const vb = value(b);
        if (va < vb) return -1 * factor;
        if (va > vb) return factor;
        return 0;
    });
}

function scheduleStatusBadgeClass(status) {
    switch (status) {
        case "COMPLETED":
            return "status--ok";
        case "FAILED":
            return "status--ko";
        case "CANCELLED":
            return "status--cancelled";
        default:
            return "status--pending";
    }
}

function cssEscape(value) {
    if (window.CSS && typeof window.CSS.escape === "function") return window.CSS.escape(value);
    return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
}
