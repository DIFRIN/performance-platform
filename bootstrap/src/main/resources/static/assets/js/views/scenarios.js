/**
 * Scenario catalog view.
 *
 * Lists the scenarios found in the configured directory
 * ({@code platform.scenarios.directory} / {@code SCENARIOS_DIR}) without
 * executing them. The grid shows the scenario id, version, phase step counts,
 * source file and validity; expanding a row reveals the raw YAML.
 *
 * The header also exposes an "Upload & run" panel: paste YAML or choose a file
 * to execute a scenario immediately (it is not added to the catalog).
 *
 * Public API:
 *   mount(el) → { cleanup: Function }
 */

import { listCatalog, runCatalogScenario, uploadScenario } from "/assets/js/api.js";

/** Display order of phases in the step counters. */
const PHASE_ORDER = ["PREPARATION", "INJECTION", "ASSERTION"];

/** Short labels used in the counters. */
const PHASE_SHORT = {
    PREPARATION: "prep",
    INJECTION: "inj",
    ASSERTION: "assert"
};

/** Full labels used in tooltips. */
const PHASE_LABELS = {
    PREPARATION: "Preparation",
    INJECTION: "Injection",
    ASSERTION: "Assertion"
};

/**
 * Mount the scenario catalog view inside the given element.
 *
 * @param {HTMLElement} el — the container element for this view
 * @returns {{cleanup: Function}}
 */
