// CSV FileSync - Frontend JavaScript
let sessionId = null;
let sourceFiles = [];
let currentMappings = [];
let autocompleteData = { fileNames: [], fieldNames: [] };
let selectedMappings = new Set();
let logPanelInitialized = false;
let configSaved = false; // Track if config is ready for execution
let importedFileMappings = []; // Store full multi-file config for preservation

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    initLogPanel();
    initModal(); // Initialize modal system
    loadAutocomplete();
    updateAutocompleteStats();
});

// --- Help System ---

function showHelp() {
    const helpContent = `
        <div style="text-align: left; max-height: 60vh; overflow-y: auto;">
            <h3>User Guide</h3>
            
            <h4>1. Discovery Tab (Start Here)</h4>
            <p>Upload a ZIP file (containing CSVs in a 'source' folder) or select multiple CSV files directly. The tool will scan and list all available files.</p>
            
            <h4>2. Mapping Tab</h4>
            <ul>
                <li><strong>Source File:</strong> Select one of the discovered CSVs.</li>
                <li><strong>Target File:</strong> Enter the name of the output file (e.g., 'output.csv').</li>
                <li><strong>Mappings:</strong> Select source fields and map them to target fields. The tool supports 'Direct' mapping (copy value).</li>
                <li><strong>Horizontal Merge:</strong> If you map multiple source files to the <em>same</em> target file name, the tool will merge them by appending columns!</li>
            </ul>
            
            <h4>3. Execute Tab</h4>
            <p>Click "Execute Transformation" to run the engine. You can then download the result as a ZIP file.</p>
            
            <h4>4. Configuration</h4>
            <p>You can <strong>Download</strong> your mapping configuration to save it for later. Use <strong>Import</strong> to restore a previous session.</p>
            
            <h4>Tips</h4>
            <ul>
                <li>Use <strong>Auto-Fill</strong> to quickly map fields with identical names.</li>
                <li>The tool <strong>auto-saves</strong> your progress (Discovery path, target names).</li>
            </ul>
        </div>
    `;
    
    showModal('FileSync Help', helpContent);
}

// --- Modal System ---
function initModal() {
    if (document.getElementById('customModal')) return;
    
    const modalHTML = `
    <div id="customModal" class="modal-overlay" style="display:none;">
        <div class="modal-content">
            <div class="modal-header">
                <h3 id="modalTitle">Alert</h3>
                <button onclick="closeModal()" class="btn-icon">✖️</button>
            </div>
            <div id="modalBody" class="modal-body"></div>
            <div class="modal-footer">
                <button onclick="closeModal()" class="btn-primary">OK</button>
            </div>
        </div>
    </div>
    <style>
        .modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; }
        .modal-content { background: white; padding: 20px; border-radius: 8px; width: 400px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); animation: modalPop 0.3s ease-out; }
        .modal-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #eee; padding-bottom: 10px; margin-bottom: 15px; }
        .modal-header h3 { margin: 0; color: #333; }
        .modal-body { font-size: 16px; color: #555; margin-bottom: 20px; }
        .modal-footer { text-align: right; }
        @keyframes modalPop { from { transform: scale(0.9); opacity: 0; } to { transform: scale(1); opacity: 1; } }
    </style>
    `;
    document.body.insertAdjacentHTML('beforeend', modalHTML);
}

function showModal(title, message) {
    const modal = document.getElementById('customModal');
    if (modal) {
        document.getElementById('modalTitle').innerText = title;
        document.getElementById('modalBody').innerHTML = message; // Allow HTML
        modal.style.display = 'flex';
    } else {
        alert(`${title}\n${message}`); // Fallback
    }
}

function closeModal() {
    document.getElementById('customModal').style.display = 'none';
}

