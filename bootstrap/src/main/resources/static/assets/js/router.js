/**
 * Hash-based SPA router with query-string state and focus management.
 *
 * Public API:
 *   registerRoute(pattern, renderFn) - register a view for a hash pattern
 *   startRouter(mountEl)             - begin listening to hashchange
 *   readQueryParams()                - URLSearchParams of the current hash query
 *   updateQueryParams(patch, opts)   - merge query state without remounting
 *
 * Pattern syntax:
 *   - Static: "executions"
 *   - Parameterized: "executions/{id}"
 *
 * The hash may carry a query string: #/executions?status=active&scenario=foo
 * Query parameters are read-only view state (filters, paging); they are updated
 * with history.replaceState so they never trigger a remount.
 *
 * Accessibility: after each route change, focus moves to the page heading
 * (or the mount container) and the new page name is announced via aria-live.
 */

import { announce } from "/assets/js/ui.js";

/** @type {Map<string, {pattern: string, paramNames: string[], renderFn: Function}>} */
const routes = new Map();

/** @type {{cleanup: Function|null, renderFn: Function|null}} */
let currentView = { cleanup: null, renderFn: null };

const PAGE_LABELS = {
    scenarios: "Scenarios",
    executions: "Executions",
    agents: "Agents",
    report: "Report"
};

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
 * Split the current hash into a path and its query parameters.
 * @returns {{path: string, query: URLSearchParams}}
 */
function parseHash() {
    const hash = window.location.hash;
    let raw = !hash || hash === "#" ? "" : (hash.startsWith("#/") ? hash.slice(2) : hash.slice(1));
    const question = raw.indexOf("?");
    let query = new URLSearchParams();
    if (question !== -1) {
        query = new URLSearchParams(raw.slice(question + 1));
        raw = raw.slice(0, question);
    }
    return { path: raw, query };
}

/**
 * Read the query parameters of the current route.
 * @returns {URLSearchParams}
 */
export function readQueryParams() {
    return parseHash().query;
}

/**
 * Merge the given values into the current hash query without remounting the view.
 * A null/undefined/empty value removes the parameter.
 *
 * @param {Object<string, string|number|null|undefined>} patch
 * @param {{replace?: boolean}} [options] - replace defaults to true
 */
export function updateQueryParams(patch, options) {
    const replace = !options || options.replace !== false;
    const { path, query } = parseHash();
    Object.keys(patch || {}).forEach(function (key) {
        const value = patch[key];
        if (value === null || value === undefined || value === "") {
            query.delete(key);
        } else {
            query.set(key, String(value));
        }
    });
    const qs = query.toString();
    const newHash = "#/" + (path || "executions") + (qs ? "?" + qs : "");
    if (replace) {
        window.history.replaceState(null, "", window.location.pathname + window.location.search + newHash);
    } else {
        window.location.hash = newHash;
    }
}

function pageLabel(path) {
    const first = (path || "executions").split("/")[0];
    return PAGE_LABELS[first] || first;
}

/**
 * Move focus to the page heading so keyboard and screen-reader users land on
 * the new view. Uses preventScroll to avoid jumping past the header.
 */
function focusHeading(mountEl) {
    const heading = mountEl.querySelector("[data-page-heading], h1");
    const target = heading || mountEl;
    if (!target) return;
    if (!target.hasAttribute("tabindex")) {
        target.setAttribute("tabindex", "-1");
    }
    try {
        target.focus({ preventScroll: true });
    } catch {
        target.focus();
    }
}

function dispatch(mountEl) {
    const location = parseHash();
    const path = location.path;

    if (currentView.cleanup) {
        try {
            currentView.cleanup();
        } catch (e) {
            console.error("router: cleanup failed", e);
        }
    }
    currentView = { cleanup: null, renderFn: null };

    const effectivePath = path || "executions";

    for (const entry of routes.values()) {
        const match = effectivePath.match(entry.regex);
        if (match) {
            const params = {};
            entry.paramNames.forEach((name, i) => {
                params[name] = match[i + 1];
            });

            const result = entry.renderFn(mountEl, params);
            if (result && typeof result.cleanup === "function") {
                currentView.cleanup = result.cleanup;
            }
            currentView.renderFn = entry.renderFn;

            focusHeading(mountEl);
            announce(pageLabel(effectivePath) + " page loaded");
            return;
        }
    }

    mountEl.innerHTML = "";
    console.warn("router: no route matched path=" + effectivePath);
}

/**
 * Register a route pattern with its render function.
 */
export function registerRoute(pattern, renderFn) {
    const { regex, paramNames } = buildPattern(pattern);
    routes.set(pattern, { pattern, regex, paramNames, renderFn });
}

/**
 * Start the router: listen for hash changes and perform the initial dispatch.
 */
export function startRouter(mountEl) {
    window.addEventListener("hashchange", () => dispatch(mountEl));
    dispatch(mountEl);
}
