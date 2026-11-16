/**
 * Theme handling: system preference by default, manual override persisted.
 *
 * The document element carries {@code data-theme="light|dark"}. An explicit
 * user choice is stored in localStorage; "system" removes the attribute so
 * the prefers-color-scheme fallback in the stylesheet applies.
 *
 * Public API:
 *   initTheme()        — resolve and apply the initial theme
 *   toggleTheme()      — switch light ↔ dark and return the applied theme
 *   currentTheme()     — "light" | "dark"
 */

const STORAGE_KEY = "pp-theme";
const media = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;

let explicit = null; // null = follow the system

/** Resolve and apply the initial theme. */
export function initTheme() {
    try {
        const stored = localStorage.getItem(STORAGE_KEY);
        explicit = stored === "light" || stored === "dark" ? stored : null;
    } catch {
        explicit = null;
    }
    apply();
    if (media) {
        const listener = function () {
            if (!explicit) apply();
        };
        if (media.addEventListener) media.addEventListener("change", listener);
        else if (media.addListener) media.addListener(listener);
    }
}

/** @returns {"light"|"dark"} the effective theme */
export function currentTheme() {
    if (explicit) return explicit;
    return media && media.matches ? "dark" : "light";
}

/**
 * Toggle light/dark, persisting the explicit choice.
 * @returns {"light"|"dark"} the applied theme
 */
export function toggleTheme() {
    const next = currentTheme() === "dark" ? "light" : "dark";
    explicit = next;
    try {
        localStorage.setItem(STORAGE_KEY, next);
    } catch {
        // storage unavailable — session-only override
    }
    apply();
    return next;
}

function apply() {
    const theme = currentTheme();
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    const btn = document.getElementById("theme-toggle");
    if (btn) {
        btn.setAttribute("aria-label", theme === "dark" ? "Switch to light theme" : "Switch to dark theme");
        btn.title = theme === "dark" ? "Switch to light theme" : "Switch to dark theme";
        btn.setAttribute("aria-pressed", theme === "dark" ? "true" : "false");
        btn.innerHTML = theme === "dark" ? SUN_ICON : MOON_ICON;
    }
}

const SUN_ICON =
    '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">' +
    '<circle cx="8" cy="8" r="3" stroke="currentColor" stroke-width="1.4"/>' +
    '<path d="M8 1.5v1.2M8 13.3v1.2M1.5 8h1.2M13.3 8h1.2M3.4 3.4l.85.85M11.75 11.75l.85.85M12.6 3.4l-.85.85M4.25 11.75l-.85.85" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>' +
    '</svg>';

const MOON_ICON =
    '<svg viewBox="0 0 16 16" width="16" height="16" fill="none" aria-hidden="true">' +
    '<path d="M13.2 9.6A5.6 5.6 0 0 1 6.4 2.8a5.6 5.6 0 1 0 6.8 6.8Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/>' +
    '</svg>';
