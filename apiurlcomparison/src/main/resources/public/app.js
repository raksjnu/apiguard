document.addEventListener('DOMContentLoaded', () => {
    // Inject Styles for Settings Panel
    const style = document.createElement('style');
    style.innerHTML = `
        .settings-card {
            max-width: 600px;
            margin: 40px auto;
            background: white;
            padding: 30px;
            border-radius: 12px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.05), 0 10px 15px rgba(0,0,0,0.1);
        }
        .settings-card h2 {
            border-bottom: 2px solid #f0f0f0;
            padding-bottom: 15px;
            margin-bottom: 25px;
            color: var(--primary-color);
            font-size: 1.5rem;
        }
        .settings-group {
            margin-bottom: 25px;
        }
        .settings-group label {
            display: block;
            font-weight: 700;
            margin-bottom: 8px;
            color: #2d3748;
        }
        .settings-group input[type="text"] {
            width: 100%;
            padding: 10px;
            border: 1px solid #e2e8f0;
            border-radius: 6px;
            font-size: 0.95rem;
            transition: border-color 0.2s;
        }
        .settings-group input[type="text"]:focus {
            border-color: var(--primary-color);
            outline: none;
            box-shadow: 0 0 0 3px rgba(107, 70, 193, 0.1);
        }
    `;
    document.head.appendChild(style);

    // console.log('[APP] Check statusIndicator removal...'); 
    // User requested removal of "Running..." indicator. We'll ensure it's hidden or removed.
    const statusIndicator = document.getElementById('statusIndicator');
    if(statusIndicator) statusIndicator.style.display = 'none'; // Force hide globally
    
    // --- State & Core Elements ---
    let loadedConfig = null;
    const compareBtn = document.getElementById('compareBtn');
    const resultsContainer = document.getElementById('resultsContainer');
    // const statusIndicator = document.getElementById('statusIndicator'); // Moved to top
    const headersTable = document.getElementById('headersTable').querySelector('tbody');
    const tokensTable = document.getElementById('tokensTable').querySelector('tbody');

    // --- Dynamic Rows ---
    const addRow = (tbody, placeholders, values = []) => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><input type="text" placeholder="${placeholders[0]}" class="key-input" value="${values[0] || ''}"></td>
            <td><input type="text" placeholder="${placeholders[1]}" class="value-input" value="${values[1] || ''}"></td>
            <td><button type="button" class="btn-remove" style="background:none; border:none; cursor:pointer; color:#dc3545; font-size:1.2rem;" onclick="this.closest('tr').remove()">×</button></td>
        `;
        tbody.appendChild(tr);
        return tr;
    };

    document.getElementById('addHeaderBtn').addEventListener('click', () => addRow(headersTable, ['Header Name', 'Value']));
    document.getElementById('addTokenBtn').addEventListener('click', () => addRow(tokensTable, ['Token Name', 'Value']));

    // --- Navigation (SPA) ---
    document.querySelectorAll('.nav-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const target = tab.dataset.view;
            document.querySelectorAll('.nav-tab').forEach(t => t.style.borderBottom = 'none');
            tab.style.borderBottom = '3px solid var(--primary-color)';
            document.querySelectorAll('.view-container').forEach(v => v.classList.remove('active'));
            document.getElementById(target).classList.add('active');
            if (target === 'utilitiesView') {
                loadExportServices();
            }
        });
    });

    const loadExportServices = async () => {
        const select = document.getElementById('exportService');
        const workDir = document.getElementById('workingDirectory').value.trim();
        try {
            const resp = await fetch(`api/baselines/services?workDir=${encodeURIComponent(workDir)}`);
            if (resp.ok) {
                const services = await resp.json();
                select.innerHTML = '<option value="ALL">-- All Services --</option>';
                services.forEach(s => {
                    const opt = document.createElement('option');
                    opt.value = s;
                    opt.innerText = s;
                    select.appendChild(opt);
                });
            }
        } catch(e) { console.error('Failed to load export services', e); }
    };

    // --- UI Activity Logging ---
    const logActivity = (msg, type = 'info', contentToCopy = null) => {
        const log = document.getElementById('activityLog');
        if (!log) return;
        const entry = document.createElement('div');
        entry.className = `log-entry log-${type}`;
        const ts = new Date().toLocaleTimeString();
        
        // Escape HTML for safety, especially for XML/WSDL content
        const escapedMsg = msg
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");

        let html = `<span class="log-timestamp">[${ts}]</span> ${escapedMsg}`;
        if (contentToCopy) {
            html += ` <button type="button" class="btn-secondary log-copy-btn" style="padding:2px 6px; font-size:0.6rem; margin-left:10px; vertical-align:middle;">📋 Copy</button>`;
        }
        
        entry.innerHTML = html;
        if (contentToCopy) {
            entry.querySelector('.log-copy-btn').onclick = (e) => {
                e.stopPropagation();
                copyToClipboard(contentToCopy, e.target);
            };
        }
        
        log.appendChild(entry);
        log.scrollTop = log.scrollHeight;
    };

    document.getElementById('clearLogBtn').onclick = () => {
        if (confirm('Clear the current session Activity Logs? This will not affect stored data.')) {
            document.getElementById('activityLog').innerHTML = '<div class="log-entry log-info" style="border:none; padding:10px; text-align:center; color:#94a3b8;">[Log Cleared]</div>';
            logActivity('New session log started. Utilities and Comparisons will be tracked here.');
        }
    };

    // --- Settings Persistence ---
    const wdInput = document.getElementById('workingDirectory');
    if (wdInput) {
        wdInput.value = localStorage.getItem('apiUrlComparison_wd') || '';
        wdInput.addEventListener('input', () => localStorage.setItem('apiUrlComparison_wd', wdInput.value.trim()));
    }

    // --- Data Management (Load Mock) ---
    document.getElementById('loadMockTestDataBtn').addEventListener('click', async () => {
        if (confirm('Populate the form with API test templates? This will overwrite your current configuration.')) {
            const btn = document.getElementById('loadMockTestDataBtn');
            const originalText = btn.innerHTML;
            btn.innerHTML = '⏳ Loading...';
            try {
                const response = await fetch('api/config');
                if (response.ok) {
                    loadedConfig = await response.json();
                    const preservedType = document.getElementById('testType').value;
                    populateFormFields(preservedType);
                    btn.innerHTML = '✅ Templates Loaded!';
                    setTimeout(() => {
                        document.querySelector('[data-view="mainView"]').click();
                        btn.innerHTML = originalText;
                        syncTypeButtons(); // Force UI sync after view swap
                    }, 800);
                }
            } catch (e) {
                console.error(e);
                alert('Error loading configuration.');
                btn.innerHTML = originalText;
            }
        }
    });

    // --- Browser State Persistence ---
    const CACHE_KEY = 'api_forge_form_state';
    const saveState = () => {
        const state = {
            testType: document.getElementById('testType').value,
            wd: document.getElementById('workingDirectory').value,
            method: document.getElementById('method').value,
            opName: document.getElementById('operationName').value,
            url1: document.getElementById('url1').value,
            url2: document.getElementById('url2').value,
            payload: document.getElementById('payload').value,
            ignoredFields: document.getElementById('ignoredFields').value,
            maxIter: document.getElementById('maxIterations').value,
            strategy: document.getElementById('iterationController').value,
            headers: Array.from(headersTable.querySelectorAll('tr')).map(tr => ({
                k: tr.querySelector('.key-input')?.value,
                v: tr.querySelector('.value-input')?.value
            })),
            tokens: Array.from(tokensTable.querySelectorAll('tr')).map(tr => ({
                k: tr.querySelector('.key-input')?.value,
                v: tr.querySelector('.value-input')?.value
            }))
        };
        localStorage.setItem(CACHE_KEY, JSON.stringify(state));
    };

    const loadState = () => {
        const saved = localStorage.getItem(CACHE_KEY);
        if (!saved) return false;
        try {
            const s = JSON.parse(saved);
            if (s.testType) {
                document.getElementById('testType').value = s.testType;
                syncTypeButtons();
            }
            if (s.wd) document.getElementById('workingDirectory').value = s.wd;
            document.getElementById('operationName').value = s.opName || '';
            document.getElementById('url1').value = s.url1 || '';
            document.getElementById('url2').value = s.url2 || '';
            document.getElementById('payload').value = s.payload || '';
            document.getElementById('ignoredFields').value = s.ignoredFields || 'timestamp';
            document.getElementById('maxIterations').value = s.maxIter || '100';
            document.getElementById('method').value = s.method || 'POST';
            document.getElementById('iterationController').value = s.strategy || 'ONE_BY_ONE';

            headersTable.innerHTML = '';
            s.headers?.forEach(h => addRow(headersTable, ['Header Name', 'Value'], [h.k, h.v]));
            
            tokensTable.innerHTML = '';
            s.tokens?.forEach(t => addRow(tokensTable, ['Token Name', 'Value'], [t.k, t.v]));

            return true;
        } catch(e) { return false; }
    };

    // Attach listeners for auto-save
    ['method','operationName','url1','url2','payload','ignoredFields','maxIterations','iterationController'].forEach(id => {
        document.getElementById(id).addEventListener('input', saveState);
        document.getElementById(id).addEventListener('change', saveState);
    });
    headersTable.addEventListener('input', saveState);
    tokensTable.addEventListener('input', saveState);

    // --- Form Population & Defaults ---
    const resetFormToStandard = (type) => {
        document.getElementById('operationName').value = '';
        document.getElementById('url1').value = '';
        document.getElementById('url2').value = '';
        document.getElementById('payload').value = '';
        headersTable.innerHTML = '';
        tokensTable.innerHTML = ''; 
        
        // Add ONLY the default Content-Type as per the standard (REST/SOAP)
        addRow(headersTable, ['Header Name', 'Value'], ['Content-Type', (type === 'SOAP' ? 'text/xml' : 'application/json')]);
        
        // Removed auto-population logic entirely from here as requested.
        // Data will ONLY be populated via the "Load Mock Test Data" button in Settings.

        const authCheck = document.getElementById('enableAuth');
        if (authCheck) {
            authCheck.checked = false;
            document.getElementById('clientId').disabled = true;
            document.getElementById('clientSecret').disabled = true;
        }
    };

    };

    const loadDefaults = async () => {
        // First, try to restore the user's exact prior state
        const stateRestored = loadState();

        try {
            const response = await fetch('api/config');
            if (!response.ok) {
                 if (!stateRestored) resetFormToStandard('REST');
                 return;
            }
            loadedConfig = await response.json();
            
            // Only use server defaults if there was no local state
            if (!stateRestored) {
                const initialType = loadedConfig.testType || 'REST';
                document.getElementById('testType').value = initialType;
                
                if (loadedConfig.maxIterations) document.getElementById('maxIterations').value = loadedConfig.maxIterations;
                if (loadedConfig.iterationController) document.getElementById('iterationController').value = loadedConfig.iterationController;
                if (loadedConfig.ignoredFields) document.getElementById('ignoredFields').value = loadedConfig.ignoredFields.join(', ');
                
                resetFormToStandard(initialType);
            }
            
            // Sync specific global settings if server has them but local doesn't (or just always sync them)
            if (loadedConfig.workingDirectory && !document.getElementById('workingDirectory').value) {
                document.getElementById('workingDirectory').value = loadedConfig.workingDirectory;
            }

        } catch (e) { console.error('Defaults error:', e); }
        syncTypeButtons();
    };

    document.getElementById('testType').addEventListener('change', (e) => resetFormToStandard(e.target.value));

    // --- Global Utility: Copy to Clipboard ---
    const copyToClipboard = (text, btn) => {
        if (!text) return;
        navigator.clipboard.writeText(text).then(() => {
            const originalText = btn.innerText;
            btn.innerText = '✅';
            setTimeout(() => btn.innerText = originalText, 1000);
        }).catch(err => {
            console.error('Could not copy text: ', err);
            // Fallback for non-secure contexts
            const textArea = document.createElement("textarea");
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            try {
                document.execCommand('copy');
                btn.innerText = '✅';
                setTimeout(() => btn.innerText = originalText, 1000);
            } catch (e) {}
            document.body.removeChild(textArea);
        });
    };

    // --- Comparison Logic ---
    const buildConfig = () => {
        const h = {};
        headersTable.querySelectorAll('tr').forEach(tr => {
            const k = tr.querySelector('.key-input').value.trim();
            const v = tr.querySelector('.value-input').value.trim();
            if (k) h[k] = v;
        });

        const t = {};
        tokensTable.querySelectorAll('tr').forEach(tr => {
            const k = tr.querySelector('.key-input').value.trim();
            const v = tr.querySelector('.value-input').value.trim();
            if (k) {
                // Support both ; and , as separators
                t[k] = v.split(/[;,]/).map(i => i.trim()).filter(i => i.length > 0);
            }
        });

        const op = {
            name: document.getElementById('operationName').value || 'operation',
            methods: [document.getElementById('method').value],
            headers: h,
            payloadTemplatePath: document.getElementById('payload').value || null
        };

        return {
            testType: document.getElementById('testType').value,
            maxIterations: parseInt(document.getElementById('maxIterations').value) || 100,
            iterationController: document.getElementById('iterationController').value,
            tokens: t,
            rest: { api1: { baseUrl: document.getElementById('url1').value, operations: [op] }, 
                  api2: { baseUrl: document.getElementById('url2').value, operations: [op] } },
            soap: { api1: { baseUrl: document.getElementById('url1').value, operations: [op] }, 
                  api2: { baseUrl: document.getElementById('url2').value, operations: [op] } },
            ignoredFields: document.getElementById('ignoredFields').value.split(',').map(s=>s.trim()).filter(s=>s),
            ignoreHeaders: document.getElementById('ignoreHeaders').checked,
            comparisonMode: document.getElementById('comparisonMode').value
        };
    };

    // --- Modal Progress ---
    const showProgressModal = (msg) => {
        let modal = document.getElementById('progressModal');
        if (!modal) {
            modal = document.createElement('div');
            modal.id = 'progressModal';
            modal.style.cssText = `
                position: fixed; top: 0; left: 0; width: 100%; height: 100%;
                background: rgba(0,0,0,0.6); z-index: 10000;
                display: flex; justify-content: center; align-items: center;
                backdrop-filter: blur(2px);
            `;
            modal.innerHTML = `
                <div style="background:white; padding:30px; border-radius:12px; box-shadow:0 10px 25px rgba(0,0,0,0.2); width:400px; text-align:center;">
                    <div class="modal-msg" style="font-size:1.2rem; font-weight:800; color:var(--primary-color); margin-bottom:15px;">${msg}</div>
                    <div style="background:#edf2f7; height:10px; border-radius:5px; overflow:hidden; position:relative;">
                        <div class="progress-bar-anim" style="background:var(--primary-color); height:100%; width:30%; position:absolute; left:0; border-radius:5px;"></div>
                    </div>
                    <div style="font-size:0.85rem; color:#718096; margin-top:10px;">Please wait...</div>
                    <style>
                        @keyframes slide { 0% { left: -30%; } 100% { left: 100%; } }
                        .progress-bar-anim { animation: slide 1.5s infinite linear; }
                    </style>
                </div>
            `;
            document.body.appendChild(modal);
        } else {
            modal.querySelector('.modal-msg').innerText = msg;
            modal.style.display = 'flex';
        }
    };

    const hideProgressModal = () => {
        const modal = document.getElementById('progressModal');
        if (modal) modal.style.display = 'none';
    };

    compareBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const config = buildConfig();
        const isBaseline = config.comparisonMode === 'BASELINE';
        const isCapture = isBaseline && document.getElementById('baselineOperation').value === 'CAPTURE';
        const btnText = isCapture ? 'Capturing Baseline...' : (isBaseline ? 'Comparing Baseline...' : 'Running Comparison...');

        if (!config.rest.api1.baseUrl && !config.soap.api1.baseUrl) { alert('URL 1 is required'); return; }

        logActivity(`STARTING: ${btnText} Operation`, 'debug');
        logActivity(`Mode: ${config.testType} | Service: ${config.operationName}`, 'info');

        showProgressModal(btnText);
        compareBtn.disabled = true;
        resultsContainer.innerHTML = ''; 

        try {
            if (isBaseline) {
                await handleBaselineComparison(config);
            } else {
                logActivity(`EXEC: POST api/compare (Internal API call)`, 'debug');
                const response = await fetch('api/compare', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(config)
                });
                if (!response.ok) throw new Error(`Execution failed: ${response.status}`);
                const data = await response.json();
                
                const matches = data.filter(r => r.status === 'MATCH').length;
                logActivity(`COMPLETED: Received ${data.length} results. Matches: ${matches}, Mismatches: ${data.length - matches}`, 'success');
                renderResults(data);
            }
        } catch (err) {
            logActivity(`ERROR: ${err.message}`, 'error');
            resultsContainer.innerHTML = `<div class="error-msg">Error: ${err.message}</div>`;
        } finally {
            hideProgressModal();
            compareBtn.disabled = false;
        }
    });

    // --- Baseline Logic ---
    const handleBaselineComparison = async (config) => {
        const op = document.getElementById('baselineOperation').value;
        const workDir = document.getElementById('workingDirectory').value.trim();
        
        if (op === 'CAPTURE') {
            const svc = document.getElementById('baselineServiceName').value.trim();
            if (!svc) { alert('Service Name required for capture'); return; }
            config.baseline = {
                operation: 'CAPTURE',
                serviceName: svc,
                description: document.getElementById('baselineDescription').value,
                tags: document.getElementById('baselineTags').value.split(',').map(t=>t.trim()),
                storageDir: workDir || null
            };
        } else {
            const svc = document.getElementById('baselineServiceSelect').value;
            const date = document.getElementById('baselineDateSelect').value;
            const run = document.getElementById('baselineRunSelect').value;
            if (!svc || !date || !run) { alert('Select service, date, and run'); return; }
            config.baseline = {
                operation: 'COMPARE',
                serviceName: svc,
                compareDate: date,
                compareRunId: run,
                storageDir: workDir || null
            };
        }

        logActivity(`BASELINE EXEC: ${config.baseline.operation} for Service: ${config.baseline.serviceName}`, 'debug');

        const response = await fetch('api/compare', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
        if (!response.ok) throw new Error(`Baseline op failed: ${response.status}`);
        const data = await response.json();
        logActivity(`BASELINE COMPLETED: ${config.baseline.operation} success for ${config.baseline.serviceName}`, 'success');
        renderResults(data);
    };

    // --- Rendering Results ---
    const renderResults = (results) => {
        resultsContainer.innerHTML = '';
        if (!results || results.length === 0) {
            resultsContainer.innerHTML = '<div class="empty-state">No results to show.</div>';
            return;
        }

        const summary = document.createElement('div');
        summary.className = 'card';
        summary.style.margin = '0 0 20px 0';
        summary.style.borderLeft = '8px solid var(--primary-color)';
        summary.style.padding = '20px';
        
        const matches = results.filter(r => r.status === 'MATCH').length;
        const mismatches = results.filter(r => r.status === 'MISMATCH').length;
        const errors = results.filter(r => r.status === 'ERROR').length;

        const isBaselineCapture = document.getElementById('comparisonMode').value === 'BASELINE' && document.getElementById('baselineOperation').value === 'CAPTURE';
        const titleText = isBaselineCapture ? '📊 Baseline Captured Result Summary' : '📊 Comparison Result Summary';

        summary.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px; border-bottom:1px solid #eee; padding-bottom:10px;">
                <div style="font-weight:800; color:var(--primary-color); font-size:1.1rem;">${titleText}</div>
                <div style="display:flex; gap:10px;">
                    <button id="expandAllBtn" class="btn-secondary" style="padding:4px 8px; font-size:0.7rem;">↕ Expand All</button>
                    <button id="collapseAllBtn" class="btn-secondary" style="padding:4px 8px; font-size:0.7rem;">↑ Collapse All</button>
                </div>
            </div>
            <div style="display:flex; gap:30px; align-items:center;">
                <div style="text-align:center;">
                    <div style="font-size:0.8rem; color:#666; margin-bottom:5px; font-weight:700;">TOTAL</div>
                    <div style="font-size:1.5rem; font-weight:800; color:var(--primary-color);">${results.length}</div>
                </div>
                <div style="height:40px; width:1px; background:#ddd;"></div>
                <div style="text-align:center;">
                    <div style="font-size:0.8rem; color:var(--success-color); margin-bottom:5px; font-weight:700;">MATCHES</div>
                    <div style="font-size:1.5rem; font-weight:800; color:var(--success-color);">${matches}</div>
                </div>
                <div style="text-align:center;">
                    <div style="font-size:0.8rem; color:var(--error-color); margin-bottom:5px; font-weight:700;">MISMATCHES</div>
                    <div style="font-size:1.5rem; font-weight:800; color:var(--error-color);">${mismatches}</div>
                </div>
                <div style="text-align:center;">
                    <div style="font-size:0.8rem; color:#f6ad55; margin-bottom:5px; font-weight:700;">ERRORS</div>
                    <div style="font-size:1.5rem; font-weight:800; color:#f6ad55;">${errors}</div>
                </div>
            </div>
        `;
        resultsContainer.appendChild(summary);

        document.getElementById('expandAllBtn').addEventListener('click', () => {
            document.querySelectorAll('.result-body').forEach(b => b.style.display = 'block');
            document.querySelectorAll('.result-header').forEach(h => h.classList.add('open'));
        });
        document.getElementById('collapseAllBtn').addEventListener('click', () => {
            document.querySelectorAll('.result-body').forEach(b => b.style.display = 'none');
            document.querySelectorAll('.result-header').forEach(h => h.classList.remove('open'));
        });

        results.forEach((res, i) => {
            const item = document.createElement('div');
            item.className = 'result-item';
            item.style.marginBottom = '12px';
            
            const isMatch = res.status === 'MATCH';
            const statusClass = `status-${res.status}`;
            
            let tokens = '';
            if (res.iterationTokens && Object.keys(res.iterationTokens).length > 0) {
                tokens = `<div style="font-size:0.7rem; color:#666;">Tokens: ${Object.entries(res.iterationTokens).map(([k,v])=>`${k}=${v}`).join('; ')}</div>`;
            } else if (i === 0) {
                tokens = `<div style="font-size:0.7rem; color:#666;">Tokens: N/A</div>`;
            }

            item.innerHTML = `
                <div class="result-header" style="cursor:pointer; padding:12px; display:flex; justify-content:space-between; align-items:center;">
                    <div>
                        <div style="font-weight:700; font-size:0.9rem;">#${i+1} - ${res.operationName}</div>
                        <div style="font-size:0.75rem; color:#0056b3; margin-bottom:1px; font-family:monospace;">1: ${res.api1 ? res.api1.url : ''}</div>
                        <div style="font-size:0.75rem; color:#0056b3; margin-bottom:2px; font-family:monospace;">2: ${res.api2 ? res.api2.url : (document.getElementById('comparisonMode').value === 'BASELINE' ? 'Baseline' : '')}</div>
                        ${tokens}
                    </div>
                    <span class="${statusClass}" style="padding:4px 12px; border-radius:12px; font-weight:700; font-size:0.75rem; color: #fff; background-color: ${res.status === 'MISMATCH' ? '#fc8181' : (res.status === 'MATCH' ? 'var(--success-color)' : (res.status === 'ERROR' ? 'var(--error-color)' : '#cbd5e0'))};">${res.status}</span>
                </div>
                <div class="result-body" style="display:none; padding:15px; border-top:1px solid #eee; background:#fafafa; border-radius: 0 0 12px 12px;">
                    ${renderPayloadContent(res, i)}
                </div>
            `;
            
            item.querySelector('.result-header').addEventListener('click', (e) => {
                // Ignore clicks on copy buttons if any were in header (though they aren't currently)
                if (e.target.tagName === 'BUTTON') return;

                const body = item.querySelector('.result-body');
                const isCurrentlyNone = body.style.display === 'none' || body.style.display === '';
                body.style.display = isCurrentlyNone ? 'block' : 'none';
                item.querySelector('.result-header').classList.toggle('open', isCurrentlyNone);
                
                if (isCurrentlyNone) { 
                    setupSync(body);
                    // Attach copy events once visible
                    body.querySelectorAll('.copy-btn').forEach(btn => {
                        btn.onclick = (e) => {
                            e.stopPropagation();
                            const target = btn.nextElementSibling;
                            if (target) {
                                copyToClipboard(target.innerText, btn);
                            }
                        };
                    });
                }
            });
            resultsContainer.appendChild(item);
        });
    };

    const escapeHtml = (text) => {
        if (!text) return '';
        return String(text)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    };

    const renderPayloadContent = (res, idx) => {
        let contentHtml = '';
        if (res.errorMessage) {
            contentHtml += `<div style="color:var(--error-color); font-size:0.85rem; padding:10px; background:#fff5f5; border-radius:6px; border:1px solid #feb2b2; margin-bottom:10px;"><strong>HTTP ERROR DETECTED:</strong> ${res.errorMessage}</div>`;
        }
        const a1 = res.api1 || {};
        const a2 = res.api2 || {};

        const areHeadersMatch = (h1, h2) => {
            if (!h1 || !h2) return h1 === h2;
            const entries1 = Object.entries(h1).sort();
            const entries2 = Object.entries(h2).sort();
            return JSON.stringify(entries1) === JSON.stringify(entries2);
        };

        const arePayloadsMatch = (p1, p2) => {
            if (!p1 || !p2) return p1 === p2;
            try {
                const j1 = typeof p1 === 'string' ? JSON.parse(p1) : p1;
                const j2 = typeof p2 === 'string' ? JSON.parse(p2) : p2;
                return JSON.stringify(j1) === JSON.stringify(j2);
            } catch (e) {
                return String(p1).trim() === String(p2).trim();
            }
        };

        const renderHeaders = (headers) => {
            if (!headers || Object.keys(headers).length === 0) return '<div style="color:#999; font-style:italic;">[No Headers]</div>';
            // Join with newline for diffing. Escape VALUES but keep our controlled <strong> tags.
            return Object.entries(headers).sort().map(([k,v])=>`<div><strong>${escapeHtml(k)}:</strong> ${escapeHtml(v)}</div>`).join('\n');
        };

        const renderComponent = (v1, v2, label, type, isHeader) => {
            const hasAPI2 = !!res.api2;
            const isMatch = isHeader ? areHeadersMatch(v1, v2) : arePayloadsMatch(v1, v2);

            // Visual Diff Logic
            let c1 = isHeader ? renderHeaders(v1) : formatData(v1);
            let c2 = isHeader ? renderHeaders(v2) : formatData(v2);

            // Compute line-by-line highlights if mismatch and dual pane
            if (!isMatch && hasAPI2) {
                // Ensure text is comparable by splitting and standardizing
                const lines1 = String(c1).split(/\r?\n/);
                const lines2 = String(c2).split(/\r?\n/);
                const max = Math.max(lines1.length, lines2.length);
                
                let out1 = '';
                let out2 = '';
                
                for (let i = 0; i < max; i++) {
                    const l1 = lines1[i] || '';
                    const l2 = lines2[i] || '';
                    const isDiff = l1.trim() !== l2.trim();
                    const style = isDiff ? 'background-color:#ffe6e6; font-weight:bold; color:#c53030; display:inline-block; width:100%; border-radius:2px;' : '';
                    
                    // If it's a header, l1 already contains safe HTML from renderHeaders.
                    // If it's a payload, we must escape it now (unless it was already escaped).
                    // Actually, let's just make l1/l2 reliable before the loop.
                    const content1 = isHeader ? l1 : escapeHtml(l1).replace(/ /g, '&nbsp;');
                    const content2 = isHeader ? l2 : escapeHtml(l2).replace(/ /g, '&nbsp;');
                    
                    out1 += `<div style="${style}">${content1 || '&nbsp;'}</div>`;
                    out2 += `<div style="${style}">${content2 || '&nbsp;'}</div>`;
                }
                c1 = out1;
                c2 = out2;
            } else {
                // If match or single pane, just wrap carefully to preserve existing style
                 if (!isHeader) {
                    // formatData returns raw string which we usually put in <pre>. 
                    // To keep consistent with diff view (divs), we'll split it too, or just use pre.
                    // Actually, let's keep it simple for matches.
                 }
            }

            const renderSingleBoxContent = (val, isDiffMode) => {
               if (isDiffMode) return `<div class="sync-p" style="margin:0; font-family:monospace; white-space:pre-wrap; font-size:0.8rem;">${val}</div>`;
               // Fallback for match/single
               return isHeader ? 
                   `<div class="sync-h" style="background:rgba(255,255,255,0.7); padding:8px; font-size:0.7rem; max-height:130px; overflow-y:auto; border-radius:4px; border:1px solid rgba(0,0,0,0.05);">${renderHeaders(val)}</div>` :
                   `<pre class="sync-p" style="margin:0;">${formatData(val)}</pre>`;
            };

            // Custom renders for mismatch Diff View
            const renderDiffBox = (valHtml, title, flex=1) => `
                <div class="${type}-box" style="flex:${flex}; min-width:0; position:relative;">
                    <div style="font-size:0.7rem; font-weight:700; color:var(--primary-color); margin-bottom:5px; text-transform:uppercase;">${title}</div>
                    <div style="position:relative;">
                        <button type="button" class="copy-btn" title="Copy ${label}">Copy</button>
                        ${isHeader ? 
                           `<div class="sync-h" style="background:rgba(255,255,255,0.7); padding:8px; font-size:0.7rem; max-height:130px; overflow-y:auto; border-radius:4px; border:1px solid rgba(0,0,0,0.05);">${valHtml}</div>` : 
                           `<div class="sync-p" style="margin:0; font-family:monospace; white-space:pre-wrap; font-size:0.8rem; overflow-x:auto;">${valHtml}</div>`
                        }
                    </div>
                </div>
            `;
            
            // Standard render for matches (reusing old logic style)
            const renderStandardBox = (val, title, flex=1) => `
                <div class="${type}-box" style="flex:${flex}; min-width:0; position:relative;">
                    <div style="font-size:0.7rem; font-weight:700; color:var(--primary-color); margin-bottom:5px; text-transform:uppercase;">${title}</div>
                     <div style="position:relative;">
                        <button type="button" class="copy-btn" title="Copy ${label}">Copy</button>
                        ${isHeader ? 
                            `<div class="sync-h" style="background:rgba(255,255,255,0.7); padding:8px; font-size:0.7rem; max-height:130px; overflow-y:auto; border-radius:4px; border:1px solid rgba(0,0,0,0.05);">${renderHeaders(val)}</div>` :
                            `<pre class="sync-p" style="margin:0;">${escapeHtml(formatData(val))}</pre>`
                        }
                    </div>
                </div>
            `;

            if (!hasAPI2 || isMatch) {
                return `<div style="display:flex; margin-bottom:10px;">${renderStandardBox(v1, `${label} ${isMatch && hasAPI2 ? '(Match)' : ''}`)}</div>`;
            } else {
                const isBaseline = document.getElementById('comparisonMode').value === 'BASELINE';
                const t1 = isBaseline ? `Current ${label}` : `API 1 ${label}`;
                const t2 = isBaseline ? `Target/Baseline ${label}` : `API 2 ${label}`;
                return `
                    <div style="display:flex; gap:12px; margin-bottom:10px;">
                        ${renderDiffBox(c1, t1)}
                        ${renderDiffBox(c2, t2)}
                    </div>
                `;
            }
        };

        let diffs = '';
        if (res.status === 'MISMATCH' && res.differences) {
            diffs = `<div style="background:#fff5f5; border:1px solid #feb2b2; padding:12px; border-radius:8px; margin-bottom:15px; font-size:0.85rem;">
                        <strong style="color:#c53030;">Mismatch Details:</strong>
                        <ul style="margin:8px 0 0 0; padding-left:20px; color:#2d3748;">${res.differences.map(d=>`<li>${d}</li>`).join('')}</ul>
                     </div>`;
        }

        return `
            ${diffs}
            <!-- Request Section -->
            <div style="padding:10px; background:#f7fafc; border-radius:8px; border:2px solid #cbd5e0; margin-bottom:15px;">
                <div style="font-size:0.75rem; font-weight:800; color:#4a5568; margin-bottom:10px; text-transform:uppercase; letter-spacing:0.05em;">Input Request</div>
                ${renderComponent(a1.requestHeaders || {}, a2.requestHeaders || {}, 'Headers', 'request', true)}
                ${renderComponent(a1.requestPayload || '', a2.requestPayload || '', 'Payload', 'request', false)}
            </div>

            <!-- Response Section -->
            <div style="padding:10px; background:#f0fff4; border-radius:8px; border:2px solid #5ab078;">
                <div style="font-size:0.75rem; font-weight:800; color:#22543d; margin-bottom:10px; text-transform:uppercase; letter-spacing:0.05em;">API Responses</div>
                ${renderComponent(a1.responseHeaders, a2.responseHeaders, 'Headers', 'response', true)}
                ${renderComponent(a1.responsePayload, a2.responsePayload, 'Payload', 'response', false)}
            </div>
        `;
    };

    const formatData = (d) => {
        if (!d) return '[Empty]';
        try { 
            return JSON.stringify(typeof d === 'string' ? JSON.parse(d) : d, null, 2); 
        } catch(e) { 
            // Check if it looks like XML
            const str = String(d).trim();
            if (str.startsWith('<') && str.endsWith('>')) {
                 return formatXml(str);
            }
            return str;
        }
    };

    const formatXml = (xml) => {
        let formatted = '';
        let reg = /(>)(<)(\/*)/g;
        xml = xml.replace(reg, '$1\r\n$2$3');
        let pad = 0;
        xml.split('\r\n').forEach(node => {
            let indent = 0;
            if (node.match(/.+<\/\w[^>]*>$/)) {
                indent = 0;
            } else if (node.match(/^<\/\w/)) {
                if (pad != 0) {
                    pad -= 1;
                }
            } else if (node.match(/^<\w[^>]*[^\/]>.*$/)) {
                indent = 1;
            } else {
                indent = 0;
            }

            let padding = '';
            for (let i = 0; i < pad; i++) {
                padding += '  ';
            }
            formatted += padding + node + '\r\n';
            pad += indent;
        });
        return formatted;
    };

    const setupSync = (body) => {
        const hs = body.querySelectorAll('.sync-h');
        const ps = body.querySelectorAll('.sync-p');
        const sync = (els) => els.forEach(e => e.onscroll = () => els.forEach(o => { if(o!==e) o.scrollTop = e.scrollTop; }));
        if(hs.length > 1) sync(hs);
        if(ps.length > 1) sync(ps);
    };

    const initResize = () => {
        const handle = document.getElementById('resizeHandle');
        const grid = document.querySelector('.main-grid');
        if (!handle || !grid) return;
        let resizing = false;
        handle.addEventListener('mousedown', (e) => { resizing = true; document.body.classList.add('resizing'); e.preventDefault(); });
        document.addEventListener('mousemove', (e) => {
            if (!resizing) return;
            const w = e.clientX - grid.getBoundingClientRect().left;
            if (w > 250 && w < 900) grid.style.setProperty('--config-width', `${w}px`);
        });
        document.addEventListener('mouseup', () => { resizing = false; document.body.classList.remove('resizing'); });
    };

    const initUtilResize = () => {
        const handle = document.getElementById('utilResizeHandle');
        const logPanel = document.getElementById('utilLogPanel');
        if (!handle || !logPanel) return;
        let resizing = false;
        handle.addEventListener('mousedown', (e) => { 
            resizing = true; 
            document.body.style.cursor = 'col-resize';
            e.preventDefault();
        });
        document.addEventListener('mousemove', (e) => {
            if (!resizing) return;
            const screenWidth = window.innerWidth;
            const newWidth = screenWidth - e.clientX - 30; // 30 for padding/buffer
            if (newWidth > 250 && newWidth < screenWidth * 0.7) {
                logPanel.style.width = `${newWidth}px`;
            }
        });
        document.addEventListener('mouseup', () => { 
            resizing = false; 
            document.body.style.cursor = 'default';
        });
    };

    // --- Baseline Metadata Loading ---
    const baselineServiceSelect = document.getElementById('baselineServiceSelect');
    const baselineDateSelect = document.getElementById('baselineDateSelect');
    const baselineRunSelect = document.getElementById('baselineRunSelect');

    const loadBaselineServices = async () => {
        const workDir = document.getElementById('workingDirectory').value.trim();
        try {
            const url = `api/baselines/services${workDir ? '?workDir=' + encodeURIComponent(workDir) : ''}`;
            const response = await fetch(url);
            if (!response.ok) return;
            const services = await response.json();
            
            baselineServiceSelect.innerHTML = '<option value="">-- Select Service --</option>';
            services.forEach(s => {
                const opt = document.createElement('option');
                opt.value = s;
                opt.textContent = s;
                baselineServiceSelect.appendChild(opt);
            });
            
            if (services.length > 0) {
                baselineServiceSelect.value = services[0];
                baselineServiceSelect.dispatchEvent(new Event('change'));
            }
        } catch (e) { console.error('Error loading services:', e); }
    };

    baselineServiceSelect.addEventListener('change', async () => {
        const service = baselineServiceSelect.value;
        const workDir = document.getElementById('workingDirectory').value.trim();
        baselineDateSelect.innerHTML = '<option value="">-- Select Date --</option>';
        baselineRunSelect.innerHTML = '<option value="">-- Select Run --</option>';
        baselineDateSelect.disabled = true;
        baselineRunSelect.disabled = true;

        if (!service) return;

        try {
            const url = `api/baselines/dates/${encodeURIComponent(service)}${workDir ? '?workDir=' + encodeURIComponent(workDir) : ''}`;
            const response = await fetch(url);
            const dates = await response.json();
            
            dates.forEach(d => {
                const opt = document.createElement('option');
                opt.value = d;
                opt.textContent = d;
                baselineDateSelect.appendChild(opt);
            });
            
            if (dates.length > 0) {
                baselineDateSelect.disabled = false;
                baselineDateSelect.value = dates[0];
                baselineDateSelect.dispatchEvent(new Event('change'));
            }
        } catch (e) { console.error('Error loading dates:', e); }
    });

    baselineDateSelect.addEventListener('change', async () => {
        const service = baselineServiceSelect.value;
        const date = baselineDateSelect.value;
        const workDir = document.getElementById('workingDirectory').value.trim();
        baselineRunSelect.innerHTML = '<option value="">-- Select Run --</option>';
        baselineRunSelect.disabled = true;

        if (!service || !date) return;

        try {
            const url = `api/baselines/runs/${encodeURIComponent(service)}/${encodeURIComponent(date)}${workDir ? '?workDir=' + encodeURIComponent(workDir) : ''}`;
            const response = await fetch(url);
            const runs = await response.json();
            
            // Sort runs: Latest (by ID/Time) last, so pop() works OR sort descending and take [0]
            // The file system returns them unsorted or alphabetical.
            // Let's sort robustly: Extract timestamp or ID.
            // Run format: "run-001" or "run-001 - desc..."
            runs.sort((a,b) => {
                // Try comparing numeric run IDs first "run-001"
                const ra = parseInt(a.runId.replace(/run-/,'')) || 0;
                const rb = parseInt(b.runId.replace(/run-/,'')) || 0;
                return ra - rb; // Ascending
            });
            
            baselineRunSelect.innerHTML = '<option value="">-- Select Run --</option>'; // Reset again to be sure
            runs.forEach(r => {
                const opt = document.createElement('option');
                opt.value = r.runId;
                const desc = r.description ? ` - ${r.description}` : '';
                const tags = (r.tags && r.tags.length > 0) ? ` [${r.tags.join(', ')}]` : '';
                
                 // Format timestamp manually to YYYY-MM-DDTHH:mm:ss CST
                const dateObj = new Date(r.timestamp);
                const year = dateObj.getFullYear();
                const month = String(dateObj.getMonth() + 1).padStart(2, '0');
                const day = String(dateObj.getDate()).padStart(2, '0');
                const hours = String(dateObj.getHours()).padStart(2, '0');
                const minutes = String(dateObj.getMinutes()).padStart(2, '0');
                const seconds = String(dateObj.getSeconds()).padStart(2, '0');
                const ts = `${year}-${month}-${day}T${hours}:${minutes}:${seconds} CST`;
                
                opt.textContent = `${r.runId}${desc}${tags} (${ts})`;
                baselineRunSelect.appendChild(opt);
            });
            
            
            if (runs.length > 0) {
                baselineRunSelect.disabled = false;
                baselineRunSelect.value = runs[runs.length - 1].runId;
                baselineRunSelect.dispatchEvent(new Event('change'));
                
                // --- Add/Update "View Baseline" Button ---
                let viewBtn = document.getElementById('viewBaselineBtn');
                if (!viewBtn) {
                     viewBtn = document.createElement('button');
                     viewBtn.id = 'viewBaselineBtn';
                     viewBtn.type = 'button';
                     viewBtn.className = 'btn-secondary';
                     viewBtn.style.cssText = 'width:100%; margin-top:10px; padding:8px; font-weight:700; background:#edf2f7; color:#4a5568;';
                     viewBtn.innerHTML = '👁️ View Saved Baseline (History)';
                     document.getElementById('compareFields').appendChild(viewBtn);
                     
                     viewBtn.onclick = async () => {
                         const svc = baselineServiceSelect.value;
                         const dt = baselineDateSelect.value;
                         const rn = baselineRunSelect.value;
                         const wd = document.getElementById('workingDirectory').value.trim();
                         if (!svc || !dt || !rn) return;
                         
                         showProgressModal('Loading Baseline History...');
                         try {
                             // Use existing /details endpoint which returns List<ComparisonResult>
                             const url = `api/baselines/runs/${encodeURIComponent(svc)}/${encodeURIComponent(dt)}/${encodeURIComponent(rn)}/details${wd ? '?workDir=' + encodeURIComponent(wd) : ''}`;
                             const res = await fetch(url);
                             if(res.ok) {
                                 const results = await res.json();
                                 renderResults(results);
                             } else {
                                 alert('Could not load saved results.');
                             }
                         } catch(e) { console.error(e); alert('Failed to load history'); }
                         finally { hideProgressModal(); }
                     };
                }
            }
        } catch (e) { console.error('Error loading runs:', e); }
    });

    baselineRunSelect.addEventListener('change', async () => {
        const service = baselineServiceSelect.value;
        const date = baselineDateSelect.value;
        const runId = baselineRunSelect.value;
        const workDir = document.getElementById('workingDirectory').value.trim();

        if (!service || !date || !runId) return;

        try {
            const url = `api/baselines/runs/${encodeURIComponent(service)}/${encodeURIComponent(date)}/${encodeURIComponent(runId)}/endpoint${workDir ? '?workDir=' + encodeURIComponent(workDir) : ''}`;
            const response = await fetch(url);
            if (!response.ok) return;
            const details = await response.json();

            if (details.endpoint) document.getElementById('url1').value = details.endpoint;
            if (details.metadata && details.metadata.operation) document.getElementById('operationName').value = details.metadata.operation;
            if (details.payload) document.getElementById('payload').value = details.payload;
            
            // DO NOT sync Test Type - preserve user's current selection
            // The baseline metadata testType is informational only

            // Populate Headers
            headersTable.innerHTML = '';
            if (details.headers) {
                Object.entries(details.headers).forEach(([k, v]) => {
                    addRow(headersTable, ['Header Name', 'Value'], [k, v]);
                });
            }
        } catch (e) {
            console.error('Error fetching baseline details:', e);
        }
    });

    // --- Initialization ---
    // --- Baseline UI Events ---
    const opCapture = document.getElementById('opCapture');
    const opCompare = document.getElementById('opCompare');
    const modeCompare = document.getElementById('modeCompare');
    const modeBaseline = document.getElementById('modeBaseline');

        opCapture.onclick = () => {
            opCapture.classList.add('active');
            opCompare.classList.remove('active');
            document.getElementById('baselineOperation').value = 'CAPTURE';
            document.getElementById('captureFields').style.display = 'block';
            document.getElementById('compareFields').style.display = 'none';
            handleBaselineUI('BASELINE');
            // Ensure UI buttons stay in sync with hidden value
            syncTypeButtons();
        };
        opCompare.onclick = () => {
            opCompare.classList.add('active');
            opCapture.classList.remove('active');
            document.getElementById('baselineOperation').value = 'COMPARE';
            document.getElementById('captureFields').style.display = 'none';
            document.getElementById('compareFields').style.display = 'block';
            handleBaselineUI('BASELINE');
            loadBaselineServices();
            syncTypeButtons();
        };


    if (modeCompare && modeBaseline) {
        modeCompare.addEventListener('click', () => {
            modeCompare.classList.add('active');
            modeBaseline.classList.remove('active');
            document.getElementById('baselineControls').style.display = 'none';
            document.getElementById('url2Group').style.display = 'block';
            document.getElementById('comparisonMode').value = 'LIVE';
            handleBaselineUI('LIVE');
            syncTypeButtons();
        });
        modeBaseline.addEventListener('click', () => {
             // CRITICAL: Capture the actual user selection BEFORE mode switch
            const preservedType = document.getElementById('testType').value;
            
            modeBaseline.classList.add('active');
            modeCompare.classList.remove('active');
            document.getElementById('baselineControls').style.display = 'block';
            document.getElementById('url2Group').style.display = 'none';
            document.getElementById('comparisonMode').value = 'BASELINE';
            
            // This click triggers handleBaselineUI which used to flip types
            opCapture.click();
            
            // Re-apply the preserved type immediately
            document.getElementById('testType').value = preservedType;
            syncTypeButtons();
        });
    }

    const syncTypeButtons = () => {
        const type = document.getElementById('testType').value || 'REST';
        const isRest = (type === 'REST');
        document.getElementById('typeRest').classList.toggle('active', isRest);
        document.getElementById('typeSoap').classList.toggle('active', !isRest);
    };

    const updateTypeToggle = (type) => {
        document.getElementById('testType').value = type;
        syncTypeButtons();
    };

    // Export internal logic for mode switches
    window.handleBaselineUI = (mode) => {
        const ignoreHeaders = document.getElementById('ignoreHeaders');
        const compareBtn = document.getElementById('compareBtn');
        const op = document.getElementById('baselineOperation').value;
        const accordions = document.querySelectorAll('.accordion');
        const isCompareOp = (mode === 'BASELINE' && op === 'COMPARE');
        
        if (mode === 'BASELINE') {
            compareBtn.innerText = (op === 'CAPTURE') ? '📸 Capture Baseline' : '🔍 Compare Baseline';
            if (op === 'CAPTURE') {
                if (ignoreHeaders) {
                    ignoreHeaders.checked = false;
                    ignoreHeaders.disabled = true;
                }
            } else {
                if (ignoreHeaders) ignoreHeaders.disabled = false;
            }
        } else {
            compareBtn.innerText = '▶ Run Comparison';
            if (ignoreHeaders) ignoreHeaders.disabled = false;
        }

        // Accordion Management: Hide Endpoint, Request, Headers, Tokens during Compare mode
        accordions.forEach((acc, idx) => {
            if (idx < 4) { // First four accordions
                acc.style.display = isCompareOp ? 'none' : 'block';
            }
        });
    };

    // --- Field Toggles ---
    document.getElementById('enableAuth').addEventListener('change', (e) => {
        document.getElementById('clientId').disabled = !e.target.checked;
        document.getElementById('clientSecret').disabled = !e.target.checked;
    });

    document.getElementById('clearFormBtn').addEventListener('click', () => {
        if (confirm('Clear all fields?')) {
            resetFormToStandard(document.getElementById('testType').value);
            resultsContainer.innerHTML = '<div class="empty-state">Form cleared.</div>';
        }
    });

    // --- Auto Format ---
    document.getElementById('autoFormatBtn').addEventListener('click', () => {
        const p = document.getElementById('payload');
        p.value = formatData(p.value);
        saveState();
    });

    // --- UI Interaction Core ---
    
    // Accordions
    document.querySelectorAll('.accordion-header').forEach(h => {
        h.addEventListener('click', () => {
            const content = h.nextElementSibling;
            const isOpen = content.classList.contains('open');
            content.classList.toggle('open');
            h.querySelector('.icon').innerText = isOpen ? '▼' : '▲';
        });
    });

    // Type Toggle (REST/SOAP)
    document.querySelectorAll('.toggle-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.toggle-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const typeValue = btn.dataset.type;
            document.getElementById('testType').value = typeValue;
            // Trigger change event to sync dependencies
            document.getElementById('testType').dispatchEvent(new Event('change'));
            saveState();
        });
    });

    // --- Utilities Hub: Toolkit Logic ---
    
    // Connectivity
    document.getElementById('pingBtn').addEventListener('click', async () => {
        const url = document.getElementById('utilUrl').value.trim();
        if (!url) { alert('URL required'); return; }
        const fullInternalUrl = `api/utils/ping?url=${encodeURIComponent(url)}`;
        logActivity(`STARTING: Connectivity Test (Ping)`, 'debug');
        logActivity(`EXEC: GET ${fullInternalUrl}`, 'info');
        
        try {
            const resp = await fetch(fullInternalUrl);
            const data = await resp.json();
            if (data.success) {
                logActivity(`PING SUCCESS: ${url} (Status: ${data.statusCode}, Latency: ${data.latency}ms)`, 'success');
                logActivity(`RAW OUTPUT: ${JSON.stringify(data)}`, 'info');
            } else {
                logActivity(`PING FAILED: ${url} (Error: ${data.error || 'N/A'}, Status: ${data.statusCode})`, 'error');
                logActivity(`RAW OUTPUT: ${JSON.stringify(data)}`, 'info');
            }
        } catch(e) { logActivity(`Connection Error during Ping: ${e.message}`, 'error'); }
    });

    document.getElementById('fetchWsdlBtn').addEventListener('click', async () => {
        const url = document.getElementById('utilUrl').value.trim();
        if (!url) { alert('URL required'); return; }
        const wsdlUrl = url.includes('?') ? url : url + '?wsdl';
        const fullInternalUrl = `api/utils/wsdl?url=${encodeURIComponent(wsdlUrl)}`;
        
        logActivity(`STARTING: WSDL Discovery`, 'debug');
        logActivity(`EXEC: GET ${fullInternalUrl}`, 'info');
        
        try {
            const resp = await fetch(fullInternalUrl);
            if (resp.ok) {
                const wsdlContent = await resp.text();
                logActivity(`WSDL DISCOVERY SUCCESS: Found source at ${wsdlUrl}`, 'success');
                logActivity(`WSDL XML CONTENT (FULL):`, 'info', wsdlContent);
            } else {
                logActivity(`WSDL DISCOVERY FAILED: Endpoint returned code ${resp.status}`, 'error');
            }
        } catch(e) { logActivity(`Discovery Error: ${e.message}`, 'error'); }
    });

    // Auth Studio
    document.getElementById('decodeJwtBtn').addEventListener('click', () => {
        const jwt = document.getElementById('jwtInput').value.trim();
        if (!jwt) { logActivity('JWT Inspection failed: No token provided', 'error'); return; }
        logActivity('Inspecting JWT Claims...', 'debug');
        try {
            const parts = jwt.split('.');
            if (parts.length !== 3) throw new Error('Malformed JWT structure');
            const base64Url = parts[1];
            const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
            const jsonPayload = decodeURIComponent(atob(base64).split('').map(c=>'%'+('00'+c.charCodeAt(0).toString(16)).slice(-2)).join(''));
            logActivity(`JWT CLAIMS:\n${JSON.stringify(JSON.parse(jsonPayload), null, 2)}`, 'success');
        } catch(e) { logActivity(`JWT DECODE ERROR: ${e.message}`, 'error'); }
    });

    // Data Foundry
    const workpad = document.getElementById('dataWorkpad');
    document.getElementById('minifyBtn').onclick = () => { 
        logActivity('Minifying JSON...', 'debug');
        try { 
            workpad.value = JSON.stringify(JSON.parse(workpad.value)); 
            logActivity('JSON Minified.', 'success');
        } catch(e) { logActivity('JSON Parse Error: Invalid structure', 'error'); } 
    };
    document.getElementById('prettyBtn').onclick = () => { 
        logActivity('Formatting Data...', 'debug');
        workpad.value = formatData(workpad.value); 
        logActivity('Data Formatted.', 'success');
    };
    document.getElementById('b64EncBtn').onclick = () => { 
        logActivity('Encoding to Base64...', 'debug');
        try { 
            workpad.value = btoa(workpad.value); 
            logActivity('Base64 Encoded.', 'success');
        } catch(e) { logActivity('Encoding Error.', 'error'); } 
    };
    document.getElementById('b64DecBtn').onclick = () => { 
        logActivity('Decoding from Base64...', 'debug');
        try { 
            workpad.value = atob(workpad.value); 
            logActivity('Base64 Decoded.', 'success');
        } catch(e) { logActivity('Decoding Error: Invalid Base64.', 'error'); } 
    };

    // Baseline Portability
    document.getElementById('exportBtn').onclick = async () => {
        const svc = document.getElementById('exportService').value;
        const workDir = document.getElementById('workingDirectory').value.trim();
        const url = `api/baselines/export?service=${encodeURIComponent(svc)}&workDir=${encodeURIComponent(workDir)}`;
        
        logActivity(`STARTING: Baseline Export for service: ${svc}`, 'debug');
        showProgressModal(`Zipping Baselines for ${svc}...`);
        const btn = document.getElementById('exportBtn');
        const orig = btn.innerText;
        btn.innerText = '⏳ Zipping...';
        
        try {
            const resp = await fetch(url);
            if (!resp.ok) throw new Error('Export failed');
            const blob = await resp.blob();
            const downloadUrl = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = downloadUrl;
            a.download = `baselines_export_${svc}_${new Date().toISOString().slice(0,10)}.zip`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            logActivity(`EXPORT SUCCESS: Baseline package downloaded.`, 'success');
            btn.innerText = '✅ Exported!';
            setTimeout(() => btn.innerText = orig, 2000);
        } catch(e) { 
            logActivity(`EXPORT ERROR: ${e.message}`, 'error');
            alert('Export failed: ' + e.message); 
            btn.innerText = orig;
        } finally {
            hideProgressModal();
        }
    };

    const handleImport = async (file, overwrite = false) => {
        const workDir = document.getElementById('workingDirectory').value.trim();
        const status = document.getElementById('importStatus');
        if(status) status.innerText = '⏳ Uploading & Importing...';
        
        logActivity(`STARTING: Baseline Import. Overwrite=${overwrite}`, 'debug');
        showProgressModal(`Unzipping & Importing Baselines...`);
        
        try {
            const resp = await fetch(`api/baselines/import?workDir=${encodeURIComponent(workDir)}&overwrite=${overwrite}`, {
                method: 'POST',
                body: await file.arrayBuffer()
            });
            
            if (resp.status === 409) {
                hideProgressModal();
                const conflicts = await resp.json();
                if (confirm(`Conflicts detected for: ${conflicts.join(', ')}. Overwrite existing data?`)) {
                    await handleImport(file, true);
                } else {
                    logActivity(`IMPORT CANCELLED: User declined overwrite.`, 'info');
                    if(status) status.innerText = 'Import cancelled by user.';
                }
            } else if (resp.ok) {
                logActivity(`IMPORT SUCCESS: Baselines restored to workspace.`, 'success');
                if(status) status.innerText = '✅ Import Successful!';
                loadExportServices(); // Refresh list
                hideProgressModal();
            } else {
                const err = await resp.json();
                logActivity(`IMPORT ERROR: ${err.error || 'Unknown'}`, 'error');
                if(status) status.innerText = '❌ Import failed: ' + (err.error || 'Unknown error');
                hideProgressModal();
            }
        } catch(e) { 
            logActivity(`IMPORT ERROR: ${e.message}`, 'error');
            if(status) status.innerText = '❌ Connection Error'; 
            hideProgressModal();
        }
    };

    document.getElementById('importFile').onchange = (e) => { 
        const file = e.target.files[0];
        if (file) handleImport(file);
    };


    initResize();
    initUtilResize();
    loadDefaults();
});
