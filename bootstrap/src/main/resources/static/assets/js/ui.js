/**
 * Shared UI utilities: non-blocking feedback, clipboard, formatting and
 * visibility-aware polling.
 *
 * Feedback policy:
 * - success / information → toast (never blocks the UI)
 * - action errors → toast + inline message next to the affected row
 * - destructive actions only (delete, abort) → blocking confirmation
 *
 * Public API:
 *   toast(message, {variant, timeout})
 *   announce(message)                     — screen-reader live region
 *   confirmDestructive(message)           — boolean, native confirm
 *   copyText(text)                        — Promise<boolean>
 *   copyableIdHtml(id, {label, title})    — click-to-copy button
 *   createPoller({run, intervalMs, onState})
 *   formatDateTime / formatTime / formatRelative / formatDuration
 *   elapsedMs(startedAt, updatedAt)
 *   timeZoneLabel()
 */

let toastRegion = null;
let liveRegion = null;

// ---------------------------------------------------------------------------
// Feedback
// ---------------------------------------------------------------------------

/**
 * Display a transient, non-blocking notification.
 *
 * @param {string} message — human readable message
 * @param {{variant?: "info"|"success"|"warning"|"error", timeout?: number}} [options]
 * @returns {HTMLElement|null} the toast element
 */
export function toast(message, options) {
    const opts = options || {};
    const variant = opts.variant || "info";
    const timeout = opts.timeout === undefined ? 4500 : opts.timeout;
    if (!message) return null;

    const region = ensureToastRegion();
    const el = document.createElement("div");
    el.className = "toast toast--" + variant;

    const text = document.createElement("span");
    text.className = "toast__text";
    text.textContent = message;

    const close = document.createElement("button");
    close.type = "button";
    close.className = "toast__close";
    close.setAttribute("aria-label", "Dismiss notification");
    close.textContent = "\u00d7";
    close.addEventListener("click", function () { dismiss(el); });

    el.appendChild(text);
    el.appendChild(close);
    region.appendChild(el);

    if (timeout > 0) {
        setTimeout(function () { dismiss(el); }, timeout);
    }
    return el;
}

function dismiss(el) {
    if (!el || !el.parentNode) return;
    el.classList.add("toast--leaving");
    setTimeout(function () {
        if (el.parentNode) el.parentNode.removeChild(el);
    }, 160);
}

function ensureToastRegion() {
    if (toastRegion && document.body.contains(toastRegion)) return toastRegion;
    toastRegion = document.createElement("div");
    toastRegion.className = "toast-region";
    toastRegion.setAttribute("role", "status");
    toastRegion.setAttribute("aria-live", "polite");
    toastRegion.setAttribute("aria-atomic", "false");
    document.body.appendChild(toastRegion);
    return toastRegion;
}

/**
 * Announce a message to assistive technologies without visual output.
 * @param {string} message
 */
export function announce(message) {
    if (!message) return;
    if (!liveRegion || !document.body.contains(liveRegion)) {
        liveRegion = document.createElement("div");
        liveRegion.className = "sr-only";
        liveRegion.id = "sr-announcer";
        liveRegion.setAttribute("role", "status");
        liveRegion.setAttribute("aria-live", "polite");
        liveRegion.setAttribute("aria-atomic", "true");
        document.body.appendChild(liveRegion);
    }
    // Reset first so repeated identical messages are still announced.
    liveRegion.textContent = "";
    setTimeout(function () { liveRegion.textContent = message; }, 30);
}

/**
 * Blocking confirmation, reserved for destructive actions.
 * @param {string} message
 * @returns {boolean}
 */
export function confirmDestructive(message) {
    return window.confirm(message);
}

// ---------------------------------------------------------------------------
// Clipboard
// ---------------------------------------------------------------------------

/**
 * Copy text to the clipboard, with a legacy fallback.
 * @param {string} text
 * @returns {Promise<boolean>}
 */
export async function copyText(text) {
    if (!text && text !== 0) return false;
    const value = String(text);
    try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(value);
            return true;
        }
    } catch {
        // fall through to the legacy path
    }
    try {
        const area = document.createElement("textarea");
        area.value = value;
        area.setAttribute("readonly", "readonly");
        area.style.position = "fixed";
        area.style.opacity = "0";
        document.body.appendChild(area);
        area.select();
        const ok = document.execCommand("copy");
        document.body.removeChild(area);
        return ok;
    } catch {
        return false;
    }
}

