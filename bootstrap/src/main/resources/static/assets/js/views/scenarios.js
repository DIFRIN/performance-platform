/**
 * Scenario catalog view.
 *
 * Lists the scenarios found in the configured directory without executing
 * them. Every valid scenario exposes two actions: Run (immediate) and
 * Schedule, which opens an opt-in editor with the shared schedule control
 * (preset dropdown + advanced mode, inline validation and next-run preview).
 * Scheduling is never implicit: only rows where the editor was opened can
 * create a deferred run, and the upload panel offers it as an optional field.
 *
 * Expanded rows show a step summary table (task, phase, dependencies,
 * timeout) and the highlighted YAML source with copy/download actions.
 *
 * Filters, search and sort order are reflected in the URL
 * (#/scenarios?q=...&filter=valid&sort=...).
 *
 * Public API:
 *   mount(el, {scenarioId}?) -> { cleanup: Function }
 */

import { listCatalog, runCatalogScenario, uploadScenario, scheduleScenario, validateScenario } from "/assets/js/api.js";
import {
    toast,
    announce,
    copyText,
    refreshStatusText,
    formatDuration,
    escapeHtml,
    escapeAttr
} from "/assets/js/ui.js";
import { readQueryParams, updateQueryParams } from "/assets/js/router.js";
import {
    enhanceScheduleControls,
    isScheduled,
    readSchedule,
    scheduleConfirmation,
    validateScheduleInput,
    scheduleFieldHtml
} from "/assets/js/schedule.js";

const PHASE_ORDER = ["PREPARATION", "INJECTION", "ASSERTION"];
const PHASE_SHORT = { PREPARATION: "prep", INJECTION: "inj", ASSERTION: "assert" };
const PHASE_LABELS = { PREPARATION: "Preparation", INJECTION: "Injection", ASSERTION: "Assertion" };

const SORTABLE_COLUMNS = {
    scenarioId: "Scenario ID",
    version: "Version",
    steps: "Steps",
    file: "File",
    status: "Status"
};

/**
 * Mount the scenario catalog view.
 *
 * @param {HTMLElement} el
 * @param {{scenarioId?: string}} [options] — deep-link filter
 * @returns {{cleanup: Function}}
 */
