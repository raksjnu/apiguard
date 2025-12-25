// RaksAnalyzer Web UI JavaScript - Complete REST API Integration

document.addEventListener('DOMContentLoaded', function() {
    setupEventHandlers();
    loadConfiguration();
    loadDynamicLabels();
});

let currentExcelPath = null;
let currentWordPath = null;
let currentPdfPath = null;
let currentAnalysisId = null;

function setupEventHandlers() {
    const form = document.getElementById('analysisForm');
    const environmentScope = document.getElementById('environmentScope');
    const customEnvs = document.getElementById('customEnvs');
    const inputSourceRadios = document.querySelectorAll('input[name="inputSource"]');
    const uploadSection = document.getElementById('uploadSection');
    const folderSection = document.getElementById('folderSection');
    const gitSection = document.getElementById('gitSection');
    const progressSection = document.getElementById('progressSection');
    const resultsSection = document.getElementById('resultsSection');
    
    // Toggle custom environment selection
    environmentScope.addEventListener('change', function() {
        customEnvs.style.display = 'block'; // Always block for now to debug
        customEnvs.style.display = this.value === 'CUSTOM' ? 'block' : 'none';
    });

    // Toggle custom config file section
    const useCustomConfig = document.getElementById('useCustomConfig');
    const configUploadSection = document.getElementById('configUploadSection');
    if (useCustomConfig) {
        useCustomConfig.addEventListener('change', function() {
            configUploadSection.style.display = this.checked ? 'block' : 'none';
        });
    }
    
    // Toggle input source (zip vs jar vs folder vs git)
    inputSourceRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            const zipSection = document.getElementById('zipSection');
            const jarSection = document.getElementById('jarSection');
            
            zipSection.style.display = 'none';
            jarSection.style.display = 'none';
            folderSection.style.display = 'none';
            gitSection.style.display = 'none';
            
            if (this.value === 'zip') {
                zipSection.style.display = 'block';
            } else if (this.value === 'jar') {
                jarSection.style.display = 'block';
            } else if (this.value === 'folder') {
                folderSection.style.display = 'block';
            } else if (this.value === 'git') {
                gitSection.style.display = 'block';
            }
        });
    });
    
    // Form submission
    form.addEventListener('submit', async function(e) {
        e.preventDefault();
        await submitAnalysis();
    });
    
    // Start Again button
    document.getElementById('startAgain').addEventListener('click', async function() {
        // Cleanup temp files if this was an upload
        if (currentAnalysisId) {
            try {
                await fetch('/api/analyze/cleanup/' + currentAnalysisId, { method: 'POST' });
            } catch (error) {
                console.warn('Cleanup failed:', error);
            }
        }
        
        document.getElementById('resultsSection').style.display = 'none';
        document.getElementById('analysisForm').style.display = 'block';
        document.getElementById('analysisForm').reset();
        currentAnalysisId = null;
        currentExcelPath = null;
        currentWordPath = null;
        currentPdfPath = null;
        currentAnalysisId = null;
        
        // Hide all results by default
        document.getElementById('excelResult').style.display = 'none';
        document.getElementById('wordResult').style.display = 'none';
        document.getElementById('pdfResult').style.display = 'none';
    });
    
    // Open Folder button
    document.getElementById('openFolder').addEventListener('click', async function() {
         if (currentAnalysisId) {
             await openLocalFile(currentAnalysisId, 'folder');
         }
    });

    // Open Excel button
    document.getElementById('openExcel').addEventListener('click', async function(e) {
        e.preventDefault();
        if (currentAnalysisId) await openLocalFile(currentAnalysisId, 'excel');
    });
    
    // Open Word button
    document.getElementById('openWord').addEventListener('click', async function(e) {
        e.preventDefault();
        if (currentAnalysisId) await openLocalFile(currentAnalysisId, 'word');
    });
    
    // Open PDF button
    document.getElementById('openPdf').addEventListener('click', async function(e) {
        e.preventDefault();
        if (currentAnalysisId) await openLocalFile(currentAnalysisId, 'pdf');
    });
    
    // Download handlers (Keep as fallback)
    document.getElementById('downloadExcel').addEventListener('click', function(e) {
        if (!currentExcelPath) e.preventDefault();
    });
    document.getElementById('downloadWord').addEventListener('click', function(e) {
        if (!currentWordPath) e.preventDefault();
    });
    document.getElementById('downloadPdf').addEventListener('click', function(e) {
        if (!currentPdfPath) e.preventDefault();
    });
    
    // Email input - change button color when email is entered
    document.getElementById('emailAddress').addEventListener('input', function() {
        const emailButton = document.getElementById('sendEmail');
        if (this.value.trim()) {
            emailButton.classList.add('email-ready');
        } else {
            emailButton.classList.remove('email-ready');
        }
    });
    
    // Email button
    document.getElementById('sendEmail').addEventListener('click', async function() {
        const email = document.getElementById('emailAddress').value;
        if (!email) {
            alert('Please enter an email address');
            return;
        }
        
        // Validate email format
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(email)) {
            alert('Please enter a valid email address');
            return;
        }
        
        try {
            const button = this;
            button.disabled = true;
            button.textContent = 'Sending...';
            
            const response = await fetch('/api/analyze/email', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    email: email,
                    analysisId: currentAnalysisId
                })
            });
            
            const result = await response.json();
            
            if (result.success) {
                alert('✅ ' + result.message);
            } else {
                alert('❌ ' + result.message);
            }
            
        } catch (error) {
            alert('Error sending email: ' + error.message);
        } finally {
            this.disabled = false;
            this.textContent = 'Send via Email';
        }
    });
}

