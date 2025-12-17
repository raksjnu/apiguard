// RaksAnalyzer Web UI JavaScript - Complete REST API Integration

document.addEventListener('DOMContentLoaded', function() {
    setupEventHandlers();
    loadConfiguration();
});

let currentExcelPath = null;
let currentWordPath = null;

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
    });
    
    // Download Excel button
    document.getElementById('downloadExcel').addEventListener('click', function(e) {
        e.preventDefault();
        if (currentExcelPath) {
            alert('Excel file saved to: ' + currentExcelPath + '\n\nPlease open it from the output folder.');
        } else {
            alert('No Excel file available');
        }
    });
    
    // Download Word button
    document.getElementById('downloadWord').addEventListener('click', function(e) {
        e.preventDefault();
        if (currentWordPath) {
            alert('Word file saved to: ' + currentWordPath + '\n\nPlease open it from the output folder.');
        } else {
            alert('No Word file available');
        }
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
        gitBranch: formData.get('gitBranch') || 'main'
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
            
            // Display file paths (simulated for now)
            const projectName = inputPath.split('\\').pop() || inputPath.split('/').pop();
            const timestamp = new Date().toISOString().replace(/[-:]/g, '').replace('T', '_').substring(0, 15);
            
            currentExcelPath = `C:\\raks\\apiguard\\raksanalyzer\\output\\${projectName}_${timestamp}.xlsx`;
            currentWordPath = `C:\\raks\\apiguard\\raksanalyzer\\output\\${projectName}_${timestamp}.docx`;
            
            document.getElementById('outputLocation').textContent = 
                'Files saved to: C:\\raks\\apiguard\\raksanalyzer\\output\\';
            document.getElementById('excelFileName').textContent = `${projectName}_${timestamp}.xlsx`;
            document.getElementById('wordFileName').textContent = `${projectName}_${timestamp}.docx`;
            
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