export function mount(el, options) {
    const params = readQueryParams();
    const deepLink = options && options.scenarioId ? String(options.scenarioId) : "";
    const state = {
        scenarios: [],
        error: null,
        loaded: false,
        query: (deepLink || params.get("q") || "").toLowerCase(),
        filter: params.get("filter") || "all",
        sortKey: SORTABLE_COLUMNS[params.get("sort")] ? params.get("sort") : "scenarioId",
        sortDir: params.get("dir") === "desc" ? "desc" : "asc",
        uploadOpen: params.get("upload") === "1",
        uploadMode: "paste",
        submitting: false,
        expanded: new Set(),
        openSchedules: new Set(),
        lastSnapshot: null
    };

    el.innerHTML = buildSkeleton();

    const bodyEl = el.querySelector("#catalog-body");
    const summaryEl = el.querySelector("[data-catalog-summary]");
    const refreshBtn = el.querySelector("[data-catalog-refresh]");
    const refreshStateEl = el.querySelector("[data-refresh-state]");
    const searchInput = el.querySelector("[data-catalog-search]");
    const filterButtons = el.querySelectorAll("[data-catalog-filter]");
    const uploadToggle = el.querySelector("[data-upload-toggle]");
    const uploadPanel = el.querySelector("[data-upload-panel]");
    const uploadForm = el.querySelector("[data-upload-form]");
    const uploadModeButtons = el.querySelectorAll("[data-upload-mode]");
    const uploadPanes = el.querySelectorAll("[data-upload-pane]");
    const uploadTextarea = el.querySelector("[data-upload-yaml]");
    const uploadFile = el.querySelector("[data-upload-file]");
    const uploadSubmit = el.querySelector("[data-upload-submit]");
    const uploadValidate = el.querySelector("[data-upload-validate]");
    const uploadPreview = el.querySelector("[data-upload-preview]");
    const uploadScheduleInput = uploadPanel.querySelector("[data-schedule-input]");

    searchInput.value = state.query;
    setFilterButtons(state.filter);
    enhanceScheduleControls(el);

    if (state.uploadOpen) setUploadOpen(true);

    /* ---- URL state ---- */

    function persistQuery() {
        updateQueryParams({
            q: state.query || null,
            filter: state.filter === "all" ? null : state.filter,
            sort: state.sortKey === "scenarioId" ? null : state.sortKey,
            dir: state.sortDir === "asc" ? null : state.sortDir
        });
    }

    function setFilterButtons(active) {
        filterButtons.forEach(function (btn) {
            const isActive = btn.dataset.catalogFilter === active;
            btn.classList.toggle("is-active", isActive);
            btn.setAttribute("aria-pressed", isActive ? "true" : "false");
        });
    }

    /* ---- Event handlers ---- */

    refreshBtn.addEventListener("click", fetchCatalog);

    let searchTimer = null;
    searchInput.addEventListener("input", function () {
        if (searchTimer !== null) clearTimeout(searchTimer);
        searchTimer = setTimeout(function () {
            state.query = searchInput.value.trim().toLowerCase();
            persistQuery();
            render();
        }, 180);
    });

    filterButtons.forEach(function (btn) {
        btn.addEventListener("click", function () {
            state.filter = btn.dataset.catalogFilter;
            setFilterButtons(state.filter);
            persistQuery();
            render();
        });
    });

    /* ---- Upload panel ---- */

    uploadToggle.addEventListener("click", function () {
        setUploadOpen(!state.uploadOpen);
    });

    uploadModeButtons.forEach(function (btn) {
        btn.addEventListener("click", function () {
            state.uploadMode = btn.dataset.uploadMode;
            uploadModeButtons.forEach(function (other) {
                const active = other === btn;
                other.classList.toggle("is-active", active);
                other.setAttribute("aria-pressed", active ? "true" : "false");
            });
            uploadPanes.forEach(function (pane) {
                pane.hidden = pane.dataset.uploadPane !== state.uploadMode;
            });
            clearUploadErrors();
        });
    });

    uploadForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        if (state.submitting) return;
        clearUploadErrors();
        clearPreview();

        const input = readUploadInput();
        if (!input) return;

        const schedule = readSchedule(uploadScheduleInput);
        if (isScheduled(schedule)) {
            const check = validateScheduleInput(uploadScheduleInput);
            if (!check.valid) {
                showUploadError("schedule", check.message);
                return;
            }
        }

        setSubmitting(true, uploadSubmit, "Starting\u2026");
        try {
            let yamlText = input.yamlText;
            let result;
            if (isScheduled(schedule)) {
                if (!yamlText) yamlText = await input.file.text();
                result = await scheduleScenario(yamlText, schedule);
            } else {
                result = await uploadScenario(state.uploadMode === "paste"
                    ? { yaml: yamlText }
                    : { file: input.file });
            }
            handleRunResult(result);
        } catch (err) {
            showApiErrors(err);
        } finally {
            setSubmitting(false, uploadSubmit, "Run");
        }
    });

    uploadValidate.addEventListener("click", async function () {
        if (state.submitting) return;
        clearUploadErrors();
        clearPreview();

        const input = readUploadInput();
        if (!input) return;

        setSubmitting(true, uploadValidate, "Validating\u2026");
        try {
            const preview = await validateScenario(state.uploadMode === "paste"
                ? { yaml: input.yamlText }
                : { file: input.file });
            showPreview(preview);
        } catch (err) {
            showApiErrors(err);
        } finally {
            setSubmitting(false, uploadValidate, "Validate");
        }
    });

    el.querySelector("[data-upload-cancel]").addEventListener("click", function () {
        setUploadOpen(false);
    });

    function readUploadInput() {
        if (state.uploadMode === "paste") {
            const yamlText = uploadTextarea.value.trim();
            if (!yamlText) {
                showUploadError("yaml", "Paste a YAML scenario first.");
                return null;
            }
            return { yamlText: yamlText, file: null };
        }
        const file = uploadFile.files && uploadFile.files.length > 0 ? uploadFile.files[0] : null;
        if (!file) {
            showUploadError("file", "Choose a .yaml or .yml file.");
            return null;
        }
        return { yamlText: "", file: file };
    }

    function setSubmitting(value, button, label) {
        state.submitting = value;
        button.disabled = value;
        button.classList.toggle("is-loading", value);
        button.textContent = label;
    }

    function setUploadOpen(open) {
        state.uploadOpen = open;
        uploadPanel.hidden = !open;
        uploadToggle.setAttribute("aria-expanded", open ? "true" : "false");
        if (open) {
            clearUploadErrors();
            clearPreview();
            if (state.uploadMode === "paste") uploadTextarea.focus();
        }
    }

    function showApiErrors(err) {
        if (err.details && err.details.length > 0) {
            err.details.forEach(function (d) {
                showUploadError(d.field, d.message);
            });
        } else {
            showUploadError("input", err.message || "Upload failed");
        }
        toast(err.message || "Upload failed", { variant: "error" });
    }

    function showUploadError(field, message) {
        const slot = uploadPanel.querySelector('[data-error-field="' + field + '"]');
        const target = slot || uploadPanel.querySelector('[data-error-field="input"]');
        if (!target) return;
        target.textContent = (target.textContent ? target.textContent + " " : "") + message;
        target.style.display = "block";
        if (field === "yaml") uploadTextarea.classList.add("upload-input--error");
        if (field === "file") uploadFile.classList.add("upload-input--error");
    }

    function clearUploadErrors() {
        uploadPanel.querySelectorAll(".upload-field-error").forEach(function (slot) {
            slot.textContent = "";
            slot.style.display = "none";
        });
        uploadTextarea.classList.remove("upload-input--error");
        uploadFile.classList.remove("upload-input--error");
    }

    function showPreview(preview) {
        if (!uploadPreview) return;
        const phaseCounts = {};
        (preview.steps || []).forEach(function (step) {
            phaseCounts[step.phase] = (phaseCounts[step.phase] || 0) + 1;
        });
        const phases = PHASE_ORDER
            .filter(function (phase) { return phaseCounts[phase]; })
            .map(function (phase) { return phaseCounts[phase] + " " + PHASE_SHORT[phase]; })
            .join(" \u00b7 ");
        uploadPreview.hidden = false;
        uploadPreview.innerHTML = '<div class="upload-preview__title">Valid scenario</div>' +
            '<div class="upload-preview__body">' +
            '  <span class="text-mono">' + escapeHtml(preview.scenarioId) + '</span>' +
            (preview.version ? ' <span class="text-muted">v' + escapeHtml(preview.version) + '</span>' : '') +
            (phases ? '  <span class="text-muted"> \u00b7 ' + escapeHtml(phases) + '</span>' : '') +
            '</div>';
        toast("Scenario is valid", { variant: "success" });
    }

    function clearPreview() {
        if (!uploadPreview) return;
        uploadPreview.hidden = true;
        uploadPreview.innerHTML = "";
    }

    /* ---- Grid handlers ---- */

    bodyEl.addEventListener("click", async function (e) {
        const copyBtn = e.target.closest("[data-copy-yaml]");
        if (copyBtn) {
            e.preventDefault();
            e.stopPropagation();
            const scenario = findScenario(copyBtn.dataset.copyYaml);
            if (!scenario || !scenario.yaml) return;
            const ok = await copyText(scenario.yaml);
            toast(ok ? "Scenario YAML copied" : "Copy failed", { variant: ok ? "success" : "error" });
            return;
        }

        const downloadBtn = e.target.closest("[data-download-yaml]");
        if (downloadBtn) {
            e.preventDefault();
            e.stopPropagation();
            const scenario = findScenario(downloadBtn.dataset.downloadYaml);
            if (scenario && scenario.yaml) downloadYaml(scenario.yaml, scenario.key);
            return;
        }

        const sortBtn = e.target.closest("[data-sort]");
        if (sortBtn) {
            const key = sortBtn.dataset.sort;
            if (state.sortKey === key) {
                state.sortDir = state.sortDir === "asc" ? "desc" : "asc";
            } else {
                state.sortKey = key;
                state.sortDir = key === "steps" ? "desc" : "asc";
            }
            persistQuery();
            render();
            return;
        }

        const toggleBtn = e.target.closest("[data-catalog-toggle]");
        if (toggleBtn && !toggleBtn.disabled) {
            toggleEntry(toggleBtn.dataset.catalogToggle);
            return;
        }

        const runBtn = e.target.closest("[data-catalog-run]");
        if (runBtn) {
            runScenario(runBtn.dataset.catalogRun, runBtn);
            return;
        }

        const scheduleToggle = e.target.closest("[data-catalog-schedule]");
        if (scheduleToggle) {
            toggleSchedule(scheduleToggle.dataset.catalogSchedule);
            return;
        }

        const scheduleCancel = e.target.closest("[data-catalog-schedule-cancel]");
        if (scheduleCancel) {
            state.openSchedules.delete(scheduleCancel.dataset.catalogScheduleCancel);
            state.lastSnapshot = null;
            render();
            return;
        }

        const scheduleSubmit = e.target.closest("[data-catalog-schedule-submit]");
        if (scheduleSubmit) {
            scheduleScenarioRun(scheduleSubmit.dataset.catalogScheduleSubmit, scheduleSubmit);
            return;
        }

        if (e.target.closest("[data-catalog-clear]")) {
            state.query = "";
            state.filter = "all";
            searchInput.value = "";
            setFilterButtons("all");
            persistQuery();
            render();
            return;
        }

        if (e.target.closest("[data-catalog-retry]")) {
            fetchCatalog();
            return;
        }

        if (e.target.closest("[data-catalog-upload]")) {
            setUploadOpen(true);
        }
    });

    function findScenario(key) {
        return state.scenarios.find(function (item) { return item.key === key; });
    }

    /* ---- Fetch & render ---- */

    function render() {
        const visible = filterScenarios(state);
        const snapshot = JSON.stringify([
            visible.map(function (s) { return [s.key, s.valid, s.error]; }),
            Array.from(state.expanded),
            Array.from(state.openSchedules),
            state.filter, state.query, state.sortKey, state.sortDir, state.loaded, state.error
        ]);
        if (snapshot !== state.lastSnapshot) {
            state.lastSnapshot = snapshot;
            bodyEl.innerHTML = renderBody(visible, state);
            enhanceScheduleControls(bodyEl);
        }
        updateSummary(summaryEl, state.scenarios, visible.length, state.query, state.filter);
    }

    async function fetchCatalog() {
        refreshBtn.disabled = true;
        refreshBtn.classList.add("is-loading");
        try {
            state.scenarios = await listCatalog();
            state.error = null;
        } catch (e) {
            console.error("scenarios: fetch failed", e);
            state.error = e.message || "Failed to load scenarios";
        } finally {
            refreshBtn.disabled = false;
            refreshBtn.classList.remove("is-loading");
        }
        state.loaded = true;
        if (refreshStateEl) {
            refreshStateEl.textContent = refreshStatusText({ refreshing: false, lastUpdated: new Date() });
        }
        render();
    }

    function toggleEntry(key) {
        if (state.expanded.has(key)) state.expanded.delete(key);
        else state.expanded.add(key);
        render();
    }

    function toggleSchedule(key) {
        if (state.openSchedules.has(key)) state.openSchedules.delete(key);
        else state.openSchedules.add(key);
        state.lastSnapshot = null;
        render();
        if (state.openSchedules.has(key)) {
            const editor = Array.prototype.find.call(
                bodyEl.querySelectorAll("[data-schedule-editor]"),
                function (row) { return row.dataset.scheduleEditor === key; }
            );
            const preset = editor ? editor.querySelector("[data-schedule-preset]") : null;
            if (preset) preset.focus();
        }
    }

    async function runScenario(key, btn) {
        if (btn.disabled) return;
        btn.disabled = true;
        btn.classList.add("is-loading");
        btn.textContent = "Starting\u2026";
        try {
            const result = await runCatalogScenario(key);
            handleRunResult(result);
        } catch (e) {
            toast("Unable to run scenario: " + (e.message || "Unknown error"), { variant: "error" });
        } finally {
            if (btn.isConnected) {
                btn.disabled = false;
                btn.classList.remove("is-loading");
                btn.textContent = "Run";
            }
        }
    }

    async function scheduleScenarioRun(key, btn) {
        if (btn.disabled) return;
        const editor = btn.closest("tr.schedule-editor-row");
        const input = editor ? editor.querySelector("[data-schedule-input]") : null;
        const check = input ? validateScheduleInput(input) : { valid: false };
        if (!check.valid) return;

        const expression = readSchedule(input);
        if (!isScheduled(expression)) {
            const hint = editor ? editor.querySelector("[data-schedule-hint]") : null;
            if (hint) {
                hint.textContent = "Choose a deferred schedule, or use Run for an immediate execution.";
                hint.className = "schedule-control__hint schedule-control__hint--error";
            }
            return;
        }

        const scenario = findScenario(key);
        if (!scenario || !scenario.yaml) {
            toast("Source YAML unavailable for this scenario", { variant: "error" });
            return;
        }

        btn.disabled = true;
        btn.classList.add("is-loading");
        btn.textContent = "Scheduling\u2026";
        try {
            const result = await scheduleScenario(scenario.yaml, expression);
            state.openSchedules.delete(key);
            state.lastSnapshot = null;
            render();
            toast(scheduleConfirmation(result ? result.nextRunAt : null), { variant: "success" });
            announce("Run scheduled");
        } catch (e) {
            toast("Unable to schedule scenario: " + (e.message || "Unknown error"), { variant: "error" });
        } finally {
            if (btn.isConnected) {
                btn.disabled = false;
                btn.classList.remove("is-loading");
                btn.textContent = "Schedule run";
            }
        }
    }

    function handleRunResult(result) {
        if (result && result.executionId) {
            window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
            return;
        }
        toast(scheduleConfirmation(result ? result.nextRunAt : null), { variant: "success" });
        announce("Run scheduled");
        window.location.hash = "#/executions";
    }

    fetchCatalog();

    return {
        cleanup: function () {
            // no timers or global listeners to release
        }
    };
}

