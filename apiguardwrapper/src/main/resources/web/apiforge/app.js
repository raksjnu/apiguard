// Main Initialization
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
        /* Toast Notifications */
        #toast-container {
            position: fixed;
            bottom: 20px;
            right: 20px;
            z-index: 10000;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .toast {
            background: #2d3748;
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            font-size: 0.9rem;
            animation: slideIn 0.3s ease-out;
            min-width: 250px;
        }
        .toast.success { border-left: 4px solid #48bb78; }
        .toast.error { border-left: 4px solid #f56565; }
        .toast.warning { border-left: 4px solid #ed8936; }
        @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
        #resultsContainer.jms-mode {
            background-color: #faf5ff !important;
            background-image: radial-gradient(circle at 0% 50%, rgba(107, 70, 193, 0.05) 0%, transparent 50%);
        }
        /* Visual cues for disabled elements */
        input:disabled, button:disabled, select:disabled {
            cursor: not-allowed !important;
            opacity: 0.6;
        }
        label.disabled-label {
            cursor: not-allowed !important;
            opacity: 0.6;
        }
    `;
    document.head.appendChild(style);

    const toastContainer = document.createElement('div');
    toastContainer.id = 'toast-container';
    document.body.appendChild(toastContainer);

    window.showToast = (msg, type = 'info') => {
        const t = document.createElement('div');
        t.className = `toast ${type}`;
        t.innerText = msg;
        toastContainer.appendChild(t);
        setTimeout(() => {
            t.style.opacity = '0';
            t.style.transition = 'opacity 0.5s';
            setTimeout(() => t.remove(), 500);
        }, 3000);
    };

    // --- mTLS Certificate Management ---
    // --- mTLS Certificate Management ---
    window.uploadCert = async (type) => {
        const fileInput = document.getElementById(type + 'File');
        const pathInput = document.getElementById(type + 'Path');
        if (!fileInput.files.length) return;
        
        const file = fileInput.files[0];
        const workDir = document.getElementById('workingDirectory').value.trim();
        
        // --- UX Refinement: Show loading state on the path input ---
        const originalPathValue = pathInput.value;
        pathInput.value = `⏳ Uploading ${file.name}...`;
        pathInput.style.fontStyle = 'italic';
        pathInput.style.color = '#718096';

        logActivity(`AUDIT: Initiating certificate upload. File: ${file.name}, Type: ${type}, Size: ${file.size} bytes`, 'info');
        
        try {
            const response = await fetch(`api/certificates/upload?fileName=${encodeURIComponent(file.name)}&workDir=${encodeURIComponent(workDir)}`, {
                method: 'POST',
                body: await file.arrayBuffer()
            });
            
            if (response.ok) {
                const data = await response.json();
                // --- UX Refinement: Store full path but optionally display name? ---
                // For now, let's keep the full path but clear the styling.
                pathInput.value = data.path;
                pathInput.style.fontStyle = 'normal';
                pathInput.style.color = 'inherit';
                
                showToast('Upload successful!', 'success');
                logActivity(`AUDIT: Certificate uploaded successfully. \n   > File: ${file.name}\n   > Saved Path: ${data.path}`, 'success');
                saveState();
            } else {
                pathInput.value = originalPathValue;
                pathInput.style.fontStyle = 'normal';
                pathInput.style.color = 'inherit';
                const errorData = await response.json().catch(() => ({}));
                const msg = errorData.error || 'Upload failed';
                showToast(msg, 'error');
                logActivity(`AUDIT: Certificate upload failed for ${file.name}: ${msg}`, 'error');
            }
        } catch (e) {
            console.error(e);
            showToast('Error uploading: ' + e.message, 'error');
            logActivity(`AUDIT: Exception during upload: ${e.message}`, 'error');
        }
    };

    const validateCerts = async () => {
        const pfxPath = document.getElementById('pfxPath').value.trim();
        const passphrase = document.getElementById('passphrase').value.trim();
        const caPath = document.getElementById('caCertPath').value.trim();
        const resultDiv = document.getElementById('validationResult');
        const btn = document.getElementById('validateCertsBtn');
        
        resultDiv.style.display = 'block';
        resultDiv.style.background = '#f7fafc';
        resultDiv.style.borderColor = '#cbd5e0';
        resultDiv.innerHTML = '⏳ Validating security configuration...';
        btn.disabled = true;

        logActivity('AUDIT: Starting Security Configuration Validation...', 'info');

        const checks = [];
        if (pfxPath) checks.push({ path: pfxPath, passphrase, type: pfxPath.toLowerCase().endsWith('.jks') ? 'JKS' : 'PFX' });
        
        const clientCertPath = document.getElementById('clientCertPath').value.trim();
        const clientKeyPath = document.getElementById('clientKeyPath').value.trim();
        if (clientCertPath && clientKeyPath) {
            checks.push({ path: clientCertPath, keyPath: clientKeyPath, passphrase, type: 'PEM_PAIR' });
        } else if (clientCertPath || clientKeyPath) {
             showToast('Both Certificate and Private Key are required for PEM identity.', 'warning');
             logActivity('AUDIT: Validation warning - Partial PEM configuration detected.', 'warning');
        }

        if (caPath) checks.push({ path: caPath, passphrase, type: caPath.toLowerCase().endsWith('.jks') ? 'JKS' : 'PEM' });

        if (checks.length === 0) {
            resultDiv.innerHTML = '⚠️ No certificate paths provided to validate.';
            resultDiv.style.background = '#fffaf0';
            resultDiv.style.borderColor = '#f6ad55';
            logActivity('AUDIT: Validation skipped - No certificate paths found in configuration.', 'warning');
            btn.disabled = false;
            return;
        }

        try {
            let allValid = true;
            let messages = [];

            for (const check of checks) {
                const resp = await fetch('api/certificates/validate', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(check)
                });
                const data = await resp.json().catch(() => ({ error: 'Could not parse validation response' }));
                if (!data.valid) {
                    allValid = false;
                    const errMsg = data.error || 'Unknown validation error';
                    messages.push(`❌ ${check.type}: ${errMsg}`);
                    logActivity(`AUDIT: [${check.type}] Validation FAILED. Error: ${errMsg} | Path: ${check.path}`, 'error');
                } else {
                    messages.push(`✅ ${check.type}: ${data.message}`);
                    let auditMsg = `AUDIT: [${check.type}] Validation PASSED. Path: ${check.path}`;
                    if (data.aliases) {
                        messages.push(`<small style="display:block; margin-left:20px; color:#4a5568;">Entries: ${data.aliases.join(', ')}</small>`);
                        auditMsg += ` | Entries: ${data.aliases.join(', ')}`;
                    }
                    if (data.expiry) {
                        messages.push(`<small style="display:block; margin-left:20px; color:#4a5568;">Expires: ${data.expiry} ${data.isExpired ? '<b style="color:red">(EXPIRED)</b>' : ''}</small>`);
                        auditMsg += ` | Expires: ${data.expiry}`;
                        if (data.isExpired) logActivity(`AUDIT: [${check.type}] Certificate EXPIRED!`, 'error');
                    }
                    logActivity(auditMsg, 'success');
                }
            }

            resultDiv.innerHTML = messages.join('<br>');
            resultDiv.style.background = allValid ? '#f0fff4' : '#fff5f5';
            resultDiv.style.borderColor = allValid ? '#48bb78' : '#f56565';
            
            if (allValid) {
                showToast('Security configuration is valid!', 'success');
                logActivity('AUDIT: ==> Security Configuration Verified: ALL VALID <==', 'success');
            } else {
                showToast('Security configuration has errors.', 'error');
                logActivity('AUDIT: ==> Security Configuration Verified: ERRORS FOUND <==', 'error');
            }

        } catch (e) {
            resultDiv.innerHTML = '❌ Error during validation: ' + e.message;
            resultDiv.style.background = '#fff5f5';
            logActivity(`AUDIT: Validation Exception: ${e.message}`, 'error');
        } finally {
            btn.disabled = false;
        }
    };

    document.getElementById('validateCertsBtn')?.addEventListener('click', validateCerts);

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
    const paramsTable = document.getElementById('paramsTable').querySelector('tbody');

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
    document.getElementById('addParamBtn').addEventListener('click', () => {
        addRow(paramsTable, ['Parameter Name', 'Value']);
        updateUrlsFromParams();
    });

    // --- URL and Parameters Synchronization Logic ---
    const getQueryParamsFromTable = () => {
        const qp = {};
        paramsTable.querySelectorAll('tr').forEach(tr => {
            const k = tr.querySelector('.key-input').value.trim();
            const v = tr.querySelector('.value-input').value.trim();
            if (k) qp[k] = v;
        });
        return qp;
    };

    const updateUrlsFromParams = () => {
        const qp = getQueryParamsFromTable();
        const searchParams = new URLSearchParams();
        Object.entries(qp).forEach(([k, v]) => searchParams.append(k, v));
        const queryString = searchParams.toString();

        const syncUrl = (id) => {
            const el = document.getElementById(id);
            if (!el) return;
            let baseUrl = el.value.split('?')[0];
            el.value = queryString ? `${baseUrl}?${queryString}` : baseUrl;
        };

        syncUrl('url1');
        syncUrl('url2');
        saveState();
    };

    const updateParamsTableFromUrl = (url) => {
        if (!url) return;
        try {
            const queryString = url.includes('?') ? url.split('?')[1] : '';
            const searchParams = new URLSearchParams(queryString);
            paramsTable.innerHTML = '';
            searchParams.forEach((v, k) => {
                addRow(paramsTable, ['Parameter Name', 'Value'], [k, v]);
            });
            saveState();
        } catch (e) {
            console.error('Error parsing URL params:', e);
        }
    };

    const handleUrlInput = (e) => {
        const url = e.target.value;
        const otherId = e.target.id === 'url1' ? 'url2' : 'url1';
        const otherEl = document.getElementById(otherId);

        // Update Table
        updateParamsTableFromUrl(url);

        // Update Other URL
        if (otherEl) {
            const otherBase = otherEl.value.split('?')[0];
            const queryString = url.includes('?') ? url.split('?')[1] : '';
            otherEl.value = queryString ? `${otherBase}?${queryString}` : otherBase;
        }
        saveState();
    };

    document.getElementById('url1').addEventListener('input', handleUrlInput);
    document.getElementById('url2').addEventListener('input', handleUrlInput);

    paramsTable.addEventListener('input', (e) => {
        if (e.target.classList.contains('key-input') || e.target.classList.contains('value-input')) {
            updateUrlsFromParams();
        }
    });

    // Handle row removal
    paramsTable.addEventListener('click', (e) => {
        if (e.target.classList.contains('btn-remove')) {
            setTimeout(updateUrlsFromParams, 10);
        }
    });

    // --- Navigation (SPA) ---
    document.querySelectorAll('.nav-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const target = tab.dataset.view;
            document.querySelectorAll('.nav-tab').forEach(t => {
                t.classList.remove('active');
                t.style.borderBottom = 'none';
            });
            tab.classList.add('active');
            tab.style.borderBottom = '3px solid var(--primary-color)';
            
            document.querySelectorAll('.view-container').forEach(v => v.classList.remove('active'));
            document.getElementById(target).classList.add('active');
            
            if (target === 'utilitiesView') {
                loadExportServices();
                // Ensure log is scrolled to bottom when view becomes visible
                 const log = document.getElementById('activityLog');
                 if(log) log.scrollTop = log.scrollHeight;
            } else if (target === 'mainView') {
                // Refresh baseline services if in Compare Baseline mode
                if (document.getElementById('comparisonMode').value === 'BASELINE' && document.getElementById('baselineOperation').value === 'COMPARE') {
                    loadBaselineServices();
                }
            }
        });
    });

    // --- Contextual Help Tiggers ---
    window.showHelp = (sectionId) => {
        const helpTab = Array.from(document.querySelectorAll('.nav-tab')).find(t => t.dataset.view === 'helpView');
        if (helpTab) helpTab.click();
        
        if (sectionId) {
            setTimeout(() => {
                const el = document.getElementById(sectionId);
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    el.style.boxShadow = '0 0 20px rgba(94, 39, 139, 0.4)';
                    setTimeout(() => el.style.boxShadow = '', 2000);
                }
            }, 100);
        }
    };

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

    // --- UI Activity Logging (Persistent) ---
    const LOG_STORAGE_KEY = 'api_forge_activity_log';
    
    // Load logs on startup
    const loadLogs = () => {
        const log = document.getElementById('activityLog');
        if (!log) return;
        const savedLogs = sessionStorage.getItem(LOG_STORAGE_KEY);
        if (savedLogs) {
            log.innerHTML = savedLogs;
            log.scrollTop = log.scrollHeight;
        } else {
             // Initial Welcome Log if empty
             // logActivity('Session started. Ready for action.');
        }
    };

    window.logActivity = (msg, type = 'info', contentToCopy = null) => {
        const log = document.getElementById('activityLog');
        if (!log) return;
        const entry = document.createElement('div');
        entry.className = `log-entry log-${type}`;
        const ts = new Date().toLocaleTimeString('en-US', { hour12: true, timeZoneName: 'short' });
        
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
        
        log.prepend(entry);
        log.scrollTop = 0; // consistent with top-posting
        
        // Persist
        sessionStorage.setItem(LOG_STORAGE_KEY, log.innerHTML);
    };
    // internal alias for consistency if needed, though window.logActivity works
    const logActivity = window.logActivity;

    document.getElementById('clearLogBtn').onclick = () => {
        if (confirm('Clear the current session Activity Logs? This will not affect stored data.')) {
            document.getElementById('activityLog').innerHTML = '<div class="log-entry log-info" style="border:none; padding:10px; text-align:center; color:#94a3b8;">[Log Cleared]</div>';
            sessionStorage.removeItem(LOG_STORAGE_KEY);
            logActivity('New session log started. Utilities and Comparisons will be tracked here.');
        }
    };

    // --- Settings Persistence ---
    const wdInput = document.getElementById('workingDirectory');
    if (wdInput) {
        wdInput.value = localStorage.getItem('apiforge_wd') || '';
        wdInput.addEventListener('input', () => localStorage.setItem('apiforge_wd', wdInput.value.trim()));
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
                    
                    // Intelligent Type Switching:
                    // If current type is JMS (which has no template support yet), switch to SOAP/REST based on config
                    let targetType = document.getElementById('testType').value;
                    if (targetType === 'JMS') {
                         targetType = loadedConfig.testType || 'SOAP';
                         document.getElementById('testType').value = targetType;
                         // Force update buttons visual state immediately
                         if (typeof syncTypeButtons === 'function') syncTypeButtons();
                    }

                    populateFormFields(targetType);
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
            })),
            params: Array.from(paramsTable.querySelectorAll('tr')).map(tr => ({
                k: tr.querySelector('.key-input')?.value,
                v: tr.querySelector('.value-input')?.value
            })),
            enableAuth: document.getElementById('enableAuth').checked,
            useMTLS: document.getElementById('useMTLS')?.checked,
            clientId: document.getElementById('clientId').value,
            clientSecret: document.getElementById('clientSecret').value,
            caCertPath: document.getElementById('caCertPath').value,
            pfxPath: document.getElementById('pfxPath').value,
            clientCertPath: document.getElementById('clientCertPath').value,
            clientKeyPath: document.getElementById('clientKeyPath').value,
            passphrase: document.getElementById('passphrase').value
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

            if (s.enableAuth !== undefined) {
                document.getElementById('enableAuth').checked = s.enableAuth;
                document.getElementById('clientId').disabled = !s.enableAuth;
                document.getElementById('clientSecret').disabled = !s.enableAuth;
            }
            if (s.clientId) document.getElementById('clientId').value = s.clientId;
            if (s.clientSecret) document.getElementById('clientSecret').value = s.clientSecret;

            if (s.useMTLS !== undefined) {
                document.getElementById('useMTLS').checked = s.useMTLS;
            } else {
                document.getElementById('useMTLS').checked = false;
            }

            if (s.caCertPath) document.getElementById('caCertPath').value = s.caCertPath;
            if (s.pfxPath) document.getElementById('pfxPath').value = s.pfxPath;
            if (s.clientCertPath) document.getElementById('clientCertPath').value = s.clientCertPath;
            if (s.clientKeyPath) document.getElementById('clientKeyPath').value = s.clientKeyPath;
            if (s.passphrase) document.getElementById('passphrase').value = s.passphrase;

            headersTable.innerHTML = '';
            s.headers?.forEach(h => addRow(headersTable, ['Header Name', 'Value'], [h.k, h.v]));
            
            tokensTable.innerHTML = '';
            s.tokens?.forEach(t => addRow(tokensTable, ['Token Name', 'Value'], [t.k, t.v]));

            paramsTable.innerHTML = '';
            s.params?.forEach(p => addRow(paramsTable, ['Parameter Name', 'Value'], [p.k, p.v]));

            // Ensure URLs are updated from restored params
            if (s.params && s.params.length > 0) {
                updateUrlsFromParams();
            }

            return true;
        } catch(e) { return false; }
    };

    // Attach listeners for auto-save
    ['method','operationName','url1','url2','payload','ignoredFields','maxIterations','iterationController', 'enableAuth', 'useMTLS', 'clientId', 'clientSecret', 'caCertPath', 'pfxPath', 'clientCertPath', 'clientKeyPath', 'passphrase'].forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener('input', saveState);
            el.addEventListener('change', saveState);
        }
    });
    headersTable.addEventListener('input', saveState);
    tokensTable.addEventListener('input', saveState);
    paramsTable.addEventListener('input', saveState);

    // --- Form Population & Defaults ---
    const resetFormToStandard = (type) => {
        document.getElementById('operationName').value = '';
        document.getElementById('url1').value = '';
        document.getElementById('url2').value = '';
        document.getElementById('payload').value = '';
        headersTable.innerHTML = '';
        tokensTable.innerHTML = ''; 
        paramsTable.innerHTML = '';
        
        // Add ONLY the default Content-Type as per the standard (REST/SOAP)
        if (type !== 'JMS') {
             addRow(headersTable, ['Header Name', 'Value'], ['Content-Type', (type === 'SOAP' ? 'text/xml' : 'application/json')]);
        }
        
        const authCheck = document.getElementById('enableAuth');
        if (authCheck) {
            authCheck.checked = false;
            document.getElementById('clientId').disabled = true;
            document.getElementById('clientSecret').disabled = true;
            document.getElementById('clientId').value = '';
            document.getElementById('clientSecret').value = '';
        }

        const mtlsCheck = document.getElementById('useMTLS');
        if (mtlsCheck) {
            mtlsCheck.checked = false;
            ['pfxPath', 'clientCertPath', 'clientKeyPath', 'caCertPath', 'passphrase'].forEach(id => {
                const el = document.getElementById(id);
                if (el) {
                    el.value = '';
                    el.disabled = true;
                }
            });
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
        saveState();
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
            
            // Priority: Server Config > SOAP (Default) - Ignore localStorage for testType
            // Unless state was restored, which we want to prioritize for continuity
            const initialType = (stateRestored) ? document.getElementById('testType').value : (loadedConfig.testType || 'SOAP');
            document.getElementById('testType').value = initialType;
            
            if (loadedConfig.maxIterations && !stateRestored) document.getElementById('maxIterations').value = loadedConfig.maxIterations;
            if (loadedConfig.iterationController && !stateRestored) document.getElementById('iterationController').value = loadedConfig.iterationController;
            if (loadedConfig.ignoredFields && !stateRestored) document.getElementById('ignoredFields').value = loadedConfig.ignoredFields.join(', ');
            
            // If No state restored, apply clean defaults
            if (!stateRestored) {
                resetFormToStandard(initialType);
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

        const qp = {};
        paramsTable.querySelectorAll('tr').forEach(tr => {
            const k = tr.querySelector('.key-input').value.trim();
            const v = tr.querySelector('.value-input').value.trim();
            if (k) qp[k] = v;
        });

        let auth = null;
        if (document.getElementById('enableAuth').checked) {
            const isSecurityEnabled = document.getElementById('useMTLS')?.checked;
            auth = {
                clientId: document.getElementById('clientId').value.trim(),
                clientSecret: document.getElementById('clientSecret').value.trim(),
                // Only send cert paths if explicit security toggle is enabled
                caCertPath: isSecurityEnabled ? document.getElementById('caCertPath').value.trim() : null,
                pfxPath: isSecurityEnabled ? document.getElementById('pfxPath').value.trim() : null,
                clientCertPath: isSecurityEnabled ? document.getElementById('clientCertPath').value.trim() : null,
                clientKeyPath: isSecurityEnabled ? document.getElementById('clientKeyPath').value.trim() : null,
                passphrase: isSecurityEnabled ? document.getElementById('passphrase').value.trim() : null
            };
        }

        const op = {
            name: document.getElementById('operationName').value || 'operation',
            methods: [document.getElementById('method').value],
            headers: h,
            queryParams: qp,
            payloadTemplatePath: document.getElementById('payload').value || null
        };

        return {
            testType: document.getElementById('testType').value,
            maxIterations: parseInt(document.getElementById('maxIterations').value) || 100,
            iterationController: document.getElementById('iterationController').value,
            tokens: t,
            rest: { 
                api1: { baseUrl: document.getElementById('url1').value, authentication: auth, operations: [op] }, 
                api2: { baseUrl: document.getElementById('url2').value, authentication: auth, operations: [op] } 
            },
            soap: { 
                api1: { baseUrl: document.getElementById('url1').value, authentication: auth, operations: [op] }, 
                api2: { baseUrl: document.getElementById('url2').value, authentication: auth, operations: [op] } 
            },
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

    // --- Action Handlers ---
    configForm.addEventListener('submit', async (e) => {
        e.preventDefault();

        // JMS INTERCEPTION
        const type = document.getElementById('testType').value;
        if (type === 'JMS') {
            if (typeof JmsModule !== 'undefined') {
                JmsModule.runAction();
            } else {
                 showToast('JMS Module not loaded. Please refresh.', 'error');
            }
            return;
        }

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
        
        const errors = data.filter(r => r.status === 'ERROR');
        if (errors.length > 0) {
             logActivity(`BASELINE COMPLETED with ERRORS: ${errors.length} error(s) detected.`, 'error');
             errors.forEach(err => logActivity(`   > ERROR [${err.operationName}]: ${err.errorMessage}`, 'error'));
             showToast('Baseline operation completed with errors', 'warning');
        } else {
             logActivity(`BASELINE COMPLETED: ${config.baseline.operation} success for ${config.baseline.serviceName}`, 'success');
             showToast('Baseline operation completed successfully', 'success');
             
              if (config.baseline.operation === 'CAPTURE') {
                  // Refresh services/dates/runs and select the captured service
                  await loadBaselineServices(config.baseline.serviceName);
              }
         }
        renderResults(data);
    };

    // --- Rendering Results ---
    // --- Rendering Results ---
    window.renderResults = (results, customTitle = null) => {
        const resultsContainer = document.getElementById('resultsContainer');
        if (!resultsContainer) return;
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
        const titleText = customTitle || (isBaselineCapture ? '📊 Baseline Captured Result Summary' : '📊 Comparison Result Summary');

        const firstMatch = results[0] || {};
        let runMetaHtml = '';
        if (firstMatch.baselineServiceName || firstMatch.baselineRunId) {
            let authSummary = 'None';
            if (firstMatch.api1 && firstMatch.api1.metadata && firstMatch.api1.metadata.authentication) {
                const auth = firstMatch.api1.metadata.authentication;
                let summaries = [];
                if (auth.clientId) summaries.push(`ID: ${auth.clientId}`);
                if (auth.pfxPath) summaries.push('PFX cert');
                if (auth.clientCertPath) summaries.push('mTLS PEM');
                authSummary = summaries.length > 0 ? summaries.join(' + ') : 'None';
            }

            runMetaHtml = `
                <div style="margin-top:20px; padding:15px; background:#fdfbff; border-radius:12px; border:1px solid var(--border-color); font-size:0.85rem; box-shadow: inset 0 2px 4px rgba(0,0,0,0.02);">
                    <div style="font-weight:800; color:var(--primary-color); margin-bottom:12px; display:flex; align-items:center; gap:8px; border-bottom:1px solid #eee; padding-bottom:8px;">
                        <span>📋 Run Information</span>
                        <span style="font-weight:400; font-size:0.75rem; color:#718096; text-transform:uppercase; letter-spacing:0.05em;">Execution Context</span>
                    </div>
                    <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap:15px;">
                        <div>
                            <div style="color:#718096; font-size:0.7rem; font-weight:700; text-transform:uppercase;">Service & Run</div>
                            <div style="margin-top:2px;"><strong>Name:</strong> <span style="color:var(--primary-color);">${escapeHtml(firstMatch.baselineServiceName || 'N/A')}</span></div>
                            <div style="margin-top:1px;"><strong>Run ID:</strong> <span>${escapeHtml(firstMatch.baselineRunId || 'N/A')}</span></div>
                        </div>
                        <div>
                            <div style="color:#718096; font-size:0.7rem; font-weight:700; text-transform:uppercase;">Date & Source</div>
                            <div style="margin-top:2px;"><strong>Capture:</strong> <span>${escapeHtml(firstMatch.baselineDate || 'N/A')}</span></div>
                            <div style="margin-top:1px; font-size:0.75rem; color:#4a5568;">${escapeHtml(firstMatch.baselineCaptureTimestamp || 'N/A')}</div>
                        </div>
                        <div>
                            <div style="color:#718096; font-size:0.7rem; font-weight:700; text-transform:uppercase;">Baseline Context</div>
                            <div style="margin-top:2px;"><strong>Mode:</strong> <span class="tech-stack-tag" style="padding:2px 8px; margin:0;">${escapeHtml(firstMatch.api1?.metadata?.testType || 'N/A')}</span></div>
                            <div style="margin-top:1px;"><strong>Op:</strong> <span>${escapeHtml(firstMatch.api1?.metadata?.operation || 'N/A')}</span></div>
                            <div style="margin-top:1px;"><strong>Total Iter:</strong> <span style="font-weight:700;">${firstMatch.api1?.metadata?.totalIterations ?? 'N/A'}</span></div>
                        </div>
                        <div>
                            <div style="color:#718096; font-size:0.7rem; font-weight:700; text-transform:uppercase;">Configuration</div>
                            <div style="margin-top:2px;"><strong>Auth:</strong> <span class="tech-stack-tag" style="padding:2px 8px; margin:0;">${escapeHtml(authSummary || 'None')}</span></div>
                            <div style="margin-top:1px;"><strong>API 1:</strong> <code style="font-size:0.7rem; word-break:break-all;">${escapeHtml(firstMatch.api1?.url || 'N/A')}</code></div>
                        </div>
                    </div>
                    ${firstMatch.baselineDescription ? `<div style="margin-top:12px; padding-top:8px; border-top:1px solid #f1f1f1;"><strong>Description:</strong> <span style="color:#4a5568;">${escapeHtml(firstMatch.baselineDescription)}</span></div>` : ''}
                    ${firstMatch.baselineTags && firstMatch.baselineTags.length ? `<div style="margin-top:8px;"><strong>Tags:</strong> ${firstMatch.baselineTags.map(t=>`<span class="tech-stack-tag" style="margin:0 4px 0 0; font-size:0.7rem;">${escapeHtml(t)}</span>`).join('')}</div>` : ''}
                </div>
            `;
        }

        summary.innerHTML = `
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px; border-bottom:1px solid #eee; padding-bottom:10px;">
                <div style="font-weight:800; color:var(--primary-color); font-size:1.1rem;">${titleText}</div>
                <div style="display:flex; gap:10px;">
                    <button id="expandAllBtn" class="btn-secondary" style="padding:4px 8px; font-size:0.7rem;">↕ Expand All</button>
                    <button id="collapseAllBtn" class="btn-secondary" style="padding:4px 8px; font-size:0.7rem;">↑ Collapse All</button>
                </div>
            </div>
            <div style="display:flex; flex-wrap:wrap; gap:30px; align-items:center;">
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
            ${runMetaHtml}
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
                        <div style="font-size:0.75rem; color:#0056b3; margin-bottom:1px; font-family:monospace;">1: ${res.api1 ? res.api1.url : (res.url || '')}</div>
                        <div style="font-size:0.75rem; color:#0056b3; margin-bottom:2px; font-family:monospace;">2: ${res.api2 ? res.api2.url : (document.getElementById('comparisonMode').value === 'BASELINE' ? (res.baselineServiceName ? `Baseline: ${res.baselineServiceName}` : 'Baseline') : '')}</div>
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
                    setupSync();
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
        const a1 = res.api1 || {};
        const a2 = res.api2 || {};
        
        // --- Helper: Dynamic Subsection Wrapper ---
        const wrapInSubSection = (title, content, isDifferent) => {
            const isOpen = isDifferent ? 'open' : '';
            const badge = isDifferent ? '<span class="diff-badge">Mismatch</span>' : '<span class="match-badge">Match</span>';
            return `
                <div class="result-subsection ${isOpen}">
                    <div class="result-subsection-header" onclick="this.parentElement.classList.toggle('open')">
                        <span>${title} ${badge}</span>
                        <span class="icon">▼</span>
                    </div>
                    <div class="result-subsection-content">
                        ${content}
                    </div>
                </div>`;
        };

        // --- Metadata Rendering Function ---
        const renderMetadataBlock = (meta, title) => {
            if (!meta) return '';
            let metaHtml = '';
            
            // Auth / mTLS
            if (meta.authentication) {
                const auth = meta.authentication;
                const isBasic = auth.type === 'basic' || auth.clientId || auth.username || auth.clientSecret;
                const isMTLS = auth.pfxPath || auth.clientCertPath || auth.clientKeyPath || auth.caCertPath;
                
                const getFileName = (path) => {
                    if (!path) return '';
                    return path.split(/[/\\]/).pop();
                };

                metaHtml += `<div><strong>🔐 Auth:</strong>`;
                if (isBasic) metaHtml += ` <span class="tech-stack-tag" title="Basic Authentication" style="background:#e6fffa; color:#2c7a7b; border-color:#81e6d9;">Basic</span>`;
                if (isMTLS) metaHtml += ` <span class="tech-stack-tag" title="mTLS / Certificate Authentication" style="background:#ebf8ff; color:#2b6cb0; border-color:#bee3f8;">mTLS</span>`;

                const id = auth.clientId || auth.username;
                if (id) metaHtml += ` <span class="tech-stack-tag" title="Client ID/Username">ID: ${id}</span>`;
                if (auth.pfxPath) metaHtml += ` <span class="tech-stack-tag" title="PFX/JKS: ${auth.pfxPath}">PFX: ${getFileName(auth.pfxPath)}</span>`;
                if (auth.clientCertPath) metaHtml += ` <span class="tech-stack-tag" title="Client Cert: ${auth.clientCertPath}">CRT: ${getFileName(auth.clientCertPath)}</span>`;
                if (auth.clientKeyPath) metaHtml += ` <span class="tech-stack-tag" title="Client Key: ${auth.clientKeyPath}">KEY: ${getFileName(auth.clientKeyPath)}</span>`;
                if (auth.caCertPath) metaHtml += ` <span class="tech-stack-tag" title="CA/Trust: ${auth.caCertPath}">CA: ${getFileName(auth.caCertPath)}</span>`;
                if (auth.passphrase) metaHtml += ` <span class="tech-stack-tag" title="Passphrase">🔑 *****</span>`;
                metaHtml += `</div>`;
            } else {
                metaHtml += `<div><strong>🔐 Auth:</strong> <span>N/A</span></div>`;
            }

            if (meta.operation) {
                const methodLabel = meta.method ? `<span class="tech-stack-tag" style="background:#edf2f7; color:#4a5568; margin-left:4px; font-weight:700;">${meta.method.toUpperCase()}</span>` : '';
                metaHtml += `<div style="margin-top:4px;"><strong>⚙️ Operation:</strong> <span style="color:var(--primary-color); font-weight:700;">${meta.operation}</span>${methodLabel}</div>`;
            }
            if (meta.iterationNumber !== undefined) {
                metaHtml += `<div style="margin-top:4px;"><strong>🔢 Iteration:</strong> <span style="font-weight:700;">${meta.iterationNumber}${meta.totalIterations ? ' / ' + meta.totalIterations : ''}</span></div>`;
            }
            
            if (!metaHtml) return '';
            const isBaselineMode = document.getElementById('comparisonMode').value === 'BASELINE';
            const displayTitle = isBaselineMode && title === 'API 1' ? 'Current' : (isBaselineMode && title === 'API 2' ? 'Baseline' : title);

            return `<div style="flex:1; background:#f0f7ff; padding:8px; border-radius:6px; border:1px solid #cce3ff; font-size:0.8rem;">
                        <div style="font-weight:700; color:#2c5282; margin-bottom:4px; border-bottom:1px solid #dae1e7; padding-bottom:2px;">${displayTitle} Metadata</div>
                        ${metaHtml}
                        ${a1.duration !== undefined && title.includes('1') ? `<div style="margin-top:2px;">⏱️ Duration: <strong>${a1.duration}ms</strong></div>` : ''}
                        ${a2.duration !== undefined && title.includes('2') ? `<div style="margin-top:2px;">⏱️ Duration: <strong>${a2.duration}ms</strong></div>` : ''}
                    </div>`;
        };

        const meta1Html = renderMetadataBlock(a1.metadata, 'API 1');
        const meta2Html = renderMetadataBlock(a2.metadata, 'API 2');
        const metadataSectionContent = `<div style="display:flex; gap:10px; margin-bottom:12px;">
                                ${meta1Html || '<div style="flex:1;"></div>'}
                                ${meta2Html || '<div style="flex:1;"></div>'}
                            </div>`;
        
        // Metadata Difference Check
        const isMetadataMatch = (m1, m2) => {
            if (!m1 || !m2) return m1 === m2;
            // Compare key fields: Auth, Operation, Method
            const auth1 = JSON.stringify(m1.authentication || {});
            const auth2 = JSON.stringify(m2.authentication || {});
            return auth1 === auth2 && m1.operation === m2.operation && m1.method === m2.method;
        };

        const metadataDiff = shouldDiff && !isMetadataMatch(a1.metadata, a2.metadata);
        contentHtml += wrapInSubSection('📋 Metadata', metadataSectionContent, metadataDiff);

        if (res.status === 'MISMATCH' && res.differences && res.differences.length > 0) {
            contentHtml += `
                <div style="margin-bottom:15px; padding:12px; background:#fff5f5; border:1.5px solid #feb2b2; border-radius:8px;">
                    <div style="font-size:0.75rem; font-weight:800; color:#c53030; text-transform:uppercase; margin-bottom:8px;">⚠️ Mismatch Details:</div>
                    <ul style="margin:0; padding-left:20px; font-size:0.85rem; color:#2d3748;">
                        ${res.differences.map(d => `<li style="margin-bottom:2px;">${escapeHtml(d)}</li>`).join('')}
                    </ul>
                </div>`;
        }

        if (res.errorMessage) {
            contentHtml += `<div style="color:var(--error-color); font-size:0.85rem; padding:10px; background:#fff5f5; border-radius:6px; border:1px solid #feb2b2; margin-bottom:10px;"><strong>HTTP ERROR:</strong> ${res.errorMessage}</div>`;
        }

        const renderComponent = (v1, v2, label, type, isHeader) => {
            const hasAPI2 = !!res.api2;
            const isMatch = isHeader ? areHeadersMatch(v1, v2) : arePayloadsMatch(v1, v2);

            let c1 = isHeader ? renderHeaders(v1) : formatData(v1);
            let c2 = isHeader ? renderHeaders(v2) : formatData(v2);

            if (!isMatch && hasAPI2) {
                const lines1 = String(c1).split(/\r?\n/);
                const lines2 = String(c2).split(/\r?\n/);
                const max = Math.max(lines1.length, lines2.length);
                let out1 = ''; let out2 = '';
                for (let i = 0; i < max; i++) {
                    const l1 = (lines1[i] || '').trim();
                    const l2 = (lines2[i] || '').trim();
                    const isLineDiff = l1 !== l2;
                    const style = isLineDiff ? 'background-color:#fff5f5; color:#c53030; font-weight:700; border-left:3px solid #f56565; padding-left:5px;' : '';
                    const content1 = isHeader ? (lines1[i] || '') : escapeHtml(lines1[i] || '').replace(/ /g, '&nbsp;');
                    const content2 = isHeader ? (lines2[i] || '') : escapeHtml(lines2[i] || '').replace(/ /g, '&nbsp;');
                    out1 += `<div style="${style} min-height:1.2em; white-space:nowrap;">${content1 || '&nbsp;'}</div>`;
                    out2 += `<div style="${style} min-height:1.2em; white-space:nowrap;">${content2 || '&nbsp;'}</div>`;
                }
                c1 = out1; c2 = out2;
            }

            if (!hasAPI2 || isMatch) {
                return renderBox(c1, `${label} ${isMatch && hasAPI2 ? '(Match)' : ''}`, type, label, isHeader, true);
            } else {
                const isBaseline = document.getElementById('comparisonMode').value === 'BASELINE';
                const t1 = isBaseline ? `Current ${label}` : `API 1 ${label}`;
                const t2 = isBaseline ? `Target/Baseline ${label}` : `API 2 ${label}`;
                const id1 = `sync-${Math.random().toString(36).substr(2, 9)}`;
                const id2 = `sync-${Math.random().toString(36).substr(2, 9)}`;
                const isLarge = (String(c1).match(/<div/g) || []).length > 8 || (String(c2).match(/<div/g) || []).length > 8;

                return `
                    <div style="display:flex; flex-direction:column; gap:8px;">
                        <div style="display:flex; gap:12px;">
                            ${renderBox(c1, t1, type, label, isHeader, false, id1, isLarge)}
                            ${renderBox(c2, t2, type, label, isHeader, false, id2, isLarge)}
                        </div>
                        ${isLarge ? `<button type="button" class="show-more-btn sync-expand-btn" style="width:100%;" onclick="toggleSyncExpand('${id1}', '${id2}', this)">Show More</button>` : ''}
                    </div>
                `;
            }
        };

        const renderBox = (valHtml, title, boxType, boxLabel, boxIsHeader, isMainValue = false, customId = null, forceCollapsed = false) => {
            const lineCount = (String(valHtml).match(/<div/g) || []).length || (String(valHtml).split('\n').length);
            const isLarge = forceCollapsed || lineCount > 10;
            const contentId = customId || `box-${Math.random().toString(36).substr(2, 9)}`;
            
            return `
                <div class="${boxType}-box" style="flex:1; min-width:0; position:relative;">
                    <div style="font-size:0.7rem; font-weight:700; color:var(--primary-color); margin-bottom:5px; text-transform:uppercase;">${title}</div>
                    <div style="position:relative;">
                        <button type="button" class="copy-btn" onclick="copyToClipboard('${contentId}')">Copy</button>
                        <div id="${contentId}" class="${boxIsHeader ? 'sync-h' : 'sync-p'} ${isLarge ? 'content-collapsed' : ''}" style="max-height:300px; overflow-y:auto; background:white; padding:8px; border-radius:4px; border:1px solid #e2e8f0; font-family:monospace; font-size:0.8rem;">${(isMainValue && !boxIsHeader) ? escapeHtml(valHtml) : valHtml}</div>
                        ${(!customId && isLarge) ? `<button type="button" class="show-more-btn" onclick="toggleExpand('${contentId}', this)">Show More</button>` : ''}
                    </div>
                </div>`;
        };

        // --- Assemble Subsections ---
        const mode = document.getElementById('comparisonMode').value;
        const op = document.getElementById('baselineOperation').value;
        // isCapture: explicitly capturing baseline
        // isViewHistory: viewing a baseline run (api2 is missing/undefined in the response object for that iteration)
        const isCapture = (mode === 'BASELINE' && op === 'CAPTURE');
        const isViewHistory = (!res.api2 || Object.keys(res.api2).length === 0);
        
        const shouldDiff = !isCapture && !isViewHistory;

        const reqHeaderDiff = shouldDiff && !areHeadersMatch(a1.requestHeaders, a2.requestHeaders);
        const reqPayloadDiff = shouldDiff && !arePayloadsMatch(a1.requestPayload, a2.requestPayload);
        const resHeaderDiff = shouldDiff && !areHeadersMatch(a1.responseHeaders, a2.responseHeaders);
        const resPayloadDiff = shouldDiff && !arePayloadsMatch(a1.responsePayload, a2.responsePayload);

        contentHtml += wrapInSubSection('📤 Request Headers', renderComponent(a1.requestHeaders || {}, a2.requestHeaders || {}, 'Headers', 'request', true), reqHeaderDiff);
        contentHtml += wrapInSubSection('📝 Request Payload', renderComponent(a1.requestPayload || '', a2.requestPayload || '', 'Payload', 'request', false), reqPayloadDiff);
        contentHtml += wrapInSubSection('📥 Response Headers', renderComponent(a1.responseHeaders, a2.responseHeaders, 'Headers', 'response', true), resHeaderDiff);
        contentHtml += wrapInSubSection('📦 Response Payload', renderComponent(a1.responsePayload, a2.responsePayload, 'Payload', 'response', false), resPayloadDiff);
            
        return contentHtml;
    };


    // Helper for Expand/Collapse
    const setupSync = () => {
        // Use a small delay to ensure DOM is updated
        setTimeout(() => {
            document.querySelectorAll('.result-item').forEach(item => {
                const ps = item.querySelectorAll('.sync-p');
                const hs = item.querySelectorAll('.sync-h');
                
                const syncGroup = (els) => {
                    if (els.length < 2) return;
                    els.forEach(el => {
                        // Avoid adding multiple listeners
                        if (el._syncInit) return;
                        el._syncInit = true;
                        
                        el.addEventListener('scroll', () => {
                            els.forEach(other => {
                                if (other !== el) {
                                    other.scrollTop = el.scrollTop;
                                    other.scrollLeft = el.scrollLeft;
                                }
                            });
                        });
                    });
                };
                syncGroup(ps);
                syncGroup(hs);
            });
        }, 50);
    };

    window.toggleExpand = (id, btn) => {
        const el = document.getElementById(id);
        if (!el) return;
        const collapsed = el.classList.toggle('content-collapsed');
        btn.innerText = collapsed ? 'Show More' : 'Show Less';
        if (collapsed) el.scrollTop = 0;
    };

    window.toggleSyncExpand = (id1, id2, btn) => {
        const el1 = document.getElementById(id1);
        const el2 = document.getElementById(id2);
        if (!el1 || !el2) return;
        const collapsed = el1.classList.toggle('content-collapsed');
        el2.classList.toggle('content-collapsed', collapsed);
        btn.innerText = collapsed ? 'Show More' : 'Show Less';
        if (collapsed) {
            el1.scrollTop = 0;
            el2.scrollTop = 0;
        }
    };

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
        return Object.entries(headers).sort().map(([k,v])=>`<div><strong>${escapeHtml(k)}:</strong> ${escapeHtml(v)}</div>`).join('\n');
    };

    const formatBytes = (bytes, decimals = 2) => {
        if (!+bytes) return '0 Bytes';
        const k = 1024;
        const dm = decimals < 0 ? 0 : decimals;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
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
        if (!xml) return '';
        try {
            const parser = new DOMParser();
            const xmlDoc = parser.parseFromString(xml.trim(), 'text/xml');
            
            // Check for parsing errors
            if (xmlDoc.getElementsByTagName('parsererror').length > 0) {
                return xml; 
            }

            const formatNode = (node, level = 0) => {
                const indent = '  '.repeat(level);
                
                if (node.nodeType === 1) { // Element
                    let tag = '<' + node.nodeName;
                    for (let i = 0; i < node.attributes.length; i++) {
                        const attr = node.attributes[i];
                        tag += ` ${attr.name}="${attr.value}"`;
                    }
                    
                    if (node.childNodes.length === 0) {
                        return indent + tag + '/>\n';
                    }

                    // Check if it's a simple text-only element
                    if (node.childNodes.length === 1 && node.childNodes[0].nodeType === 3) {
                        const text = node.childNodes[0].textContent.trim();
                        return indent + tag + '>' + text + '</' + node.nodeName + '>\n';
                    }
                    
                    let result = indent + tag + '>\n';
                    for (let i = 0; i < node.childNodes.length; i++) {
                        const child = node.childNodes[i];
                        if (child.nodeType === 1) {
                            result += formatNode(child, level + 1);
                        } else if (child.nodeType === 3) {
                            const txt = child.textContent.trim();
                            if (txt) result += '  '.repeat(level + 1) + txt + '\n';
                        }
                    }
                    result += indent + '</' + node.nodeName + '>\n';
                    return result;
                }
                return '';
            };

            let output = '';
            for (let i = 0; i < xmlDoc.childNodes.length; i++) {
                const child = xmlDoc.childNodes[i];
                if (child.nodeType === 1) output += formatNode(child);
                else if (child.nodeType === 7) output += `<?${child.target} ${child.data}?>\n`;
            }
            return output.trim();
        } catch (e) {
            console.warn('[APP] XML Format Error:', e);
            return xml;
        }
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

    // --- Sidebar Toggle ---
    const initSidebarToggle = () => {
        const sidebarToggle = document.getElementById('sidebarToggle');
        if (sidebarToggle) {
            sidebarToggle.onclick = () => {
                const grid = document.querySelector('.main-grid');
                if (grid) {
                    grid.classList.toggle('collapsed');
                    sidebarToggle.textContent = grid.classList.contains('collapsed') ? '▶' : '◀';
                }
            };
        }
    };

    // --- Baseline Metadata Loading ---
    const baselineServiceSelect = document.getElementById('baselineServiceSelect');
    const baselineDateSelect = document.getElementById('baselineDateSelect');
    const baselineRunSelect = document.getElementById('baselineRunSelect');

    const loadBaselineServices = async (preferredService = null) => {
        const workDir = document.getElementById('workingDirectory').value.trim();
        const type = document.getElementById('testType').value; // Get current type
        try {
            const url = `api/baselines/services?type=${encodeURIComponent(type)}${workDir ? '&workDir=' + encodeURIComponent(workDir) : ''}`;
            const response = await fetch(url);
            if (!response.ok) return;
            const services = await response.json();
            
            const currentVal = baselineServiceSelect.value;
            baselineServiceSelect.innerHTML = '<option value="">-- Select Service --</option>';
            services.forEach(s => {
                const opt = document.createElement('option');
                opt.value = s;
                opt.textContent = s;
                baselineServiceSelect.appendChild(opt);
            });
            
            if (services.length > 0) {
                // Priority: preferredService > currentVal > services[0]
                if (preferredService && services.indexOf(preferredService) !== -1) {
                    baselineServiceSelect.value = preferredService;
                } else if (currentVal && services.indexOf(currentVal) !== -1) {
                    baselineServiceSelect.value = currentVal;
                } else {
                    baselineServiceSelect.value = services[0];
                }
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
                if (!a.runId || !b.runId) return 0;
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

        // CRITICAL: Only populate form fields if we are in Baseline COMPARE mode.
        // If we are in CAPTURE mode, we don't want to overwrite user's current paths with baseline relative paths.
        const isCompareOp = (document.getElementById('comparisonMode').value === 'BASELINE' && document.getElementById('baselineOperation').value === 'COMPARE');
        if (!isCompareOp) return;

        try {
            const url = `api/baselines/runs/${encodeURIComponent(service)}/${encodeURIComponent(date)}/${encodeURIComponent(runId)}/endpoint${workDir ? '?workDir=' + encodeURIComponent(workDir) : ''}`;
            const response = await fetch(url);
            if (!response.ok) return;
            const details = await response.json();

            if (details.endpoint) document.getElementById('url1').value = details.endpoint;
            if (details.metadata && details.metadata.operation) document.getElementById('operationName').value = details.metadata.operation;
            if (details.payload) document.getElementById('payload').value = details.payload;
            
            // Populate Security Fields from Baseline Metadata
            const meta = details.metadata;
            if (meta && meta.configUsed && meta.configUsed.authentication) {
                const auth = meta.configUsed.authentication;
                const enableAuth = document.getElementById('enableAuth');
                const isAuthEnabled = !!(auth.clientId || auth.pfxPath || auth.clientCertPath || auth.caCertPath);
                
                if (enableAuth) {
                    enableAuth.checked = isAuthEnabled;
                    // Manually trigger change to update disabled states if not in Baseline mode 
                    // (though handleBaselineUI will likely handle it anyway)
                    enableAuth.dispatchEvent(new Event('change'));
                }

                if (auth.clientId) document.getElementById('clientId').value = auth.clientId;
                if (auth.clientSecret) document.getElementById('clientSecret').value = auth.clientSecret;
                
                const useMTLS = document.getElementById('useMTLS');
                const isMTLSEnabledFromBaseline = !!(auth.pfxPath || auth.clientCertPath || auth.caCertPath);
                if (useMTLS) {
                    useMTLS.checked = isMTLSEnabledFromBaseline;
                    useMTLS.dispatchEvent(new Event('change'));
                }

                if (auth.pfxPath) document.getElementById('pfxPath').value = auth.pfxPath;
                if (auth.clientCertPath) document.getElementById('clientCertPath').value = auth.clientCertPath;
                if (auth.clientKeyPath) document.getElementById('clientKeyPath').value = auth.clientKeyPath;
                if (auth.passphrase) document.getElementById('passphrase').value = auth.passphrase;
                if (auth.caCertPath) document.getElementById('caCertPath').value = auth.caCertPath;
            } else {
                // Reset auth if no info in metadata
                const enableAuth = document.getElementById('enableAuth');
                if (enableAuth) {
                   enableAuth.checked = false;
                   enableAuth.dispatchEvent(new Event('change'));
                }
            }

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
        const isSoap = (type === 'SOAP');
        const isJms = (type === 'JMS');
        
        document.getElementById('typeRest').classList.toggle('active', isRest);
        document.getElementById('typeSoap').classList.toggle('active', isSoap);
        
        // Reload baselines if in Baseline mode
        if (document.getElementById('comparisonMode').value === 'BASELINE') {
            loadBaselineServices();
        }
        const jmsBtn = document.getElementById('typeJms');
        if(jmsBtn) jmsBtn.classList.toggle('active', isJms);

        // Toggle Field Visibility
    const httpFields = document.getElementById('httpFields');
    const jmsFields = document.getElementById('jmsFields');
    
    if (httpFields) {
        httpFields.style.display = isJms ? 'none' : 'block';
    }
    if (jmsFields) {
        jmsFields.style.display = isJms ? 'block' : 'none';
    } else if (isJms) {
        // If JMS selected but fields not loaded yet, show loading indicator
        const loading = document.getElementById('jmsLoadingIndicator');
        if (loading) loading.style.display = 'block';
    }
        
        // JMS Mode hides the "Compare/Baseline" tabs effectively as it's a different beast, 
        // but for now we just disable the baseline button or hide the tabs?
        // Let's keep them visible but maybe disable Baseline mode if JMS is active?
        const modeBaseline = document.getElementById('modeBaseline');
        if (modeBaseline) {
            if (isJms) {
                 modeBaseline.style.opacity = '0.5';
                 modeBaseline.style.pointerEvents = 'none';
                 if (document.getElementById('comparisonMode').value === 'BASELINE') {
                     document.getElementById('modeCompare').click();
                 }
            } else {
                 modeBaseline.style.opacity = '1';
                 modeBaseline.style.pointerEvents = 'auto';
            }
        }
        
        // Hide Mode Tabs (Compare/Baseline) in JMS mode
        const modeTabs = document.getElementById('modeTabsContainer') || document.querySelector('.mode-tabs');
        if (modeTabs) {
            modeTabs.style.display = isJms ? 'none' : 'flex';
        }
        
        // JMS Specific UI Sync
        const compareBtn = document.getElementById('compareBtn');
        const resultsContainer = document.getElementById('resultsContainer');
        
        if (isJms) {
            if (resultsContainer) resultsContainer.classList.add('jms-mode');
            const opModeSelect = document.getElementById('jmsOpMode');
            const mode = opModeSelect ? opModeSelect.value : 'PUBLISH';
            
            // Re-sync text just in case syncJmsFields hasn't run yet
            if (compareBtn) {
                switch(mode) {
                    case 'PUBLISH': compareBtn.innerText = '🚀 Publish Message'; break;
                    case 'LISTEN': compareBtn.innerText = '👂 Start Listener'; break;
                    case 'CONSUME': compareBtn.innerText = '📥 Consume Once'; break;
                    default: compareBtn.innerText = 'Run Action';
                }
            }
            
            // Clear previous REST/SOAP results if present
            if (resultsContainer && resultsContainer.querySelector('.summary-card')) {
                resultsContainer.innerHTML = `
                    <div class="empty-state" style="text-align:center; padding:40px; color:#553c9a;">
                        <h3 style="font-size:1.5rem; margin-bottom:10px;">🛡️ JMS Console</h3>
                        <p style="font-weight:600;">Ready for unified operations.</p>
                    </div>`;
            }
        } else {
             if (resultsContainer) resultsContainer.classList.remove('jms-mode');
             // Restore standard text
             if (compareBtn) {
                 const baselineOp = document.getElementById('baselineOperation').value;
                 if (document.getElementById('comparisonMode').value === 'BASELINE') {
                     compareBtn.innerText = (baselineOp === 'CAPTURE') ? '📸 Capture Baseline' : '🔍 Compare Baseline';
                 } else {
                     compareBtn.innerText = '▶ Run Comparison';
                 }
             }
        }
    };

    const updateTypeToggle = (type) => {
        document.getElementById('testType').value = type;
        syncTypeButtons();
    };

    document.getElementById('typeRest').addEventListener('click', () => updateTypeToggle('REST'));
    document.getElementById('typeSoap').addEventListener('click', () => updateTypeToggle('SOAP'));
    // Fixed: Add listener for JMS to ensure UI Sync happens
    const jmsBtn = document.getElementById('typeJms');
    if(jmsBtn) jmsBtn.addEventListener('click', () => {
        // Feature disabled for Wrapper mode as per user request
        alert('This feature (JMS mode) is not enabled for Wrapper mode yet.');
        return; 
        // updateTypeToggle('JMS'); 
    });

    // Expose for JMS Module
    window.syncTypeButtons = syncTypeButtons;

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

        // --- Security Field Disablement in Baseline Compare ---
        const securityFieldIds = [
            'enableAuth', 'clientId', 'clientSecret', 
            'useMTLS', 'pfxPath', 'clientCertPath', 'clientKeyPath', 
            'passphrase', 'caCertPath', 'validateCertsBtn'
        ];
        
        const isAuthEnabled = document.getElementById('enableAuth').checked;
        const isMTLSEnabled = document.getElementById('useMTLS')?.checked;

        securityFieldIds.forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                if (isCompareOp) {
                    el.disabled = true;
                } else {
                    // Respect master toggles when re-enabling
                    if (id === 'clientId' || id === 'clientSecret') {
                        el.disabled = !isAuthEnabled;
                    } else if (['pfxPath', 'clientCertPath', 'clientKeyPath', 'passphrase', 'caCertPath'].includes(id)) {
                        el.disabled = !isMTLSEnabled;
                    } else {
                        el.disabled = false;
                    }
                }
                
                const label = el.closest('label');
                if (label) {
                    label.classList.toggle('disabled-label', el.disabled);
                }
            }
        });

        // Disable folder buttons and file inputs in security section
        document.querySelectorAll('#securityAccordion .btn-secondary, #securityAccordion input[type="file"]').forEach(btn => {
            // Check if it's an upload button (has folder emoji or is validate btn)
            if (btn.innerText.includes('📂') || btn.id === 'validateCertsBtn' || btn.type === 'file') {
                btn.disabled = isCompareOp;
                btn.style.opacity = isCompareOp ? '0.5' : '1';
                btn.style.cursor = isCompareOp ? 'not-allowed' : 'pointer';
            }
        });

        // Accordion Management: Hide Endpoint, Request, Headers, Tokens during Compare mode
        accordions.forEach((acc, idx) => {
            if (idx < 5) { // First five accordions: Endpoint, Request, Parameters, Headers, Tokens
                acc.style.display = isCompareOp ? 'none' : 'block';
            }
        });
    };

    // --- Field Toggles ---
    document.getElementById('enableAuth').addEventListener('change', (e) => {
        const isCompareOp = (document.getElementById('comparisonMode').value === 'BASELINE' && document.getElementById('baselineOperation').value === 'COMPARE');
        const shouldDisable = !e.target.checked || isCompareOp;
        document.getElementById('clientId').disabled = shouldDisable;
        document.getElementById('clientSecret').disabled = shouldDisable;

        // CRITICAL: If auth is disabled, remove Authorization header to ensure synchronization
        if (!e.target.checked) {
            headersTable.querySelectorAll('tr').forEach(tr => {
                const keyInput = tr.querySelector('.key-input');
                if (keyInput && keyInput.value.trim().toLowerCase() === 'authorization') {
                    tr.remove();
                    logActivity('Sync: Removed stale Authorization header after disabling Auth.', 'info');
                }
            });
        } else {
            // Restore Authorization header if missing and credentials exist
            let exists = false;
            headersTable.querySelectorAll('tr').forEach(tr => {
                const keyInput = tr.querySelector('.key-input');
                if (keyInput && keyInput.value.trim().toLowerCase() === 'authorization') exists = true;
            });

            if (!exists) {
                const cid = document.getElementById('clientId').value.trim();
                const csec = document.getElementById('clientSecret').value.trim();
                if (cid && csec) {
                    const basic = btoa(`${cid}:${csec}`);
                    addRow(headersTable, ['Header Name', 'Value'], ['Authorization', `Basic ${basic}`]);
                    logActivity('Sync: Restored Authorization header after enabling Auth.', 'success');
                }
            }
        }
    });

    document.getElementById('useMTLS')?.addEventListener('change', (e) => {
        const isCompareOp = (document.getElementById('comparisonMode').value === 'BASELINE' && document.getElementById('baselineOperation').value === 'COMPARE');
        const shouldDisable = !e.target.checked || isCompareOp;
        ['pfxPath', 'clientCertPath', 'clientKeyPath', 'caCertPath', 'passphrase'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.disabled = shouldDisable;
        });
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
        const original = workpad.value.trim();
        if (!original) {
            logActivity('Data Foundry: No content to format.', 'info');
            return;
        }
        
        const formatted = formatData(original);
        workpad.value = formatted;
        
        let subType = 'Data';
        if (original.startsWith('{') || original.startsWith('[')) subType = 'JSON';
        else if (original.startsWith('<')) subType = 'XML';
        
        logActivity(`${subType} Formatted.`, 'success');
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
                logActivity(`IMPORT CONFLICTS: ${conflicts.length} services already exist in working directory.`, 'warning');
                if (confirm(`Conflicts detected for: ${conflicts.join(', ')}. Overwrite existing data?`)) {
                    await handleImport(file, true);
                } else {
                    logActivity(`IMPORT CANCELLED: User declined overwrite for ${conflicts.length} services.`, 'info');
                    if(status) status.innerText = 'Import cancelled by user.';
                }
                return;
            } else if (resp.ok) {
                const resData = await resp.json();
                if (resData.success) {
                    alert('Mock Test Data Loaded Successfully!');
                    // User Request: Ensure SOAP/REST is selected by default after loading mock data
                    document.getElementById('testType').value = 'SOAP'; 
                    syncTypeButtons();
                    const count = resData.imported ? resData.imported.length : 0;
                    const fileList = resData.imported ? resData.imported.join(', ') : 'Unknown files';
                    
                    logActivity(`IMPORT SUCCESS: Restored ${count} files.`, 'success');
                    logActivity(`IMPORTED FILES: ${fileList}`, 'debug');
                    
                    if(status) status.innerText = '✅ Import Successful!';
                    loadExportServices(); // Refresh list
                    setTimeout(hideProgressModal, 1000); 
                } else {
                     throw new Error(resData.error || 'Unknown error');
                }
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
    initSidebarToggle();
    loadDefaults();
    
    // Lazy Load JMS UI
    const loadJmsUI = async () => {
        const loading = document.getElementById('jmsLoadingIndicator');
        if (loading) loading.style.display = 'block';
        
        try {
            const resp = await fetch('jms.html');
            if (resp.ok) {
                const html = await resp.text();
                const container = document.getElementById('jmsContainer');
                if (container) {
                    // Preserve the loading indicator if it exists, or just overwrite it
                    container.innerHTML = html;
                    
                    // Initialize JMS Module if available
                    if (typeof JmsModule !== 'undefined') {
                        JmsModule.init();
                    }
                    
                    // Sync UI after injection
                    if (window.syncTypeButtons) window.syncTypeButtons();

                    // Re-bind accordions for the new content
                    container.querySelectorAll('.accordion-header').forEach(h => {
                        h.addEventListener('click', () => {
                            const content = h.nextElementSibling;
                            content.classList.toggle('open');
                            h.querySelector('.icon').innerText = content.classList.contains('open') ? '▼' : '▲';
                        });
                    });
                }
            }
        } catch (e) {
            console.error('Failed to load JMS UI', e);
        } finally {
            if (loading) loading.style.display = 'none';
        }
    };

    loadJmsUI();

    window.copyToClipboard = (id, btn) => {
        const el = document.getElementById(id);
        if (!el) return;
        
        const textArea = document.createElement("textarea");
        textArea.value = el.innerText || el.value;
        document.body.appendChild(textArea);
        textArea.select();
        try {
            document.execCommand('copy');
            if (btn) {
                const orig = btn.innerText;
                btn.innerText = '✅';
                setTimeout(() => btn.innerText = orig, 1000);
            } else {
                showToast('Copied to clipboard!', 'success');
            }
        } catch (err) {
            console.error('Copy failed', err);
        }
        document.body.removeChild(textArea);
    };

});
