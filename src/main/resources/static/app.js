const API = '/api';

// This page has no per-order drill-down anymore -- just two auto-refreshing lists: Orders
// (every instance, for a quick status glance) and Tasks (every manual step currently waiting on
// a business-user decision, with its own Complete button right there). Refreshed on a timer so a
// task shows up the moment its order reaches it, without the user having to click anything.
const REFRESH_INTERVAL_MS = 3000;

// Transient bottom-right notification -- the successor to the old always-on HTTP activity log
// pinned to the page bottom. Only surfaces things worth interrupting for (errors, and a handful
// of explicit confirmations) rather than tracing every request.
function log(message, isError) {
    const stack = document.getElementById('toastStack');
    const toast = document.createElement('div');
    toast.className = 'toast' + (isError ? ' error' : '');
    toast.textContent = message;
    stack.appendChild(toast);
    setTimeout(() => toast.remove(), isError ? 6000 : 3000);
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

async function api(method, path, body) {
    let response;
    try {
        response = await fetch(API + path, {
            method,
            headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
            body: body !== undefined ? JSON.stringify(body) : undefined
        });
    } catch (err) {
        log(`${method} ${path} network error: ${err.message}`, true);
        throw err;
    }
    if (response.status === 204) {
        return null;
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
        const message = (data && data.message) || response.statusText;
        log(`${method} ${path} -> ${response.status} ${message}`, true);
        throw new Error(message);
    }
    return data;
}

function fmtDate(iso) {
    return iso ? new Date(iso).toLocaleString() : '';
}

// ---- Process version (demo-only convenience) ----

async function deployVersion(path) {
    const errorEl = document.getElementById('deployVersionError');
    errorEl.textContent = '';
    let view;
    try {
        view = await api('POST', path);
    } catch (e) {
        errorEl.textContent = 'Could not load that version: ' + e.message;
        return;
    }
    errorEl.textContent = `Deployed order-fulfillment v${view.version} — start a new order to run it.`;
    selectedProcessKey = view.processKey; // just-deployed version is almost always the one you want to try next
    selectedVersion = view.version;
    await refresh();
    await refreshVersions();
    await refreshDeploymentStatus();
}

// "Clear all data" wipes every deployed definition for every process key, not just
// order-fulfillment -- the other 7 bundled processes (activate-insurance(-rollback), send-pdf
// (-rollback), supplier-quote, multi-supplier-quote, customer-notification) need this too before
// anything that calls them as a sub-process can run again.
async function redeployAllBundledProcesses() {
    const errorEl = document.getElementById('deployVersionError');
    errorEl.textContent = '';
    try {
        await api('POST', '/demo/redeploy-all');
    } catch (e) {
        errorEl.textContent = 'Could not redeploy bundled processes: ' + e.message;
        return;
    }
    errorEl.textContent = 'Redeployed every bundled process still missing after the wipe.';
    await refresh();
    await refreshVersions();
    await refreshDeploymentStatus();
}

// Load v1/v2 only make sense (and only stay enabled) while that exact spec isn't already
// deployed under this key -- an exact content match via the engine's own matchAmong, not a
// heuristic, so this stays correct no matter how many versions pile up.
async function refreshDeploymentStatus() {
    let status;
    try {
        status = await api('GET', '/demo/order-fulfillment/deployment-status');
    } catch (e) {
        return;
    }
    document.getElementById('btnDeployV1').disabled = status.v1Deployed;
    document.getElementById('btnDeployV2').disabled = status.v2Deployed;
}

// ---- Version picker (which deployed version a new order starts against) ----
// Lists every version of every deployed process key -- not just the bundled order-fulfillment
// one -- so this page can also start instances of any other process sharing its step handlers.

let selectedProcessKey = null;
let selectedVersion = null;
let currentVersions = [];

function latestVersionByKey() {
    const latest = new Map();
    for (const v of currentVersions) {
        const cur = latest.get(v.processKey);
        if (cur === undefined || v.version > cur) latest.set(v.processKey, v.version);
    }
    return latest;
}

async function refreshVersions() {
    let keys;
    try {
        keys = await api('GET', '/process-definitions');
    } catch (e) {
        keys = [];
    }
    const perKeyVersions = await Promise.all(keys.map(k =>
        api('GET', `/process-definitions/${encodeURIComponent(k.processKey)}/versions`).catch(() => [])));
    currentVersions = perKeyVersions.flat();

    const stillValid = currentVersions.some(v => v.processKey === selectedProcessKey && v.version === selectedVersion);
    if (!stillValid) {
        const first = [...currentVersions].sort((a, b) =>
            a.processKey.localeCompare(b.processKey) || b.version - a.version)[0];
        selectedProcessKey = first ? first.processKey : null;
        selectedVersion = first ? first.version : null;
    }
    renderVersions();
    renderVariableForm(document.getElementById('variableForm'), currentVersionVariables());
}

function currentVersionVariables() {
    const v = currentVersions.find(v => v.processKey === selectedProcessKey && v.version === selectedVersion);
    return v ? v.startVariables : [];
}

function selectVersion(processKey, version) {
    selectedProcessKey = processKey;
    selectedVersion = version;
    renderVersions();
    renderVariableForm(document.getElementById('variableForm'), currentVersionVariables());
}

function renderVersions() {
    document.getElementById('btnNewOrder').disabled = selectedProcessKey === null;
    const tbody = document.querySelector('#versionsTable tbody');
    tbody.innerHTML = '';
    if (currentVersions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="hint">Nothing deployed yet — click Load v1 below, or deploy one from the admin console.</td></tr>';
        return;
    }
    const latestByKey = latestVersionByKey();
    const sorted = [...currentVersions].sort((a, b) =>
        a.processKey.localeCompare(b.processKey) || b.version - a.version);
    for (const v of sorted) {
        const isSelected = v.processKey === selectedProcessKey && v.version === selectedVersion;
        const tr = document.createElement('tr');
        tr.className = 'clickable' + (isSelected ? ' selected' : '');
        tr.innerHTML = `
            <td><input type="radio" name="versionPick" ${isSelected ? 'checked' : ''}></td>
            <td class="mono">${escapeHtml(v.processKey)}</td>
            <td>v${v.version}${v.version === latestByKey.get(v.processKey) ? ' (latest)' : ''}</td>
            <td>${escapeHtml(v.name)}</td>
            <td>${v.steps.length}</td>
            <td>${fmtDate(v.createdAt)}</td>
        `;
        tr.addEventListener('click', () => selectVersion(v.processKey, v.version));
        tbody.appendChild(tr);
    }
}

// ---- Graphical variables editor -- generic, driven by whatever the selected version declares
// in its startVariables (see VariableSpec/VariableSpecs in the engine, and DemoProcessDefinitions
// for what this page's own process actually declares). Shared, identical mechanism with the admin
// console's admin.js -- neither page hardcodes "orderId" or "generateInvoiceFail" anywhere. ----

function renderVariableForm(container, variableSpecs, options = {}) {
    container.innerHTML = '';
    if (variableSpecs.length === 0) {
        if (options.fallbackToJson) {
            container.innerHTML = `
                <p class="hint">This version declares no start variables -- edit the raw JSON instead.</p>
                <textarea id="variablesJsonFallback" rows="3">{}</textarea>
            `;
        } else {
            container.innerHTML = '<p class="hint">This version declares no start variables.</p>';
        }
        return;
    }
    const fields = variableSpecs.filter(v => v.type !== 'BOOLEAN');
    const switches = variableSpecs.filter(v => v.type === 'BOOLEAN');
    for (const v of fields) {
        const defaultValue = v.defaultValue === null || v.defaultValue === undefined ? '' : v.defaultValue;
        const isNumber = v.type === 'NUMBER';
        // min=0/step=1: these are all non-negative counters (retry/fail-attempt counts) -- the
        // browser's own number-input validation and spinner enforce that, no negative values.
        const numberAttrs = isNumber ? 'min="0" step="1" inputmode="numeric"' : '';
        const row = document.createElement('div');
        row.className = 'row';
        row.innerHTML = `
            <label class="inline-field">${escapeHtml(v.name)}
                <input type="${isNumber ? 'number' : 'text'}" class="var-field-input" ${numberAttrs} data-var-name="${escapeHtml(v.name)}" data-var-type="${v.type}" value="${escapeHtml(defaultValue)}">
            </label>
            ${v.description ? `<span class="hint">${escapeHtml(v.description)}</span>` : ''}
        `;
        container.appendChild(row);
    }
    for (const v of switches) {
        const row = document.createElement('div');
        row.className = 'row';
        row.innerHTML = `
            <label class="checkbox"><input type="checkbox" data-var-name="${escapeHtml(v.name)}" data-var-type="BOOLEAN" ${v.defaultValue ? 'checked' : ''}> ${escapeHtml(v.name)}</label>
            ${v.description ? `<span class="hint">${escapeHtml(v.description)}</span>` : ''}
        `;
        container.appendChild(row);
    }
}

// Throws on invalid JSON in the fallback textarea -- callers must catch and toast.
function collectVariablesFromForm(container) {
    const jsonFallback = container.querySelector('#variablesJsonFallback');
    if (jsonFallback) {
        return JSON.parse(jsonFallback.value || '{}');
    }
    const variables = {};
    for (const el of container.querySelectorAll('[data-var-name]')) {
        const name = el.dataset.varName;
        if (el.dataset.varType === 'BOOLEAN') {
            if (el.checked) variables[name] = true;
        } else if (el.dataset.varType === 'NUMBER') {
            // Clamped, not just min="0" on the element -- that only stops the spinner arrows and
            // flags the field invalid, it doesn't stop someone typing "-5" directly.
            if (el.value.trim() !== '') variables[name] = Math.max(0, Number(el.value));
        } else if (el.value.trim() !== '') {
            variables[name] = el.value.trim();
        }
    }
    return variables;
}

// ---- Start instance ----
// The version table stays visible always (a business user needs it to browse/pick a version
// regardless of whether they're about to start anything) -- only "Start order" and the Variables
// form gate behind "New instance", since those are the two elements specific to actually composing
// a new one. Matches the admin console's own "New instance" gate (see admin.js).

function openNewOrderForm() {
    document.getElementById('btnNewOrder').classList.add('hidden');
    document.getElementById('newOrderForm').classList.remove('hidden');
}

function closeNewOrderForm() {
    document.getElementById('newOrderForm').classList.add('hidden');
    document.getElementById('btnNewOrder').classList.remove('hidden');
}

async function startInstance() {
    if (selectedProcessKey === null || selectedVersion === null) { log('Select a version to start first', true); return; }
    let variables;
    try {
        variables = collectVariablesFromForm(document.getElementById('variableForm'));
    } catch (e) {
        log('Variables is not valid JSON: ' + e.message, true);
        return;
    }
    const latest = latestVersionByKey().get(selectedProcessKey);
    const versionParam = selectedVersion !== latest ? `&version=${selectedVersion}` : '';
    try {
        await api('POST', `/process-instances?processKey=${encodeURIComponent(selectedProcessKey)}${versionParam}`, { variables });
    } catch (e) {
        return;
    }
    closeNewOrderForm();
    await refresh();
}

// ---- Orders + Tasks (one fetch of GET /process-instances, with no processKey filter, covers
// both -- every instance of every deployed process, not just order-fulfillment, so a manual task
// belonging to some other process built from these same step handlers still shows up here. It
// already embeds each instance's steps, so no per-instance detail call is needed) ----

async function refresh() {
    let instances;
    try {
        instances = await api('GET', '/process-instances');
    } catch (e) {
        return;
    }
    const keys = [...new Set(instances.map(i => i.processKey))];
    const stepTypeByKeyVersion = new Map(); // `${processKey}::${version}` -> Map(stepKey -> type)
    for (const key of keys) {
        let versions;
        try {
            versions = await api('GET', `/process-definitions/${encodeURIComponent(key)}/versions`);
        } catch (e) {
            versions = [];
        }
        for (const v of versions) {
            stepTypeByKeyVersion.set(`${key}::${v.version}`, new Map(v.steps.map(s => [s.stepKey, s.type])));
        }
    }

    renderOrders(instances);
    renderTasks(instances, stepTypeByKeyVersion);
}

function renderOrders(instances) {
    const tbody = document.querySelector('#ordersTable tbody');
    tbody.innerHTML = '';
    if (instances.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="hint">No orders yet — start one above.</td></tr>';
        return;
    }
    for (const inst of instances) {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="mono">${inst.id}</td>
            <td class="mono">${escapeHtml(inst.processKey)}</td>
            <td>${inst.version}</td>
            <td><span class="badge ${inst.status}">${inst.status}</span></td>
            <td>${fmtDate(inst.updatedAt)}</td>
        `;
        tbody.appendChild(tr);
    }
}

function renderTasks(instances, stepTypeByKeyVersion) {
    const tbody = document.querySelector('#tasksTable tbody');
    tbody.innerHTML = '';
    const tasks = [];
    for (const inst of instances) {
        const stepTypeByKey = stepTypeByKeyVersion.get(`${inst.processKey}::${inst.version}`) || new Map();
        for (const step of inst.steps) {
            if (step.status === 'ACTIVE' && stepTypeByKey.get(step.stepKey) === 'USER_TASK') {
                tasks.push({ instance: inst, step });
            }
        }
    }
    if (tasks.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="hint">No tasks waiting right now.</td></tr>';
        return;
    }
    for (const { instance, step } of tasks) {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td class="mono">${escapeHtml(instance.processKey)}</td>
            <td class="mono">${instance.id}</td>
            <td>${escapeHtml(step.stepKey)}</td>
            <td>${fmtDate(step.activatedAt)}</td>
            <td></td>
        `;
        const btn = document.createElement('button');
        btn.textContent = 'Complete';
        btn.addEventListener('click', () => completeTask(instance.id, step.id));
        tr.lastElementChild.appendChild(btn);
        tbody.appendChild(tr);
    }
}

async function completeTask(instanceId, stepInstanceId) {
    try {
        await api('POST', `/process-instances/${instanceId}/steps/${stepInstanceId}/complete`, { variables: {} });
    } catch (e) {
        return;
    }
    await refresh();
}

// ---- Live advisory warnings (SLA breach, ambiguous dispatch, compensation failure) -- pushed by
// DemoAdvisoryListener the moment one occurs, over one long-lived Server-Sent Events connection.
// No polling: the connection just sits open, and the browser's own EventSource reconnects on its
// own if it ever drops. ----

function showWarningBanner(warning) {
    const detail = warning.detail ? ` -- ${warning.detail}` : '';
    document.getElementById('warningBannerText').textContent =
        `${warning.type}: order ${warning.orderId} (${warning.processKey} / ${warning.stepKey})${detail}`;
    document.getElementById('warningBanner').classList.remove('hidden');
}

function hideWarningBanner() {
    document.getElementById('warningBanner').classList.add('hidden');
}

const warningSource = new EventSource('/api/demo/warnings/stream');
warningSource.addEventListener('warning', (evt) => showWarningBanner(JSON.parse(evt.data)));

// ---- Wiring ----

document.getElementById('warningBannerClose').addEventListener('click', hideWarningBanner);
document.getElementById('btnNewOrder').addEventListener('click', openNewOrderForm);
document.getElementById('btnStart').addEventListener('click', startInstance);
document.getElementById('btnDeployV1').addEventListener('click', () => deployVersion('/demo/order-fulfillment/deploy-v1'));
document.getElementById('btnDeployV2').addEventListener('click', () => deployVersion('/demo/order-fulfillment/deploy-v2'));
document.getElementById('btnRedeployAll').addEventListener('click', redeployAllBundledProcesses);

// initial load, then keep polling so new orders and newly-active tasks show up on their own.
// The version picker isn't part of that poll -- it only changes on a deploy action, and
// re-rendering it every tick would reset mid-interaction (a click, a half-filled variable) for
// no reason.
refresh();
refreshVersions();
refreshDeploymentStatus();
setInterval(refresh, REFRESH_INTERVAL_MS);