// ---------------------------------------------------------------------------
// Filtering & sorting
// ---------------------------------------------------------------------------

function matches(scenario, query, filter) {
    if (filter === "valid" && !scenario.valid) return false;
    if (filter === "invalid" && scenario.valid) return false;
    if (!query) return true;

    const haystack = [
        scenario.key,
        scenario.scenarioId,
        scenario.name,
        scenario.version,
        (scenario.tags || []).join(" ")
    ].filter(Boolean).join(" ").toLowerCase();

    return haystack.indexOf(query) !== -1;
}

function filterScenarios(state) {
    const visible = state.scenarios.filter(function (scenario) {
        return matches(scenario, state.query, state.filter);
    });
    return sortScenarios(visible, state.sortKey, state.sortDir);
}

function sortValue(scenario, key) {
    switch (key) {
        case "version":
            return String(scenario.version || "");
        case "steps":
            return scenario.stepCount || 0;
        case "file":
            return String(scenario.key || "");
        case "status":
            return scenario.valid ? 1 : 0;
        case "scenarioId":
        default:
            return String(scenario.scenarioId || scenario.key || "");
    }
}

function sortScenarios(scenarios, key, dir) {
    const factor = dir === "asc" ? 1 : -1;
    return scenarios.slice().sort(function (a, b) {
        const va = sortValue(a, key);
        const vb = sortValue(b, key);
        if (va < vb) return -1 * factor;
        if (va > vb) return factor;
        return 0;
    });
}

