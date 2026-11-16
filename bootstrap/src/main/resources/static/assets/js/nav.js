/**
 * Navigation component.
 * PDR-028 — ISSUE-126
 *
 * Renders the sidebar navigation with links to hash routes.
 * The "agents" link is only visible when the runtime role is ORCHESTRATOR.
 *
 * Public API:
 *   renderNav(navEl, { orchestrator }) — renders the nav inside navEl
 */

/**
 * Route definitions. Each entry={path, label, icon, orchestratorOnly}.
 * Icons are static inline SVGs (16×16, stroke = currentColor).
 */
const NAV_ITEMS = [
    {
        path: "scenarios",
        label: "Scenarios",
        orchestratorOnly: false,
        icon: '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true">' +
              '<path d="M3 3.5h10M3 8h10M3 12.5h6" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>'
    },
    {
        path: "executions",
        label: "Executions",
        orchestratorOnly: false,
        icon: '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true">' +
              '<circle cx="8" cy="8" r="5.5" stroke="currentColor" stroke-width="1.5"/>' +
              '<path d="M6.8 5.8 10 8l-3.2 2.2V5.8Z" fill="currentColor"/></svg>'
    },
    {
        path: "agents",
        label: "Agents",
        orchestratorOnly: true,
        icon: '<svg viewBox="0 0 16 16" fill="none" aria-hidden="true">' +
              '<rect x="2.5" y="3" width="11" height="4" rx="1.2" stroke="currentColor" stroke-width="1.5"/>' +
              '<rect x="2.5" y="9" width="11" height="4" rx="1.2" stroke="currentColor" stroke-width="1.5"/>' +
              '<path d="M5 5h.01M5 11h.01" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>'
    }
];

/**
 * Render the navigation inside the given element.
 *
 * @param {HTMLElement} navEl — the container for the navigation
 * @param {{orchestrator: boolean}} context — runtime context (orchestrator flag)
 */
export function renderNav(navEl, { orchestrator }) {
    const list = document.createElement("ul");
    list.className = "nav-list";

    const currentPath = getCurrentHashPath();

    NAV_ITEMS.forEach((item) => {
        // Hide agents link when not orchestrator
        if (item.orchestratorOnly && !orchestrator) {
            return;
        }

        const li = document.createElement("li");
        li.className = "nav-item";

        const a = document.createElement("a");
        a.href = "#/" + item.path;
        a.className = "nav-link";

        const icon = document.createElement("span");
        icon.className = "nav-link__icon";
        icon.innerHTML = item.icon;

        const label = document.createElement("span");
        label.className = "nav-link__label";
        label.textContent = item.label;

        a.appendChild(icon);
        a.appendChild(label);

        // Highlight active route (also for nested routes such as scenarios/{id})
        const active = currentPath === item.path
            || currentPath.indexOf(item.path + "/") === 0;
        if (active) {
            a.classList.add("nav-link--active");
        }

        a.setAttribute("aria-current", active ? "page" : "false");

        li.appendChild(a);
        list.appendChild(li);
    });

    // Clear previous content and append new list
    navEl.innerHTML = "";
    navEl.appendChild(list);
}

/**
 * Extract the current hash path (without "#/").
 * @returns {string}
 */
function getCurrentHashPath() {
    const hash = window.location.hash;
    if (!hash || hash === "#") return "executions";
    const path = hash.startsWith("#/") ? hash.slice(2) : hash.slice(1);
    const question = path.indexOf("?");
    return question === -1 ? path : path.slice(0, question);
}
