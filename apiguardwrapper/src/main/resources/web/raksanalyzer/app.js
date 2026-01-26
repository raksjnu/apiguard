// RaksAnalyzer Web UI JavaScript - Complete REST API Integration

// API Base Configuration
const API_BASE = '/apiguard/raksanalyzer';

document.addEventListener('DOMContentLoaded', function() {
    setupEventHandlers();
    setupGitHandlers(); // New Git connection logic
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
                
                // Disable local folder option
                if (localOption) {
                    localOption.disabled = true;
                    const localLabel = localOption.parentElement;
                    localLabel.style.opacity = '0.5';
                    localLabel.style.cursor = 'not-allowed';
                    localLabel.title = 'Local folder input is only available in standalone mode';
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
            alert('🚧 ' + selectedOption.text + ' is not enabled yet.\n\nPlease select Mule 4.x Application or Tibco BusinessWorks 5.x.');
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
    
    // Restore input source from localStorage
    const savedSource = localStorage.getItem('raks_input_source');
    if (savedSource) {
        const targetRadio = document.querySelector(`input[name="inputSource"][value="${savedSource}"]`);
        if (targetRadio) targetRadio.checked = true;
    }

    // Centralized visibility logic for input sources
    function updateInputSourceUI() {
        const selectedRadio = document.querySelector('input[name="inputSource"]:checked');
        const selectedSource = selectedRadio ? selectedRadio.value : 'folder';
        
        // Save to localStorage
        localStorage.setItem('raks_input_source', selectedSource);

        const zipSection = document.getElementById('zipSection');
        const jarSection = document.getElementById('jarSection');
        const folderSection = document.getElementById('folderSection');
        const gitSection = document.getElementById('gitSection');
        
        // Hide all first
        if (zipSection) zipSection.style.display = 'none';
        if (jarSection) jarSection.style.display = 'none';
        if (folderSection) folderSection.style.display = 'none';
        if (gitSection) gitSection.style.display = 'none';
        
        // Show only active
        if (selectedSource === 'zip') {
            if (zipSection) zipSection.style.display = 'block';
        } else if (selectedSource === 'jar') {
            if (jarSection) jarSection.style.display = 'block';
        } else if (selectedSource === 'folder') {
            if (folderSection) folderSection.style.display = 'block';
        } else if (selectedSource === 'git') {
            if (gitSection) gitSection.style.display = 'block';
        }
    }

    // Toggle input source (zip vs jar vs folder vs git)
    inputSourceRadios.forEach(radio => {
        radio.addEventListener('change', updateInputSourceUI);
    });
    
    // Initial call to set correct visibility
    updateInputSourceUI();
    
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

        // Clear dynamically populated Git dropdowns
        const gitRepoSelect = document.getElementById('gitRepo');
        const gitBranchSelect = document.getElementById('gitBranch');
        if (gitRepoSelect) gitRepoSelect.innerHTML = '<option value="">-- Click Fetch to see Repos --</option>';
        if (gitBranchSelect) gitBranchSelect.innerHTML = '<option value="main">main</option>';
        
        // Reset Git status UI
        const statusPulse = document.getElementById('connectionStatus');
        const statusMsg = document.getElementById('statusMessage');
        if (statusPulse) statusPulse.className = 'status-pulse';
        if (statusMsg) {
            statusMsg.textContent = 'Disconnected';
            statusMsg.style.color = '#888';
        }

        // Trigger UI update after reset to hide Git section if default is Folder
        updateInputSourceUI();

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
                            const parts = filename.split('_');
                            if (parts[0]) {
                                return parts[0];
                            }
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

/**
 * Git Connection and Discovery Logic
 */
function setupGitHandlers() {
    const providerSelect = document.getElementById('gitProviderSelect');
    const tokenInput = document.getElementById('gitTokenInput');
    const connectionCard = document.getElementById('gitConnectionCard');
    const validateBtn = document.getElementById('validateTokenBtn');
    const statusPulse = document.getElementById('connectionStatus');
    const statusMsg = document.getElementById('statusMessage');
    
    const discoverReposBtn = document.getElementById('discoverReposBtn');
    const refreshBranchesBtn = document.getElementById('refreshBranchesBtn');
    const gitRepoSelect = document.getElementById('gitRepo');
    const gitBranchSelect = document.getElementById('gitBranch');
    const gitGroupInput = document.getElementById('gitGroup');

    const KEYS = {
        'gitlab': 'raks_gitlab_token',
        'github': 'raks_github_token'
    };

    // 1. Initial Load
    const savedProvider = localStorage.getItem('raks_git_provider') || 'gitlab';
    if (providerSelect) providerSelect.value = savedProvider;
    updateGitTheme(savedProvider);
    
    // Restore group and filter
    if (gitGroupInput) {
        gitGroupInput.value = localStorage.getItem('raks_git_group') || '';
        gitGroupInput.addEventListener('input', (e) => localStorage.setItem('raks_git_group', e.target.value));
    }
    const filterInput = document.getElementById('gitFilter');
    if (filterInput) {
        filterInput.value = localStorage.getItem('raks_git_filter') || '';
        filterInput.addEventListener('input', (e) => localStorage.setItem('raks_git_filter', e.target.value));
    }

    if (tokenInput) {
        tokenInput.value = localStorage.getItem(KEYS[savedProvider]) || '';
        if (tokenInput.value) {
            validateGitToken(false); // Validate silently on load
        }
    }

    // 2. Event Listeners
    if (providerSelect) {
        providerSelect.addEventListener('change', (e) => {
            const newProvider = e.target.value;
            localStorage.setItem('raks_git_provider', newProvider);
            updateGitTheme(newProvider);
            tokenInput.value = localStorage.getItem(KEYS[newProvider]) || '';
            setGitStatus('disconnected');
        });
    }

    if (tokenInput) {
        tokenInput.addEventListener('input', (e) => {
            const currentProvider = providerSelect.value;
            localStorage.setItem(KEYS[currentProvider], e.target.value);
            setGitStatus('disconnected');
        });
    }

    if (validateBtn) {
        validateBtn.addEventListener('click', () => validateGitToken(true));
    }

    if (discoverReposBtn) {
        discoverReposBtn.addEventListener('click', discoverRepositories);
    }

    if (refreshBranchesBtn) {
        refreshBranchesBtn.addEventListener('click', refreshBranches);
    }

    if (gitRepoSelect) {
        gitRepoSelect.addEventListener('change', refreshBranches);
    }

    // Helper functions
    function updateGitTheme(provider) {
        if (connectionCard) {
            connectionCard.classList.remove('github-theme', 'gitlab-theme');
            connectionCard.classList.add(provider + '-theme');
        }
    }

    function setGitStatus(status) {
        if (!statusPulse) return;
        statusPulse.className = 'status-pulse';
        if (status === 'loading') {
            statusPulse.classList.add('loading');
            statusMsg.innerText = 'Validating...';
            statusMsg.style.color = '#ffc107';
        } else if (status === 'connected') {
            statusPulse.classList.add('active');
            statusMsg.innerText = 'Connected';
            statusMsg.style.color = '#28a745';
        } else {
            statusMsg.innerText = 'Disconnected';
            statusMsg.style.color = '#888';
        }
    }

    async function validateGitToken(showAlert) {
        const token = tokenInput.value;
        const provider = providerSelect.value;
        if (!token) {
            if (showAlert) alert('Please enter a token');
            return;
        }

        setGitStatus('loading');
        try {
            const response = await fetch(API_BASE + '/api/git/validate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token, provider })
            });
            const data = await response.json();
            if (data.status === 'success') {
                setGitStatus('connected');
                if (showAlert) alert('✅ Credentials validated and saved!');
            } else {
                setGitStatus('disconnected');
                if (showAlert) alert('❌ Validation failed: ' + data.message);
            }
        } catch (err) {
            setGitStatus('disconnected');
            if (showAlert) alert('Connection Error: ' + err.message);
        }
    }

    async function discoverRepositories() {
        const token = tokenInput.value;
        const provider = providerSelect.value;
        const group = gitGroupInput.value;
        const filter = document.getElementById('gitFilter').value.toLowerCase();
        
        if (!token) {
            alert('Please enter and validate your token first');
            return;
        }

        discoverReposBtn.disabled = true;
        discoverReposBtn.innerText = 'Fetching...';
        gitRepoSelect.innerHTML = '<option value="">Loading repositories...</option>';

        try {
            const url = API_BASE + `/api/git/repos?provider=${provider}&token=${token}&group=${encodeURIComponent(group)}`;
            const response = await fetch(url);
            if (!response.ok) throw new Error('Failed to fetch repositories');
            
            let repos = await response.json();
            
            // Client-side filtering
            if (filter) {
                repos = repos.filter(repo => repo.name.toLowerCase().includes(filter));
            }

            gitRepoSelect.innerHTML = '';
            
            if (repos.length === 0) {
                gitRepoSelect.innerHTML = '<option value="">No repositories found matching filter</option>';
            } else {
                repos.forEach(repo => {
                    const option = document.createElement('option');
                    option.value = repo.cloneUrl || repo.name; 
                    option.textContent = repo.name;
                    gitRepoSelect.appendChild(option);
                });
                
                updateGitUrl();
                refreshBranches(); 
            }
        } catch (err) {
            alert('Error fetching repos: ' + err.message);
            gitRepoSelect.innerHTML = '<option value="">Error loading repos</option>';
        } finally {
            discoverReposBtn.disabled = false;
            discoverReposBtn.innerText = 'Fetch';
        }
    }

    function updateGitUrl() {
        const repo = gitRepoSelect.value;
        if (repo) {
            document.getElementById('gitUrl').value = repo;
        }
    }

    async function refreshBranches() {
        const repo = gitRepoSelect.value;
        if (!repo) return;

        const token = tokenInput.value;
        const provider = providerSelect.value;
        
        refreshBranchesBtn.disabled = true;
        gitBranchSelect.innerHTML = '<option value="">Loading...</option>';

        try {
            const url = API_BASE + `/api/git/branches?provider=${provider}&token=${token}&repo=${encodeURIComponent(repo)}`;
            const response = await fetch(url);
            if (!response.ok) throw new Error('Failed to fetch branches');
            
            const branches = await response.json();
            gitBranchSelect.innerHTML = '';
            
            branches.forEach(branch => {
                const option = document.createElement('option');
                option.value = branch;
                option.textContent = branch;
                if (branch === 'main' || branch === 'master') option.selected = true;
                gitBranchSelect.appendChild(option);
            });
        } catch (err) {
            console.warn('Error fetching branches:', err);
            gitBranchSelect.innerHTML = '<option value="main">main (fallback)</option>';
        } finally {
            refreshBranchesBtn.disabled = false;
        }
    }
}

async function openLocalFile(id, type) {
    if (type === 'folder') {
        alert('Open Folder is only available when analyzing local directories. For uploaded files, use the Download links.');
        return;
    }
    
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
        const response = await fetch(API_BASE + '/api/config/environments');
        const environments = await response.json();
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
        
        const frameworkResponse = await fetch(API_BASE + '/api/config/framework');
        const frameworkConfig = await frameworkResponse.json();
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
    
    form.style.display = 'none';
    progressSection.style.display = 'block';
    resultsSection.style.display = 'none';
    
    const environmentScope = document.getElementById('environmentScope');
    let environments = 'ALL';
    if (environmentScope.value === 'CUSTOM') {
        const selectedEnvs = Array.from(document.querySelectorAll('input[name="envs"]:checked'))
            .map(cb => cb.value);
        environments = selectedEnvs.join(',');
    }
    
    const inputSource = formData.get('inputSource');
    let inputPath = '';
    let uploadId = null;
    let gitToken = null;
    
    if (inputSource === 'folder') {
        inputPath = formData.get('folderPath');
    } else if (inputSource === 'git') {
        inputPath = document.getElementById('gitRepo').value;
        gitToken = document.getElementById('gitTokenInput').value;
        if (!inputPath) {
            alert('Please select a repository first');
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
    } else if (inputSource === 'zip' || inputSource === 'jar') {
        const fileInputId = inputSource === 'zip' ? 'zipFile' : 'jarFile';
        const fileInput = document.getElementById(fileInputId);
        const file = fileInput.files[0];
        
        if (!file) {
            alert('Please select a ' + inputSource.toUpperCase() + ' file to upload');
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
        
        const maxSize = 500 * 1024 * 1024;
        if (file.size > maxSize) {
            alert('File size exceeds 500MB limit');
            form.style.display = 'block';
            progressSection.style.display = 'none';
            return;
        }
        
        currentAnalysisId = null;
        currentExcelPath = '';
        currentWordPath = '';
        currentPdfPath = '';
        document.getElementById('excelResult').style.display = 'none';
        document.getElementById('wordResult').style.display = 'none';
        document.getElementById('pdfResult').style.display = 'none';
        
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
    
    let configFilePath = null;
    const configFileInput = document.getElementById('configFile');
    const configFile = configFileInput ? configFileInput.files[0] : null;
    if (configFile) {
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
    }
    
    if (!inputPath) {
        alert('Please provide an input path');
        form.style.display = 'block';
        progressSection.style.display = 'none';
        return;
    }
    
    const requestData = {
        projectType: formData.get('projectType'),
        documentGenerationExecutionMode: formData.get('executionMode'),
        environmentAnalysisScope: environments,
        inputSourceType: inputSource,
        inputPath: inputPath,
        gitToken: gitToken,
        gitBranch: inputSource === 'git' ? document.getElementById('gitBranch').value : (formData.get('gitBranch') || 'main'),
        uploadId: uploadId,
        configFilePath: configFilePath,
        generatePdf: document.getElementById('generatePdf').checked,
        generateWord: document.getElementById('generateWord').checked,
        generateExcel: document.getElementById('generateExcel').checked
    };
    
    try {
        const response = await fetch(API_BASE + '/api/analyze', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestData)
        });
        let result = await response.json();
        
        if (result.status === 'success' || result.status === 'STARTED') {
            currentAnalysisId = result.analysisId || result.uploadId;
            if (result.status === 'STARTED') {
                updateProgress('Analysis in progress...', 20);
                const finalResult = await pollForCompletion(currentAnalysisId);
                if (finalResult && finalResult.status === 'COMPLETED') {
                    result = finalResult;
                    result.status = 'success'; 
                } else {
                    throw new Error(finalResult || 'Analysis timed out');
                }
            }

            if (result.status === 'success') {
                updateProgress('Analysis completed successfully!', 100);
                progressSection.style.display = 'none';
                resultsSection.style.display = 'block';
                
                currentExcelPath = result.excelPath || '';
                currentWordPath = result.wordPath || '';
                currentPdfPath = result.pdfPath || '';
                
                const getFileName = (path) => path ? path.replace('download/', '') : '';
                document.getElementById('excelFileName').textContent = getFileName(currentExcelPath);
                document.getElementById('wordFileName').textContent = getFileName(currentWordPath);
                document.getElementById('pdfFileName').textContent = getFileName(currentPdfPath);
                
                if (currentExcelPath) document.getElementById('downloadExcel').href = API_BASE + '/api/analyze/' + currentExcelPath;
                if (currentWordPath) document.getElementById('downloadWord').href = API_BASE + '/api/analyze/' + currentWordPath;
                if (currentPdfPath) document.getElementById('downloadPdf').href = API_BASE + '/api/analyze/' + currentPdfPath;
                
                const outputDir = result.reportsPath || 'output';
                const openFolderBtn = document.getElementById('openFolder');
                if (inputSource === 'folder') {
                    document.getElementById('outputLocation').textContent = 'Files saved to: ' + outputDir;
                    openFolderBtn.style.display = 'inline-block';
                    openFolderBtn.disabled = false;
                } else {
                    document.getElementById('outputLocation').textContent = 'Files generated in temporary directory';
                    openFolderBtn.style.display = 'none';
                }
                
                document.getElementById('excelResult').style.display = requestData.generateExcel ? 'block' : 'none';
                document.getElementById('wordResult').style.display = requestData.generateWord ? 'block' : 'none';
                document.getElementById('pdfResult').style.display = requestData.generatePdf ? 'block' : 'none';
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

function updateProgress(message, percentage) {
    const progressFill = document.getElementById('progressFill');
    const progressMessage = document.getElementById('progressMessage');
    progressMessage.textContent = message;
    progressFill.style.width = percentage + '%';
}

async function pollForCompletion(analysisId) {
    const maxAttempts = 600;
    let attempts = 0;
    while (attempts < maxAttempts) {
        try {
            const response = await fetch(API_BASE + '/api/analyze/status/' + analysisId);
            const status = await response.json();
            if (status.status === 'COMPLETED') {
                updateProgress('Analysis completed!', 100);
                return status;
            } else if (status.status === 'FAILED') {
                const errorMsg = status.errorMessage || 'Analysis failed';
                updateProgress('Error: ' + errorMsg, 0);
                return errorMsg;
            }
            if (status.progress > 0) {
                 updateProgress(status.message || 'Analyzing...', status.progress);
            } else {
                 const simProgress = 20 + (attempts / maxAttempts * 70);
                 updateProgress('Analyzing project... (' + Math.round(simProgress) + '%)', simProgress);
            }
            await new Promise(resolve => setTimeout(resolve, 1000));
            attempts++;
        } catch (error) {
            console.error('Error polling for status:', error);
            await new Promise(resolve => setTimeout(resolve, 1000));
            attempts++;
        }
    }
    return false;
}

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
            return { success: true, tempPath: result.tempPath, uploadId: result.uploadId };
        } else {
            return { success: false, error: result.error || 'Upload failed' };
        }
    } catch (error) {
        return { success: false, error: error.message };
    }
}

async function loadDynamicLabels() {
    try {
        const response = await fetch(API_BASE + '/api/config/ui-labels');
        const labels = await response.json();
        if (labels['ui.app.title']) document.getElementById('appTitle').textContent = labels['ui.app.title'];
        if (labels['ui.app.subtitle']) document.getElementById('appSubtitle').textContent = labels['ui.app.subtitle'];
        if (labels['ui.app.description']) document.getElementById('appDescription').textContent = labels['ui.app.description'];
        if (labels['ui.label.project.type']) document.getElementById('labelProjectType').textContent = labels['ui.label.project.type'];
        if (labels['ui.label.execution.mode']) document.getElementById('labelExecutionMode').textContent = labels['ui.label.execution.mode'];
        if (labels['ui.label.environment.scope']) document.getElementById('labelEnvironmentScope').textContent = labels['ui.label.environment.scope'];
        if (labels['ui.label.select.environments']) document.getElementById('labelSelectEnvironments').textContent = labels['ui.label.select.environments'];
        if (labels['ui.label.input.source']) document.getElementById('labelInputSource').textContent = labels['ui.label.input.source'];
        if (labels['ui.label.project.file']) document.getElementById('labelProjectFile').textContent = labels['ui.label.project.file'];
        if (labels['ui.footer.text']) document.getElementById('footerText').textContent = labels['ui.footer.text'];
    } catch (error) {
        console.warn('Failed to load dynamic labels:', error);
    }
}