function updateSummary(summaryEl, all, visible, query, filter) {
    if (!summaryEl) return;

    if (all.length === 0) {
        summaryEl.textContent = "No scenarios loaded";
        return;
    }

    const steps = all.reduce(function (sum, scenario) {
        return sum + (scenario.stepCount || 0);
    }, 0);
    const filtered = query || filter !== "all";

    summaryEl.textContent = filtered
        ? visible + " of " + all.length + " scenarios"
        : all.length + (all.length === 1 ? " scenario" : " scenarios") +
          " \u00b7 " + steps + (steps === 1 ? " step" : " steps");
}

// ---------------------------------------------------------------------------
// Skeleton
// ---------------------------------------------------------------------------

function buildSkeleton() {
    return '' +
        '<div class="page-header">' +
        '  <h1 class="page-title" data-page-heading>Scenarios</h1>' +
        '  <span class="refresh-state" data-refresh-state aria-live="polite">Waiting for data\u2026</span>' +
        '</div>' +
        '<div class="card">' +
        '  <div class="scenarios-header">' +
        '    <div>' +
        '      <div class="card__title">Catalog</div>' +
        '      <div class="scenarios-summary text-muted" data-catalog-summary>Loading\u2026</div>' +
        '    </div>' +
        '    <div class="scenarios-controls">' +
        '      <input type="search" class="scenarios-search" placeholder="Filter by id, file or tag\u2026"' +
        '             aria-label="Filter scenarios" data-catalog-search>' +
        '      <div class="segmented" role="group" aria-label="Filter by validity">' +
        '        <button type="button" class="segmented__btn is-active" data-catalog-filter="all" aria-pressed="true">All</button>' +
        '        <button type="button" class="segmented__btn" data-catalog-filter="valid" aria-pressed="false">Valid</button>' +
        '        <button type="button" class="segmented__btn" data-catalog-filter="invalid" aria-pressed="false">Invalid</button>' +
        '      </div>' +
        '      <button type="button" class="btn btn--sm" data-upload-toggle aria-expanded="false">Upload scenario</button>' +
        '      <button type="button" class="btn btn--sm" data-catalog-refresh>Refresh</button>' +
        '    </div>' +
        '  </div>' +
        '  <section class="scenarios-upload" data-upload-panel hidden>' +
        '    <div class="scenarios-upload__head">' +
        '      <div class="segmented" role="group" aria-label="Scenario input">' +
        '        <button type="button" class="segmented__btn is-active" data-upload-mode="paste" aria-pressed="true">Paste YAML</button>' +
        '        <button type="button" class="segmented__btn" data-upload-mode="file" aria-pressed="false">Choose file</button>' +
        '      </div>' +
        '      <span class="scenarios-upload__hint text-muted">Validate parses the YAML; Run executes it (immediately or per schedule).</span>' +
        '    </div>' +
        '    <form data-upload-form>' +
        '      <div data-upload-pane="paste">' +
        '        <textarea class="upload-textarea" rows="10" spellcheck="false"' +
        '                  placeholder="scenario:&#10;  id: my-scenario&#10;  steps: ..."' +
        '                  data-upload-yaml></textarea>' +
        '        <div class="upload-field-error" data-error-field="yaml"></div>' +
        '      </div>' +
        '      <div data-upload-pane="file" hidden>' +
        '        <input type="file" class="upload-input-file" accept=".yaml,.yml" data-upload-file>' +
        '        <div class="upload-field-error" data-error-field="file"></div>' +
        '      </div>' +
        '      <div class="upload-field-error upload-field-error--global" data-error-field="input"></div>' +
        '      <div class="upload-field-error" data-error-field="schedule"></div>' +
        '      <div class="upload-preview" data-upload-preview hidden></div>' +
        '      ' + scheduleFieldHtml("upload", { label: "Schedule (optional)" }) +
        '      <div class="scenarios-upload__actions">' +
        '        <button type="button" class="btn btn--sm" data-upload-cancel>Cancel</button>' +
        '        <button type="button" class="btn btn--sm" data-upload-validate>Validate</button>' +
        '        <button type="submit" class="btn btn--sm btn--primary" data-upload-submit>Run</button>' +
        '      </div>' +
        '    </form>' +
        '  </section>' +
        '  <div id="catalog-body" class="scenarios-list">' + loadingSkeleton() + '</div>' +
        '</div>';
}