export function mount(el) {
    /* ---- State ---- */
    let scenarios = [];
    let error = null;
    let query = "";
    let filter = "all";           // "all" | "valid" | "invalid"
    let uploadOpen = false;
    let uploadMode = "paste";     // "paste" | "file"
    let submitting = false;
    const expanded = new Set();

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton();

    const bodyEl = el.querySelector("#catalog-body");
    const summaryEl = el.querySelector("[data-catalog-summary]");
    const refreshBtn = el.querySelector("[data-catalog-refresh]");
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

    /* ---- Event handlers ---- */

    refreshBtn.addEventListener("click", function () {
        fetchCatalog();
    });

    searchInput.addEventListener("input", function () {
        query = searchInput.value.trim().toLowerCase();
        render();
    });

    filterButtons.forEach(function (btn) {
        btn.addEventListener("click", function () {
            filter = btn.dataset.catalogFilter;
            filterButtons.forEach(function (other) {
                var active = other === btn;
                other.classList.toggle("is-active", active);
                other.setAttribute("aria-pressed", active ? "true" : "false");
            });
            render();
        });
    });

    /* ---- Upload panel ---- */

    uploadToggle.addEventListener("click", function () {
        setUploadOpen(!uploadOpen);
    });

    uploadModeButtons.forEach(function (btn) {
        btn.addEventListener("click", function () {
            uploadMode = btn.dataset.uploadMode;
            uploadModeButtons.forEach(function (other) {
                var active = other === btn;
                other.classList.toggle("is-active", active);
                other.setAttribute("aria-pressed", active ? "true" : "false");
            });
            uploadPanes.forEach(function (pane) {
                pane.hidden = pane.dataset.uploadPane !== uploadMode;
            });
            clearUploadErrors();
        });
    });

    uploadForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        if (submitting) return;
        clearUploadErrors();

        var payload;
        if (uploadMode === "paste") {
            var yaml = uploadTextarea.value.trim();
            if (!yaml) {
                showUploadError("yaml", "Paste a YAML scenario first.");
                return;
            }
            payload = { yaml: yaml };
        } else {
            var file = uploadFile.files && uploadFile.files.length > 0 ? uploadFile.files[0] : null;
            if (!file) {
                showUploadError("file", "Choose a .yaml or .yml file.");
                return;
            }
            payload = { file: file };
        }

        submitting = true;
        uploadSubmit.disabled = true;
        uploadSubmit.classList.add("is-loading");
        uploadSubmit.textContent = "Starting…";
        try {
            var result = await uploadScenario(payload);
            window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
        } catch (err) {
            if (err.details && err.details.length > 0) {
                err.details.forEach(function (d) {
                    showUploadError(d.field, d.message);
                });
            } else {
                showUploadError("input", err.message || "Upload failed");
            }
        } finally {
            submitting = false;
            uploadSubmit.disabled = false;
            uploadSubmit.classList.remove("is-loading");
            uploadSubmit.textContent = "Run";
        }
    });

    el.querySelector("[data-upload-cancel]").addEventListener("click", function () {
        setUploadOpen(false);
    });

    function setUploadOpen(open) {
        uploadOpen = open;
        uploadPanel.hidden = !open;
        uploadToggle.setAttribute("aria-expanded", open ? "true" : "false");
        if (open) {
            clearUploadErrors();
            if (uploadMode === "paste") uploadTextarea.focus();
        }
    }

    function showUploadError(field, message) {
        var slot = uploadPanel.querySelector('[data-error-field="' + field + '"]');
        var target = slot || uploadPanel.querySelector('[data-error-field="input"]');
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

    /* ---- Grid handlers ---- */

    bodyEl.addEventListener("click", function (e) {
        var toggleBtn = e.target.closest("[data-catalog-toggle]");
        if (toggleBtn && !toggleBtn.disabled) {
            toggleEntry(toggleBtn.dataset.catalogToggle);
            return;
        }

        var runBtn = e.target.closest("[data-catalog-run]");
        if (runBtn) {
            runScenario(runBtn.dataset.catalogRun, runBtn);
            return;
        }

        var clearBtn = e.target.closest("[data-catalog-clear]");
        if (clearBtn) {
            query = "";
            filter = "all";
            searchInput.value = "";
            filterButtons.forEach(function (btn) {
                var active = btn.dataset.catalogFilter === "all";
                btn.classList.toggle("is-active", active);
                btn.setAttribute("aria-pressed", active ? "true" : "false");
            });
            render();
        }
    });

    /* ---- Fetch & render ---- */

    function render() {
        var visible = scenarios.filter(function (scenario) {
            return matches(scenario, query, filter);
        });
        bodyEl.innerHTML = renderBody(visible, scenarios, error, expanded);
        updateSummary(summaryEl, scenarios, visible.length, query, filter);
    }

    async function fetchCatalog() {
        refreshBtn.disabled = true;
        refreshBtn.classList.add("is-loading");
        try {
            scenarios = await listCatalog();
            error = null;
        } catch (e) {
            console.error("scenarios: fetch failed", e);
            error = e.message || "Failed to load scenarios";
        } finally {
            refreshBtn.disabled = false;
            refreshBtn.classList.remove("is-loading");
        }
        render();
    }

    function toggleEntry(key) {
        if (expanded.has(key)) {
            expanded.delete(key);
        } else {
            expanded.add(key);
        }
        render();
    }

    async function runScenario(key, btn) {
        if (btn.disabled) return;
        btn.disabled = true;
        btn.classList.add("is-loading");
        btn.textContent = "Starting…";
        try {
            var result = await runCatalogScenario(key);
            window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
        } catch (e) {
            alert("Unable to run scenario: " + (e.message || "Unknown error"));
            btn.disabled = false;
            btn.classList.remove("is-loading");
            btn.textContent = "Run";
        }
    }

    fetchCatalog();

    return {
        cleanup: function () {
            // No timers or global listeners to release.
        }
    };
}

// ---------------------------------------------------------------------------
// Filtering
// ---------------------------------------------------------------------------

function matches(scenario, query, filter) {
    if (filter === "valid" && !scenario.valid) return false;
    if (filter === "invalid" && scenario.valid) return false;
    if (!query) return true;

    var haystack = [
        scenario.key,
        scenario.scenarioId,
        scenario.name,
        scenario.version,
        (scenario.tags || []).join(" ")
    ].filter(Boolean).join(" ").toLowerCase();

    return haystack.indexOf(query) !== -1;
}

function updateSummary(summaryEl, all, visible, query, filter) {
    if (!summaryEl) return;

    if (all.length === 0) {
        summaryEl.textContent = "No scenarios loaded";
        return;
    }

    var steps = all.reduce(function (sum, scenario) {
        return sum + (scenario.stepCount || 0);
    }, 0);
    var filtered = query || filter !== "all";

    summaryEl.textContent = filtered
        ? visible + " of " + all.length + " scenarios"
        : all.length + (all.length === 1 ? " scenario" : " scenarios") +
          " · " + steps + (steps === 1 ? " step" : " steps");
}

