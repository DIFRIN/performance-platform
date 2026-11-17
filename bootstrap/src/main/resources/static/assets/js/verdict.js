/**
 * verdict.js — INVALID verdict badge (ADR-069 §5).
 *
 * The verdict lives in the JSON report. Terminal executions get a placeholder
 * slot, hydrated asynchronously; only INVALID is surfaced as a badge, distinct
 * from FAILED (ko) and WARNING.
 */
import { reportUrl } from "/assets/js/api.js";

export const INVALID_TOOLTIP =
    "INVALID: the experiment did not apply what the scenario describes " +
    "(failed integrity check). Results are not conclusive — rerun the test.";

const cache = new Map();

/** Placeholder to embed in a terminal execution's row/info. */
export function verdictSlotHtml(executionId, status) {
    if (status !== "COMPLETED") return "";
    return ' <span class="verdict-slot" data-verdict-id="' +
        String(executionId).replace(/"/g, "&quot;") + '"></span>';
}

export function verdictBadgeHtml(verdict) {
    if (verdict !== "INVALID") return "";
    return '<span class="status status--invalid" title="' + INVALID_TOOLTIP + '">INVALID</span>';
}

async function fetchVerdict(id) {
    if (cache.has(id)) return cache.get(id);
    const p = fetch(reportUrl(id, "json"), { headers: { "Accept": "application/json" } })
        .then(function (res) { return res.ok ? res.json() : null; })
        .then(function (report) { return report && report.verdict ? report.verdict : null; })
        .catch(function () { return null; });
    cache.set(id, p);
    const v = await p;
    if (v === null) cache.delete(id); // report may not be written yet — retry later
    return v;
}

/** Fill every verdict slot under root. */
export function hydrateVerdictBadges(root) {
    if (!root) return;
    root.querySelectorAll(".verdict-slot:empty").forEach(function (slot) {
        fetchVerdict(slot.getAttribute("data-verdict-id")).then(function (verdict) {
            if (slot.isConnected) slot.innerHTML = verdictBadgeHtml(verdict);
        });
    });
}