function loadingSkeleton() {
    let html = '<div class="skeleton-table" aria-hidden="true">';
    for (let i = 0; i < 4; i++) {
        html += '<div class="skeleton-row">';
        for (let c = 0; c < 7; c++) html += '<span class="skeleton-bar"></span>';
        html += '</div>';
    }
    return html + '</div>';
}

// ---------------------------------------------------------------------------
// Rendering — grid
// ---------------------------------------------------------------------------

function renderBody(visible, state) {
    if (!state.loaded) return loadingSkeleton();

    if (state.error) {
        return '<div class="exec-error-banner">' + escapeHtml(state.error) + '</div>' +
            '<div class="empty-state">' +
            '<button type="button" class="btn btn--sm" data-catalog-retry>Retry</button>' +
            '</div>';
    }

    if (state.scenarios.length === 0) {
        return '<div class="empty-state">' +
            '<div class="empty-state__text">No scenarios configured</div>' +
            '<p class="empty-state__hint text-muted">Set <code>SCENARIOS_DIR</code> ' +
            '(or <code>platform.scenarios.directory</code>) to a folder of YAML files, then refresh \u2014 ' +
            'or upload one right here.</p>' +
            '<button type="button" class="btn btn--sm btn--primary" data-catalog-upload>Upload a scenario</button>' +
            '</div>';
    }

    if (visible.length === 0) {
        return '<div class="empty-state">' +
            '<div class="empty-state__text">No matching scenario</div>' +
            '<p class="empty-state__hint text-muted">' +
            escapeHtml(state.scenarios.length + " loaded \u00b7 try another id, file or filter") + '</p>' +
            '<button type="button" class="btn btn--sm" data-catalog-clear>Clear filters</button>' +
            '</div>';
    }

    return '<div class="table-scroll">' +
        '<table class="table scenarios-table">' +
        '  <thead><tr>' +
        '      <th class="scenarios-table__expand" aria-label="Expand"></th>' +
        sortableHeader("scenarioId", "Scenario ID", state, false) +
        sortableHeader("version", "Version", state, false) +
        sortableHeader("steps", "Steps", state, false) +
        sortableHeader("file", "File", state, false) +
        sortableHeader("status", "Status", state, false) +
        '      <th class="scenarios-table__actions-col">Actions</th>' +
        '  </tr></thead>' +
        '  <tbody>' +
        visible.map(function (scenario) {
            return renderScenarioRows(scenario, state);
        }).join("") +
        '  </tbody>' +
        '</table></div>';
}

