// RaksAnalyzer Web UI JavaScript - Complete REST API Integration

// API Base Configuration
const API_BASE = '/apiguard/raksanalyzer';

document.addEventListener('DOMContentLoaded', function() {
    setupEventHandlers();
    loadConfiguration();
    loadDynamicLabels();
    initConfiguration();
    handleUrlParameters(); // Handle ?type=mule or ?type=tibco from portal tiles
});

let currentExcelPath = null;
let currentWordPath = null;
let currentPdfPath = null;
let currentAnalysisId = null;

async function initConfiguration() {
    try {
        const response = await fetch('api/config');
        if (response.ok) {
            const config = await response.json();
            
            // Apply Local Input Configuration - disable instead of hide
            if (config.localInputEnabled === false) {
                const localOption = document.querySelector('input[name="inputSource"][value="folder"]');
                const gitOption = document.querySelector('input[name="inputSource"][value="git"]');
                
                // Disable local folder option
                if (localOption) {
                    localOption.disabled = true;
                    const localLabel = localOption.parentElement;
                    localLabel.style.opacity = '0.5';
                    localLabel.style.cursor = 'not-allowed';
                    localLabel.title = 'Local folder input is only available in standalone mode';
                }
                
                // Disable git option
                if (gitOption) {
                    gitOption.disabled = true;
                    const gitLabel = gitOption.parentElement;
                    gitLabel.style.opacity = '0.5';
                    gitLabel.style.cursor = 'not-allowed';
                    gitLabel.title = 'Git repository input is only available in standalone mode';
                }
                
                // If local was checked, switch to ZIP
                if (localOption && localOption.checked) {
                    localOption.checked = false;
                    const zipOption = document.querySelector('input[name="inputSource"][value="zip"]');
                    if (zipOption) {
                        zipOption.checked = true;
                        zipOption.dispatchEvent(new Event('change'));
                    }
                }
                
                // If git was checked, switch to ZIP
                if (gitOption && gitOption.checked) {
                    gitOption.checked = false;
                    const zipOption = document.querySelector('input[name="inputSource"][value="zip"]');
                    if (zipOption) {
                        zipOption.checked = true;
                        zipOption.dispatchEvent(new Event('change'));
                    }
                }
            }
        }
    } catch (error) {
        console.warn('Failed to load dynamic configuration:', error);
    }
}

/**
 * Handle URL parameters to pre-select technology type
 * Supports ?type=mule or ?type=tibco from portal tiles
 */
function handleUrlParameters() {
    const urlParams = new URLSearchParams(window.location.search);
    const typeParam = urlParams.get('type');
    
    console.log('[URL-PARAM] Type parameter:', typeParam);
    
    if (typeParam) {
        const projectTypeSelect = document.getElementById('projectType');
        
        if (typeParam.toLowerCase() === 'mule') {
            console.log('[URL-PARAM] Setting to MULE');
            projectTypeSelect.value = 'MULE';
        } else if (typeParam.toLowerCase() === 'tibco') {
            console.log('[URL-PARAM] Setting to TIBCO_BW5');
            projectTypeSelect.value = 'TIBCO_BW5';
        }
        
        console.log('[URL-PARAM] Final value:', projectTypeSelect.value);
    }
}


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
    
    // Project type validation for disabled options
    const projectTypeSelect = document.getElementById('projectType');
    projectTypeSelect.addEventListener('change', function() {
        const selectedOption = this.options[this.selectedIndex];
        if (selectedOption.disabled) {
            alert('🚧 ' + selectedOption.text + ' is not enabled yet.\\n\\nPlease select Mule 4.x Application or Tibco BusinessWorks 5.x.');
            // Reset to Mule
            this.value = 'MULE';
        }
    });
    
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
                await fetch(API_BASE + '/api/analyze/cleanup/' + currentAnalysisId, { method: 'POST' });
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
            
            const response = await fetch(API_BASE + '/api/analyze/send-email', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    email: email,
                    excelPath: currentExcelPath,
                    wordPath: currentWordPath,
                    pdfPath: currentPdfPath,
                    projectName: (() => {
                        const path = currentExcelPath || currentWordPath || currentPdfPath;
                        if (path) {
                            const filename = path.replace(/\\/g, '/').split('/').pop();
                            
                            // Debug: Show what filename is being processed
                            // alert('DEBUG: Filename for Project Extraction: ' + filename);
                            
                            // Extract project name: Take everything before the first underscore
                            // Standard format: ProjectName_Type_... or ProjectName_Analysis_...
                            const parts = filename.split('_');
                            if (parts[0]) {
                                return parts[0];
                            }
                            
                            // Fallback: If no _Analysis pattern, return filename without extension
                            // But avoid returning empty string
                            const simpleName = filename.split('.')[0];
                            if (simpleName) return simpleName;
                        }
                        return 'Analysis_' + new Date().toISOString().split('T')[0];
                    })()
                })
            });
            
            const result = await response.json();
            
            if (result.status === 'success') {
                alert('Email sent successfully!');
            } else if (result.status === 'disabled') {
                alert('Email functionality is currently disabled');
            } else {
                alert('Failed to send email: ' + (result.message || 'Unknown error'));
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
    // For folder type, show message that it's not supported
    if (type === 'folder') {
        alert('Open Folder is only available when analyzing local directories. For uploaded files, use the Download links.');
        return;
    }
    
    // Use download endpoint with inline=true to open in browser
    const downloadUrl = API_BASE + '/api/analyze/' + 
        (type === 'excel' ? currentExcelPath : 
         type === 'word' ? currentWordPath : 
         currentPdfPath) + '?inline=true';
    
    if (downloadUrl && !downloadUrl.includes('null')) {
        window.open(downloadUrl, '_blank');
    } else {
        alert('File path not available');
    }
}

