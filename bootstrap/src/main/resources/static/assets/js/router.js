/**
 * Hash-based SPA router.
 * PDR-028 — ISSUE-126
 *
 * Public API:
 *   registerRoute(pattern, renderFn) — register a view for a hash pattern
 *   startRouter(mountEl)           — begin listening to hashchange
 *
 * Pattern syntax:
 *   - Static: "executions"
 *   - Parameterized: "executions/{id}"
 *
 * The router calls a cleanup hook on the previous view before rendering
 * the new one (to stop polling, clear timers, etc.).
 */

/** @type {Map<string, {pattern: string, paramNames: string[], renderFn: Function}>} */
const routes = new Map();

/** @type {{cleanup: Function|null, renderFn: Function|null}} */
let currentView = { cleanup: null, renderFn: null };

/**
 * Build a regex from a pattern string.
 * Static segments match literally; "{param}" segments become capture groups.
 * @param {string} pattern
 * @returns {{regex: RegExp, paramNames: string[]}}
 */
function buildPattern(pattern) {
    const paramNames = [];
    const parts = pattern.split("/");
    const regexParts = parts.map((segment) => {
        if (segment.startsWith("{") && segment.endsWith("}")) {
            const paramName = segment.slice(1, -1);
            paramNames.push(paramName);
            return "([^/]+)";
        }
        return segment;
    });
    const regex = new RegExp("^" + regexParts.join("/") + "$");
    return { regex, paramNames };
}

/**
 * Extract the hash path without the leading "#".
 * Returns "" when there is no hash.
 * @returns {string}
 */
function getHashPath() {
    const hash = window.location.hash;
    if (!hash || hash === "#") return "";
    return hash.startsWith("#/") ? hash.slice(2) : hash.slice(1);
}

/**
 * Parse the current hash and call the matching render function.
 * Calls cleanup on the previous view before rendering the new one.
 * @param {HTMLElement} mountEl — the container where the view is mounted
 */
function dispatch(mountEl) {
    const path = getHashPath();

    // Call cleanup of previous view
    if (currentView.cleanup) {
        try {
            currentView.cleanup();
        } catch (e) {
            console.error("router: cleanup failed", e);
        }
    }
    currentView = { cleanup: null, renderFn: null };

    // Empty path or no hash → default to "executions"
    const effectivePath = path || "executions";

    for (const entry of routes.values()) {
        const match = effectivePath.match(entry.regex);
        if (match) {
            // Build params object from named groups
            const params = {};
            entry.paramNames.forEach((name, i) => {
                params[name] = match[i + 1];
            });

            // Render
            const result = entry.renderFn(mountEl, params);
            if (result && typeof result.cleanup === "function") {
                currentView.cleanup = result.cleanup;
            }
            currentView.renderFn = entry.renderFn;
            return;
        }
    }

    // No match — render empty content
    mountEl.innerHTML = "";
    console.warn("router: no route matched path=" + effectivePath);
}

/**
 * Register a route pattern with its render function.
 *
 * @param {string} pattern — e.g. "executions", "executions/{id}"
 * @param {function(HTMLElement, Object): {cleanup?: Function}|void} renderFn
 *   Called with (mountEl, params). May return {cleanup} for teardown.
 */
export function registerRoute(pattern, renderFn) {
    const { regex, paramNames } = buildPattern(pattern);
    routes.set(pattern, { pattern, regex, paramNames, renderFn });
}

/**
 * Start the router: listen for hash changes and perform the initial dispatch.
 *
 * @param {HTMLElement} mountEl — the container element for all views
 */
export function startRouter(mountEl) {
    window.addEventListener("hashchange", () => dispatch(mountEl));
    dispatch(mountEl);
}