// ---------------------------------------------------------------------------
// Skeleton
// ---------------------------------------------------------------------------

function buildSkeleton() {
    return '' +
        '<div class="card">' +
        '  <div class="scenarios-header">' +
        '    <div>' +
        '      <div class="card__title">Scenarios</div>' +
        '      <div class="scenarios-summary text-muted" data-catalog-summary>Loading…</div>' +
        '    </div>' +
        '    <div class="scenarios-controls">' +
        '      <input type="search" class="scenarios-search" placeholder="Filter by id, file or tag…"' +
        '             aria-label="Filter scenarios" data-catalog-search>' +
        '      <div class="segmented" role="group" aria-label="Filter by validity">' +
        '        <button type="button" class="segmented__btn is-active" data-catalog-filter="all" aria-pressed="true">All</button>' +
        '        <button type="button" class="segmented__btn" data-catalog-filter="valid" aria-pressed="false">Valid</button>' +
        '        <button type="button" class="segmented__btn" data-catalog-filter="invalid" aria-pressed="false">Invalid</button>' +
        '      </div>' +
        '      <button type="button" class="btn btn--sm" data-upload-toggle aria-expanded="false">Upload &amp; run</button>' +
        '      <button type="button" class="btn btn--sm" data-catalog-refresh>Refresh</button>' +
        '    </div>' +
        '  </div>' +
        '  <section class="scenarios-upload" data-upload-panel hidden>' +
        '    <div class="scenarios-upload__head">' +
        '      <div class="segmented" role="group" aria-label="Scenario input">' +
        '        <button type="button" class="segmented__btn is-active" data-upload-mode="paste" aria-pressed="true">Paste YAML</button>' +
        '        <button type="button" class="segmented__btn" data-upload-mode="file" aria-pressed="false">Choose file</button>' +
        '      </div>' +
        '      <span class="scenarios-upload__hint text-muted">Runs immediately; not added to the catalog.</span>' +
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
        '      <div class="scenarios-upload__actions">' +
        '        <button type="button" class="btn btn--sm" data-upload-cancel>Cancel</button>' +
        '        <button type="submit" class="btn btn--sm btn--primary" data-upload-submit>Run</button>' +
        '      </div>' +
        '    </form>' +
        '  </section>' +
        '  <div id="catalog-body" class="scenarios-list">' +
        '    <div class="detail-loading">Loading…</div>' +
        '  </div>' +
        '</div>';
}

// ---------------------------------------------------------------------------
// Rendering — grid
// ---------------------------------------------------------------------------

function renderBody(visible, all, error, expanded) {
    if (error) {
        return '<div class="exec-error-banner">' + escapeHtml(error) + '</div>';
    }

    if (all.length === 0) {
        return '' +
            '<div class="empty-state">' +
            '  <div class="empty-state__text">No scenarios configured</div>' +
            '  <p class="empty-state__hint text-muted">Set <code>SCENARIOS_DIR</code> ' +
            '      (or <code>platform.scenarios.directory</code>) to a folder of YAML files, then refresh.</p>' +
            '</div>';
    }

    if (visible.length === 0) {
        return '' +
            '<div class="empty-state">' +
            '  <div class="empty-state__text">No matching scenario</div>' +
            '  <p class="empty-state__hint text-muted">Try another id, file or filter.</p>' +
            '  <button type="button" class="btn btn--sm" data-catalog-clear>Clear filters</button>' +
            '</div>';
    }

    return '' +
        '<table class="table scenarios-table">' +
        '  <thead>' +
        '    <tr>' +
        '      <th class="scenarios-table__expand" aria-label="Expand"></th>' +
        '      <th>Scenario ID</th>' +
        '      <th>Version</th>' +
        '      <th>Steps</th>' +
        '      <th>File</th>' +
        '      <th>Status</th>' +
        '      <th class="scenarios-table__actions-col">Actions</th>' +
        '    </tr>' +
        '  </thead>' +
        '  <tbody>' +
        visible.map(function (scenario) {
            return renderScenarioRows(scenario, expanded.has(scenario.key));
        }).join("") +
        '  </tbody>' +
        '</table>';
}

