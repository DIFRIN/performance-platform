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

    // 204 No Content — nothing to parse
    if (res.status === 204) return null;

    // 202 Accepted may or may not have a body
    if (res.status === 202) {
        try {
            const text = await res.text();
            return text ? JSON.parse(text) : null;
        } catch {
            return null;
        }
    }

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
 * @returns {Promise<{executionId: string, total: number, tasks: Array}>}
 */
export async function listExecutionTasks(id) {
    return request(BASE + "/executions/" + encodeURIComponent(id) + "/tasks");
}

/**
 * Alias for {@link listExecutionTasks} — canonical name per ISSUE-128.
 * @param {string} id — execution identifier
 * @returns {Promise<{executionId: string, total: number, tasks: Array}>}
 */
export async function getExecutionTasks(id) {
    return listExecutionTasks(id);
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
 * Upload a scenario for immediate execution.
 * POST /api/v1/scenarios/upload (multipart)
 *
 * Accepts either a File object ({@code file}) or a YAML string ({@code yaml}).
 * On 202 success, returns {@code {executionId, status}}.
 * On 400 validation failure, throws an Error with {@code err.details} containing
 * an array of {@code FieldError {field, message}} objects for field-level display.
 *
 * @param {{file?: File, yaml?: string}} params
 * @returns {Promise<{executionId: string, status: string}>}
 * @throws {Error} with {@code .code} and {@code .details[]} on validation failure
 */
export async function uploadScenario({ file, yaml } = {}) {
    const formData = new FormData();

    if (file) {
        formData.append("file", file);
    } else if (yaml) {
        const blob = new Blob([yaml], { type: "text/plain" });
        formData.append("file", blob, "scenario.yaml");
    } else {
        throw new Error("Either 'file' or 'yaml' parameter is required");
    }

    const res = await fetch(BASE + "/scenarios/upload", {
        method: "POST",
        body: formData,
    });

    const body = await res.json();

    if (res.ok) {
        return body; // 202 → {executionId, status}
    }

    // 400 → ValidationErrorResponse or generic error
    const err = new Error(body.message || body.error || "Upload failed");
    err.code = body.error;
    err.details = body.details || [];
    throw err;
}

// ---- Reports ----

/**
 * Build the URL for a report in the given format.
 * The report is NOT generated by this call — reports are generated automatically
 * at the end of the execution lifecycle. This URL points to a pre-existing file.
 *
 * @param {string} id — execution identifier
 * @param {string} format — "html", "pdf", or "json"
 * @returns {string} the fully-qualified URL for the report
 */
export function reportUrl(id, format) {
    return BASE + "/executions/" + encodeURIComponent(id) + "/report?format=" + encodeURIComponent(format);
}

/**
 * Check whether a report is available for the given execution.
 * Uses a GET request for the HTML format. Returns true on 200, false on 404.
 *
 * @param {string} id — execution identifier
 * @returns {Promise<boolean>} true if the report is available, false otherwise
 */
export async function isReportAvailable(id) {
    try {
        var res = await fetch(reportUrl(id, "html"), {
            method: "GET",
            headers: { "Accept": "text/html" },
        });
        return res.ok;
    } catch (e) {
        return false;
    }
}
