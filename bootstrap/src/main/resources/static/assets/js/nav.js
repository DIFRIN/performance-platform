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
 * Route definitions. Each entry={path, label, orchestratorOnly}.
 */
const NAV_ITEMS = [
    { path: "executions", label: "Executions", orchestratorOnly: false },
    { path: "upload",     label: "Upload",     orchestratorOnly: false },
    { path: "agents",     label: "Agents",     orchestratorOnly: true  },
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
        a.textContent = item.label;

        // Highlight active route
        if (currentPath === item.path) {
            a.classList.add("nav-link--active");
        }

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
    return hash.startsWith("#/") ? hash.slice(2) : hash.slice(1);
}
