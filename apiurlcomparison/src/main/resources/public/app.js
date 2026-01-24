document.addEventListener('DOMContentLoaded', () => {
    console.log('[APP] Initializing API Comparison Tool...');
    
    // --- State & Core Elements ---
    let loadedConfig = null;
    const compareBtn = document.getElementById('compareBtn');
    const resultsContainer = document.getElementById('resultsContainer');
    const statusIndicator = document.getElementById('statusIndicator');
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
        });
    });

    // --- Settings Persistence ---
    const wdInput = document.getElementById('workingDirectory');
    if (wdInput) {
        wdInput.value = localStorage.getItem('apiUrlComparison_wd') || '';
        wdInput.addEventListener('input', () => localStorage.setItem('apiUrlComparison_wd', wdInput.value.trim()));
    }

    // --- Data Management (Load Mock) ---
    document.getElementById('loadMockTestDataBtn').addEventListener('click', async () => {
        if (confirm('Populate the form with real Mule service test data? Existing data will be replaced.')) {
            const btn = document.getElementById('loadMockTestDataBtn');
            const originalText = btn.innerText;
            btn.innerText = '⏳ Loading...';
            try {
                const response = await fetch('api/config');
                if (response.ok) {
                    loadedConfig = await response.json();
                    // Determine type from UI state (more reliable than hidden input during transitions)
                    const isSoap = document.getElementById('typeSoap').classList.contains('active');
                    const currentType = isSoap ? 'SOAP' : 'REST';
                    console.log('Populating Mock Data for:', currentType);
                    populateFormFields(currentType);
                    btn.innerText = '✅ Data Loaded!';
                    setTimeout(() => {
                        document.querySelector('[data-view="mainView"]').click();
                        btn.innerText = originalText;
                    }, 800);
                }
            } catch (e) {
                console.error(e);
                alert('Error loading configuration.');
                btn.innerText = originalText;
            }
        }
    });

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

    const populateFormFields = (type) => {
        if (!loadedConfig) return;
        const activeApis = (type === 'SOAP') ? loadedConfig.soap : loadedConfig.rest;
        if (!activeApis) return;

        document.getElementById('url1').value = activeApis.api1?.baseUrl || '';
        document.getElementById('url2').value = activeApis.api2?.baseUrl || '';

        const op = activeApis.api1?.operations?.[0];
        if (op) {
            document.getElementById('operationName').value = op.name || '';
            document.getElementById('payload').value = op.payloadTemplatePath || '';
            document.getElementById('method').value = op.methods?.[0] || 'POST';
            
            headersTable.innerHTML = '';
            if (op.headers) {
                Object.entries(op.headers).forEach(([k, v]) => addRow(headersTable, ['Header Name', 'Value'], [k, v]));
            }
        }

        // Only populate tokens during explicit "Load Mock" action
        tokensTable.innerHTML = '';
        if (loadedConfig.tokens) {
            Object.entries(loadedConfig.tokens).forEach(([k, list]) => {
                addRow(tokensTable, ['Token Name', 'Value'], [k, list.join('; ')]);
            });
        }
    };

    const loadDefaults = async () => {
        try {
            const response = await fetch('api/config');
            if (!response.ok) return;
            loadedConfig = await response.json();
            
            const initialType = loadedConfig.testType || 'REST';
            document.getElementById('testType').value = initialType;
            
            // Set global settings
            if (loadedConfig.maxIterations) document.getElementById('maxIterations').value = loadedConfig.maxIterations;
            if (loadedConfig.iterationController) document.getElementById('iterationController').value = loadedConfig.iterationController;
            if (loadedConfig.ignoredFields) document.getElementById('ignoredFields').value = loadedConfig.ignoredFields.join(', ');

            resetFormToStandard(initialType);
        } catch (e) { console.error('Defaults error:', e); }
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

    compareBtn.addEventListener('click', async (e) => {
        e.preventDefault();
        const config = buildConfig();
        if (!config.rest.api1.baseUrl && !config.soap.api1.baseUrl) { alert('URL 1 is required'); return; }

        const syncBtnText = () => {
            const mode = document.getElementById('comparisonMode').value;
            const op = document.getElementById('baselineOperation').value;
            const btn = document.getElementById('compareBtn');
            if (mode === 'BASELINE') {
                btn.innerText = (op === 'CAPTURE') ? '📸 Capture Baseline' : '🔍 Compare Baseline';
                btn.style.fontWeight = '800';
            } else {
                btn.innerText = '▶ Run Comparison';
                btn.style.fontWeight = '800';
            }
        };

        const handleBaselineUI = (mode) => {
            const ignoreHeaders = document.getElementById('ignoreHeaders');
            if (mode === 'BASELINE') {
                const op = document.getElementById('baselineOperation').value;
                if (op === 'CAPTURE') {
                    if (ignoreHeaders) {
                        ignoreHeaders.checked = false;
                        ignoreHeaders.disabled = true;
                    }
                } else {
                    if (ignoreHeaders) ignoreHeaders.disabled = false;
                }
            } else {
                if (ignoreHeaders) ignoreHeaders.disabled = false;
            }
            syncBtnText();
        };

        statusIndicator.classList.remove('hidden');
        compareBtn.disabled = true;
        resultsContainer.innerHTML = '<div class="empty-state">Running comparison...</div>';

        try {
            if (config.comparisonMode === 'BASELINE') {
                await handleBaselineComparison(config);
            } else {
                const response = await fetch('api/compare', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(config)
                });
                if (!response.ok) throw new Error('Execution failed');
                renderResults(await response.json());
            }
        } catch (err) {
            resultsContainer.innerHTML = `<div class="error-msg">Error: ${err.message}</div>`;
        } finally {
            statusIndicator.classList.add('hidden');
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

        const response = await fetch('api/compare', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(config)
        });
        if (!response.ok) throw new Error('Baseline op failed');
        renderResults(await response.json());
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
            <div style="font-weight:800; color:var(--primary-color); font-size:1.1rem; margin-bottom:15px; border-bottom:1px solid #eee; padding-bottom:10px;">${titleText}</div>
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
                <div class="result-body" style="display:none; padding:15px; border-top:1px solid #eee; background:#fafafa;">
                    ${renderPayloadContent(res, i)}
                </div>
            `;
            
            item.querySelector('.result-header').addEventListener('click', () => {
                const header = item.querySelector('.result-header');
                const body = item.querySelector('.result-body');
                const isOpen = body.style.display === 'block';
                body.style.display = isOpen ? 'none' : 'block';
                header.classList.toggle('open', !isOpen);
                
                if (!isOpen) { 
                    setupSync(body);
                    // Attach copy events
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
            return Object.entries(headers).map(([k,v])=>`<div><strong>${k}:</strong> ${v}</div>`).join('');
        };

        const renderComponent = (v1, v2, label, type, isHeader) => {
            const hasAPI2 = !!res.api2;
            const isMatch = isHeader ? areHeadersMatch(v1, v2) : arePayloadsMatch(v1, v2);

            const renderSingleBox = (val, title, flex = 1) => `
                <div class="${type}-box" style="flex:${flex}; min-width:0; position:relative;">
                    <div style="font-size:0.7rem; font-weight:700; color:var(--primary-color); margin-bottom:5px; text-transform:uppercase;">${title}</div>
                    <div style="position:relative;">
                        <button type="button" class="copy-btn" title="Copy ${label}">${isHeader ? 'Copy' : 'Copy'}</button>
                        ${isHeader ? 
                            `<div class="sync-h" style="background:rgba(255,255,255,0.7); padding:8px; font-size:0.7rem; max-height:80px; overflow-y:auto; border-radius:4px; border:1px solid rgba(0,0,0,0.05);">${renderHeaders(val)}</div>` :
                            `<pre class="sync-p" style="margin:0;">${formatData(val)}</pre>`
                        }
                    </div>
                </div>
            `;

            if (!hasAPI2 || isMatch) {
                return `<div style="display:flex; margin-bottom:10px;">${renderSingleBox(v1, `${label} ${isMatch && hasAPI2 ? '(Match)' : ''}`)}</div>`;
            } else {
                const isBaseline = document.getElementById('comparisonMode').value === 'BASELINE';
                const t1 = isBaseline ? `Current ${label}` : `API 1 ${label}`;
                const t2 = isBaseline ? `Target/Baseline ${label}` : `API 2 ${label}`;
                return `
                    <div style="display:flex; gap:12px; margin-bottom:10px;">
                        ${renderSingleBox(v1, t1)}
                        ${renderSingleBox(v2, t2)}
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
            <div style="padding:10px; background:#f7fafc; border-radius:8px; border:1px solid #edf2f7; margin-bottom:15px;">
                <div style="font-size:0.75rem; font-weight:800; color:#4a5568; margin-bottom:10px; text-transform:uppercase; letter-spacing:0.05em;">Input Request</div>
                ${renderComponent(a1.requestHeaders || {}, a2.requestHeaders || {}, 'Headers', 'request', true)}
                ${renderComponent(a1.requestPayload || '', a2.requestPayload || '', 'Payload', 'request', false)}
            </div>

            <!-- Response Section -->
            <div style="padding:10px; background:#f0fff4; border-radius:8px; border:1px solid #c6f6d5;">
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
        // Escape HTML for display
        return formatted.replace(/&/g, '&amp;')
               .replace(/</g, '&lt;')
               .replace(/>/g, '&gt;')
               .replace(/"/g, '&quot;')
               .replace(/'/g, '&#039;');
    };

    const setupSync = (body) => {
        const hs = body.querySelectorAll('.sync-h');
        const ps = body.querySelectorAll('.sync-p');
        const sync = (els) => els.forEach(e => e.onscroll = () => els.forEach(o => { if(o!==e) o.scrollTop = e.scrollTop; }));
        if(hs.length > 1) sync(hs);
        if(ps.length > 1) sync(ps);
    };

    // --- Resizing ---
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
            
            runs.forEach(r => {
                const opt = document.createElement('option');
                opt.value = r.runId;
                // Enhanced label with Description and Tags
                const desc = r.description ? ` - ${r.description}` : '';
                const tags = (r.tags && r.tags.length > 0) ? ` [${r.tags.join(', ')}]` : '';
                // Format timestamp manually to YYYY-MM-DDTHH:mm:ss CST
                // Note: JS Date doesn't support CST strictly without libs, simplifying to local ISO-like
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
                // Trigger detail population for the auto-selected run
                baselineRunSelect.dispatchEvent(new Event('change'));
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

    if (opCapture && opCompare) {
        opCapture.onclick = () => {
            const currentType = document.getElementById('testType').value;
            opCapture.classList.add('active');
            opCompare.classList.remove('active');
            document.getElementById('baselineOperation').value = 'CAPTURE';
            document.getElementById('captureFields').style.display = 'block';
            document.getElementById('compareFields').style.display = 'none';
            handleBaselineUI('BASELINE');
            // Preserve the specific type the user was on
            document.getElementById('testType').value = currentType;
            if (currentType === 'REST') {
                document.getElementById('typeRest').classList.add('active');
                document.getElementById('typeSoap').classList.remove('active');
            } else {
                document.getElementById('typeSoap').classList.add('active');
                document.getElementById('typeRest').classList.remove('active');
            }
        };
        opCompare.onclick = () => {
            const currentType = document.getElementById('testType').value;
            opCompare.classList.add('active');
            opCapture.classList.remove('active');
            document.getElementById('baselineOperation').value = 'COMPARE';
            document.getElementById('captureFields').style.display = 'none';
            document.getElementById('compareFields').style.display = 'block';
            handleBaselineUI('BASELINE');
            // Populate services when switching to compare
            loadBaselineServices();
            // Preserve type and sync UI
            document.getElementById('testType').value = currentType;
            if (currentType === 'REST') {
                document.getElementById('typeRest').classList.add('active');
                document.getElementById('typeSoap').classList.remove('active');
            } else {
                document.getElementById('typeSoap').classList.add('active');
                document.getElementById('typeRest').classList.remove('active');
            }
        };
    }

    if (modeCompare && modeBaseline) {
        modeCompare.addEventListener('click', () => handleBaselineUI('LIVE'));
        modeBaseline.addEventListener('click', () => {
            const currentType = document.getElementById('testType').value;
            // Ensure default Capture is set
            opCapture.click();
            handleBaselineUI('BASELINE');
            
            // Force Re-sync type and UI after a minimal delay to override any race conditions
            setTimeout(() => {
                document.getElementById('testType').value = currentType;
                if (currentType === 'REST') {
                    document.getElementById('typeRest').classList.add('active');
                    document.getElementById('typeSoap').classList.remove('active');
                } else {
                    document.getElementById('typeSoap').classList.add('active');
                    document.getElementById('typeRest').classList.remove('active');
                }
            }, 50);
        });
    }

    // Export internal logic for mode switches
    window.handleBaselineUI = (mode) => {
        const ignoreHeaders = document.getElementById('ignoreHeaders');
        const compareBtn = document.getElementById('compareBtn');
        const op = document.getElementById('baselineOperation').value;
        
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
        try { p.value = JSON.stringify(JSON.parse(p.value), null, 2); } catch(e) {}
    });

    initResize();
    loadDefaults();
});