function sortableHeader(key, label, state, trailing) {
    const active = state.sortKey === key;
    const arrow = active ? (state.sortDir === "asc" ? "\u25b2" : "\u25bc") : "";
    return '<th aria-sort="' + (active ? (state.sortDir === "asc" ? "ascending" : "descending") : "none") + '">' +
        '<button type="button" class="sort-btn' + (active ? " sort-btn--active" : "") + '" data-sort="' + key + '">' +
        escapeHtml(label) + '<span class="sort-btn__arrow" aria-hidden="true">' + arrow + '</span>' +
        '</button></th>';
}

function renderScenarioRows(scenario, state) {
    const key = scenario.key;
    const isExpanded = state.expanded.has(key);
    const rowClass = "scenarios-table__row" + (isExpanded ? " scenario-item--expanded" : "");

    const expand = '<button type="button" class="scenario-item__expand"' +
        ' data-catalog-toggle="' + escapeAttr(key) + '"' +
        ' aria-expanded="' + (isExpanded ? "true" : "false") + '"' +
        ' aria-label="' + (isExpanded ? "Hide details" : "Show details") + '"' +
        ' title="' + (isExpanded ? "Hide details" : "Show details") + '">' +
        '<span class="scenario-item__chevron" aria-hidden="true">\u25b8</span></button>';

    const run = scenario.valid
        ? '<button type="button" class="btn btn--sm btn--primary" data-catalog-run="' +
          escapeAttr(key) + '">Run</button>'
        : '<button type="button" class="btn btn--sm" disabled' +
          ' title="Fix the YAML before running this scenario">Run</button>';

    const scheduleOpen = state.openSchedules.has(key);
    const scheduleBtn = scenario.valid
        ? '<button type="button" class="btn btn--sm" data-catalog-schedule="' + escapeAttr(key) + '"' +
          ' aria-expanded="' + (scheduleOpen ? "true" : "false") + '"' +
          ' title="Schedule this scenario instead of running it now">' +
          (scheduleOpen ? "Close schedule" : "Schedule") + '</button>'
        : '<button type="button" class="btn btn--sm" disabled' +
          ' title="Fix the YAML before scheduling this scenario">Schedule</button>';

    const row = '<tr class="' + rowClass + '" data-catalog-key="' + escapeAttr(key) + '">' +
        '  <td class="scenarios-table__expand">' + expand + '</td>' +
        '  <td><span class="scenarios-table__id">' + escapeHtml(scenario.scenarioId || "\u2014") + '</span>' +
             (scenario.name ? '<span class="cell-subtext">' + escapeHtml(scenario.name) + '</span>' : '') + '</td>' +
        '  <td><span class="scenarios-table__version">' +
             (scenario.version ? "v" + escapeHtml(scenario.version) : "\u2014") + '</span></td>' +
        '  <td><span class="scenario-counts">' + renderCounts(scenario.steps) + '</span></td>' +
        '  <td><span class="scenarios-table__file text-mono">' + escapeHtml(key) + '</span></td>' +
        '  <td>' + renderValidity(scenario) + '</td>' +
        '  <td class="scenarios-table__actions-col"><span class="table-actions table-actions--schedule">' +
             run + scheduleBtn + '</span></td>' +
        '</tr>';

    const editorRow = scheduleOpen
        ? '<tr class="schedule-editor-row" data-schedule-editor="' + escapeAttr(key) + '">' +
          '<td colspan="7">' +
          '<div class="schedule-editor">' +
          scheduleFieldHtml("scenario-" + key, { label: "Schedule this scenario", allowImmediate: false }) +
          '<div class="schedule-editor__actions">' +
          '<button type="button" class="btn btn--sm" data-catalog-schedule-cancel="' +
          escapeAttr(key) + '">Cancel</button>' +
          '<button type="button" class="btn btn--sm btn--primary" data-catalog-schedule-submit="' +
          escapeAttr(key) + '">Schedule run</button>' +
          '</div></div></td></tr>'
        : '';

    const detailsRow = isExpanded
        ? '<tr class="scenarios-details-row"><td colspan="7">' + renderDetails(scenario) + '</td></tr>'
        : '';

    return row + editorRow + detailsRow;
}