// --- Log Panel System ---
function initLogPanel() {
    if (document.getElementById('logPanel')) return;

    const logHTML = `
    <div id="logPanel" class="log-panel" style="display:none;">
        <div class="log-header">
            <span>📋 Activity Log</span>
            <div class="log-controls">
                <button onclick="toggleLogHeight()" class="btn-icon">↕️</button>
                <button onclick="clearLog()" class="btn-clear-log">Clear</button>
                <button onclick="closeLog()" class="btn-icon">✖️</button>
            </div>
        </div>
        <div id="logContent" class="log-content"></div>
    </div>
    <div id="notification" class="notification"></div>
    `;
    
    // Inject log panel INSIDE the container to make it "attached"
    const container = document.querySelector('.container');
    if (container) {
        container.insertAdjacentHTML('beforeend', logHTML);
    } else {
        document.body.insertAdjacentHTML('beforeend', logHTML);
    }
    logPanelInitialized = true;
}

function showNotification(message, type = 'info') {
    const notification = document.getElementById('notification');
    if (notification) {
        notification.className = `notification ${type} show`;
        notification.innerText = message;
        setTimeout(() => { notification.classList.remove('show'); }, 3000);
    }
    addToLog(message, type);
}

function addToLog(message, type) {
    const logPanel = document.getElementById('logPanel');
    const logContent = document.getElementById('logContent');
    if (!logPanel || !logContent) return;
    
    logPanel.style.display = 'flex';
    const timestamp = new Date().toLocaleTimeString();
    const entry = document.createElement('div');
    entry.className = `log-entry ${type}`;
    entry.innerHTML = `<span class="log-timestamp">[${timestamp}]</span> <span class="log-message">${message}</span>`;
    logContent.appendChild(entry);
    logContent.scrollTop = logContent.scrollHeight;
}

function clearLog() {
    const logContent = document.getElementById('logContent');
    if (logContent) logContent.innerHTML = '';
}

function closeLog() {
    document.getElementById('logPanel').style.display = 'none';
}

function toggleLogHeight() {
    document.getElementById('logPanel').classList.toggle('expanded');
}


// --- Tab Switching ---
function switchTab(tabName) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
    
    const tabEl = document.getElementById(`${tabName}-tab`);
    if (tabEl) tabEl.classList.add('active');
    
    if (tabName === 'execute') {
        updateExecuteTabState();
    }
    
    // Active class for buttons happens via click or manual lookup
    const btn = document.querySelector(`.tab-button[onclick="switchTab('${tabName}')"]`);
    if (btn) btn.classList.add('active');
}

function updateExecuteTabState() {
    const executeTab = document.getElementById('execute-tab');
    if (!executeTab) return;
    
    const targetFile = document.getElementById('targetFileName') ? document.getElementById('targetFileName').value : '';
    
    if (!configSaved) {
        executeTab.innerHTML = `
            <div class="card">
                <h2>Execute Transformation</h2>
                <div class="alert status-warning">
                    ⚠️ No configuration loaded. Please go to the <b>Mapping</b> tab, create mappings, and click <b>Save Configuration</b>.
                </div>
                <button class="btn-primary" disabled style="opacity:0.5; cursor:not-allowed;">Execute Transformation</button>
            </div>
        `;
    } else {
        let targetsHtml = '';
        if (window.savedTargetFiles && window.savedTargetFiles.length > 0) {
            targetsHtml = `<b>${window.savedTargetFiles.length} Target Files Configured</b>:<br> ${window.savedTargetFiles.join(', ')}`;
        } else {
            targetsHtml = `Target File: <b>${targetFile}</b>`;
        }

        executeTab.innerHTML = `
            <div class="card">
                <h2>Execute Transformation</h2>
                <div class="alert status-success">
                    ✅ Configuration saved!<br>
                    ${targetsHtml}<br>
                    <small>Ready to execute transformation.</small>
                </div>
                <div style="margin-top:20px;">
                    <button class="btn-primary" onclick="executeTransformation()">⚡ Execute Transformation</button>
                </div>
                <div id="executionResult" style="margin-top:20px;"></div>
            </div>
        `;
    }
}


