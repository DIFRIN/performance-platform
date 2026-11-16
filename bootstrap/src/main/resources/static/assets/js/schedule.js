/**
 * Shared scheduling control used by the scenario catalog, the executions list
 * and the execution detail page.
 *
 * The control has two modes:
 * - preset dropdown: Now, In 15 minutes, In 1 hour, Tomorrow 09:00, Every day 02:00
 * - advanced text mode ("Cron / custom..."): in 1h30m, ISO date-time, cron expression
 *
 * The expression is validated and resolved client-side: the hint under the
 * input shows the next run ("Runs once at 21:16", "Next: tomorrow 02:00") and
 * invalid input is rejected inline, before any API call.
 *
 * Accepted grammar (mirrors the server-side SpringScheduleCalculator):
 *   now | empty                -> immediate run
 *   in 10m, in 1h30m, in 2d    -> one-shot delay
 *   2026-11-13T09:00           -> one-shot absolute time
 *   cron:0 0 2 * * *           -> recurring run (5- or 6-field cron)
 *
 * Public API:
 *   scheduleFieldHtml(idPrefix, {compact, label})
 *   enhanceScheduleControls(root)
 *   readSchedule(input)
 *   validateScheduleInput(input)  -> {valid, message}
 *   describeSchedule(value)       -> {valid, message, nextRunAt}
 *   isScheduled(value)
 *   scheduleConfirmation(nextRunAt)
 */

const CUSTOM_OPTION = "__custom__";
const DEFAULT_CRON = "cron:0 0 2 * * *";

const MONTH_NAMES = {
    JAN: 1, FEB: 2, MAR: 3, APR: 4, MAY: 5, JUN: 6,
    JUL: 7, AUG: 8, SEP: 9, OCT: 10, NOV: 11, DEC: 12
};
const DOW_NAMES = { SUN: 0, MON: 1, TUE: 2, WED: 3, THU: 4, FRI: 5, SAT: 6 };
const SHORT_DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

// ---------------------------------------------------------------------------
// Markup
// ---------------------------------------------------------------------------

/**
 * Build the schedule control markup.
 *
 * @param {string} idPrefix - unique prefix for element ids
 * @param {{compact?: boolean, label?: string}} [options]
 * @returns {string} HTML
 */
export function scheduleFieldHtml(idPrefix, options) {
    const opts = options || {};
    const compact = !!opts.compact;
    const allowImmediate = opts.allowImmediate !== false;
    const label = opts.label || "Schedule";
    const id = idPrefix + "-schedule";
    const fallback = allowImmediate ? "" : "in 15m";
    const value = opts.value === undefined || opts.value === null ? fallback : String(opts.value);
    const placeholder = "in 10m, 2026-11-13T09:00, cron:0 0 2 * * *";

    return '' +
        '<div class="schedule-control' + (compact ? ' schedule-control--compact' : '') + '" data-schedule-control>' +
        (compact
            ? ''
            : '  <label class="schedule-control__label" for="' + escapeAttr(id) + '">' + escapeHtml(label) + '</label>') +
        '  <div class="schedule-control__row">' +
        '    <select class="schedule-control__preset" data-schedule-preset' +
        '            aria-label="' + escapeAttr(label + " preset") + '">' +
             presetOptionsHtml(allowImmediate) +
        '    </select>' +
        '    <input type="text" id="' + escapeAttr(id) + '" data-schedule-input' +
        '           class="schedule-control__input" autocomplete="off" spellcheck="false"' +
        '           placeholder="' + escapeAttr(placeholder) + '"' + (value ? '' : ' hidden') +
        '           value="' + escapeAttr(value) + '"' +
        '           aria-label="' + escapeAttr(label + " expression") + '">' +
        '  </div>' +
        '  <div class="schedule-control__hint" data-schedule-hint aria-live="polite"></div>' +
        '</div>';
}

/** @returns {string} the preset <option> list. */
function presetOptionsHtml(allowImmediate) {
    return proposals(allowImmediate).map(function (preset) {
        return '<option value="' + escapeAttr(preset.value) + '">' +
            escapeHtml(preset.label) + '</option>';
    }).join("");
}