/**
 * Short, click-to-copy identifier.
 * Delegated views copy the full value carried by {@code data-copy-id}.
 *
 * @param {string} id — full identifier
 * @param {{label?: string, title?: string}} [options]
 * @returns {string} HTML
 */
export function copyableIdHtml(id, options) {
    if (!id) return '<span class="text-muted">\u2014</span>';
    const opts = options || {};
    const label = opts.label || shortId(id);
    const title = opts.title || ("Copy " + id);
    return '<button type="button" class="copy-id" data-copy-id="' + escapeAttr(id) + '"' +
        ' title="' + escapeAttr(title) + '" aria-label="' + escapeAttr(title) + '">' +
        '<span class="copy-id__text text-mono">' + escapeHtml(label) + '</span>' +
        '<span class="copy-id__icon" aria-hidden="true">' +
        '<svg viewBox="0 0 16 16" width="12" height="12" fill="none">' +
        '<rect x="5.5" y="5.5" width="8" height="8" rx="1.2" stroke="currentColor" stroke-width="1.4"/>' +
        '<path d="M10.5 5.5V3.7c0-.66-.54-1.2-1.2-1.2H3.7c-.66 0-1.2.54-1.2 1.2v5.6c0 .66.54 1.2 1.2 1.2H5.5" stroke="currentColor" stroke-width="1.4"/>' +
        '</svg></span>' +
        '</button>';
}

/**
 * Handle a click on any {@code [data-copy-id]} element.
 * @param {MouseEvent} event
 * @returns {boolean} true when a copy was attempted
 */
export function handleCopyClick(event) {
    const target = event.target.closest("[data-copy-id]");
    if (!target) return false;
    event.preventDefault();
    event.stopPropagation();
    const value = target.dataset.copyId;
    copyText(value).then(function (ok) {
        toast(ok ? "Copied " + shortId(value) : "Copy failed", {
            variant: ok ? "success" : "error",
            timeout: 2200
        });
    });
    return true;
}

// ---------------------------------------------------------------------------
// Polling
// ---------------------------------------------------------------------------

/**
 * Create a polling loop that pauses while the tab is hidden.
 *
 * The {@code run} callback is responsible for its own error handling; it may
 * return a promise. {@code onState} receives {@code {refreshing, lastUpdated}}.
 *
 * @param {{run: function(): (Promise|void), intervalMs?: number,
 *          onState?: function({refreshing: boolean, lastUpdated: Date|null})}} config
 * @returns {{start: function(), stop: function(), refresh: function(),
 *            isRunning: function(), lastUpdated: Date|null}}
 */
export function createPoller(config) {
    const run = config.run;
    const intervalMs = config.intervalMs || 3000;
    const onState = config.onState || null;

    let timerId = null;
    let started = false;
    let disposed = false;
    let inFlight = false;
    let lastUpdated = null;

    function emit(refreshing) {
        if (onState) onState({ refreshing: refreshing, lastUpdated: lastUpdated });
    }

    async function refresh() {
        if (disposed || inFlight) return;
        if (document.visibilityState === "hidden") return;
        inFlight = true;
        emit(true);
        try {
            await run();
            lastUpdated = new Date();
        } finally {
            inFlight = false;
            if (!disposed) emit(false);
        }
    }

    function startTimer() {
        if (timerId !== null || disposed) return;
        timerId = setInterval(refresh, intervalMs);
    }

    function stopTimer() {
        if (timerId !== null) {
            clearInterval(timerId);
            timerId = null;
        }
    }

    function onVisibilityChange() {
        if (document.visibilityState === "hidden") {
            stopTimer();
        } else if (started) {
            startTimer();
            refresh();
        }
    }

    return {
        start: function () {
            if (started || disposed) return;
            started = true;
            document.addEventListener("visibilitychange", onVisibilityChange);
            startTimer();
            refresh();
        },
        stop: function () {
            started = false;
            disposed = true;
            stopTimer();
            document.removeEventListener("visibilitychange", onVisibilityChange);
        },
        /** Pause/resume without tearing down (e.g. terminal execution). */
        pause: function () {
            started = false;
            stopTimer();
        },
        refresh: refresh,
        isRunning: function () { return started && timerId !== null; },
        get lastUpdated() { return lastUpdated; }
    };
}

