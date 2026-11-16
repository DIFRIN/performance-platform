/**
 * Report view.
 *
 * - Polls the HTML report every 3 seconds until it is available
 *   (visibility-aware: polling pauses while the tab is hidden)
 * - Shows "report generation in progress" until the file exists
 * - Renders the report in a full-width iframe once available
 * - Actions: PDF, JSON, HTML download, open in new tab, print
 *
 * The client NEVER triggers report generation: reports are produced at the
 * end of the execution lifecycle.
 *
 * Public API:
 *   mount(el, executionId) -> { cleanup: Function }
 */

import { reportUrl, isReportAvailable } from "/assets/js/api.js";
import {
    createPoller,
    refreshStatusText,
    toast,
    escapeHtml,
    escapeAttr
} from "/assets/js/ui.js";

const POLL_INTERVAL_MS = 3000;

/**
 * Mount the report view.
 *
 * @param {HTMLElement} el
 * @param {string} executionId
 * @returns {{cleanup: Function}}
 */
export function mount(el, executionId) {
    let reportReady = false;
    let disposed = false;
    let iframeEl = null;

    el.innerHTML = buildSkeleton(executionId);

    const bodyEl = el.querySelector("#report-body");
    const backLink = el.querySelector("[data-report-back]");
    const refreshStateEl = el.querySelector("[data-refresh-state]");
    const actionButtons = el.querySelectorAll("[data-report-action]");

    if (backLink) {
        backLink.addEventListener("click", function (e) {
            e.preventDefault();
            window.location.hash = "#/executions/" + encodeURIComponent(executionId);
        });
    }

    actionButtons.forEach(function (btn) {
        btn.addEventListener("click", function (e) {
            e.preventDefault();
            if (btn.disabled) return;
            const action = btn.dataset.reportAction;
            const format = btn.dataset.reportFormat;

            if (action === "open" && format) {
                window.open(reportUrl(executionId, format), "_blank", "noopener");
                return;
            }
            if (action === "download" && format) {
                const link = document.createElement("a");
                link.href = reportUrl(executionId, format);
                link.download = "report-" + executionId + "." + format;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                return;
            }
            if (action === "print") {
                printReport();
            }
        });
    });

    function printReport() {
        if (!iframeEl || !iframeEl.contentWindow) {
            window.open(reportUrl(executionId, "html"), "_blank", "noopener");
            return;
        }
        try {
            iframeEl.contentWindow.focus();
            iframeEl.contentWindow.print();
        } catch (e) {
            console.error("report: print failed", e);
            toast("Print unavailable, opening the report instead", { variant: "warning" });
            window.open(reportUrl(executionId, "html"), "_blank", "noopener");
        }
    }

    async function pollAndRender() {
        if (disposed || reportReady) return;
        try {
            const available = await isReportAvailable(executionId);
            if (disposed) return;
            if (available) {
                renderIframe(bodyEl, executionId);
                iframeEl = bodyEl.querySelector("iframe");
                enableActions(actionButtons);
                reportReady = true;
                poller.pause();
            } else {
                renderPending(bodyEl);
            }
        } catch (e) {
            console.error("report: poll failed", e);
            if (!disposed && !reportReady) {
                renderError(bodyEl, e.message || "Failed to check report availability", function () {
                    poller.refresh();
                });
            }
        }
    }

    const poller = createPoller({
        run: pollAndRender,
        intervalMs: POLL_INTERVAL_MS,
        onState: function (next) {
            if (refreshStateEl) refreshStateEl.textContent = refreshStatusText(next);
        }
    });
    poller.start();

    return {
        cleanup: function () {
            disposed = true;
            poller.stop();
        }
    };
}

// ---------------------------------------------------------------------------
// Skeleton
// ---------------------------------------------------------------------------

function buildSkeleton(executionId) {
    return '' +
        '<div class="detail-card report-card">' +
        '  <div class="detail-card__header">' +
        '    <div class="detail-header__left">' +
        '      <a href="#/executions/' + escapeAttr(executionId) + '" class="detail-back-link" data-report-back>&larr; Back to Execution</a>' +
        '      <h1 class="detail-header__title" data-page-heading>Report for <span class="text-mono">' +
        escapeHtml(executionId) + '</span></h1>' +
        '      <span class="refresh-state" data-refresh-state aria-live="polite">Waiting for data\u2026</span>' +
        '    </div>' +
        '    <div class="detail-header__right report-download-actions">' +
        '      <button class="btn btn--sm" data-report-action="open" data-report-format="html" disabled title="Open the HTML report in a new tab">Open in new tab</button>' +
        '      <button class="btn btn--sm" data-report-action="print" disabled title="Print the report">Print</button>' +
        '      <button class="btn btn--sm" data-report-action="download" data-report-format="html" disabled title="Download HTML">HTML</button>' +
        '      <button class="btn btn--sm" data-report-action="download" data-report-format="pdf" disabled title="Download PDF">PDF</button>' +
        '      <button class="btn btn--sm" data-report-action="download" data-report-format="json" disabled title="Download JSON">JSON</button>' +
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
// Rendering
// ---------------------------------------------------------------------------

function renderPending(bodyEl) {
    bodyEl.innerHTML = '<div class="detail-loading report-loading">' +
        '  <span class="report-loading__text">Report generation in progress...</span>' +
        '  <span class="report-loading__hint text-muted">The report is generated automatically. Please wait.</span>' +
        '</div>';
}

function renderIframe(bodyEl, executionId) {
    bodyEl.innerHTML = '<iframe class="report-iframe" src="' + escapeAttr(reportUrl(executionId, "html")) + '"' +
        '        sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox allow-modals"' +
        '        title="Execution Report"' +
        '        loading="lazy">' +
        '</iframe>';
}

function renderError(bodyEl, message, onRetry) {
    bodyEl.innerHTML = '<div class="empty-state">' +
        '  <div class="empty-state__text exec-error">' + escapeHtml(message) + '</div>' +
        '  <button type="button" class="btn btn--sm" data-report-retry>Retry</button>' +
        '</div>';
    const retry = bodyEl.querySelector("[data-report-retry]");
    if (retry && onRetry) retry.addEventListener("click", onRetry);
}

function enableActions(buttons) {
    buttons.forEach(function (btn) {
        if (btn.dataset.reportAction === "print") btn.disabled = false;
        else btn.disabled = false;
    });
}