/** @returns {{value: string, label: string}[]} */
function proposals(allowImmediate) {
    const presets = [
        { value: "in 15m", label: "In 15 minutes" },
        { value: "in 1h", label: "In 1 hour" },
        { value: tomorrowMorning(), label: "Tomorrow 09:00" },
        { value: "cron:0 0 2 * * *", label: "Every day at 02:00" },
        { value: CUSTOM_OPTION, label: "Cron / custom\u2026" }
    ];
    if (allowImmediate) {
        presets.unshift({ value: "", label: "Now (immediate)" });
    }
    return presets;
}

// ---------------------------------------------------------------------------
// Wiring
// ---------------------------------------------------------------------------

/**
 * Wire every schedule control under root: preset selection, advanced mode,
 * live validation and next-run preview.
 *
 * Safe to call repeatedly (e.g. after each table re-render).
 *
 * @param {ParentNode} root
 */
export function enhanceScheduleControls(root) {
    root.querySelectorAll("[data-schedule-control]").forEach(function (control) {
        if (control.dataset.scheduleEnhanced === "true") {
            validateControl(control);
            return;
        }
        control.dataset.scheduleEnhanced = "true";

        const select = control.querySelector("[data-schedule-preset]");
        const input = control.querySelector("[data-schedule-input]");
        if (!select || !input) return;

        select.addEventListener("change", function () {
            if (select.value === CUSTOM_OPTION) {
                input.hidden = false;
                if (!input.value || isPresetValue(select, input.value)) {
                    input.value = DEFAULT_CRON;
                }
                input.focus();
                input.select();
            } else {
                input.value = select.value;
                input.hidden = true;
            }
            const result = validateControl(control);
            notifyControl(control, input, result);
        });

        let timer = null;
        input.addEventListener("input", function () {
            if (timer !== null) clearTimeout(timer);
            timer = setTimeout(function () {
                syncPreset(select, input);
                const result = validateControl(control);
                notifyControl(control, input, result);
            }, 180);
        });

        input.addEventListener("change", function () {
            syncPreset(select, input);
            const result = validateControl(control);
            notifyControl(control, input, result);
        });

        syncPreset(select, input);
        const initial = validateControl(control);
        if (initial.valid) notifyControl(control, input, initial);
    });
}

function notifyControl(control, input, result) {
    control.dispatchEvent(new CustomEvent("schedule-change", {
        bubbles: true,
        detail: { value: input.value, valid: result.valid, nextRunAt: result.nextRunAt || null }
    }));
}

function isPresetValue(select, value) {
    const trimmed = String(value || "").trim();
    return Array.prototype.some.call(select.options, function (option) {
        return option.value === trimmed && option.value !== CUSTOM_OPTION;
    });
}

function syncPreset(select, input) {
    const value = input.value.trim();
    if (!value) {
        select.value = "";
        return;
    }
    const match = Array.prototype.find.call(select.options, function (option) {
        return option.value === value;
    });
    select.value = match ? match.value : CUSTOM_OPTION;
    if (!match) input.hidden = false;
}

function validateControl(control) {
    const input = control.querySelector("[data-schedule-input]");
    const hint = control.querySelector("[data-schedule-hint]");
    const result = describeSchedule(input.value);

    control.dataset.scheduleInvalid = result.valid ? "false" : "true";
    input.classList.toggle("is-invalid", !result.valid);
    input.setAttribute("aria-invalid", result.valid ? "false" : "true");
    if (hint) {
        hint.textContent = result.message || "";
        hint.className = "schedule-control__hint" +
            (!result.valid
                ? " schedule-control__hint--error"
                : (result.nextRunAt ? " schedule-control__hint--ok" : ""));
    }
    return result;
}

// ---------------------------------------------------------------------------
// Reading & validation
// ---------------------------------------------------------------------------

/** @returns {string} the current schedule expression. */
export function readSchedule(input) {
    return input ? String(input.value || "").trim() : "";
}

/**
 * Validate the schedule expression of a given input, rendering the inline
 * error when invalid. Returns the resolved description.
 *
 * @param {HTMLInputElement} input
 * @returns {{valid: boolean, message: string, nextRunAt?: Date}}
 */
export function validateScheduleInput(input) {
    if (!input) return { valid: true, message: "" };
    const control = input.closest("[data-schedule-control]");
    if (!control) return describeSchedule(input.value);
    return validateControl(control, input);
}