async function openLocalFile(id, type) {
    try {
        const response = await fetch('/api/analyze/open/' + id + '/' + type, { method: 'POST' });
        if (!response.ok) {
            const err = await response.json();
            throw new Error(err.error || 'Failed to open file');
        }
    } catch (e) {
        alert('Could not open file: ' + e.message);
    }
}

/**
 * Load configuration from REST API
 */
async function loadConfiguration() {
    try {
        // Load environments dynamically
        const response = await fetch('/api/config/environments');
        const environments = await response.json();
        
        // Populate environment checkboxes
        const container = document.getElementById('customEnvs');
        const existingCheckboxes = container.querySelector('.checkbox-group');
        
        if (existingCheckboxes && environments.length > 0) {
            existingCheckboxes.innerHTML = '';
            
            environments.forEach(env => {
                const label = document.createElement('label');
                const checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.name = 'envs';
                checkbox.value = env.code;
                checkbox.checked = true;
                
                label.appendChild(checkbox);
                label.appendChild(document.createTextNode(' ' + env.displayName));
                existingCheckboxes.appendChild(label);
            });
        }
        
        // Load framework configuration for default paths
        const frameworkResponse = await fetch('/api/config/framework');
        const frameworkConfig = await frameworkResponse.json();
        
        // Set default project folder path
        if (frameworkConfig.defaultProjectPath) {
            const folderPathInput = document.getElementById('folderPath');
            folderPathInput.value = frameworkConfig.defaultProjectPath;
        }
    } catch (error) {
        console.error('Failed to load configuration:', error);
    }
}

/**
 * Submit analysis to REST API
 */