// --- File Upload ---
async function uploadZip() {
    const fileInput = document.getElementById('zipUpload');
    if (!fileInput.files.length) {
        showNotification('Please select a ZIP file', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
        showNotification('Uploading and extracting ZIP...', 'info');
        const response = await fetch('/apiguard/csvfilesync/upload/zip', {
            method: 'POST',
            body: formData
        });
        const result = await response.json();
        
        if (result.error) {
            showNotification('Error: ' + result.error, 'error');
            return;
        }
        handleUploadSuccess(result);
        
    } catch (error) {
        showNotification('Upload failed: ' + error.message, 'error');
    }
}

async function uploadCsvs() {
    const fileInput = document.getElementById('csvUpload');
    if (!fileInput.files.length) {
        showNotification('Please select CSV files', 'error');
        return;
    }

    const formData = new FormData();
    for (let i = 0; i < fileInput.files.length; i++) {
        formData.append('file', fileInput.files[i]);
    }

    try {
        showNotification('Uploading CSV files...', 'info');
        const response = await fetch('/apiguard/csvfilesync/upload/csvs', {
            method: 'POST',
            body: formData
        });
        
        // Handle non-JSON responses gracefully
        const text = await response.text();
        let result;
        try {
            result = JSON.parse(text);
        } catch (e) {
            console.error('Non-JSON response:', text);
            showNotification('Server Error: ' + text.substring(0, 50), 'error');
            return;
        }
        
        if (result.status === 'error' || result.error) {
            showNotification('Error: ' + (result.message || result.error || 'Upload failed'), 'error');
            return;
        }
        handleUploadSuccess(result);
        
    } catch (error) {
        showNotification('Upload failed: ' + error.message, 'error');
    }
}

function handleUploadSuccess(result) {
    sessionId = result.sessionId;
    
    if (Array.isArray(result.files)) {
        sourceFiles = result.files;
    } else if (result.files) {
        sourceFiles = [result.files];
    } else {
        sourceFiles = [];
    }
    
    // Stats
    const totalRows = sourceFiles.reduce((sum, f) => sum + Math.max(0, (f.rowCount || 0) - 1), 0);

    displayUploadedFiles();
    populateSourceFileSelect();
    
    showNotification(`✓ Success! ${result.fileCount} files ready. (${totalRows} data rows)`, 'success');
}

function displayUploadedFiles() {
    const fileList = document.getElementById('fileList');
    if (fileList) {
        fileList.innerHTML = '';
        if (!sourceFiles || sourceFiles.length === 0) {
            fileList.innerHTML = '<p>No files uploaded yet.</p>';
        } else {
            sourceFiles.forEach(file => {
                const div = document.createElement('div');
                div.className = 'file-item';
                const fieldCount = file.headers ? file.headers.length : (file.fields || 0);
                const rowCount = file.rowCount !== undefined ? file.rowCount : (file.rows || 0);
                div.innerHTML = `
                    <span class="file-icon">📄</span>
                    <div class="file-info">
                        <span class="file-name">${file.name}</span>
                        <span class="file-meta">${fieldCount} fields, ${rowCount} rows</span>
                    </div>
                `;
                fileList.appendChild(div);
            });
        }
    }
}

function populateSourceFileSelect() {
    const sourceSelect = document.getElementById('sourceFileSelect');
    if (!sourceSelect) return;
    sourceSelect.innerHTML = '<option value="">-- Select Source File --</option>';
    sourceFiles.forEach((file, index) => {
        const option = document.createElement('option');
        option.value = index;
        option.textContent = file.name;
        sourceSelect.appendChild(option);
    });
}

function loadFileForMapping() {
    const sourceSelect = document.getElementById('sourceFileSelect');
    const selectedIndex = sourceSelect.value;
    
    if (!selectedIndex || selectedIndex === '') {
        currentMappings = [];
        displayMappings();
        return;
    }
    
    const selectedFile = sourceFiles[parseInt(selectedIndex)];
    if (!selectedFile) {
        showNotification('Selected file not found', 'error');
        return;
    }
    
    const fields = selectedFile.headers || selectedFile.fieldNames || [];
    currentMappings = fields.map(fieldName => ({
        sourceField: fieldName,
        targetField: '',
        transformation: 'direct'
    }));
    
    displayMappings();
}

function displayMappings() {
    const container = document.getElementById('mappingsList');
    if (!container) return;
    container.innerHTML = '';
    
    if (!currentMappings || currentMappings.length === 0) {
        container.innerHTML = '<p>Select a source file to create mappings.</p>';
        return;
    }
    
    currentMappings.forEach((mapping, index) => {
        const div = document.createElement('div');
        div.className = 'mapping-item';
        div.innerHTML = `
            <div class="mapping-row">
                <input type="checkbox" class="mapping-checkbox" data-index="${index}" 
                       onchange="toggleMappingSelection(${index})">
                <span class="source-field">${mapping.sourceField}</span>
                <span class="arrow">→</span>
                <input type="text" class="target-field-input form-control" 
                       placeholder="Target Field" 
                       value="${mapping.targetField || ''}"
                       onchange="updateMapping(${index}, this.value)"
                       list="fieldSuggestions">
            </div>
        `;
        container.appendChild(div);
    });
}

function updateMapping(index, value) {
    if (currentMappings[index]) currentMappings[index].targetField = value;
}

function toggleMappingSelection(index) {
    const checkbox = document.querySelector(`.mapping-checkbox[data-index="${index}"]`);
    if (checkbox.checked) selectedMappings.add(index);
    else selectedMappings.delete(index);
}

function toggleSelectAll() {
    const selectAll = document.getElementById('selectAllMappings');
    const checkboxes = document.querySelectorAll('.mapping-checkbox');
    const isChecked = selectAll.checked;
    selectedMappings.clear();
    checkboxes.forEach(cb => {
        cb.checked = isChecked;
        if (isChecked) selectedMappings.add(parseInt(cb.dataset.index));
    });
}

function autoFillSameNames() {
    let count = 0;
    currentMappings.forEach((mapping) => {
        if (!mapping.targetField) {
            mapping.targetField = mapping.sourceField;
            count++;
        }
    });
    displayMappings();
    if (count > 0) showNotification(`Auto-filled ${count} fields`, 'success');
}

function deleteSelectedMappings() {
    if (selectedMappings.size === 0) return;
    if (!confirm(`Delete ${selectedMappings.size} mappings?`)) return;
    currentMappings = currentMappings.filter((_, index) => !selectedMappings.has(index));
    selectedMappings.clear();
    const selectAll = document.getElementById('selectAllMappings');
    if (selectAll) selectAll.checked = false;
    displayMappings();
    showNotification('Mappings deleted', 'info');
}

function addMapping() {
    currentMappings.push({ sourceField: '', targetField: '', transformation: 'direct' });
    displayMappings();
}

async function loadAutocomplete() {
    try {
        const response = await fetch('/apiguard/csvfilesync/autocomplete/get');
        const result = await response.json();
        autocompleteData = result;
        const datalist = document.getElementById('fieldSuggestions') || document.createElement('datalist');
        datalist.id = 'fieldSuggestions';
        datalist.innerHTML = '';
        (autocompleteData.fieldNames || []).forEach(name => {
            const opt = document.createElement('option');
            opt.value = name;
            datalist.appendChild(opt);
        });
        if (!document.getElementById('fieldSuggestions')) document.body.appendChild(datalist);
    } catch (e) {
        console.warn('Failed to load autocomplete', e);
    }
}

function updateAutocompleteStats() {
    const container = document.getElementById('autocompleteStats');
    if (container) {
        container.innerHTML = `Saved Fields: ${autocompleteData.fieldNames?.length || 0} | Saved Files: ${autocompleteData.fileNames?.length || 0}`;
    }
}

// --- Config Management (Import/Download) ---

function downloadConfig() {
    if (!sessionId) {
        showNotification('No active session. Please upload files and save config first.', 'error');
        return;
    }
    // Check if saved
    if (!configSaved) {
        if (!confirm('Configuration has NOT been saved yet. You might download an empty or old config. Save first?')) {
             // proceed anyway
        } else {
            return;
        }
    }
    window.location.href = `/apiguard/csvfilesync/config/download/${sessionId}`;
}

function importConfig(input) {
    if (!input.files || !input.files[0]) return;
    
    const file = input.files[0];
    const reader = new FileReader();
    
    reader.onload = function(e) {
        const content = e.target.result.trim();
        
        // Pre-check for common text error responses masquerading as files
        if (content.startsWith('Configurat') || content.startsWith('Error') || !content.startsWith('{')) {
             showNotification('Invalid Config File: The file contains a text message/error, not JSON.', 'error');
             console.error('Import detection: File content starts with non-JSON characters:', content.substring(0, 50));
             input.value = ''; // Reset
             return;
        }

        try {
            const config = JSON.parse(content);
            
            // Handle new structure (fileMappings) vs legacy (mappings)
            let loadedMappings = [];
            let targetFile = config.targetFile || '';
            
            if (config.fileMappings && Array.isArray(config.fileMappings) && config.fileMappings.length > 0) {
                // Store ALL mappings for preservation
                importedFileMappings = config.fileMappings;
                const fileCount = config.fileMappings.length;
                addToLog(`Imported multi-file config with ${fileCount} target files.`, 'info');
                
                // Load first mapping
                const firstMapping = config.fileMappings[0];
                loadedMappings = firstMapping.fieldMappings || [];
                targetFile = firstMapping.targetFile || targetFile;
                
                // Try to select the source file if it exists in current uploads
                const sourceSelect = document.getElementById('sourceFileSelect');
                if (sourceSelect) {
                    for(let i=0; i<sourceSelect.options.length; i++) {
                        if (sourceSelect.options[i].text === firstMapping.sourceFile) {
                            sourceSelect.value = sourceSelect.options[i].value;
                            break;
                        }
                    }
                }
            } else if (config.mappings && Array.isArray(config.mappings)) {
                // Legacy
                importedFileMappings = [{
                    sourceFile: targetFile, // Assuming legacy structure, this might be imprecise but sufficient
                    targetFile: targetFile,
                    fieldMappings: config.mappings
                }];
                loadedMappings = config.mappings;
            } else {
                throw new Error('Invalid config structure: missing mappings');
            }
            
            // Apply to UI
            document.getElementById('mappingsList').innerHTML = ''; // Clear existing visual elements (Correct ID)
            document.getElementById('targetFileName').value = targetFile;
            currentMappings = loadedMappings;
            displayMappings();
            
            showNotification(`Configuration loaded! Target: ${targetFile}`, 'success');
            addToLog(`Loaded mapping view for ${targetFile} from ${file.name}`, 'info');
            
            // Reset Input
            input.value = '';
            
            // Note: User still needs to click "Save" to apply this to the current session (backend)
            configSaved = false; 
            updateExecuteTabState(); // Will disable execute until saved
            
        } catch (err) {
            console.error('Import Error', err);
            showNotification('Failed to parse config file: ' + err.message, 'error');
        }
    };
    
    reader.readAsText(file);
}

// --- Save Config ---
async function saveConfig() {
    const targetFile = document.getElementById('targetFileName').value;
    if (!targetFile) {
        showModal('Validation Error', 'Please enter a <b>Target File Name</b> before saving.');
        return;
    }
    
    // Get Source File name
    const sourceSelect = document.getElementById('sourceFileSelect');
    if (!sourceSelect || !sourceSelect.value) {
        showModal('Validation Error', 'Please select a <b>Source File</b> first.');
        return;
    }
    const sourceFileName = sourceSelect.options[sourceSelect.selectedIndex].text;
    
    // Sanitization
    const sanitize = (str) => {
        if (!str) return '';
        return String(str).replace(/[^\x20-\x7E]/g, '').trim();
    };

    const sanitizedMappings = currentMappings.map(m => ({
        sourceField: sanitize(m.sourceField),
        targetField: sanitize(m.targetField),
        transformation: sanitize(m.transformation || 'direct')
    }));
    
    const sanitizedTargetFile = sanitize(targetFile);
    const sanitizedSessionId = sanitize(sessionId);
    const sanitizedSourceFile = sanitize(sourceFileName);

    if (!sanitizedTargetFile) {
        showModal('Validation Error', 'Invalid Target File Name.');
        return;
    }

    // Construct Backend-Compatible Payload
    // MERGE STRATEGY: Start with imported mappings (if any), then update/add current one
    let finalFileMappings = [];
    
    if (importedFileMappings && importedFileMappings.length > 0) {
        // Clone to avoid side effects
        finalFileMappings = JSON.parse(JSON.stringify(importedFileMappings));
        
        // Find if we are editing an existing mapping block (Unique key = Source + Target)
        const existingIndex = finalFileMappings.findIndex(m => 
            m.targetFile === sanitizedTargetFile && 
            m.sourceFile === sanitizedSourceFile
        );
        
        if (existingIndex >= 0) {
            // Update existing block for this specific Source->Target pair
            finalFileMappings[existingIndex] = {
                sourceFile: sanitizedSourceFile,
                targetFile: sanitizedTargetFile,
                fieldMappings: sanitizedMappings
            };
        } else {
            // Append new block (e.g. same target but new source, or completely new target)
            finalFileMappings.push({
                sourceFile: sanitizedSourceFile,
                targetFile: sanitizedTargetFile,
                fieldMappings: sanitizedMappings
            });
        }
    } else {
        // No import / Fresh Start
        finalFileMappings = [
            {
                sourceFile: sanitizedSourceFile,
                targetFile: sanitizedTargetFile,
                fieldMappings: sanitizedMappings
            }
        ];
    }
    
    const config = {
        fileMappings: finalFileMappings
    };
    
    console.log('Save Payload:', JSON.stringify({sessionId: sanitizedSessionId, config}));

    try {
        const response = await fetch('/apiguard/csvfilesync/config/save', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({sessionId: sanitizedSessionId, config})
        });

        const textResponse = await response.text();
        let res;
        try { res = JSON.parse(textResponse); } catch (e) {
            showNotification('Server Error: ' + textResponse.substring(0, 100), 'error');
            addToLog('Raw Response: ' + textResponse, 'error');
            return;
        }

        if (res.status === 'success') {
            configSaved = true; // MARK READY
            
            // Capture target files list from response
            if (res.targetFiles && Array.isArray(res.targetFiles)) {
                window.savedTargetFiles = [...new Set(res.targetFiles)]; // Deduplicate
                showNotification(`Configuration saved! Targets: ${window.savedTargetFiles.join(', ')}`, 'success');
                // Log detailed stats as requested
                const fileCount = window.savedTargetFiles.length;
                addToLog(`Configuration Added/Updated. Total Targets: ${fileCount} (${window.savedTargetFiles.join(', ')})`, 'success');
            } else {
                window.savedTargetFiles = [sanitizedTargetFile];
                showNotification(`Configuration saved! Target: ${sanitizedTargetFile}`, 'success');
            }
            
            updateExecuteTabState(); // Update UI with new list
            loadAutocomplete();
            
            // Visual feedback on button
            const saveBtn = document.querySelector('button[onclick="saveConfig()"]');
            const originalText = saveBtn ? saveBtn.innerText : "Save Configuration";
            if(saveBtn) saveBtn.innerText = "✅ Saved!";
            setTimeout(() => { if(saveBtn) saveBtn.innerText = originalText; }, 2000);
            
        } else {
            showModal('Save Failed', res.message);
        }
    } catch (e) {
        showModal('Save Failed', e.message);
    }
}