function renderDetails(scenario) {
    return '<div class="scenario-details">' +
        renderStepSummary(scenario) +
        renderYaml(scenario) +
        '</div>';
}

/**
 * Step summary table: task, phase, dependencies, timeout.
 */
function renderStepSummary(scenario) {
    const steps = scenario.steps || [];
    if (steps.length === 0) {
        return '<p class="text-muted">No parsed steps available.</p>';
    }

    const warnings = [];
    steps.forEach(function (step) {
        if (!step.phase) warnings.push("Step " + step.id + " has no phase.");
        if (step.dependsOn && step.dependsOn.length > 0) {
            step.dependsOn.forEach(function (dep) {
                const exists = steps.some(function (other) { return other.id === dep; });
                if (!exists) warnings.push("Step " + step.id + " depends on unknown step " + dep + ".");
            });
        }
    });

    const rows = steps.map(function (step) {
        return '<tr>' +
            '<td class="text-mono">' + escapeHtml(step.id) + '</td>' +
            '<td>' + escapeHtml(step.taskName || "\u2014") + '</td>' +
            '<td><span class="phase-chip phase-chip--' + phaseClass(step.phase) + '">' +
                 escapeHtml(step.phase || "UNKNOWN") + '</span></td>' +
            '<td class="text-mono">' + (step.dependsOn && step.dependsOn.length
                ? escapeHtml(step.dependsOn.join(", "))
                : '<span class="text-muted">\u2014</span>') + '</td>' +
            '<td>' + escapeHtml(step.timeoutMillis ? formatDuration(step.timeoutMillis) : "\u2014") + '</td>' +
            '</tr>';
    }).join("");

    const warningHtml = warnings.length
        ? '<ul class="scenario-warnings">' + warnings.map(function (w) {
            return '<li>' + escapeHtml(w) + '</li>';
        }).join("") + '</ul>'
        : '';

    return '<div class="scenario-section">' +
        '<div class="scenario-section__title">Steps (' + steps.length + ')</div>' +
        warningHtml +
        '<div class="table-scroll table-scroll--short">' +
        '<table class="table table--compact">' +
        '  <thead><tr><th>Step</th><th>Task</th><th>Phase</th><th>Depends on</th><th>Timeout</th></tr></thead>' +
        '  <tbody>' + rows + '</tbody>' +
        '</table></div>' +
        '</div>';
}

