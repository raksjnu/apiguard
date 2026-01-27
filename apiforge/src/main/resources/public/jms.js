/**
 * JMS Module for API Forge
 * Handles JMS specific logic, UI interactions, and API calls.
 */

const JmsModule = (() => {
    
    // -- State --
    let isConnected = false;
    let currentProvider = '';
    let isInitialized = false;
    const seenMessageIds = new Set();

    // -- Initialization --
    const init = () => {
        if (isInitialized) return;
        console.log('[JMS] Initializing Module...');
        restoreFields();
        bindEvents();
        checkConnection();
        isInitialized = true;
    };

    const checkConnection = async () => {
        try {
            const resp = await fetch('api/jms/status');
            if (resp.ok) {
                const status = await resp.json();
                if (status.connected) {
                    isConnected = true;
                    const profile = document.getElementById('jmsActiveProfile');
                    if (profile) {
                        profile.innerText = `✅ Connected: ${status.provider} (${status.url})`;
                        profile.style.color = 'var(--success-color)';
                    }

                    // Auto-re-attach to listener if active on backend
                    if (status.isListening) {
                        const opMode = document.getElementById('jmsOpMode');
                        if (opMode) {
                            opMode.value = 'LISTEN';
                            syncJmsFields();
                            
                            // Load existing buffer on re-attach
                            const statsResp = await fetch('api/jms/listen/stats');
                            const stats = await statsResp.json();
                            if (stats.buffer && stats.buffer.length > 0) {
                                renderJmsResults(stats.buffer, `Resumed Session (${status.activeDestination})`);
                            }
                        }

                        const btn = document.getElementById('compareBtn');
                        if (btn) {
                            btn.innerText = '🛑 Stop Listeners';
                            btn.style.backgroundColor = '#e53e3e';
                            // Removed manual onclick assignment to avoid double-triggering with app.js submit handler
                            if (listenerInterval) clearInterval(listenerInterval);
                            listenerInterval = setInterval(pollStats, 2000);
                        }
                    }
                }
            }
        } catch (e) {
            console.error('Failed to check JMS connection status', e);
        }
    };

    const bindEvents = () => {
        // Properties Table Logic
        const addPropBtn = document.getElementById('addJmsPropBtn');
        if (addPropBtn) {
            addPropBtn.addEventListener('click', () => {
                const tbody = document.querySelector('#jmsPropsTable tbody');
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><input type="text" placeholder="Key" class="key-input" style="width:100%"></td>
                    <td><input type="text" placeholder="Value" class="value-input" style="width:100%"></td>
                    <td><select class="type-input" style="font-size:0.7rem;"><option value="string">String</option><option value="int">Int</option><option value="bool">Bool</option></select></td>
                    <td><button type="button" class="btn-remove" style="color:red; border:none; background:none;">×</button></td>
                `;
                tr.querySelector('.btn-remove').onclick = () => tr.remove();
                tbody.appendChild(tr);
            });
        }
        
        // Admin Options Table
        const addAdminBtn = document.getElementById('addAdminPropBtn');
        if (addAdminBtn) {
            addAdminBtn.addEventListener('click', () => {
                const tbody = document.querySelector('#jmsAdminPropsTable tbody');
                const tr = document.createElement('tr');
                tr.innerHTML = `
                    <td><input type="text" placeholder="Option" class="key-input" style="width:100%"></td>
                    <td><input type="text" placeholder="Value" class="value-input" style="width:100%"></td>
                    <td><button type="button" class="btn-remove" style="color:red; border:none; background:none;">×</button></td>
                `;
                tr.querySelector('.btn-remove').onclick = () => tr.remove();
                tbody.appendChild(tr);
            });
        }
        
        // Provider Toggle
        const providerSelect = document.getElementById('jmsProvider');
        if (providerSelect) {
            providerSelect.addEventListener('change', (e) => {
                const customDiv = document.getElementById('jmsCustomFields');
                if (['IBM MQ', 'Custom'].includes(e.target.value)) {
                    customDiv.style.display = 'block';
                } else {
                    customDiv.style.display = 'none';
                }
            });
        }

        // Operation Mode Toggle
        const opMode = document.getElementById('jmsOpMode');
        if (opMode) {
            opMode.addEventListener('change', () => {
                const mode = opMode.value;
                const mainBtn = document.getElementById('compareBtn');
                const configDiv = document.getElementById('jmsConsumerConfig');
                
                if (mainBtn) {
                     // Check if currently running? Maybe unsafe to switch while running.
                     // For now, just update text.
                     if (!mainBtn.disabled) {
                         mainBtn.innerText = (mode === 'CONSUME') ? '📥 Start Listener' : '📤 Send JMS Message';
                     }
                }
                
                // Toggle Payload vs Consumer Config
                const payload = document.getElementById('jmsPayload');
                
                if (mode === 'CONSUME') {
                    if(payload) {
                        payload.parentElement.style.display = 'none'; // Hide Body label too? 
                        // Actually payload textarea is sibling to label.
                        // Let's just hide the textarea for now.
                        payload.style.display = 'none';
                    }
                    if(configDiv) configDiv.style.display = 'block';
                } else {
                    if(payload) payload.style.display = 'block';
                    if(configDiv) configDiv.style.display = 'none';
                }
            });
            // Trigger initial state
            opMode.dispatchEvent(new Event('change'));
        }

        const opSelect = document.getElementById('jmsOpMode');
        if (opSelect) {
            opSelect.addEventListener('change', syncJmsFields);
            syncJmsFields(); // Initial run
        }

        // Bind Actions
        const connectBtn = document.getElementById('jmsConnectBtn');
        if (connectBtn) connectBtn.addEventListener('click', handleConnect);

        const startBrokerBtn = document.getElementById('startLocalBrokerBtn');
        if (startBrokerBtn) startBrokerBtn.addEventListener('click', handleStartEmbedded);

        // Destination Manager
        const createDestBtn = document.getElementById('jmsCreateDestBtn');
        if (createDestBtn) createDestBtn.addEventListener('click', handleCreateDest);

        const drainDestBtn = document.getElementById('jmsDrainDestBtn');
        if (drainDestBtn) drainDestBtn.addEventListener('click', handleDrainDest);

        const browseBtn = document.getElementById('jmsBrowseBtn');
        if (browseBtn) browseBtn.addEventListener('click', handleBrowse);

        const consumeBtn = document.getElementById('jmsConsumeBtn');
        if (consumeBtn) consumeBtn.addEventListener('click', handleConsumeOne);
        
        // Removed redundant compareBtn listener (handled by app.js)

        // Auto-save fields to localStorage
        ['jmsProvider', 'jmsUrl', 'jmsUser', 'jmsDestination', 'jmsDestType', 'jmsConcurrent', 'jmsOpMode', 'jmsFetchMax', 'jmsRateLimit', 'jmsRateUnit'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.addEventListener('change', saveFields);
        });
    };

    const saveFields = () => {
        const fields = ['jmsProvider', 'jmsUrl', 'jmsUser', 'jmsDestination', 'jmsDestType', 'jmsConcurrent', 'jmsOpMode', 'jmsFetchMax', 'jmsRateLimit', 'jmsRateUnit'];
        const values = {};
        fields.forEach(id => {
            const el = document.getElementById(id);
            if (el) values[id] = el.value;
        });
        localStorage.setItem('apiforge_jms_fields', JSON.stringify(values));
    };

    const restoreFields = () => {
        const saved = localStorage.getItem('apiforge_jms_fields');
        if (saved) {
            try {
                const values = JSON.parse(saved);
                Object.entries(values).forEach(([id, val]) => {
                    const el = document.getElementById(id);
                    if (el) {
                        el.value = val;
                        // Trigger change for provider toggle etc
                        el.dispatchEvent(new Event('change'));
                    }
                });
            } catch (e) { console.warn('Failed to restore JMS fields', e); }
        }
    };

    const syncJmsFields = () => {
        const modeSelect = document.getElementById('jmsOpMode');
        if (!modeSelect) return;
        const mode = modeSelect.value;
        
        const publishSection = document.getElementById('jmsPublishSection');
        const consumerConfig = document.getElementById('jmsConsumerConfig');
        const smartOpAccordion = document.getElementById('jmsSmartOptionsAccordion');
        const adminExtra = document.getElementById('jmsAdminExtra');
        const fetchConfig = document.getElementById('jmsFetchConfig');
        
        const isPublish = (mode === 'PUBLISH');
        const isListen = (mode === 'LISTEN');
        const isAdmin = (['CREATE', 'DELETE', 'PURGE'].includes(mode));
        const isFetch = (['BROWSE', 'CONSUME'].includes(mode));
        
        if (publishSection) publishSection.style.display = isPublish ? 'block' : 'none';
        if (consumerConfig) consumerConfig.style.display = isListen ? 'block' : 'none';
        if (smartOpAccordion) smartOpAccordion.style.display = isPublish ? 'block' : 'none';
        if (adminExtra) adminExtra.style.display = (mode === 'CREATE') ? 'block' : 'none';
        if (fetchConfig) fetchConfig.style.display = isFetch ? 'block' : 'none';

        const compareBtn = document.getElementById('compareBtn');
        if (compareBtn) {
            // Reset Button State (Handles the "Stop Listener" glitch)
            resetListenerBtn(); 
            
            switch(mode) {
                case 'PUBLISH': compareBtn.innerText = '🚀 Publish Message'; break;
                case 'BROWSE': compareBtn.innerText = '👁️ Browse Destination'; break;
                case 'CONSUME': compareBtn.innerText = '📥 Consume Message'; break;
                case 'LISTEN': compareBtn.innerText = '👂 Start Listener'; break;
                case 'PURGE': compareBtn.innerText = '🗑️ Purge Destination'; break;
                case 'CREATE': compareBtn.innerText = '➕ Create/Init'; break;
                case 'DELETE': compareBtn.innerText = '❌ Delete Destination'; break;
            }
        }
    };

    // -- Actions --

    const handleConnect = async () => {
        const btn = document.getElementById('jmsConnectBtn');
        const origText = btn.innerText;
        btn.innerText = 'Connecting...';
        btn.disabled = true;

        const config = {
            provider: document.getElementById('jmsProvider').value,
            url: document.getElementById('jmsUrl').value,
            username: document.getElementById('jmsUser').value,
            password: document.getElementById('jmsPassword').value,
            factoryClass: document.getElementById('jmsFactoryClass').value,
            driverJarPath: document.getElementById('jmsJarPath').value
        };

        try {
            const resp = await fetch('api/jms/connect', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(config)
            });
            const data = await resp.json();
            
            if (data.success) {
                isConnected = true;
                currentProvider = config.provider;
                document.getElementById('jmsActiveProfile').innerText = `✅ Connected to ${config.provider} (${config.url})`;
                logActivity(`JMS Connected: ${config.provider}`, 'success');
                
                // Auto-Switch to JMS View
                document.getElementById('testType').value = 'JMS';
                if (window.syncTypeButtons) window.syncTypeButtons();
            } else {
                alert('Connection Failed: ' + data.error);
                logActivity(`JMS Connection Failed: ${data.error}`, 'error');
            }
        } catch (e) {
            console.error(e);
            alert('Request failed');
        } finally {
            btn.innerText = origText;
            btn.disabled = false;
        }
    };

    const handleStartEmbedded = async () => {
        const btn = document.getElementById('startLocalBrokerBtn');
        const origText = btn.innerText;
        btn.innerText = 'Starting...';
        
        try {
            const resp = await fetch('api/jms/embed/start', { method: 'POST' });
            const data = await resp.json();
            if (data.success) {
                logActivity('Embedded ActiveMQ Started: tcp://localhost:61616', 'success');
                alert('ActiveMQ Embedded Broker Started Successfully!\nURL: tcp://localhost:61616');
                document.getElementById('jmsUrl').value = 'tcp://localhost:61616';
                document.getElementById('jmsProvider').value = 'ActiveMQ';

                // Auto-Switch to JMS View
                document.getElementById('testType').value = 'JMS';
                if (window.syncTypeButtons) window.syncTypeButtons();
            } else {
                 alert('Failed to start broker: ' + (data.error || 'Unknown Error'));
            }
        } catch(e) { console.error(e); }
        finally { btn.innerText = origText; }
    };


    // -- Listener State --
    let listenerInterval = null;

    /**
     * Sends a message using the current UI configuration
     */
    const handleSend = async () => {
        if (!isConnected) {
            const msg = 'Please connect to a JMS provider first (Utilities > JMS Manager).';
            logActivity(`SEND ABORTED: ${msg}`, 'warning');
            alert(msg);
            return;
        }

        // Collect Props
        const props = {};
        document.querySelectorAll('#jmsPropsTable tbody tr').forEach(tr => {
            const k = tr.querySelector('.key-input').value;
            let v = tr.querySelector('.value-input').value;
            const t = tr.querySelector('.type-input').value;
            if (k) {
                if (t === 'int') v = parseInt(v);
                else if (t === 'bool') v = (v === 'true');
                props[k] = v;
            }
        });

        // Extraction Logic
        const destination = document.getElementById('jmsDestination').value;
        const destType = document.getElementById('jmsDestType').value;
        const payload = document.getElementById('jmsPayload').value;
        const rateLimit = parseInt(document.getElementById('jmsRateLimit').value) || 0;
        const rateUnit = document.getElementById('jmsRateUnit').value;
        let msBetween = 0;
        
        if (rateLimit > 0) {
            const multi = { sec: 1000, min: 60000, hour: 3600000, day: 86400000 };
            msBetween = (multi[rateUnit] || 60000) / rateLimit;
        }

        const reqBody = {
            destination,
            destType,
            payload,
            properties: props,
            rateLimit,
            rateUnit,
            msBetween
        };

        logActivity(`JMS SEND: ${destType}://${destination}`, 'debug');

        try {
            const resp = await fetch('api/jms/send', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(reqBody)
            });
            const data = await resp.json();
            if (data.success) {
                logActivity('Message Sent Successfully', 'success');
                showToast('Message Published Successfully', 'success');
            } else {
                logActivity(`Send Failed: ${data.data || data.error}`, 'error');
            }
        } catch (e) {
            logActivity(`Send Error: ${e.message}`, 'error');
        }
    };

    // -- Listener Logic --

    const handleStartListener = async () => {
        const btn = document.getElementById('compareBtn');
        const destination = document.getElementById('jmsDestination').value;
        const destType = document.getElementById('jmsDestType').value;
        const consumers = document.getElementById('jmsConcurrent').value;
        
        btn.innerText = 'Starting Listeners...';
        btn.disabled = true;
        
        try {
            const resp = await fetch('api/jms/listen/start', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ destination, destType, consumers })
            });
            const data = await resp.json();
            if (data.success) {
                logActivity(`Started ${consumers} consumers on ${destination}`, 'success');
                showToast(`JMS Listeners started on ${destination}`, 'success');
                btn.innerText = '🛑 Stop Listeners';
                btn.classList.add('btn-danger'); 
                btn.style.backgroundColor = '#e53e3e';
                
                // Start Polling Stats
                if (listenerInterval) clearInterval(listenerInterval);
                listenerInterval = setInterval(pollStats, 2000);
            } else {
                showToast('Failed to start listeners: ' + data.error, 'error');
                logActivity('Listener start failed: ' + data.error, 'error');
                resetListenerBtn();
            }
        } catch (e) {
            console.error(e);
            showToast('Request Failed', 'error');
            resetListenerBtn();
        } finally {
            btn.disabled = false;
        }
    };
    
    const handleStopListener = async () => {
        const btn = document.getElementById('compareBtn');
        btn.innerText = 'Stopping...';
        btn.disabled = true;
        
        try {
            await fetch('api/jms/listen/stop', { method: 'POST' });
            logActivity('Listeners Stopped', 'warning');
            showToast('JMS Listener Stopped', 'info');
        } catch (e) { console.error(e); }
        finally {
            resetListenerBtn();
            if (listenerInterval) clearInterval(listenerInterval);
        }
    };
    
    const resetListenerBtn = () => {
        const btn = document.getElementById('compareBtn');
        if(!btn) return;
        btn.innerText = '👂 Start Listener';
        btn.style.backgroundColor = ''; // Reset
        btn.classList.remove('btn-danger');
        btn.disabled = false;
        // Removed manual onclick assignment
    };

    const handleCreateDest = async () => {
        const destination = document.getElementById('jmsDestination').value;
        const destType = document.getElementById('jmsDestType').value;
        if (!destination) { showToast('Destination name required.', 'warning'); return; }

        // Collect Admin Props
        const props = {};
        document.querySelectorAll('#jmsAdminPropsTable tbody tr').forEach(tr => {
            const k = tr.querySelector('.key-input').value;
            const v = tr.querySelector('.value-input').value;
            if (k) props[k] = v;
        });

        logActivity(`Admin: Creating/Initializing ${destType} ${destination}`, 'info');
        try {
            const resp = await fetch('api/jms/create', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ destination, destType, options: props })
            });
            const res = await resp.json();
            if (res.success) showToast(`Destination ${destination} initialized.`, 'success');
            else showToast('Failed: ' + res.error, 'error');
        } catch(e) { console.error(e); }
    };

    const handleDeleteDest = async () => {
        const destination = document.getElementById('jmsDestination').value;
        const destType = document.getElementById('jmsDestType').value;
        if (!destination) { showToast('Destination name required.', 'warning'); return; }
        
        if(!confirm(`Are you sure you want to DELETE ${destType}: ${destination}? This cannot be undone.`)) return;

        logActivity(`Admin: Deleting ${destType} ${destination}`, 'info');
        try {
            const resp = await fetch('api/jms/delete', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ destination, destType })
            });
            const res = await resp.json();
            if (res.success) showToast(`Destination ${destination} deleted.`, 'success');
            else showToast('Failed (Provider might not support deletion): ' + res.error, 'error');
        } catch(e) { console.error(e); }
    };

    const handleDrainDest = async () => {
        const destination = document.getElementById('jmsDestination').value;
        const destType = document.getElementById('jmsDestType').value;
        if (!destination) { showToast('Destination name required.', 'warning'); return; }
        
        logActivity(`Purging: ${destination}`, 'info');
        try {
            const resp = await fetch('api/jms/drain', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ destination, destType })
            });
            const res = await resp.json();
            if (res.success) showToast(`Purged ${res.count} messages from ${destination}.`, 'success');
            else showToast('Purge failed: ' + res.error, 'error');
        } catch(e) { console.error(e); }
    };

    const handleBrowse = async () => {
        const destination = document.getElementById('jmsDestination').value;
        const fetchMax = parseInt(document.getElementById('jmsFetchMax').value) || 10;
        if (!destination) { showToast('Destination name required.', 'warning'); return; }
        
        logActivity(`Browsing Destination: ${destination} (limit ${fetchMax})`, 'info');
        try {
            const resp = await fetch('api/jms/browse', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ destination, count: fetchMax })
            });
            const messages = await resp.json();
            if (messages.error) {
                showToast('Browse failed: ' + messages.error, 'error');
            } else {
                showToast(`Browsed ${messages.length} messages from ${destination}`, 'success');
                renderJmsResults(messages, `Browse Results: ${destination}`);
            }
        } catch(e) { console.error(e); }
    };

    const handleConsumeOne = async () => {
        const destination = document.getElementById('jmsDestination').value;
        const destType = document.getElementById('jmsDestType').value;
        const fetchMax = parseInt(document.getElementById('jmsFetchMax').value) || 1;
        if (!destination) { showToast('Destination name required.', 'warning'); return; }

        logActivity(`Consuming ${fetchMax} from: ${destination}`, 'info');
        try {
            const resp = await fetch('api/jms/consume', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ destination, destType, count: fetchMax })
            });
            const data = await resp.json();
            if (data.error) {
                showToast('Consume failed: ' + data.error, 'error');
            } else if (data.empty) {
                showToast('Destination is empty.', 'info');
            } else {
                const msgs = Array.isArray(data) ? data : [data];
                showToast(`Consumed ${msgs.length} messages from ${destination}`, 'success');
                renderJmsResults(msgs, `Consumed from ${destination}`);
            }
        } catch(e) { console.error(e); }
    };

    const renderJmsResults = (messages, title = 'JMS Messages') => {
        const container = document.getElementById('resultsContainer');
        if (!container) return;

        // Deduplicate
        const newMessages = messages.filter(m => {
            if (m.JMSMessageID && seenMessageIds.has(m.JMSMessageID)) return false;
            if (m.JMSMessageID) seenMessageIds.add(m.JMSMessageID);
            return true;
        });

        if (newMessages.length === 0 && title.includes('Live')) return; // Don't create empty blocks for polling

        // Auto-Collapse previous blocks
        container.querySelectorAll('.jms-result-block.open').forEach(block => {
            block.classList.remove('open');
            const h = block.querySelector('.jms-block-header');
            if (h) h.querySelector('.icon').innerText = '▼';
        });

        const blockId = 'jms_block_' + Date.now();
        const block = document.createElement('div');
        block.className = 'jms-result-block open';
        block.id = blockId;
        block.style.cssText = `margin-bottom:20px; border:1px solid #e9d8fd; border-radius:12px; overflow:hidden; background:white; transition:all 0.3s ease;`;

        const headerHtml = `
            <div class="jms-block-header" onclick="JmsModule.toggleBlock('${blockId}')" style="background:#f3e8ff; padding:12px 20px; display:flex; justify-content:space-between; align-items:center; cursor:pointer; border-bottom:1px solid #e9d8fd;">
                <div style="display:flex; align-items:center; gap:10px;">
                    <span class="icon" style="color:#553c9a; font-weight:bold;">▲</span>
                    <strong style="color:#44337a; font-size:1rem;">🛰️ ${title}</strong>
                    <span style="font-size:0.75rem; background:#d6bcfa; color:white; padding:2px 8px; border-radius:10px; font-weight:bold;">${newMessages.length} msgs</span>
                </div>
                <div style="display:flex; align-items:center; gap:15px;">
                    <input type="text" placeholder="🔍 Filter results..." onclick="event.stopPropagation()" oninput="JmsModule.filterResults('${blockId}', this.value)" style="font-size:0.75rem; padding:4px 8px; border-radius:15px; border:1px solid #d6bcfa; width:150px; outline:none;">
                    <div style="font-size:0.75rem; color:#6b46c1; font-weight:600;">${new Date().toLocaleTimeString()}</div>
                </div>
            </div>
        `;

        const msgHtml = newMessages.map(m => {
            const body = m.body || '';
            const timestamp = m.formattedTimestamp || m.JMSTimestamp || 'N/A';
            
            // Categorized Properties
            const renderProps = (props, label, color) => {
                if (!props || Object.keys(props).length === 0) return '';
                let items = '';
                Object.entries(props).forEach(([k, v]) => {
                    items += `<div style="background:${color}0a; color:${color}; padding:4px 10px; border-radius:15px; font-size:0.7rem; border:1px solid ${color}33; margin:2px; display:inline-block;">${k}: <strong>${v}</strong></div>`;
                });
                return `
                    <div style="margin-bottom:10px;">
                        <label style="font-size:0.6rem; font-weight:800; color:${color}; display:block; margin-bottom:3px; text-transform:uppercase; letter-spacing:0.05em;">${label}</label>
                        <div style="display:flex; flex-wrap:wrap; gap:4px;">${items}</div>
                    </div>
                `;
            };

            const sysPropsHtml = renderProps(m.systemProperties, 'System Headers', '#553c9a');
            const customPropsHtml = renderProps(m.customProperties, 'Application Properties', '#805ad5');

            return `
                <div class="msg-card" style="border-left:5px solid var(--primary-color); padding:15px; background:#fdfaff; border-bottom:1px solid #e9d8fd;">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; border-bottom:1px dotted #d6bcfa; padding-bottom:5px;">
                        <span style="font-weight:bold; color:#553c9a; font-size:0.85rem;">💎 ID: ${m.JMSMessageID || 'N/A'}</span>
                        <span style="font-size:0.7rem; color:#6b46c1; font-weight:600;">🕒 ${timestamp}</span>
                    </div>
                    ${sysPropsHtml}
                    ${customPropsHtml}
                    <div>
                        <div style="display:flex; justify-content:space-between; align-items:center; cursor:pointer;" onclick="this.nextElementSibling.style.display = (this.nextElementSibling.style.display==='none'?'block':'none')">
                             <label style="font-size:0.6rem; font-weight:800; color:#805ad5; text-transform:uppercase;">Payload (Click to Expand)</label>
                             <span style="font-size:0.7rem; color:#805ad5;">⌄</span>
                        </div>
                        <pre style="display:none; background:#f3e8ff; color:#2d3748; padding:12px; border-radius:10px; font-size:0.85rem; margin:5px 0 0 0; font-family:'Outfit', sans-serif; white-space:pre-wrap; border-left:4px solid #b794f4; line-height:1.4;">${escapeHtml(body)}</pre>
                    </div>
                </div>
            `;
        }).join('');

        const contentHtml = `
            <div class="jms-block-content" style="max-height:1500px; transition:max-height 0.3s ease-in-out; overflow-y:auto;">
                ${msgHtml}
            </div>
            <style>
                .jms-result-block:not(.open) .jms-block-content { max-height: 0 !important; overflow:hidden !important; }
            </style>
        `;

        block.innerHTML = headerHtml + contentHtml;
        container.appendChild(block);
        
        if (newMessages.length > 0) {
            block.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    };

    const toggleBlock = (id) => {
        const b = document.getElementById(id);
        if (b) {
            b.classList.toggle('open');
            const icon = b.querySelector('.icon');
            if (icon) icon.innerText = b.classList.contains('open') ? '▲' : '▼';
        }
    };

    const filterResults = (blockId, query) => {
        const block = document.getElementById(blockId);
        if (!block) return;
        const q = query.toLowerCase();
        block.querySelectorAll('.msg-card').forEach(card => {
            // Use textContent to include hidden payload <pre> content
            const text = card.textContent.toLowerCase();
            card.style.display = text.includes(q) ? 'block' : 'none';
        });
    };
    
    const pollStats = async () => {
        try {
            const resp = await fetch('api/jms/listen/stats');
            const stats = await resp.json();
            
            // Update Status Badge or similar?
            const statusInd = document.getElementById('statusIndicator');
            if (statusInd) {
                statusInd.classList.remove('hidden');
                statusInd.innerHTML = `Consumed: <strong>${stats.totalConsumed}</strong> | Rate: <strong>${stats.rate} msg/s</strong>`;
            }
            
            // Dump Messages to Results if any
            if (stats.buffer && stats.buffer.length > 0) {
                renderJmsResults(stats.buffer, `Live Consumed (${stats.buffer.length})`);
            }
            
        } catch (e) { console.error('Stats Poll Error', e); }
    };
    
    // Helper
    const escapeHtml = (text) => {
        if (!text) return text;
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    };

    const runAction = async () => {
        const modeSelect = document.getElementById('jmsOpMode');
        if (!modeSelect) return;
        const mode = modeSelect.value;

        switch(mode) {
            case 'PUBLISH': await handleSend(); break;
            case 'BROWSE': await handleBrowse(); break;
            case 'CONSUME': await handleConsumeOne(); break;
            case 'LISTEN': 
                const btn = document.getElementById('compareBtn');
                if (btn && btn.innerText.includes('Stop')) handleStopListener();
                else handleStartListener();
                break;
            case 'PURGE': await handleDrainDest(); break;
            case 'CREATE': await handleCreateDest(); break;
            case 'DELETE': await handleDeleteDest(); break;
        }
    };

    return {
        init,
        runAction,
        toggleBlock,
        filterResults,
        stopListener: handleStopListener
    };
})();

// Auto-init if loaded late? or waiting for main app?
// We'll let app.js call JmsModule.init() when ready or when the HTML is injected.