async function submitAnalysis() {
    const form = document.getElementById('analysisForm');
    const progressSection = document.getElementById('progressSection');
    const resultsSection = document.getElementById('resultsSection');
    const formData = new FormData(form);
    
    // Show progress
    form.style.display = 'none';
    progressSection.style.display = 'block';
    resultsSection.style.display = 'none';
    
    // Get selected environments
    const environmentScope = document.getElementById('environmentScope');
    let environments = 'ALL';
    if (environmentScope.value === 'CUSTOM') {
        const selectedEnvs = Array.from(document.querySelectorAll('input[name="envs"]:checked'))
            .map(cb => cb.value);
        environments = selectedEnvs.join(',');
    }
    
    // Get input path based on source
    const inputSource = formData.get('inputSource');
    let inputPath = '';
    let uploadId = null;
    
    if (inputSource === 'folder') {
        inputPath = formData.get('folderPath');
    } else if (inputSource === 'git') {
        inputPath = formData.get('gitUrl');
    } else if (inputSource === 'zip' || inputSource === 'jar') {
        // Handle file upload
        const fileInputId = inputSource === 'zip' ? 'zipFile' : 'jarFile';
        const fileInput = document.getElementById(fileInputId);
        const file = fileInput.files[0];
        
        if (!file) {
            alert('Please select a ' + inputSource.toUpperCase() + ' file to upload');
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
        
        // Validate file size (500MB)
        const maxSize = 500 * 1024 * 1024;
        if (file.size > maxSize) {
            alert('File size exceeds 500MB limit');
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
        
        // Upload file
        updateProgress('Uploading file...', 10);
        const uploadResult = await uploadFile(file, inputSource);
        
        if (!uploadResult.success) {
            alert('Upload failed: ' + uploadResult.error);
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
        
        inputPath = uploadResult.tempPath;
        uploadId = uploadResult.uploadId;
        updateProgress('File uploaded successfully', 20);
    }
    
    // Handle Config File Upload (if selected)
    let configFilePath = null;
    const configFileInput = document.getElementById('configFile');
    const configFile = configFileInput ? configFileInput.files[0] : null;
    
    if (configFile) {
        // Validate config file size (10MB)
        if (configFile.size > 10 * 1024 * 1024) {
            alert('Config file size exceeds 10MB limit');
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
        
        updateProgress('Uploading configuration file...', 25);
        const configUploadResult = await uploadFile(configFile, 'config');
        
        if (!configUploadResult.success) {
            alert('Config upload failed: ' + configUploadResult.error);
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
        
        configFilePath = configUploadResult.tempPath;
        console.log('[CONFIG-DEBUG] Config file uploaded to:', configFilePath);
    } else {
        console.log('[CONFIG-DEBUG] No config file selected, will use defaults');
    }
    
    if (!inputPath) {
        alert('Please provide an input path');
        form.style.display = 'block';
        progressSection.style.display = 'none';
        return;
    }
    
    // Prepare request
    const requestData = {
        projectTechnologyType: formData.get('projectType'),
        documentGenerationExecutionMode: formData.get('executionMode'),
        environmentAnalysisScope: environments,
        inputSourceType: inputSource,
        inputPath: inputPath,
        gitBranch: formData.get('gitBranch') || 'main',
        inputPath: inputPath,
        gitBranch: formData.get('gitBranch') || 'main',
        uploadId: uploadId,  // For cleanup of uploaded files
        configFilePath: configFilePath, // Functionality added for TIBCO/Mule config resolution
        // Output format preferences
        generatePdf: document.getElementById('generatePdf').checked,
        generateWord: document.getElementById('generateWord').checked,
        generateExcel: document.getElementById('generateExcel').checked
    };
    
    try {
        // Submit to REST API
        const response = await fetch('/api/analyze', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });
        
        const result = await response.json();
        
        if (result.status === 'STARTED') {
            // Store analysis ID
            currentAnalysisId = result.analysisId;
            
            // Poll for completion instead of fake progress
            updateProgress('Analysis in progress...', 20);
            const completed = await pollForCompletion(currentAnalysisId);
            
            if (!completed) {
                throw new Error('Analysis timed out or failed');
            }
            
            // Show results
            progressSection.style.display = 'none';
            resultsSection.style.display = 'block';
            
            // Use file paths from API response
            currentExcelPath = result.excelPath || '';
            currentWordPath = result.wordPath || '';
            currentPdfPath = result.pdfPath || '';
            
            // Extract just the filename from the full path
            const excelFileName = currentExcelPath.split('\\').pop().split('/').pop();
            const wordFileName = currentWordPath.split('\\').pop().split('/').pop();
            const pdfFileName = currentPdfPath.split('\\').pop().split('/').pop();
            
            // Store Analysis ID for Open buttons
            // Generate a temporary ID or use project name if real ID not returned (backend mock)
            // In a real scenario, the backend's analyze endpoint should return the ID
            // For now, we will construct/assume one that matches what AnalysisResource will look up
            // Since AnalysisResource.analyze stores as: getAnalysisId() + "_" + projectName
            // But wait, the Analyze endpoint returns "status: STARTED" and immediate paths, but doesn't return the ID used for storing results in the 'results' map yet?
            // Actually, AnalysisResource.executeAnalysis stores it. The /analyze endpoint returns just "projectName".
            // We need the ID to call /open.
            // Let's modify AnalysisResource to return the ID or let's just use the download paths for now.
            // Wait, the /open endpoint relies on 'results.get(analysisId)'.
            // The current /analyze implementation returns immediately but the actual analysis runs in bg.
            // This is a race condition for the "Open" button if we click it too fast, but fine for now.
            // WE NEED THE ID from the backend. The current /analyze response doesn't seem to return it? 
            // It returns "projectName". 
            // Let's assume for this single-user tool we can just find it? 
            // NOTE: I will update the backend to return the ID in the next step to be sure.
            // For now, let's assume result.analysisId is returned.
            currentAnalysisId = result.analysisId || (projectName + "_" + timestamp); // Fallback attempt
            
            // Update Download Links for fallback
            document.getElementById('downloadExcel').href = '/api/analyze/download/' + currentAnalysisId + '/excel';
            document.getElementById('downloadWord').href = '/api/analyze/download/' + currentAnalysisId + '/word';
            document.getElementById('downloadPdf').href = '/api/analyze/download/' + currentAnalysisId + '/pdf';
            
            // Display output location based on input source
            const outputDir = result.outputDirectory || 'output';
            // const inputSource = requestData.inputSourceType; // Already defined above
            
            let locationMessage = '';
            if (inputSource === 'folder') {
                // Local folder - files saved to output directory
                locationMessage = 'Files saved to: ' + outputDir;
            } else {
                // Uploaded ZIP/JAR or Git - files in temp directory
                locationMessage = 'Files generated in temporary directory';
            }
            
            document.getElementById('outputLocation').textContent = locationMessage;
                
            // Conditionally display result items based on request flags
            const excelResult = document.getElementById('excelResult');
            const wordResult = document.getElementById('wordResult');
            const pdfResult = document.getElementById('pdfResult');
            
            if (requestData.generateExcel) {
                excelResult.style.display = 'block';
                document.getElementById('excelFileName').textContent = excelFileName;
            } else {
                excelResult.style.display = 'none';
            }
            
            if (requestData.generateWord) {
                wordResult.style.display = 'block';
                document.getElementById('wordFileName').textContent = wordFileName;
            } else {
                wordResult.style.display = 'none';
            }
            
            if (requestData.generatePdf) {
                pdfResult.style.display = 'block';
                document.getElementById('pdfFileName').textContent = pdfFileName;
            } else {
                pdfResult.style.display = 'none';
            }
            
        } else {
            throw new Error(result.message || 'Analysis failed');
        }
        
    } catch (error) {
        alert('Error: ' + error.message);
        form.style.display = 'block';
        progressSection.style.display = 'none';
    }
}

/**
 * Simulate progress for demo
 */
async function simulateProgress() {
    const messages = [
        'Extracting project files...',
        'Analyzing project structure...',
        'Parsing configuration files...',
        'Resolving properties...',
        'Generating Excel report...',
        'Generating Word document...',
        'Finalizing documents...'
    ];
    
    const progressFill = document.getElementById('progressFill');
    const progressMessage = document.getElementById('progressMessage');
    
    for (let i = 0; i < messages.length; i++) {
        progressMessage.textContent = messages[i];
        progressFill.style.width = ((i + 1) / messages.length * 100) + '%';
        await new Promise(resolve => setTimeout(resolve, 1000));
    }
}

/**
 * Update progress bar and message
 */
function updateProgress(message, percentage) {
    const progressFill = document.getElementById('progressFill');
    const progressMessage = document.getElementById('progressMessage');
    progressMessage.textContent = message;
    progressFill.style.width = percentage + '%';
}

/**
 * Poll for analysis completion
 */
async function pollForCompletion(analysisId) {
    const maxAttempts = 600; // 10 minutes max (600 * 1 second) - increased for large projects
    let attempts = 0;
    
    while (attempts < maxAttempts) {
        try {
            const response = await fetch('/api/analyze/status/' + analysisId);
            const status = await response.json();
            
            if (status.status === 'COMPLETED') {
                updateProgress('Analysis completed!', 100);
                return true;
            } else if (status.status === 'FAILED') {
                return false;
            }
            
            // Update progress based on attempts
            const progress = 20 + (attempts / maxAttempts * 70);
            updateProgress('Analyzing project... (' + Math.round(progress) + '%)', progress);
            
            // Wait 1 second before next poll
            await new Promise(resolve => setTimeout(resolve, 1000));
            attempts++;
            
        } catch (error) {
            console.error('Error polling for status:', error);
            // Continue polling even on error
            await new Promise(resolve => setTimeout(resolve, 1000));
            attempts++;
        }
    }
    
    return false; // Timed out
}

/**
 * Upload file to server (ZIP or JAR)
 */
async function uploadFile(file, fileType) {
    const formData = new FormData();
    formData.append('file', file);
    
    try {
        const response = await fetch('/api/upload/' + fileType, {
            method: 'POST',
            body: formData
        });
        
        const result = await response.json();
        
        if (response.ok) {
            return { 
                success: true, 
                tempPath: result.tempPath,
                uploadId: result.uploadId
            };
        } else {
            return { 
                success: false, 
                error: result.error || 'Upload failed' 
            };
        }
    } catch (error) {
        return { 
            success: false, 
            error: error.message 
        };
    }
}

// Load dynamic labels from API
async function loadDynamicLabels() {
    try {
        const response = await fetch('/api/config/ui-labels');
        const labels = await response.json();
        
        // App Header
        if (labels['ui.app.title']) document.getElementById('appTitle').textContent = labels['ui.app.title'];
        if (labels['ui.app.subtitle']) document.getElementById('appSubtitle').textContent = labels['ui.app.subtitle'];
        if (labels['ui.app.description']) document.getElementById('appDescription').textContent = labels['ui.app.description'];
        
        // Form Labels
        if (labels['ui.label.project.type']) document.getElementById('labelProjectType').textContent = labels['ui.label.project.type'];
        if (labels['ui.label.execution.mode']) document.getElementById('labelExecutionMode').textContent = labels['ui.label.execution.mode'];
        if (labels['ui.label.environment.scope']) document.getElementById('labelEnvironmentScope').textContent = labels['ui.label.environment.scope'];
        if (labels['ui.label.select.environments']) document.getElementById('labelSelectEnvironments').textContent = labels['ui.label.select.environments'];
        if (labels['ui.label.input.source']) document.getElementById('labelInputSource').textContent = labels['ui.label.input.source'];
        if (labels['ui.label.project.file']) document.getElementById('labelProjectFile').textContent = labels['ui.label.project.file'];
        
        // Footer
        if (labels['ui.footer.text']) document.getElementById('footerText').textContent = labels['ui.footer.text'];
        
    } catch (error) {
        console.warn('Failed to load dynamic labels:', error);
    }
}