function phaseClass(phase) {
    switch (phase) {
        case "PREPARATION": return "prep";
        case "INJECTION": return "inj";
        case "ASSERTION": return "assert";
        default: return "unknown";
    }
}

function renderCounts(steps) {
    const counts = countByPhase(steps);
    return PHASE_ORDER.map(function (phase) {
        return '<span class="scenario-count scenario-count--' + PHASE_SHORT[phase] + '"' +
            ' title="' + escapeAttr(PHASE_LABELS[phase]) + ' steps">' +
            '<b>' + (counts[phase] || 0) + '</b> ' + PHASE_SHORT[phase] + '</span>';
    }).join("");
}

function renderValidity(scenario) {
    if (scenario.valid) {
        return '<span class="scenario-validity scenario-validity--valid" title="Scenario parsed successfully">' +
            '<span class="scenario-validity__mark" aria-hidden="true">\u2713</span>Valid</span>';
    }
    return '<span class="scenario-validity scenario-validity--invalid" title="' +
        escapeAttr(scenario.error || "Invalid scenario") + '">' +
        '<span class="scenario-validity__mark" aria-hidden="true">!</span>Invalid</span>';
}

/**
 * Highlighted YAML source with copy and download actions.
 */
function renderYaml(scenario) {
    if (!scenario.yaml) {
        return '<p class="text-muted">Source YAML unavailable.</p>';
    }

    const errorBanner = scenario.error
        ? '<div class="exec-error-banner">' + escapeHtml(scenario.error) + '</div>'
        : '';

    return errorBanner +
        '<div class="scenario-yaml">' +
        '  <div class="scenario-yaml__head">' +
        '    <span class="scenario-yaml__label">Source YAML</span>' +
        '    <span class="code-toolbar">' +
        '      <button type="button" class="btn btn--sm" data-copy-yaml="' + escapeAttr(scenario.key) + '">Copy</button>' +
        '      <button type="button" class="btn btn--sm" data-download-yaml="' + escapeAttr(scenario.key) + '">Download</button>' +
        '    </span>' +
        '  </div>' +
        '  <pre class="scenario-yaml__code">' + highlightYaml(scenario.yaml) + '</pre>' +
        '</div>';
}

/**
 * Minimal YAML highlighter: keys, comments, strings and numbers.
 * Every chunk is escaped individually, so injected markup cannot be forged
 * from the YAML content.
 *
 * @param {string} yaml
 * @returns {string} HTML
 */
function highlightYaml(yaml) {
    return String(yaml).split("\n").map(highlightYamlLine).join("\n");
}

function highlightYamlLine(line) {
    const token = /(^\s*-\s+)|(^\s*[A-Za-z0-9_.\/-]+(?=\s*:))|(#[^\n]*)|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|(\btrue\b|\bfalse\b|\bnull\b)|(\b-?\d+(?:\.\d+)?\b)/g;
    let html = "";
    let lastIndex = 0;
    let match;
    while ((match = token.exec(line)) !== null) {
        if (match.index > lastIndex) {
            html += escapeHtml(line.slice(lastIndex, match.index));
        }
        const text = match[0];
        if (match[1] !== undefined) html += '<span class="yaml-dash">' + escapeHtml(text) + '</span>';
        else if (match[2] !== undefined) html += '<span class="yaml-key">' + escapeHtml(text) + '</span>';
        else if (match[3] !== undefined) html += '<span class="yaml-comment">' + escapeHtml(text) + '</span>';
        else if (match[4] !== undefined) html += '<span class="yaml-string">' + escapeHtml(text) + '</span>';
        else if (match[5] !== undefined) html += '<span class="yaml-literal">' + escapeHtml(text) + '</span>';
        else html += '<span class="yaml-number">' + escapeHtml(text) + '</span>';
        lastIndex = match.index + text.length;
    }
    html += escapeHtml(line.slice(lastIndex));
    return html;
}

function downloadYaml(yaml, filename) {
    const blob = new Blob([yaml], { type: "text/yaml" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename || "scenario.yaml";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

function countByPhase(steps) {
    const counts = {};
    (steps || []).forEach(function (step) {
        const phase = step.phase || "UNKNOWN";
        counts[phase] = (counts[phase] || 0) + 1;
    });
    return counts;
}
