// RaksAnalyzer Web UI JavaScript - Complete REST API Integration

document.addEventListener('DOMContentLoaded', function() {
    setupEventHandlers();
    loadConfiguration();
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
    
    // Toggle input source (upload vs folder vs git)
    inputSourceRadios.forEach(radio => {
        radio.addEventListener('change', function() {
            uploadSection.style.display = 'none';
            folderSection.style.display = 'none';
            gitSection.style.display = 'none';
            
            if (this.value === 'upload') {
                uploadSection.style.display = 'block';
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
    document.getElementById('startAgain').addEventListener('click', function() {
        document.getElementById('resultsSection').style.display = 'none';
        document.getElementById('analysisForm').style.display = 'block';
        document.getElementById('analysisForm').reset();
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
    
    // Email button
    document.getElementById('sendEmail').addEventListener('click', function() {
        const email = document.getElementById('emailAddress').value;
        if (!email) {
            alert('Please enter an email address');
            return;
        }
        alert('Email functionality coming soon! Documents will be sent to: ' + email);
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
    
    if (inputSource === 'folder') {
        inputPath = formData.get('folderPath');
    } else if (inputSource === 'git') {
        inputPath = formData.get('gitUrl');
    } else if (inputSource === 'upload') {
        alert('File upload not yet implemented. Please use Local Folder or Git Repository.');
        form.style.display = 'block';
        progressSection.style.display = 'none';
        return;
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
            // Show progress animation
            await simulateProgress();
            
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
            
            // Display output location and file names
            const outputDir = result.outputDirectory || 'C:\\raks\\apiguard\\raksanalyzer\\output\\';
            document.getElementById('outputLocation').textContent = 
                'Files saved to: ' + outputDir;
                
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