/**
 * Text for a "last updated / refreshing" indicator.
 * @param {{refreshing: boolean, lastUpdated: Date|null}|null} state
 * @returns {string}
 */
export function refreshStatusText(state) {
    if (!state) return "";
    if (state.refreshing) {
        return state.lastUpdated
            ? "Refreshing\u2026 \u00b7 updated " + formatTime(state.lastUpdated)
            : "Refreshing\u2026";
    }
    if (!state.lastUpdated) return "Waiting for data\u2026";
    return "Updated " + formatTime(state.lastUpdated);
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

/** @param {string} id @returns {string} */
export function shortId(id) {
    if (!id) return "";
    return String(id).length > 8 ? String(id).substring(0, 8) : String(id);
}

/**
 * @param {string|Date|null} value
 * @returns {string} locale date-time, or an em dash
 */
export function formatDateTime(value) {
    const date = toDate(value);
    return date ? date.toLocaleString() : "\u2014";
}

/** @param {string|Date|null} value @returns {string} */
export function formatTime(value) {
    const date = toDate(value);
    return date
        ? date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" })
        : "\u2014";
}

/**
 * Relative, human-readable instant ("in 3 min", "2 min ago").
 * @param {string|Date|null} value
 * @returns {string}
 */
export function formatRelative(value) {
    const date = toDate(value);
    if (!date) return "\u2014";
    const diffMs = date.getTime() - Date.now();
    const abs = Math.abs(diffMs);
    const future = diffMs > 0;
    let amount;
    let unit;
    if (abs < 45 * 1000) {
        return future ? "in a few seconds" : "just now";
    } else if (abs < 45 * 60 * 1000) {
        amount = Math.round(abs / 60000);
        unit = "min";
    } else if (abs < 22 * 3600 * 1000) {
        amount = Math.round(abs / 3600000);
        unit = "h";
    } else if (abs < 26 * 86400 * 1000) {
        amount = Math.round(abs / 86400000);
        unit = amount === 1 ? "day" : "days";
    } else {
        return date.toLocaleDateString() + " " + formatTime(date);
    }
    return future ? "in " + amount + " " + unit : amount + " " + unit + " ago";
}

/**
 * Human-readable duration.
 * @param {number|null} ms
 * @returns {string}
 */
export function formatDuration(ms) {
    if (ms === null || ms === undefined || isNaN(ms)) return "\u2014";
    const value = Math.max(0, Number(ms));
    if (value < 1000) return Math.round(value) + " ms";
    const seconds = value / 1000;
    if (seconds < 60) return seconds.toFixed(seconds < 10 ? 2 : 1) + " s";
    const minutes = Math.floor(seconds / 60);
    const rest = Math.round(seconds % 60);
    if (minutes < 60) return minutes + " min " + (rest < 10 ? "0" : "") + rest + " s";
    const hours = Math.floor(minutes / 60);
    return hours + " h " + (minutes % 60 < 10 ? "0" : "") + (minutes % 60) + " min";
}

/**
 * Elapsed time between two instants (defaults to now for the end).
 * @param {string|Date|null} startedAt
 * @param {string|Date|null} [endedAt]
 * @returns {number|null} milliseconds
 */
export function elapsedMs(startedAt, endedAt) {
    const start = toDate(startedAt);
    if (!start) return null;
    const end = endedAt ? toDate(endedAt) : new Date();
    if (!end) return null;
    return Math.max(0, end.getTime() - start.getTime());
}

/** @returns {string} the browser timezone name, e.g. "Europe/Paris" */
export function timeZoneLabel() {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || "local time";
    } catch {
        return "local time";
    }
}

function toDate(value) {
    if (!value) return null;
    if (value instanceof Date) return isNaN(value.getTime()) ? null : value;
    const date = new Date(value);
    return isNaN(date.getTime()) ? null : date;
}

// ---------------------------------------------------------------------------
// Escaping
// ---------------------------------------------------------------------------

/** @param {string|null} text @returns {string} */
export function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

/** @param {string|null} text @returns {string} */
export function escapeAttr(text) {
    if (text === null || text === undefined) return "";
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}
