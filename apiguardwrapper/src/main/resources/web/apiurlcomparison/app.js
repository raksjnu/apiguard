document.addEventListener('DOMContentLoaded', () => {
    console.log('[APP] DOMContentLoaded event fired - Starting initialization...');

    // Variable declarations
    let loadedConfig = null;
    
    const compareBtn = document.getElementById('compareBtn');
    const resultsContainer = document.getElementById('resultsContainer');
    const statusIndicator = document.getElementById('statusIndicator');
    const mockServerStatus = document.getElementById('mockServerStatus'); // New variable
    const mockServerControls = document.getElementById('mockServerControls'); // New variable

    // Note: url1Input and url2Input are not defined in this scope, will log as undefined.
    console.log('[APP] Core elements found:', {
        url1: !!document.getElementById('url1'), // Assuming user meant to check existence of element
        url2: !!document.getElementById('url2'), // Assuming user meant to check existence of element
        results: !!resultsContainer,
        compareBtn: !!compareBtn,
        mockStatus: !!mockServerStatus
    });

    const addHeaderBtn = document.getElementById('addHeaderBtn');
    const headersTable = document.getElementById('headersTable').querySelector('tbody');
    const addTokenBtn = document.getElementById('addTokenBtn');
    const tokensTable = document.getElementById('tokensTable').querySelector('tbody');

    // -- Dynamic Rows Logic --
    if (addHeaderBtn && headersTable) {
        addHeaderBtn.addEventListener('click', () => addRow(headersTable, ['Header Name', 'Value']));
    }
    if (addTokenBtn && tokensTable) {
        addTokenBtn.addEventListener('click', () => addRow(tokensTable, ['Token Name', 'Values (semicolon separated)']));
    }

    // -- Resize Handle Logic --
    console.log('[APP] Initializing resize handle...');
    initResizeHandle();

    // Load defaults on startup
    console.log('[APP] Loading defaults...');
    loadDefaults();

    // Initialize baseline controls
    console.log('[APP] Initializing baseline controls...');
    initBaselineControls();

    // -- Clear Form Button Logic --
    const clearBtn = document.getElementById('clearBtn'); // Changed from clearFormBtn to clearBtn
    if (clearBtn) {
        console.log('[APP] Clear button found, attaching listener');
        clearBtn.addEventListener('click', () => {
            console.log('[APP] Clear button clicked');
            if (confirm('Are you sure you want to clear the form?')) {
                document.getElementById('configForm').reset(); // Assuming 'configForm' is the ID of your form
                if (resultsContainer) resultsContainer.innerHTML = '';
            }
        });
    } else {
        console.warn('[APP] Clear button NOT found');
    }


    // -- Mock Server Logic --
    const mockStatusText = document.getElementById('mockStatusText');
    const toggleMockBtn = document.getElementById('toggleMockBtn');

    // Mock Server Status Check
    const checkMockServerStatus = async () => { // Renamed from checkMockStatus to checkMockServerStatus
        // console.log('[APP] Checking Mock Server status...');
        if (!mockStatusText || !toggleMockBtn) return; // Added check for toggleMockBtn

        try {
            const response = await fetch('api/mock/status');
            const data = await response.json();
            const isRunning = data.status === 'running';

            toggleMockBtn.dataset.running = isRunning;
            if (isRunning) {
                mockStatusText.textContent = 'Mock Server: RUNNING';
                mockStatusText.style.color = '#28a745'; // Green
                toggleMockBtn.textContent = 'Stop Server';
                toggleMockBtn.className = 'btn-secondary';
                toggleMockBtn.style.backgroundColor = '#28a745'; // Green button
                toggleMockBtn.style.borderColor = '#28a745';
                toggleMockBtn.style.color = '#fff';
            } else {
                mockStatusText.textContent = 'Mock Server: STOPPED';
                mockStatusText.style.color = '#dc3545'; // Red
                toggleMockBtn.textContent = 'Start Server';
                toggleMockBtn.className = 'btn-secondary';
                toggleMockBtn.style.backgroundColor = '#dc3545'; // Red button
                toggleMockBtn.style.borderColor = '#dc3545';
                toggleMockBtn.style.color = '#fff';
            }
            
            // Enable the button after status is loaded
            toggleMockBtn.disabled = false;
        } catch (e) {
            console.error('Mock Status Error:', e);
            mockStatusText.textContent = 'Mock Server: UNKNOWN';
            mockStatusText.style.color = '#666';
            toggleMockBtn.textContent = 'Check Status'; // Reset button text so it doesn't get stuck
             // Reset style to default
            toggleMockBtn.className = 'btn-secondary';
            toggleMockBtn.style.backgroundColor = '';
            toggleMockBtn.style.borderColor = '';
            toggleMockBtn.style.color = '';
        }
    }

    if (mockStatusText && toggleMockBtn) {
        checkMockServerStatus(); // Call the renamed function

        toggleMockBtn.addEventListener('click', async () => {
            const isRunning = toggleMockBtn.dataset.running === 'true';
            toggleMockBtn.disabled = true;
            toggleMockBtn.textContent = 'Processing...';
            
            try {
                const endpoint = isRunning ? 'api/mock/stop' : 'api/mock/start';
                const response = await fetch(endpoint, { method: 'POST' });
                if (response.ok) {
                    await checkMockServerStatus(); // Call the renamed function
                } else {
                    const err = await response.json();
                    alert('Error: ' + (err.error || 'Unknown error'));
                }
            } catch (e) {
                console.error('Mock Toggle Error:', e);
                alert('Failed to toggle mock server');
            } finally {
                toggleMockBtn.disabled = false;
            }
        });
    }

    async function loadDefaults() {
        try {
            const response = await fetch('api/config');
            if (!response.ok) return; // Silent fail if no config api or file
            loadedConfig = await response.json();
            if (!loadedConfig) return;

            // 1. Basic Fields
            if (loadedConfig.testType) document.getElementById('testType').value = loadedConfig.testType;
            if (loadedConfig.iterationController) document.getElementById('iterationController').value = loadedConfig.iterationController;
            if (loadedConfig.maxIterations) document.getElementById('maxIterations').value = loadedConfig.maxIterations;
            if (loadedConfig.ignoredFields && Array.isArray(loadedConfig.ignoredFields) && loadedConfig.ignoredFields.length > 0) {
                document.getElementById('ignoredFields').value = loadedConfig.ignoredFields.join(', ');
            } else {
                document.getElementById('ignoredFields').value = 'timestamp, X-Dynamic-Header';
            }
            if (loadedConfig.ignoreHeaders !== undefined) {
                 const ignoreHeaderBox = document.getElementById('ignoreHeaders');
                 if(ignoreHeaderBox) ignoreHeaderBox.checked = loadedConfig.ignoreHeaders;
            }

            // Populate initially based on loaded config
            populateFormFields(loadedConfig.testType);

        } catch (e) {
            console.error("Failed to load defaults", e);
        }
    }

    // Listener for Type change to reload defaults
    const testTypeSelect = document.getElementById('testType');
    if (testTypeSelect) {
        testTypeSelect.addEventListener('change', () => {
            if (loadedConfig) {
                populateFormFields(testTypeSelect.value);
            }
        });
    }

    function populateFormFields(type) {
        if (!loadedConfig) return;
        
        // 2. Determine which API config to use (REST or SOAP)
        const activeApis = (type === 'SOAP') ? loadedConfig.soap : loadedConfig.rest;

        if (activeApis && activeApis.api1 && activeApis.api2) {
            document.getElementById('url1').value = activeApis.api1.baseUrl || '';
            document.getElementById('url2').value = activeApis.api2.baseUrl || '';

            // Extract Auth from api1 (assume symmetric)
            const enableAuth = document.getElementById('enableAuth');
            const clientId = document.getElementById('clientId');
            const clientSecret = document.getElementById('clientSecret');

            if (activeApis.api1.authentication) {
                if(enableAuth) enableAuth.checked = true;
                if(clientId) {
                    clientId.value = activeApis.api1.authentication.clientId || '';
                    clientId.disabled = false;
                }
                if(clientSecret) {
                    clientSecret.value = activeApis.api1.authentication.clientSecret || '';
                    clientSecret.disabled = false;
                }
            } else {
                if(enableAuth) enableAuth.checked = false;
                if(clientId) clientId.disabled = true;
                if(clientSecret) clientSecret.disabled = true;
            }

            // Extract Operation Details from api1 (assume 1st operation is main)
            if (activeApis.api1.operations && activeApis.api1.operations.length > 0) {
                const op = activeApis.api1.operations[0];
                if (op.name) document.getElementById('operationName').value = op.name;
                // Updated: Ensure payload is cleared if null, or set if present
                document.getElementById('payload').value = op.payloadTemplatePath || ''; 
                
                // Populate Method
                if (op.methods && op.methods.length > 0) {
                    const methodVal = op.methods[0];
                    const methodSelect = document.getElementById('method');
                    // Ensure value exists in select options
                    if ([...methodSelect.options].some(o => o.value === methodVal)) {
                        methodSelect.value = methodVal;
                    }
                }

                // Populate Headers
                if (headersTable) {
                    headersTable.innerHTML = ''; // Clear existing
                    if (op.headers) {
                        Object.entries(op.headers).forEach(([k, v]) => {
                            addRow(headersTable, ['Header Name', 'Value']);
                            const lastRow = headersTable.lastElementChild;
                            if (lastRow) {
                                lastRow.querySelector('.key-input').value = k;
                                lastRow.querySelector('.value-input').value = v;
                            }
                        });
                    }
                }
            }
        }

        // 3. Populate Tokens (Global, not per type usually, but refreshing cleanly)
        if (loadedConfig.tokens && tokensTable) {
            tokensTable.innerHTML = ''; // Clear existing
            Object.entries(loadedConfig.tokens).forEach(([k, list]) => {
                if (Array.isArray(list)) {
                    const valStr = list.join('; ');
                    addRow(tokensTable, ['Token Name', 'Values (semicolon separated)']);
                    const lastRow = tokensTable.lastElementChild;
                    if (lastRow) {
                        lastRow.querySelector('.key-input').value = k;
                        lastRow.querySelector('.value-input').value = valStr;
                    }
                }
            });
        }
    }

    function clearForm() {
        const testType = document.getElementById('testType').value;
        
        // Clear basic fields
        document.getElementById('operationName').value = '';
        document.getElementById('url1').value = '';
        document.getElementById('url2').value = '';
        document.getElementById('payload').value = '';
        document.getElementById('ignoredFields').value = '';
        
        // Clear auth
        document.getElementById('enableAuth').checked = false;
        document.getElementById('clientId').value = '';
        document.getElementById('clientSecret').value = '';
        document.getElementById('clientId').disabled = true;
        document.getElementById('clientSecret').disabled = true;
        
        // Clear tokens
        if (tokensTable) {
            tokensTable.innerHTML = '';
        }
        
        // Clear headers and add only Content-Type based on test type
        if (headersTable) {
            headersTable.innerHTML = '';
            addRow(headersTable, ['Header Name', 'Value']);
            const lastRow = headersTable.lastElementChild;
            if (lastRow) {
                lastRow.querySelector('.key-input').value = 'Content-Type';
                if (testType === 'SOAP') {
                    lastRow.querySelector('.value-input').value = 'text/xml';
                } else {
                    lastRow.querySelector('.value-input').value = 'application/json';
                }
            }
        }
        
        // Reset iteration settings to defaults
        document.getElementById('iterationController').value = 'ONE_BY_ONE';
        document.getElementById('maxIterations').value = '100';
        document.getElementById('ignoreHeaders').checked = false;
    }


    function addRow(tbody, placeholders) {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><input type="text" placeholder="${placeholders[0]}" class="key-input"></td>
            <td><input type="text" placeholder="${placeholders[1]}" class="value-input"></td>
            <td><button type="button" class="btn-remove" onclick="this.closest('tr').remove()">×</button></td>
        `;
        tbody.appendChild(tr);
    }

    // -- Main Comparison Logic --
    compareBtn.addEventListener('click', async (e) => {
        e.preventDefault();

        // 1. Gather Data
        const config = buildConfig();
        if (!validateConfig(config)) return;

        // 2. Update UI State
        setLoading(true);

        try {
            // 3. Check if baseline mode
            const comparisonMode = document.getElementById('comparisonMode').value;

            if (comparisonMode === 'BASELINE') {
                // Handle baseline mode
                await handleBaselineComparison();
            } else {
                // Handle normal LIVE comparison
                const response = await fetch('api/compare', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(config)
                });

                if (!response.ok) throw new Error('Network response was not ok');

                const results = await response.json();

                // 4. Render Results
                renderResults(results);
            }
        } catch (error) {
            console.error('Error:', error);
            resultsContainer.innerHTML = `<div class="error-msg">Error executing comparison: ${error.message}</div>`;
        } finally {
            setLoading(false);
        }
    });

    // Handle baseline comparison (capture or compare)
    async function handleBaselineComparison() {
        const operation = document.getElementById('baselineOperation').value;

        if (operation === 'CAPTURE') {
            // Capture baseline
            const serviceName = document.getElementById('baselineServiceName').value.trim();
            const description = document.getElementById('baselineDescription').value.trim();
            const tagsStr = document.getElementById('baselineTags').value.trim();
            const tags = tagsStr ? tagsStr.split(',').map(t => t.trim()) : [];

            if (!serviceName) {
                alert('Please enter a service name for baseline capture');
                return;
            }

            // Build config for baseline capture
            const config = buildConfig();
            config.comparisonMode = 'BASELINE';
            const workDir = document.getElementById('workingDirectory')?.value?.trim() || '';
            config.baseline = {
                operation: 'CAPTURE',
                serviceName: serviceName,
                description: description,
                tags: tags,
                storageDir: workDir || null
            };

            const response = await fetch('api/compare', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            if (!response.ok) throw new Error('Baseline capture failed');

            const results = await response.json();
            renderResults(results);

        } else {
            // Compare with baseline
            const serviceName = document.getElementById('baselineServiceSelect').value;
            const date = document.getElementById('baselineDateSelect').value;
            const runId = document.getElementById('baselineRunSelect').value;

            if (!serviceName || !date || !runId) {
                alert('Please select service, date, and run for baseline comparison');
                return;
            }

            // Build config for baseline comparison
            const config = buildConfig();
            config.comparisonMode = 'BASELINE';
            const workDir = document.getElementById('workingDirectory')?.value?.trim() || '';
            config.baseline = {
                operation: 'COMPARE',
                serviceName: serviceName,
                compareDate: date,
                compareRunId: runId,
                storageDir: workDir || null
            };

            const response = await fetch('api/compare', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });

            if (!response.ok) throw new Error('Baseline comparison failed');

            const results = await response.json();
            renderResults(results);
        }
    }


    function buildConfig() {
        const testType = document.getElementById('testType').value;
        const opName = document.getElementById('operationName').value || 'web-operation';
        const method = document.getElementById('method').value || 'POST';
        const url1 = document.getElementById('url1').value;
        const url2 = document.getElementById('url2').value;
        const payloadTemplate = document.getElementById('payload').value;
        const iterationType = document.getElementById('iterationController').value;
        const maxIterations = parseInt(document.getElementById('maxIterations').value) || 100;
        
        const ignoredFieldsStr = document.getElementById('ignoredFields').value;
        const ignoredFields = ignoredFieldsStr ? ignoredFieldsStr.split(',').map(s => s.trim()).filter(s => s.length > 0) : [];

        const clientId = document.getElementById('clientId').value;
        const clientSecret = document.getElementById('clientSecret').value;

        // Parse Headers
        const headers = {};
        if (headersTable) {
            headersTable.querySelectorAll('tr').forEach(tr => {
                const keyInput = tr.querySelector('.key-input');
                const valInput = tr.querySelector('.value-input');
                if (keyInput && valInput) {
                    const key = keyInput.value.trim();
                    const val = valInput.value.trim();
                    if (key) headers[key] = val;
                }
            });
        }

        // Parse Tokens
        const tokens = {};
        if (tokensTable) {
            tokensTable.querySelectorAll('tr').forEach(tr => {
                const keyInput = tr.querySelector('.key-input');
                const valInput = tr.querySelector('.value-input');
                if (keyInput && valInput) {
                    const key = keyInput.value.trim();
                    const valStr = valInput.value;
                    if (key) {
                        // Split by semicolon, trimming whitespace
                        let items = valStr.split(';').map(v => v.trim());

                        // Remove last item if it is empty (trailing semicolon effect)
                        if (items.length > 0 && items[items.length - 1] === '') {
                            items.pop();
                        }

                        // Convert to numbers where appropriate (DISABLED: Keep as strings to match CLI/YAML behavior)
                        const finalItems = items.map(v => v);

                        if (finalItems.length > 0) tokens[key] = finalItems;
                    }
                }
            });
        }

        const isAuthEnabled = document.getElementById('enableAuth').checked;
        const auth = isAuthEnabled ? {
            tokenUrl: null, // Basic auth doesn't strictly need this for simple username/password
            clientId: clientId || null,
            clientSecret: clientSecret || null
        } : null;

        const opConfig = {
            name: opName,
            methods: [method], // Use selected method
            headers: headers,
            payloadTemplatePath: payloadTemplate || null
        };

        const apiConfig1 = {
            baseUrl: url1, // Full URL now
            authentication: auth,
            operations: [{ ...opConfig }]
        };

        const apiConfig2 = {
            baseUrl: url2,
            authentication: auth,
            operations: [{ ...opConfig }]
        };

        const config = {
            testType: testType,
            maxIterations: maxIterations,
            iterationController: iterationType,
            tokens: tokens,
            rest: { api1: apiConfig1, api2: apiConfig2 },
            soap: { api1: apiConfig1, api2: apiConfig2 },
            ignoredFields: ignoredFields,
            ignoreHeaders: document.getElementById('ignoreHeaders')?.checked || false
        };

        return config;
    }

    function validateConfig(config) {
        if (!document.getElementById('url1').value) {
            alert("URL 1 is required");
            return false;
        }
        return true;
    }

    function setLoading(isLoading) {
        if (isLoading) {
            compareBtn.disabled = true;
            compareBtn.innerText = "Running...";
            resultsContainer.innerHTML = '<div class="empty-state"><p>Processing...</p></div>';
        } else {
            compareBtn.disabled = false;
            compareBtn.innerText = "Run Comparison";
        }
    }

    function renderResults(results) {
        resultsContainer.innerHTML = '';

        if (results.length === 0) {
            resultsContainer.innerHTML = '<div class="empty-state">No results returned.</div>';
            return;
        }

        // --- Summary Section ---
        const total = results.length;
        const matches = results.filter(r => r.status === 'MATCH').length;
        const mismatches = results.filter(r => r.status === 'MISMATCH').length;
        const errors = results.filter(r => r.status === 'ERROR').length;
        const totalDuration = results.reduce((acc, r) => acc + (r.api1?.duration || 0) + (r.api2?.duration || 0), 0);

        // Generate timestamp with timezone
        const now = new Date();
        const timestamp = now.toLocaleString('en-US', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false,
            timeZoneName: 'short'
        });

        // Check if this is a baseline operation
        const isBaselineMode = results.length > 0 && results[0].baselinePath;
        const baselinePath = isBaselineMode ? results[0].baselinePath : null;
        // Determine exact mode: 'Baseline Used' (Compare) or 'Baseline Captured' (Capture)
        const baselineOperation = isBaselineMode ? (results[0].api2 ? 'Baseline Used' : 'Baseline Captured') : null;
        const isCaptureMode = baselineOperation === 'Baseline Captured';

        const summaryContainer = document.createElement('div');
        summaryContainer.style.marginBottom = '20px';

        // Build execution summary HTML
        let execSummaryHtml = `
            <div><strong>Total Iterations:</strong> ${total}</div>
            <div><strong>Total Duration:</strong> ${totalDuration} ms</div>`;

        // Add baseline path if present
        if (baselinePath && baselineOperation) {
            execSummaryHtml += `<div><strong>${baselineOperation}:</strong> ${escapeHtml(baselinePath)}</div>`;
        }

        execSummaryHtml += `<div><strong>Report Generated:</strong> ${timestamp}</div>`;

        let comparisonSummaryContent = '';
                if (isCaptureMode) {
                     comparisonSummaryContent = `
                        <div><span class="status-MATCH" style="background-color: #e3f2fd; color: #1565c0; border: 1px solid #bbdefb;">Captured: ${matches}</span></div>
                        <div style="margin-top:5px;"><span class="status-ERROR">Errors: ${errors}</span></div>
                     `;
                } else {
                     comparisonSummaryContent = `
                        <div><span class="status-MATCH">Matches: ${matches}</span></div>
                        <div style="margin-top:5px;"><span class="status-MISMATCH">Mismatches: ${mismatches}</span></div>
                        <div style="margin-top:5px;"><span class="status-ERROR">Errors: ${errors}</span></div>
                     `;
                }

        summaryContainer.innerHTML = `
            <div class="comparison-grid" style="gap: 10px;">
                <div class="card" style="padding: 15px; background: #fff; box-shadow: 0 2px 4px rgba(0,0,0,0.05); border-left: 5px solid #27173e;">
                    <h3 style="margin-bottom: 10px; font-size: 1rem; color: #27173e;">Execution Summary</h3>
                    <div style="font-size: 0.9rem; line-height: 1.6;">
                        ${execSummaryHtml}
                    </div>
                </div>
                <div class="card" style="padding: 15px; background: #fff; box-shadow: 0 2px 4px rgba(0,0,0,0.05); border-left: 5px solid #27173e;">
                    <h3 style="margin-bottom: 10px; font-size: 1rem; color: #27173e;">${isCaptureMode ? 'Capture Summary' : 'Comparison Summary'}</h3>
                    <div style="font-size: 0.9rem; line-height: 1.6;">
                        ${comparisonSummaryContent}
                    </div>
                </div>
            </div>
        `;
        resultsContainer.appendChild(summaryContainer);
        // -----------------------

        results.forEach((res, index) => {
            const isMatch = res.status === 'MATCH';
            let statusClass = isMatch ? 'status-MATCH' : (res.status === 'MISMATCH' ? 'status-MISMATCH' : 'status-ERROR');
            let statusLabel = res.status;

            if (isCaptureMode && isMatch) {
                statusLabel = "CAPTURED";
            }

            // Format tokens string: "account=123; id=456"
            let tokenStr = '';
            if (res.iterationTokens) {
                tokenStr = Object.entries(res.iterationTokens)
                    .map(([k, v]) => `${k}=${v}`)
                    .join('; ');
            }
            const tokenDisplay = tokenStr ? `<br><small style="color:#666; font-weight:normal;">Tokens: ${tokenStr}</small>` : '';

            // Helper for formatted timestamp
            const fmtTime = (ts) => {
                if (!ts) return 'Now';
                // Check if it looks like a date string
                if (typeof ts === 'string' && ts.length > 5) {
                    try {
                        return new Date(ts).toLocaleString('en-US', { 
                            year: 'numeric', month: '2-digit', day: '2-digit', 
                            hour: '2-digit', minute: '2-digit', second: '2-digit', 
                            timeZoneName: 'short' 
                        });
                    } catch (e) { return ts; }
                }
                return ts;
            };

            // Timestamp Logic
            let timeDisplay = '';
            if (res.baselineCaptureTimestamp) {
                // Dual timestamp for baseline comparison
                timeDisplay = `
                    <div style="text-align:right; font-size:0.75rem; color:#666;">
                        <div>Baseline: ${fmtTime(res.baselineCaptureTimestamp)}</div>
                        <div>Current: ${fmtTime(res.timestamp)}</div>
                    </div>`;
            } else {
                // Single timestamp for live comparison
                timeDisplay = res.timestamp ? `<span style="font-size:0.75rem; color:#999; margin-left: 10px;">${fmtTime(res.timestamp)}</span>` : '';
            }

            const card = document.createElement('div');
            card.className = `result-item`;

            // Adjust badge style for CAPTURED if desired
            const badgeStyle = (isCaptureMode && isMatch) ? 'background-color: #e3f2fd; color: #1565c0; border: 1px solid #bbdefb;' : '';

            const header = document.createElement('div');
            header.className = 'result-header';
            header.innerHTML = `
                <div>
                    <span>Iteration #${index + 1} - ${res.operationName}</span>
                    ${tokenDisplay}
                </div>
                <div>
                   ${timeDisplay}
                   <span class="${statusClass}" style="margin-left:10px; ${badgeStyle}">${statusLabel}</span>
                </div>
            `;
            header.onclick = () => card.classList.toggle('expanded');

            const body = document.createElement('div');
            body.className = 'result-body';

            if (res.errorMessage) {
                body.innerHTML = `<p class="error-text">${res.errorMessage}</p>`;
            } else {
                let diffHtml = '';
                if (res.status === 'MISMATCH' && res.differences && res.differences.length > 0) {
                    diffHtml = `
                        <div class="diff-list">
                            <h5>Differences Found</h5>
                            <ul>
                                ${res.differences.map(d => `<li>${escapeHtml(d)}</li>`).join('')}
                            </ul>
                        </div>
                     `;
                }

                // Helper to render payload safely with sync class
                const renderPayload = (payload, syncGroup) => {
                    const content = (payload === null || payload === undefined) ? '<span style="color:#999; font-style:italic;">[Null Response]</span>' :
                                    (payload === '') ? '<span style="color:#999; font-style:italic;">[Empty Response]</span>' :
                                    `<pre class="sync-payload" data-sync-group="${syncGroup}">${formatJson(payload)}</pre>`;
                    return content;
                };

                // Helper to render headers with sync class
                const renderHeaders = (headers, syncGroup) => {
                    if (!headers || Object.keys(headers).length === 0) return '<div style="color:#999; font-style:italic;">[No Headers]</div>';
                    let html = `<div class="sync-header" data-sync-group="${syncGroup}" style="max-height:150px; overflow-y:auto; border:1px solid #eee; padding:5px; background:#fafafa;">`;
                    html += '<table style="width:100%; font-size:0.8rem; border-collapse:collapse;">';
                    Object.entries(headers).forEach(([k, v]) => {
                        html += `<tr style="border-bottom:1px solid #f0f0f0;"><td style="padding:2px 5px; font-weight:600; color:#444; width:30%; vertical-align:top;">${escapeHtml(k)}:</td><td style="padding:2px 5px; color:#333; word-break:break-all;">${escapeHtml(v)}</td></tr>`;
                    });
                    html += '</table></div>';
                    return html;
                };

                // Request Payload (Common)
                const reqPayload = res.api1 && res.api1.requestPayload ? res.api1.requestPayload : '';
                const reqDisplay = reqPayload ? `
                    <div class="request-box" style="margin-bottom: 20px;">
                        <h4 style="margin-bottom: 10px; font-size: 0.9rem; color: #27173e; font-weight: 600;">Request Payload (Original)</h4>
                        <pre>${formatJson(reqPayload)}</pre>
                    </div>` : '';

                if (isMatch) {
                    const successTitle = isCaptureMode ? 'Response Received' : 'Response (Identical)';
                    const titleColor = isCaptureMode ? '#27173e' : '#2e7d32'; // Purple for capture, Green for match

                    body.innerHTML = `
                        ${diffHtml}
                        ${reqDisplay}
                        <div class="single-view">
                            <h4 style="margin-bottom: 10px; font-size: 0.9rem; color: ${titleColor}; font-weight: 600;">${successTitle}</h4>
                            
                            <div style="margin-bottom:10px;">
                                <strong style="font-size:0.8rem;">Headers:</strong>
                                ${renderHeaders(res.api1.responseHeaders, `headers-${index}`)}
                            </div>

                            <strong style="font-size:0.8rem;">Payload:</strong>
                            ${renderPayload(res.api1.responsePayload, `payloads-${index}`)}
                            <p><small>Duration: ${res.api1.duration}ms</small></p>
                        </div>
                    `;
                } else {
                    // Determine labels based on baseline mode
                    const isBaselineComparison = res.baselineServiceName != null;
                    const api1Label = isBaselineComparison ? 'API Current' : 'API 1';
                    const api2Label = isBaselineComparison ? 'API Baseline' : 'API 2';

                    body.innerHTML = `
                        ${diffHtml}
                        ${reqDisplay}
                        <div class="comparison-grid">
                            <div class="payload-box">
                                <h4>${api1Label} Response (${res.api1.duration}ms)</h4>
                                <div style="margin-bottom:10px;">
                                    <strong style="font-size:0.8rem;">Headers:</strong>
                                    ${renderHeaders(res.api1.responseHeaders, `headers-${index}`)}
                                </div>
                                <strong style="font-size:0.8rem;">Payload:</strong>
                                ${renderPayload(res.api1.responsePayload, `payloads-${index}`)}
                            </div>
                            <div class="payload-box">
                                <h4>${api2Label} Response (${res.api2.duration}ms)</h4>
                                <div style="margin-bottom:10px;">
                                    <strong style="font-size:0.8rem;">Headers:</strong>
                                    ${renderHeaders(res.api2.responseHeaders, `headers-${index}`)}
                                </div>
                                <strong style="font-size:0.8rem;">Payload:</strong>
                                ${renderPayload(res.api2.responsePayload, `payloads-${index}`)}
                            </div>
                        </div>
                    `;
                }
            }

            card.appendChild(header);
            card.appendChild(body);
            resultsContainer.appendChild(card);
        });
        
        // Enable synchronized scrolling
        enableSyncScroll();
    }
    
    function enableSyncScroll() {
        // Sync Headers
        const headerGroups = {};
        document.querySelectorAll('.sync-header').forEach(el => {
            const group = el.dataset.syncGroup;
            if (!headerGroups[group]) headerGroups[group] = [];
            headerGroups[group].push(el);
        });

        Object.values(headerGroups).forEach(group => {
            if (group.length > 1) {
                group.forEach(el => {
                    el.addEventListener('scroll', (e) => {
                        if (el.dataset.isScrolling) return; // Prevent loop
                        group.forEach(other => {
                            if (other !== el) {
                                other.dataset.isScrolling = 'true';
                                other.scrollTop = el.scrollTop;
                                other.scrollLeft = el.scrollLeft;
                                setTimeout(() => other.removeAttribute('data-is-scrolling'), 50);
                            }
                        });
                    });
                });
            }
        });

        // Sync Payloads
        const payloadGroups = {};
        document.querySelectorAll('.sync-payload').forEach(el => {
            const group = el.dataset.syncGroup;
            if (!payloadGroups[group]) payloadGroups[group] = [];
            payloadGroups[group].push(el);
        });

        Object.values(payloadGroups).forEach(group => {
            if (group.length > 1) {
                group.forEach(el => {
                    el.addEventListener('scroll', (e) => {
                        if (el.dataset.isScrolling) return; // Prevent loop
                        group.forEach(other => {
                            if (other !== el) {
                                other.dataset.isScrolling = 'true';
                                other.scrollTop = el.scrollTop;
                                other.scrollLeft = el.scrollLeft;
                                setTimeout(() => other.removeAttribute('data-is-scrolling'), 50);
                            }
                        });
                    });
                });
            }
        });
    }

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function formatXml(xml) {
        const PADDING = '  '; // 2 spaces for indentation
        const reg = /(>)(<)(\/*)/g;
        let formatted = '';
        let pad = 0;

        xml = xml.replace(reg, '$1\n$2$3');
        const lines = xml.split('\n');

        lines.forEach((line) => {
            let indent = 0;
            if (line.match(/.+<\/\w[^>]*>$/)) {
                indent = 0;
            } else if (line.match(/^<\/\w/)) {
                if (pad !== 0) {
                    pad -= 1;
                }

            } else if (line.match(/^<\w([^>]*[^\/])?>.*$/)) {
                indent = 1;
            } else {
                indent = 0;
            }

            formatted += PADDING.repeat(pad) + line + '\n';
            pad += indent;
        });

        return formatted.trim();
    }

    function formatJson(str) {
        if (!str) return '';

        // If it's a string, check if it's JSON or XML
        if (typeof str === 'string') {
            // Try JSON first
            try {
                const parsed = JSON.parse(str);
                return escapeHtml(JSON.stringify(parsed, null, 2));
            } catch (e) {
                // Not JSON, check if it's XML and format it
                if (str.trim().startsWith('<')) {
                    try {
                        return escapeHtml(formatXml(str));
                    } catch (xmlError) {
                        // If XML formatting fails, return escaped as-is
                        return escapeHtml(str);
                    }
                }
                // Not JSON or XML, return HTML-escaped
                return escapeHtml(str);
            }
        }

        // If it's already an object, stringify it
        try {
            return escapeHtml(JSON.stringify(str, null, 2));
        } catch (e) {
            return escapeHtml(String(str));
        }
    }

    function initResizeHandle() {
        const resizeHandle = document.getElementById('resizeHandle');
        const configPanel = document.getElementById('configPanel');
        const mainGrid = document.querySelector('.main-grid');

        if (!resizeHandle || !configPanel || !mainGrid) return;

        let isResizing = false;
        let startX = 0;
        let startWidth = 0;

        resizeHandle.addEventListener('mousedown', (e) => {
            isResizing = true;
            startX = e.clientX;
            startWidth = configPanel.offsetWidth;
            document.body.classList.add('resizing');
            e.preventDefault();
        });

        document.addEventListener('mousemove', (e) => {
            if (!isResizing) return;

            const delta = e.clientX - startX;
            const newWidth = startWidth + delta;

            // Set min and max width constraints
            const minWidth = 250;
            const maxWidth = window.innerWidth * 0.6; // Max 60% of window width

            if (newWidth >= minWidth && newWidth <= maxWidth) {
                mainGrid.style.setProperty('--config-width', `${newWidth}px`);
            }
        });

        document.addEventListener('mouseup', () => {
            if (isResizing) {
                isResizing = false;
                document.body.classList.remove('resizing');
            }
        });
    }

    // ============================================
    // BASELINE TESTING FUNCTIONALITY
    // ============================================

    // Initialize baseline controls
    function initBaselineControls() {
        const comparisonMode = document.getElementById('comparisonMode');
        const baselineControls = document.getElementById('baselineControls');
        const baselineOperation = document.getElementById('baselineOperation');
        const captureFields = document.getElementById('captureFields');
        const compareFields = document.getElementById('compareFields');
        const baselineServiceSelect = document.getElementById('baselineServiceSelect');
        const baselineDateSelect = document.getElementById('baselineDateSelect');
        const baselineRunSelect = document.getElementById('baselineRunSelect');

        if (!comparisonMode || !baselineControls) return;

        // Update button label based on mode and operation
        function updateButtonLabel() {
            const mode = comparisonMode.value;
            const operation = baselineOperation ? baselineOperation.value : 'CAPTURE';

            if (mode === 'BASELINE') {
                if (operation === 'CAPTURE') {
                    compareBtn.innerText = 'Capture Baseline';
                } else {
                    compareBtn.innerText = 'Compare with Baseline';
                }
            } else {
                compareBtn.innerText = 'Run Comparison';
            }
        }

        // Toggle baseline controls visibility
        comparisonMode.addEventListener('change', function () {
            const url2Group = document.getElementById('url2Group');
            const url1Label = document.querySelector('#urlRow .input-group:first-child label');
            const tokensContainer = document.getElementById('tokensContainer');

            if (this.value === 'BASELINE') {
                baselineControls.style.display = 'block';
                if (url2Group) url2Group.style.display = 'none';
                if (url1Label) url1Label.textContent = 'API Endpoint URL';
                // Disable tokens in baseline mode (uses stored payloads)
                if (tokensContainer) {
                    tokensContainer.style.opacity = '0.5';
                    tokensContainer.querySelectorAll('input, textarea').forEach(el => el.disabled = true);
                }
                loadBaselineServices();
                updateButtonLabel();
            } else {
                baselineControls.style.display = 'none';
                if (url2Group) url2Group.style.display = 'block';
                if (url1Label) url1Label.textContent = 'Endpoint 1 URL';
                // Re-enable tokens in live mode
                if (tokensContainer) {
                    tokensContainer.style.opacity = '1';
                    tokensContainer.querySelectorAll('input, textarea').forEach(el => el.disabled = false);
                }
                compareBtn.innerText = 'Run Comparison';
            }
        });

        // Toggle between capture and compare fields
        if (baselineOperation) {
            baselineOperation.addEventListener('change', function () {
                if (this.value === 'CAPTURE') {
                    captureFields.style.display = 'block';
                    compareFields.style.display = 'none';
                    // Auto-disable ignoreHeaders for capture
                    const ignoreHeaderBox = document.getElementById('ignoreHeaders');
                    if(ignoreHeaderBox) {
                        ignoreHeaderBox.checked = false;
                        ignoreHeaderBox.disabled = true;
                    }
                } else {
                    captureFields.style.display = 'none';
                    compareFields.style.display = 'block';
                    loadBaselineServices();
                     // Re-enable ignoreHeaders for compare
                     const ignoreHeaderBox = document.getElementById('ignoreHeaders');
                     if(ignoreHeaderBox) {
                         ignoreHeaderBox.disabled = false;
                     }
                }
                updateButtonLabel();
            });

            // Trigger change event to set initial state
            baselineOperation.dispatchEvent(new Event('change'));
        }

        // Load dates when service is selected
        if (baselineServiceSelect) {
            baselineServiceSelect.addEventListener('change', function () {
                if (this.value) {
                    loadBaselineDates(this.value);
                } else {
                    baselineDateSelect.innerHTML = '<option value="">-- Select Date --</option>';
                    baselineDateSelect.disabled = true;
                    baselineRunSelect.innerHTML = '<option value="">-- Select Run --</option>';
                    baselineRunSelect.disabled = true;
                }
            });
        }

        // Load runs when date is selected
        if (baselineDateSelect) {
            baselineDateSelect.addEventListener('change', function () {
                const service = baselineServiceSelect.value;
                if (service && this.value) {
                    loadBaselineRuns(service, this.value);
                } else {
                    baselineRunSelect.innerHTML = '<option value="">-- Select Run --</option>';
                    baselineRunSelect.disabled = true;
                }
            });
        }

        // Fetch endpoint when run is selected
        if (baselineRunSelect) {
            baselineRunSelect.addEventListener('change', function() {
                const service = baselineServiceSelect.value;
                const date = baselineDateSelect.value;
                if (service && date && this.value) {
                    fetchBaselineEndpoint(service, date, this.value);
                }
            });
        }
    }

    // Load available baseline services
    async function loadBaselineServices() {
        try {
            const workDir = document.getElementById('workingDirectory')?.value?.trim() || '';
            const url = workDir ? `api/baselines/services?workDir=${encodeURIComponent(workDir)}` : 'api/baselines/services';
            
            const response = await fetch(url);
            const services = await response.json();

            const select = document.getElementById('baselineServiceSelect');
            if (!select) return;

            select.innerHTML = '<option value="">-- Select Service --</option>';

            services.forEach(service => {
                const option = document.createElement('option');
                option.value = service;
                option.textContent = service;
                select.appendChild(option);
            });

            select.disabled = false;

            // Auto-select latest (last) service
            if (services.length > 0) {
                const latestService = services[services.length - 1];
                select.value = latestService;
                loadBaselineDates(latestService);
            }
        } catch (error) {
            console.error('Error loading baseline services:', error);
        }
    }

    // Load dates for selected service
    async function loadBaselineDates(serviceName) {
        try {
            const workDir = document.getElementById('workingDirectory')?.value?.trim() || '';
            const url = workDir 
                ? `api/baselines/dates/${serviceName}?workDir=${encodeURIComponent(workDir)}` 
                : `api/baselines/dates/${serviceName}`;
            const response = await fetch(url);
            const dates = await response.json();

            const select = document.getElementById('baselineDateSelect');
            if (!select) return;

            select.innerHTML = '<option value="">-- Select Date --</option>';

            dates.forEach(date => {
                const option = document.createElement('option');
                option.value = date;
                option.textContent = date;
                select.appendChild(option);
            });

            select.disabled = false;
            
            // Auto-select latest (last) date
            if (dates.length > 0) {
                const latestDate = dates[dates.length - 1];
                select.value = latestDate;
                loadBaselineRuns(serviceName, latestDate);
            }
        } catch (error) {
            console.error('Error loading baseline dates:', error);
        }
    }

    // Load runs for selected service and date
    async function loadBaselineRuns(serviceName, date) {
        try {
            const workDir = document.getElementById('workingDirectory')?.value?.trim() || '';
            const url = workDir 
                ? `api/baselines/runs/${serviceName}/${date}?workDir=${encodeURIComponent(workDir)}` 
                : `api/baselines/runs/${serviceName}/${date}`;
            const response = await fetch(url);
            const runs = await response.json();

            const select = document.getElementById('baselineRunSelect');
            if (!select) return;

            select.innerHTML = '<option value="">-- Select Run --</option>';

            runs.forEach(run => {
                const option = document.createElement('option');
                option.value = run.runId;
                const formattedTags = (run.tags && run.tags.length > 0) ? ` [${run.tags.join(', ')}]` : '';
                const label = `${run.runId} - ${run.description || 'No description'}${formattedTags} (${run.totalIterations} iterations)`;
                option.textContent = label;
                select.appendChild(option);
            });

            select.disabled = false;
            
            // Auto-select latest (last) run
            if (runs.length > 0) {
                const latestRunId = runs[runs.length - 1].runId;
                select.value = latestRunId;
                fetchBaselineEndpoint(serviceName, date, latestRunId);
            }
        } catch (error) {
            console.error('Error loading baseline runs:', error);
        }
    }

    async function fetchBaselineEndpoint(service, date, runId) {
        try {
            const workDir = document.getElementById('workingDirectory')?.value?.trim() || '';
            const enc = encodeURIComponent;
            const url = workDir 
                ? `api/baselines/runs/${enc(service)}/${enc(date)}/${enc(runId)}/endpoint?workDir=${enc(workDir)}` 
                : `api/baselines/runs/${enc(service)}/${enc(date)}/${enc(runId)}/endpoint`;
            
            const response = await fetch(url);
            const data = await response.json();
            
            if (data && data.endpoint) {
                const url1Input = document.getElementById('url1');
                if (url1Input) {
                    url1Input.value = data.endpoint;
                    // Visual feedback
                    url1Input.style.backgroundColor = '#e8f5e9';
                    setTimeout(() => url1Input.style.backgroundColor = '', 1000);
                }
            } else {
                 // Warning if no endpoint found? Or just log
                 console.log("No endpoint found for this run");
            }

            if (data && data.payload) {
                const payloadInput = document.getElementById('payload');
                if (payloadInput) {
                    payloadInput.value = data.payload;
                    // Visual feedback
                    payloadInput.style.backgroundColor = '#e8f5e9';
                    setTimeout(() => payloadInput.style.backgroundColor = '', 1000);
                }
            }
        } catch (error) {
            console.error('Error fetching baseline endpoint:', error);
        }
    }
    // --- NEW: Auth Toggle Logic ---
    const enableAuth = document.getElementById('enableAuth');
    const clientIdInput = document.getElementById('clientId');
    const clientSecretInput = document.getElementById('clientSecret');

    if (enableAuth) {
        enableAuth.addEventListener('change', () => {
            const isEnabled = enableAuth.checked;
            if (clientIdInput) clientIdInput.disabled = !isEnabled;
            if (clientSecretInput) clientSecretInput.disabled = !isEnabled;
        });
    }

    // --- NEW: Ignored Fields Logic ---
    function updateIgnoredFieldsState() {
        const comparisonMode = document.getElementById('comparisonMode');
        const baselineOperation = document.getElementById('baselineOperation');
        
        const mode = comparisonMode ? comparisonMode.value : 'LIVE';
        const operation = baselineOperation ? baselineOperation.value : 'CAPTURE';
        const ignoredFieldsInput = document.getElementById('ignoredFields');
        
        if (ignoredFieldsInput) {
            // Logic Update:
            // ENABLED for LIVE mode
            // ENABLED for BASELINE COMPARE mode
            // DISABLED for BASELINE CAPTURE mode
            
            let shouldEnable = true;
            if (mode === 'BASELINE' && operation === 'CAPTURE') {
                shouldEnable = false;
            }

            if (shouldEnable) {
                ignoredFieldsInput.disabled = false;
                ignoredFieldsInput.style.opacity = '1';
                ignoredFieldsInput.closest('.input-group').style.opacity = '1';
            } else {
                ignoredFieldsInput.disabled = true;
                ignoredFieldsInput.style.opacity = '0.6';
                ignoredFieldsInput.closest('.input-group').style.opacity = '0.6';
            }
        }
    }

    // Hook up listeners for Ignored Fields
    const comparisonMode = document.getElementById('comparisonMode');
    const baselineOperation = document.getElementById('baselineOperation');

    if (comparisonMode) {
        comparisonMode.addEventListener('change', updateIgnoredFieldsState);
    }
    if (baselineOperation) {
        baselineOperation.addEventListener('change', updateIgnoredFieldsState);
    }
    
    // Initial call
    updateIgnoredFieldsState();
});