/** @returns {boolean} true when the expression defers the run. */
export function isScheduled(value) {
    const normalized = (value || "").trim().toLowerCase();
    return normalized !== "" && normalized !== "now" && normalized !== "immediate";
}

/** Human-readable confirmation used after a deferred submission. */
export function scheduleConfirmation(nextRunAt) {
    if (!nextRunAt) return "Run scheduled.";
    const date = new Date(nextRunAt);
    if (isNaN(date.getTime())) return "Run scheduled for " + nextRunAt + ".";
    return "Run scheduled for " + date.toLocaleString() + " (" + timeZoneLabel() + ")";
}

// ---------------------------------------------------------------------------
// Expression resolution
// ---------------------------------------------------------------------------

/**
 * Resolve a schedule expression locally: validity + next occurrence.
 *
 * @param {string} value
 * @returns {{valid: boolean, message: string, nextRunAt?: Date, unknown?: boolean}}
 */
export function describeSchedule(value) {
    const raw = String(value || "").trim();

    if (!raw || /^(now|immediate)$/i.test(raw)) {
        return { valid: true, message: "Runs immediately" };
    }

    const lower = raw.toLowerCase();

    if (lower.indexOf("in ") === 0) {
        const delay = parseDelay(raw.slice(3).trim());
        if (delay === null) {
            return {
                valid: false,
                message: "Invalid delay. Use e.g. 'in 15m', 'in 1h30m' or 'in 2d'."
            };
        }
        if (delay <= 0) {
            return { valid: false, message: "Delay must be positive." };
        }
        return onceAt(new Date(Date.now() + delay));
    }

    if (lower.indexOf("cron:") === 0) {
        return describeCron(raw.slice(5).trim(), raw);
    }

    if (lower.indexOf("at ") === 0) {
        return describeAbsolute(raw.slice(3).trim());
    }

    if (looksLikeCron(raw)) {
        return describeCron(raw, raw);
    }

    return describeAbsolute(raw);
}

function describeAbsolute(value) {
    if (!value) return { valid: false, message: "Enter a date or a cron expression." };
    const normalized = value.replace(" ", "T");
    let date;
    if (/^\d{4}-\d{2}-\d{2}$/.test(normalized)) {
        date = new Date(normalized + "T00:00:00");
    } else {
        date = new Date(normalized);
    }
    if (!date || isNaN(date.getTime())) {
        return {
            valid: false,
            message: "Invalid date. Use '2026-11-13T09:00' or 'in 15m'."
        };
    }
    return onceAt(date);
}

function onceAt(date) {
    if (date.getTime() <= Date.now()) {
        return { valid: false, message: "Scheduled time must be in the future." };
    }
    return {
        valid: true,
        nextRunAt: date,
        message: "Runs once " + describeWhen(date)
    };
}

function describeCron(expression, original) {
    let fields;
    try {
        fields = normalizeCron(expression);
    } catch (e) {
        if (e && e.unsupported) {
            return {
                valid: true,
                unknown: true,
                message: "Advanced cron expression - validated on submit."
            };
        }
        return { valid: false, message: (e && e.message) || ("Invalid cron expression: " + original) };
    }

    const next = nextCronDate(fields, new Date());
    if (!next) {
        return { valid: false, message: "Cron expression has no occurrence in the next year." };
    }
    return {
        valid: true,
        nextRunAt: next,
        message: "Next: " + describeWhen(next)
    };
}

// ---------------------------------------------------------------------------
// Delay parsing
// ---------------------------------------------------------------------------

const DELAY_TOKEN = /(\d+(?:\.\d+)?)\s*(ms|s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)/gi;

/** @returns {number|null} delay in milliseconds */
function parseDelay(value) {
    if (!value) return null;
    if (/^P/i.test(value)) return parseIsoDuration(value);

    let total = 0;
    let matched = false;
    let match;
    DELAY_TOKEN.lastIndex = 0;
    while ((match = DELAY_TOKEN.exec(value)) !== null) {
        matched = true;
        total += unitMillis(parseFloat(match[1]), match[2]);
    }
    return matched ? total : null;
}

