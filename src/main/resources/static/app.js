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
    errorEl.textContent = `Deployed order-fulfillment v${view.version}: start orders of it from the admin console.`;
    await refresh();
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

// ---- Live advisory warnings (SLA breach, unknown outcome, compensation failure) -- pushed by
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
document.getElementById('btnDeployV1').addEventListener('click', () => deployVersion('/demo/order-fulfillment/deploy-v1'));
document.getElementById('btnDeployV2').addEventListener('click', () => deployVersion('/demo/order-fulfillment/deploy-v2'));
document.getElementById('btnRedeployAll').addEventListener('click', redeployAllBundledProcesses);

// initial load, then keep polling so new orders and newly-active tasks show up on their own.
// The version picker isn't part of that poll -- it only changes on a deploy action, and
// re-rendering it every tick would reset mid-interaction (a click, a half-filled variable) for
// no reason.
refresh();
refreshDeploymentStatus();
setInterval(refresh, REFRESH_INTERVAL_MS);