// --- Execute ---
async function executeTransformation() {
    if (!sessionId) return;
    
    const resultDiv = document.getElementById('executionResult');
    if (!resultDiv) return;
    
    resultDiv.innerHTML = '<div class="spinner"></div><p>Processing...</p>';
    
    try {
        const response = await fetch('/apiguard/csvfilesync/execute', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({sessionId: sessionId})
        });
        
        const text = await response.text();
        let res;
        try { res = JSON.parse(text); } catch(e) { res = {status:'error', message: text}; }
        
        if (res.status === 'success') {
            resultDiv.innerHTML = `
                <div class="alert status-success">
                    <h3>🎉 Success!</h3>
                    <p>${res.message || 'Transformation complete.'}</p>
                    ${res.downloadUrl ? `<div style="margin-top:15px;"><a href="${res.downloadUrl}" class="btn-success">⬇️ Download Result</a></div>` : ''}
                </div>
            `;
            showNotification('Transformation complete!', 'success');
        } else {
            resultDiv.innerHTML = `<div class="alert status-error">❌ Error: ${res.message}</div>`;
            showModal('Execution Failed', `The transformation process failed.<br><br><b>Reason:</b> ${res.message}`);
        }
    } catch (e) {
        resultDiv.innerHTML = `<div class="alert status-error">❌ Exception: ${e.message}</div>`;
        showModal('Execution Error', e.message);
    }
}
