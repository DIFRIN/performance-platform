/**
 * API client — fetch wrapper for /api/v1/** endpoints.
 * PDR-029 — ISSUE-127
 *
 * Provides typed async functions for all execution, agent, scenario,
 * and report API endpoints.
 *
 * Each function returns the parsed JSON response.
 * On HTTP errors (non-2xx), throws an Error with the status and message.
 * On 204 No Content responses, returns null.
 */

const BASE = "/api/v1";

// ---- Internal helpers ----

/**
 * Core request function. Sends a fetch, handles errors, parses JSON.
 * @param {string} url
 * @param {RequestInit} [options]
 * @returns {Promise<any>}
 */
async function request(url, options = {}) {
    const res = await fetch(url, {
        headers: {
            "Accept": "application/json",
            ...options.headers,
        },
        ...options,
    });

    if (!res.ok) {
        let message;
        try {
            const body = await res.json();
            message = body.message || body.error || res.statusText;
        } catch {
            message = res.statusText;
        }
        throw new Error("API " + res.status + ": " + message);
    }

    // 204 No Content / 202 Accepted — nothing to parse
    if (res.status === 204 || res.status === 202) return null;
    return res.json();
}

// ---- Executions ----

/**
 * List recent executions with progress.
 * GET /api/v1/executions?limit=N
 *
 * @param {number} [limit=50] — max number of executions (default 50)
 * @returns {Promise<Array<{executionId: string, scenarioId: string, status: string, startedAt: string, updatedAt: string, progress: {total: number, ok: number, ko: number, running: number}}>>}
 */
export async function listExecutions(limit) {
    if (limit === undefined) limit = 50;
    const params = new URLSearchParams();
    if (limit > 0) params.set("limit", String(limit));
    const qs = params.toString();
    return request(BASE + "/executions" + (qs ? "?" + qs : ""));
}

/**
 * Get full status of a single execution.
 * GET /api/v1/executions/{id}
 *
 * @param {string} id — execution identifier
 * @returns {Promise<{executionId: string, scenarioId: string, status: string, phaseStatuses: Object<string,string>, startedAt: string, updatedAt: string, progress: {total: number, ok: number, ko: number, running: number}}>}
 */
export async function getExecutionStatus(id) {
    return request(BASE + "/executions/" + encodeURIComponent(id));
}

/**
 * List task summaries for an execution.
 * GET /api/v1/executions/{id}/tasks
 *
 * @param {string} id — execution identifier
 * @returns {Promise<{executionId: string, count: number, tasks: Array}>}
 */
export async function listExecutionTasks(id) {
    return request(BASE + "/executions/" + encodeURIComponent(id) + "/tasks");
}

/**
 * Cancel an in-progress execution.
 * POST /api/v1/executions/{id}/cancel
 *
 * @param {string} id — execution identifier
 * @returns {Promise<null>} 202 Accepted (no body)
 */
export async function cancelExecution(id) {
    return request(BASE + "/executions/" + encodeURIComponent(id) + "/cancel", {
        method: "POST",
    });
}

/**
 * Delete a completed execution.
 * DELETE /api/v1/executions/{id}
 *
 * @param {string} id — execution identifier
 * @returns {Promise<null>} 204 No Content
 */
export async function deleteExecution(id) {
    return request(BASE + "/executions/" + encodeURIComponent(id), {
        method: "DELETE",
    });
}

// ---- Agents ----

/**
 * List all registered agents.
 * GET /api/v1/agents
 *
 * @returns {Promise<Array<{agentId: string, name: string, state: string, supportedTasks: string[], lastHeartbeatAt: string}>>}
 */
export async function listAgents() {
    return request(BASE + "/agents");
}

// ---- Scenarios ----

/**
 * Submit a YAML scenario for execution (raw text body).
 * POST /api/v1/scenarios
 *
 * @param {string} yaml — scenario YAML content
 * @returns {Promise<{executionId: string, status: string}>}
 */
export async function submitScenario(yaml) {
    return request(BASE + "/scenarios", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: yaml,
    });
}

/**
 * Upload a scenario via multipart form (file or yaml text field).
 * POST /api/v1/scenarios/upload
 *
 * @param {FormData} formData — form with 'file' or 'yaml' field
 * @returns {Promise<{executionId: string, status: string}>}
 */
export async function uploadScenario(formData) {
    return request(BASE + "/scenarios/upload", {
        method: "POST",
        body: formData,
    });
}

// ---- Reports ----

/**
 * Generate a report for a completed execution.
 * GET /api/v1/executions/{id}/report
 *
 * @param {string} id — execution identifier
 * @returns {Promise<{reportId: string}>}
 */
export async function getReport(id) {
    return request(BASE + "/executions/" + encodeURIComponent(id) + "/report");
}
