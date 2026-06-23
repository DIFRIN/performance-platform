/**
 * Report view.
 * PDR-029 — ISSUE-130
 *
 * Mounts the report view for a completed execution:
 * - Polls GET /api/v1/executions/{id}/report?format=html every 3 seconds
 * - While 404: displays "rapport en cours de generation"
 * - On 200: loads the HTML report in a full-width <iframe>
 * - Download buttons for PDF and JSON formats (enabled only when report is available)
 * - The client NEVER triggers report generation (reports are generated automatically
 *   at the end of the execution lifecycle).
 *
 * Public API:
 *   mount(el, executionId) → { cleanup: Function }
 *
 * The cleanup function clears the polling interval so there is no leak
 * when the router switches to another view.
 */

import { reportUrl, isReportAvailable } from "/assets/js/api.js";

/** Polling interval in milliseconds. */
const POLL_INTERVAL_MS = 3000;

/**
 * Mount the report view inside the given element.
 *
 * @param {HTMLElement} el — the container element for this view
 * @param {string} executionId — the execution identifier from the route
 * @returns {{cleanup: Function}}
 */
export function mount(el, executionId) {
    /* ---- State ---- */
    var intervalId = null;
    var reportReady = false;

    /* ---- Skeleton ---- */
    el.innerHTML = buildSkeleton(executionId);

    var bodyEl = el.querySelector("#report-body");
    var backLink = el.querySelector("[data-report-back]");
    var downloadPdfBtn = el.querySelector("[data-report-download='pdf']");
    var downloadJsonBtn = el.querySelector("[data-report-download='json']");

    /* ---- Event handlers ---- */

    // Back link
    if (backLink) {
        backLink.addEventListener("click", function (e) {
            e.preventDefault();
            window.location.hash = "#/executions/" + encodeURIComponent(executionId);
        });
    }

    // Download buttons
    if (downloadPdfBtn) {
        downloadPdfBtn.addEventListener("click", function (e) {
            e.preventDefault();
            window.open(reportUrl(executionId, "pdf"), "_blank");
        });
    }

    if (downloadJsonBtn) {
        downloadJsonBtn.addEventListener("click", function (e) {
            e.preventDefault();
            window.open(reportUrl(executionId, "json"), "_blank");
        });
    }

    /* ---- Poll & Render loop ---- */

    async function pollAndRender() {
        try {
            var available = await isReportAvailable(executionId);
            if (available) {
                renderIframe(bodyEl, executionId);
                enableDownloadButtons(downloadPdfBtn, downloadJsonBtn);
                reportReady = true;
            } else if (!reportReady) {
                renderPending(bodyEl);
            }
        } catch (e) {
            console.error("report: poll failed", e);
            if (!reportReady) {
                renderError(bodyEl, e.message || "Failed to check report availability");
            }
        }
    }

    // Initial fetch
    pollAndRender();

    // Start polling
    intervalId = setInterval(pollAndRender, POLL_INTERVAL_MS);

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
        '<div class="detail-card report-card">' +
        '  <div class="detail-card__header">' +
        '    <div class="detail-header__left">' +
        '      <a href="#/executions/' + escapeAttr(executionId) + '" class="detail-back-link" data-report-back>&larr; Back to Execution</a>' +
        '      <h1 class="detail-header__title">Report for <span class="text-mono">' + escapeHtml(executionId) + '</span></h1>' +
        '    </div>' +
        '    <div class="detail-header__right report-download-actions">' +
        '      <button class="btn btn--sm" data-report-download="pdf" disabled title="Download PDF">PDF</button>' +
        '      <button class="btn btn--sm" data-report-download="json" disabled title="Download JSON">JSON</button>' +
        '    </div>' +
        '  </div>' +
        '  <div id="report-body">' +
        '    <div class="detail-loading report-loading">' +
        '      <span class="report-loading__text">Report generation in progress...</span>' +
        '    </div>' +
        '  </div>' +
        '</div>';
}

// ---------------------------------------------------------------------------
// Internal helpers — rendering
// ---------------------------------------------------------------------------

/**
 * Render the pending state: report not yet available.
 * @param {HTMLElement} bodyEl
 */
function renderPending(bodyEl) {
    bodyEl.innerHTML = '' +
        '<div class="detail-loading report-loading">' +
        '  <span class="report-loading__text">Report generation in progress...</span>' +
        '  <span class="report-loading__hint text-muted">The report is generated automatically. Please wait.</span>' +
        '</div>';
}

/**
 * Render the HTML report in an iframe.
 * @param {HTMLElement} bodyEl
 * @param {string} executionId
 */
function renderIframe(bodyEl, executionId) {
    bodyEl.innerHTML = '' +
        '<iframe class="report-iframe" src="' + escapeAttr(reportUrl(executionId, "html")) + '"' +
        '        sandbox="allow-scripts allow-same-origin"' +
        '        title="Execution Report"' +
        '        loading="lazy">' +
        '</iframe>';
}

/**
 * Render an error state.
 * @param {HTMLElement} bodyEl
 * @param {string} message
 */
function renderError(bodyEl, message) {
    bodyEl.innerHTML = '' +
        '<div class="empty-state">' +
        '  <div class="empty-state__text exec-error">' + escapeHtml(message) + '</div>' +
        '</div>';
}

/**
 * Enable the download buttons once the report is available.
 * @param {HTMLElement|null} pdfBtn
 * @param {HTMLElement|null} jsonBtn
 */
function enableDownloadButtons(pdfBtn, jsonBtn) {
    if (pdfBtn) pdfBtn.disabled = false;
    if (jsonBtn) jsonBtn.disabled = false;
}

// ---------------------------------------------------------------------------
// Internal helpers — formatting & utilities
// ---------------------------------------------------------------------------

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
