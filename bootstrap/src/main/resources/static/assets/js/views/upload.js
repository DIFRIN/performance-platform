/**
 * Upload scenario view.
 * PDR-029 — ISSUE-129
 *
 * Mounts the scenario upload form with:
 * - Two inputs: file upload (multipart) OR textarea (inline YAML)
 * - Submit button with execution-immediate (no catalog)
 * - Validation errors displayed field-level inline (near each input)
 * - On 202 success: redirects to /#/executions/{executionId}
 * - Execution is immediate — no catalog, no manual report trigger
 *
 * Public API:
 *   mount(el) → { cleanup: Function }
 */

import { uploadScenario } from "/assets/js/api.js";

/**
 * Mount the upload view inside the given element.
 *
 * @param {HTMLElement} el — the container element for this view
 * @returns {{cleanup: Function}}
 */
export function mount(el) {
    /* ---- State ---- */
    let submitting = false;

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton();

    const formEl = el.querySelector("#upload-form");
    const fileInput = el.querySelector("#upload-file");
    const yamlTextarea = el.querySelector("#upload-yaml");
    const submitBtn = el.querySelector("#upload-submit");
    const feedbackEl = el.querySelector("#upload-feedback");

    /* ---- Event handlers ---- */

    // Clear the other input when one is focused/changed
    fileInput.addEventListener("change", function () {
        if (fileInput.files && fileInput.files.length > 0) {
            yamlTextarea.value = "";
            clearFieldErrors();
        }
    });

    yamlTextarea.addEventListener("input", function () {
        if (yamlTextarea.value.trim().length > 0) {
            fileInput.value = "";
            clearFieldErrors();
        }
    });

    // Submit
    formEl.addEventListener("submit", async function (e) {
        e.preventDefault();

        if (submitting) return;

        var file = fileInput.files && fileInput.files.length > 0
            ? fileInput.files[0]
            : null;
        var yaml = yamlTextarea.value.trim() || null;

        if (!file && !yaml) {
            showFieldErrors([{
                field: "input",
                message: "Either a file or YAML content is required"
            }]);
            return;
        }

        submitting = true;
        submitBtn.disabled = true;
        clearFieldErrors();
        hideFeedback();

        try {
            var result = await uploadScenario(file ? { file: file } : { yaml: yaml });
            // 202 — redirect to execution detail
            window.location.hash = "#/executions/" + encodeURIComponent(result.executionId);
        } catch (err) {
            if (err.details && err.details.length > 0) {
                showFieldErrors(err.details);
            } else {
                showFeedback(err.message || "Upload failed", "error");
            }
        } finally {
            submitting = false;
            submitBtn.disabled = false;
        }
    });

    /* ---- Cleanup ---- */
    return {
        cleanup: function () {
            // No polling to clear; just a no-op
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
        '<div class="card upload-card">' +
        '  <div class="card__title mb-2">Upload Scenario</div>' +
        '  <p class="text-muted mb-2" style="font-size:0.8125rem">' +
        'Upload a YAML file or paste scenario content. The scenario is executed immediately — no catalog.</p>' +
        '  <div id="upload-feedback" class="upload-feedback" style="display:none"></div>' +
        '  <form id="upload-form" class="upload-form">' +
        '    <!-- File input -->' +
        '    <div class="upload-field">' +
        '      <label class="upload-label" for="upload-file">Scenario File (.yaml / .yml)</label>' +
        '      <input type="file" id="upload-file" class="upload-input-file" accept=".yaml,.yml">' +
        '      <span class="upload-field-hint">Upload a YAML scenario file</span>' +
        '      <div class="upload-field-error" data-error-field="file"></div>' +
        '    </div>' +
        '    <div class="upload-divider">' +
        '      <span class="upload-divider__text">or</span>' +
        '    </div>' +
        '    <!-- Textarea input -->' +
        '    <div class="upload-field">' +
        '      <label class="upload-label" for="upload-yaml">YAML Content</label>' +
        '      <textarea id="upload-yaml" class="upload-textarea" rows="12"' +
        '        placeholder="Paste your scenario YAML here..." spellcheck="false"></textarea>' +
        '      <span class="upload-field-hint">Paste YAML scenario content directly</span>' +
        '      <div class="upload-field-error" data-error-field="yaml"></div>' +
        '    </div>' +
        '    <!-- Global error area -->' +
        '    <div class="upload-field-error upload-field-error--global" data-error-field="input"></div>' +
        '    <!-- Submit -->' +
        '    <button type="submit" id="upload-submit" class="btn btn--primary upload-submit">' +
        '      Execute Scenario</button>' +
        '  </form>' +
        '</div>';
}

/**
 * Display field-level validation errors inline next to each field.
 * @param {Array<{field: string, message: string}>} errors
 */
function showFieldErrors(errors) {
    // Reset all error slots
    clearFieldErrors();

    errors.forEach(function (err) {
        var slot = document.querySelector('[data-error-field="' + CSS.escape(err.field) + '"]');
        if (slot) {
            slot.textContent = err.message;
            slot.style.display = "block";
        } else {
            // Fallback: show in the global "input" slot
            var fallbackSlot = document.querySelector('[data-error-field="input"]');
            if (fallbackSlot) {
                fallbackSlot.textContent = (fallbackSlot.textContent
                    ? fallbackSlot.textContent + "; "
                    : "") + err.field + ": " + err.message;
                fallbackSlot.style.display = "block";
            }
        }
    });

    // Highlight the relevant input
    errors.forEach(function (err) {
        if (err.field === "file") {
            var fileInput = document.getElementById("upload-file");
            if (fileInput) fileInput.classList.add("upload-input--error");
        }
        if (err.field === "yaml") {
            var textarea = document.getElementById("upload-yaml");
            if (textarea) textarea.classList.add("upload-input--error");
        }
    });
}

/**
 * Clear all inline field errors.
 */
function clearFieldErrors() {
    var slots = document.querySelectorAll(".upload-field-error");
    slots.forEach(function (slot) {
        slot.textContent = "";
        slot.style.display = "none";
    });

    var inputs = document.querySelectorAll(".upload-input--error");
    inputs.forEach(function (input) {
        input.classList.remove("upload-input--error");
    });
}

/**
 * Show a feedback message (success or error).
 * @param {string} message
 * @param {string} type — "error" or "success"
 */
function showFeedback(message, type) {
    var el = document.getElementById("upload-feedback");
    if (!el) return;
    el.textContent = message;
    el.className = "upload-feedback upload-feedback--" + type;
    el.style.display = "block";
}

/**
 * Hide the feedback banner.
 */
function hideFeedback() {
    var el = document.getElementById("upload-feedback");
    if (el) {
        el.textContent = "";
        el.style.display = "none";
    }
}