/**
 * Load configuration from REST API
 */
async function loadConfiguration() {
    try {
        // Load environments dynamically
        const response = await fetch(API_BASE + '/api/config/environments');
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
        const frameworkResponse = await fetch(API_BASE + '/api/config/framework');
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
        
        // Clear previous results
        currentAnalysisId = null;
        currentExcelPath = '';
        currentWordPath = '';
        currentPdfPath = '';
        document.getElementById('excelResult').style.display = 'none';
        document.getElementById('wordResult').style.display = 'none';
        document.getElementById('pdfResult').style.display = 'none';
        
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
        const response = await fetch(API_BASE + '/api/analyze', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });
        
        const result = await response.json();
        
        if (result.status === 'success' || result.status === 'STARTED') {
            // Store analysis ID
            currentAnalysisId = result.analysisId || result.uploadId;
            
            // Check for immediate success (synchronous mode)
            if (result.status === 'success') {
                updateProgress('Analysis completed successfully!', 100);
                
                // Show results
                progressSection.style.display = 'none';
                resultsSection.style.display = 'block';
                
                // Use file paths from API response
                // Backend returns them with generic names but we might want just the filename
                // Store paths from response (these are already in download/filename format)
                currentExcelPath = result.excelPath || '';
                currentWordPath = result.wordPath || '';
                currentPdfPath = result.pdfPath || '';
                
                // Extract just the filename for display
                const getFileName = (path) => path ? path.replace('download/', '') : '';
                
                const excelFileName = getFileName(currentExcelPath);
                const wordFileName = getFileName(currentWordPath);
                const pdfFileName = getFileName(currentPdfPath);
                
                // Update text
                document.getElementById('excelFileName').textContent = excelFileName;
                document.getElementById('wordFileName').textContent = wordFileName;
                document.getElementById('pdfFileName').textContent = pdfFileName;
                
                // Set download link hrefs for direct downloads (paths are already in correct format)
                if (currentExcelPath) {
                    document.getElementById('downloadExcel').href = API_BASE + '/api/analyze/' + currentExcelPath;
                }
                if (currentWordPath) {
                    document.getElementById('downloadWord').href = API_BASE + '/api/analyze/' + currentWordPath;
                }
                if (currentPdfPath) {
                    document.getElementById('downloadPdf').href = API_BASE + '/api/analyze/' + currentPdfPath;
                }
                
                // Display output location based on input source
                const outputDir = result.reportsPath || 'output';
                
                let locationMessage = '';
                const openFolderBtn = document.getElementById('openFolder');
                
                if (inputSource === 'folder') {
                    locationMessage = 'Files saved to: ' + outputDir;
                    // Enable Open Folder button for local folder analysis
                    openFolderBtn.style.display = 'inline-block';
                    openFolderBtn.disabled = false;
                } else {
                    locationMessage = 'Files generated in temporary directory: ' + outputDir;
                    // Hide Open Folder button for ZIP/JAR/Git uploads (not supported in web/CloudHub)
                    openFolderBtn.style.display = 'none';
                }
                document.getElementById('outputLocation').textContent = locationMessage;
                
                // Visibility
                document.getElementById('excelResult').style.display = requestData.generateExcel ? 'block' : 'none';
                document.getElementById('wordResult').style.display = requestData.generateWord ? 'block' : 'none';
                document.getElementById('pdfResult').style.display = requestData.generatePdf ? 'block' : 'none';
                
            } else {
                // Poll for completion (ASYNC mode)
                updateProgress('Analysis in progress...', 20);
                const completed = await pollForCompletion(currentAnalysisId);
                
                if (!completed) {
                    throw new Error('Analysis timed out or failed');
                }
                
                // For async, we would need another call to get the results details if pollForCompletion doesn't return them
                // But currently the poll logic just checks status.
                // Ideally, the 'COMPLETED' status response should include the paths.
                // Since we are fixing the sync issue first, we will assume sync for now.
                // If async is needed later, we'd update pollForCompletion to return the final payload.
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
            const response = await fetch(API_BASE + '/api/analyze/status/' + analysisId);
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
        const response = await fetch(API_BASE + '/api/upload/' + fileType, {
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
        const response = await fetch(API_BASE + '/api/config/ui-labels');
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