function unitMillis(amount, unit) {
    switch (unit.toLowerCase()) {
        case "ms": return amount;
        case "s": case "sec": case "secs": case "second": case "seconds": return amount * 1000;
        case "m": case "min": case "mins": case "minute": case "minutes": return amount * 60000;
        case "h": case "hr": case "hrs": case "hour": case "hours": return amount * 3600000;
        case "d": case "day": case "days": return amount * 86400000;
        default: return 0;
    }
}

function parseIsoDuration(value) {
    const match = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$/i.exec(value);
    if (!match) return null;
    const days = parseInt(match[1] || "0", 10);
    const hours = parseInt(match[2] || "0", 10);
    const minutes = parseInt(match[3] || "0", 10);
    const seconds = parseFloat(match[4] || "0");
    return ((days * 24 + hours) * 60 + minutes) * 60000 + seconds * 1000;
}

// ---------------------------------------------------------------------------
// Cron parsing (5 or 6 fields, Spring semantics)
// ---------------------------------------------------------------------------

function looksLikeCron(value) {
    if (value.charAt(0) === "@") return true;
    const parts = value.split(/\s+/);
    if (parts.length < 5 || parts.length > 6) return false;
    return parts.every(function (part) { return /^[0-9A-Za-z*?/,#L\-]+$/.test(part); });
}

function normalizeCron(expression) {
    let value = String(expression || "").trim();
    if (!value) throw new Error("Missing cron expression");

    if (value.charAt(0) === "@") {
        value = expandMacro(value.toLowerCase());
    }

    const parts = value.split(/\s+/);
    if (parts.length === 5) parts.unshift("0");
    if (parts.length !== 6) {
        throw new Error("Cron expression must have 5 or 6 fields.");
    }

    const fields = {
        seconds: parseField(parts[0], 0, 59, null, "seconds"),
        minutes: parseField(parts[1], 0, 59, null, "minutes"),
        hours: parseField(parts[2], 0, 23, null, "hours"),
        daysOfMonth: parseField(parts[3], 1, 31, null, "day of month"),
        months: parseField(parts[4], 1, 12, MONTH_NAMES, "month"),
        daysOfWeek: parseField(parts[5], 0, 7, DOW_NAMES, "day of week")
    };
    if (fields.daysOfWeek.values.indexOf(7) !== -1) {
        fields.daysOfWeek.values = uniqueSorted(fields.daysOfWeek.values.map(function (d) {
            return d === 7 ? 0 : d;
        }));
    }
    return fields;
}

function expandMacro(macro) {
    switch (macro) {
        case "@yearly": case "@annually": return "0 0 0 1 1 *";
        case "@monthly": return "0 0 0 1 * *";
        case "@weekly": return "0 0 0 * * SUN";
        case "@daily": case "@midnight": return "0 0 0 * * *";
        case "@hourly": return "0 0 * * * *";
        default: {
            const error = new Error("Unsupported cron macro: " + macro);
            error.unsupported = true;
            throw error;
        }
    }
}

function parseField(token, min, max, names, name) {
    const raw = String(token || "").trim();
    if (!raw) throw new Error("Missing " + name + " field.");
    if (raw === "*" || raw === "?") {
        return { wildcard: true, values: range(min, max) };
    }
    const stripped = raw.replace(/[A-Za-z]{2,3}/g, "");
    if (/[LW#]/.test(stripped)) {
        const error = new Error("Unsupported " + name + " syntax: " + raw);
        error.unsupported = true;
        throw error;
    }

    const values = [];
    raw.split(",").forEach(function (part) {
        const pieces = part.split("/");
        const base = pieces[0];
        const stepText = pieces.length > 1 ? pieces[1] : undefined;
        const step = stepText === undefined ? 1 : parseInt(stepText, 10);
        if (stepText !== undefined && (isNaN(step) || step <= 0)) {
            throw new Error("Invalid step in " + name + " field: " + raw);
        }
        if (base === "*" || base === "?") {
            for (let i = min; i <= max; i += step) values.push(i);
            return;
        }
        const rangeMatch = /^([A-Za-z0-9]+)-([A-Za-z0-9]+)$/.exec(base);
        if (rangeMatch) {
            const from = fieldValue(rangeMatch[1], names, min, max, name);
            const to = fieldValue(rangeMatch[2], names, min, max, name);
            if (to < from) throw new Error("Invalid range in " + name + " field: " + raw);
            for (let i = from; i <= to; i += step) values.push(i);
            return;
        }
        if (stepText !== undefined) {
            const from = fieldValue(base, names, min, max, name);
            for (let i = from; i <= max; i += step) values.push(i);
            return;
        }
        values.push(fieldValue(base, names, min, max, name));
    });

    if (values.length === 0) throw new Error("Empty " + name + " field.");
    return { wildcard: false, values: uniqueSorted(values) };
}

function fieldValue(token, names, min, max, name) {
    let value = parseInt(token, 10);
    if (isNaN(value) && names) {
        const key = token.toUpperCase();
        if (Object.prototype.hasOwnProperty.call(names, key)) {
            value = names[key];
        }
    }
    if (isNaN(value) || value < min || value > max) {
        throw new Error("Value out of range in " + name + " field: " + token);
    }
    return value;
}

function nextCronDate(fields, from) {
    const seconds = fields.seconds.wildcard ? [0] : fields.seconds.values;
    const start = new Date(from.getTime());
    start.setMilliseconds(0);

    for (let dayOffset = 0; dayOffset <= 366; dayOffset++) {
        const day = new Date(start.getFullYear(), start.getMonth(), start.getDate() + dayOffset);
        if (!matches(fields.months, day.getMonth() + 1)) continue;
        if (!dayMatches(fields, day)) continue;

        for (let h = 0; h < fields.hours.values.length; h++) {
            const hour = fields.hours.values[h];
            for (let m = 0; m < fields.minutes.values.length; m++) {
                const minute = fields.minutes.values[m];
                for (let s = 0; s < seconds.length; s++) {
                    const candidate = new Date(day.getFullYear(), day.getMonth(), day.getDate(),
                        hour, minute, seconds[s], 0);
                    if (candidate.getTime() > from.getTime()) return candidate;
                }
            }
        }
    }
    return null;
}

function dayMatches(fields, day) {
    const domRestricted = !fields.daysOfMonth.wildcard;
    const dowRestricted = !fields.daysOfWeek.wildcard;
    const domMatch = fields.daysOfMonth.values.indexOf(day.getDate()) !== -1;
    const dowMatch = fields.daysOfWeek.values.indexOf(day.getDay()) !== -1;

    if (domRestricted && dowRestricted) return domMatch || dowMatch;
    if (domRestricted) return domMatch;
    if (dowRestricted) return dowMatch;
    return true;
}

function matches(field, value) {
    return field.values.indexOf(value) !== -1;
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

function describeWhen(date) {
    const now = new Date();
    const time = formatClock(date);
    if (date.toDateString() === now.toDateString()) return "at " + time;

    const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    if (date.toDateString() === tomorrow.toDateString()) return "tomorrow " + time;

    const diffDays = Math.round((startOfDay(date) - startOfDay(now)) / 86400000);
    if (diffDays > 1 && diffDays < 7) return SHORT_DAYS[date.getDay()] + " " + time;
    return "on " + date.toLocaleDateString() + " " + time;
}

function formatClock(date) {
    return date.toLocaleTimeString(undefined, {
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
    });
}

function startOfDay(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

function range(min, max) {
    const values = [];
    for (let i = min; i <= max; i++) values.push(i);
    return values;
}

function uniqueSorted(values) {
    return values
        .filter(function (value, index, self) { return self.indexOf(value) === index; })
        .sort(function (a, b) { return a - b; });
}

/** Tomorrow at 09:00, formatted as a local ISO-8601 date-time (no offset). */
function tomorrowMorning() {
    const date = new Date();
    date.setDate(date.getDate() + 1);
    date.setHours(9, 0, 0, 0);
    return formatLocalIso(date);
}

function formatLocalIso(date) {
    const pad = function (n) { return String(n).padStart(2, "0"); };
    return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate()) +
        "T" + pad(date.getHours()) + ":" + pad(date.getMinutes());
}

function timeZoneLabel() {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || "local time";
    } catch {
        return "local time";
    }
}

function escapeHtml(text) {
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function escapeAttr(text) {
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
}