function renderScenarioRows(scenario, isExpanded) {
    var key = scenario.key;
    var rowClass = "scenarios-table__row" + (isExpanded ? " scenario-item--expanded" : "");

    var expand = '<button type="button" class="scenario-item__expand"' +
        ' data-catalog-toggle="' + escapeAttr(key) + '"' +
        ' aria-expanded="' + (isExpanded ? "true" : "false") + '"' +
        ' aria-label="' + (isExpanded ? "Hide YAML" : "Show YAML") + '"' +
        ' title="' + (isExpanded ? "Hide YAML" : "Show YAML") + '">' +
        '<span class="scenario-item__chevron" aria-hidden="true">▸</span></button>';

    var run = scenario.valid
        ? '<button type="button" class="btn btn--sm btn--primary" data-catalog-run="' +
          escapeAttr(key) + '">Run</button>'
        : '<button type="button" class="btn btn--sm" disabled' +
          ' title="Fix the YAML before running this scenario">Run</button>';

    var row = '<tr class="' + rowClass + '" data-catalog-key="' + escapeAttr(key) + '">' +
        '  <td class="scenarios-table__expand">' + expand + '</td>' +
        '  <td><span class="scenarios-table__id">' + escapeHtml(scenario.scenarioId || "—") + '</span></td>' +
        '  <td><span class="scenarios-table__version">' +
             (scenario.version ? "v" + escapeHtml(scenario.version) : "—") + '</span></td>' +
        '  <td><span class="scenario-counts">' + renderCounts(scenario.steps) + '</span></td>' +
        '  <td><span class="scenarios-table__file">' + escapeHtml(key) + '</span></td>' +
        '  <td>' + renderValidity(scenario) + '</td>' +
        '  <td class="scenarios-table__actions-col"><span class="table-actions">' + run + '</span></td>' +
        '</tr>';

    if (!isExpanded) {
        return row;
    }

    return row + '<tr class="scenarios-details-row"><td colspan="7">' +
        renderYaml(scenario.yaml) + '</td></tr>';
}

function renderCounts(steps) {
    var counts = countByPhase(steps);
    var parts = PHASE_ORDER.map(function (phase) {
        return '<span class="scenario-count scenario-count--' + PHASE_SHORT[phase] + '"' +
               ' title="' + escapeAttr(PHASE_LABELS[phase]) + ' steps">' +
               '<b>' + (counts[phase] || 0) + '</b> ' + PHASE_SHORT[phase] + '</span>';
    });
    return parts.join("");
}

function renderValidity(scenario) {
    if (scenario.valid) {
        return '<span class="scenario-validity scenario-validity--valid" title="Scenario parsed successfully">' +
               '<span class="scenario-validity__mark" aria-hidden="true">✓</span>Valid</span>';
    }
    return '<span class="scenario-validity scenario-validity--invalid" title="' +
           escapeAttr(scenario.error || "Invalid scenario") + '">' +
           '<span class="scenario-validity__mark" aria-hidden="true">!</span>Invalid</span>';
}

/**
 * Raw YAML source, displayed over a neutral gray background.
 */
function renderYaml(yaml) {
    if (!yaml) {
        return '<p class="text-muted">Source YAML unavailable.</p>';
    }

    return '' +
        '<div class="scenario-yaml">' +
        '  <div class="scenario-yaml__label">Source YAML</div>' +
        '  <pre class="scenario-yaml__code">' + escapeHtml(yaml) + '</pre>' +
        '</div>';
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

function countByPhase(steps) {
    var counts = {};
    (steps || []).forEach(function (step) {
        var phase = step.phase || "UNKNOWN";
        counts[phase] = (counts[phase] || 0) + 1;
    });
    return counts;
}

// ---- HTML escaping utilities ----

function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function escapeAttr(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}
