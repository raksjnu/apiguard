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
    let selectedMsgCards = []; // Tracks { id, data }

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
            opMode.addEventListener('change', syncJmsFields);
            // Trigger initial state
            syncJmsFields();
        }

        // Destination Auto-Discovery
        const jmsDest = document.getElementById('jmsDestination');
        if (jmsDest) {
            jmsDest.addEventListener('input', handleDestDiscovery);
            // Add a dropdown/list for discovery if not exists
            if (!document.getElementById('jmsDestSuggestions')) {
                const datalist = document.createElement('datalist');
                datalist.id = 'jmsDestSuggestions';
                document.body.appendChild(datalist);
                jmsDest.setAttribute('list', 'jmsDestSuggestions');
            }
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
        const baselineSec = document.getElementById('jmsBaselineSection');
        
        const isPublish = (mode === 'PUBLISH');
        const isListen = (mode === 'LISTEN');
        const isAdmin = (['CREATE', 'DELETE', 'PURGE'].includes(mode));
        const isFetch = (['BROWSE', 'CONSUME'].includes(mode));
        const isBaseline = (mode === 'BASELINE');
        
        if (publishSection) publishSection.style.display = isPublish ? 'block' : 'none';
        if (consumerConfig) consumerConfig.style.display = isListen ? 'block' : 'none';
        if (smartOpAccordion) smartOpAccordion.style.display = isPublish ? 'block' : 'none';
        if (adminExtra) adminExtra.style.display = (mode === 'CREATE' || mode === 'DELETE') ? 'block' : 'none';
        if (fetchConfig) fetchConfig.style.display = isFetch ? 'block' : 'none';
        if (baselineSec) {
            baselineSec.style.display = isBaseline ? 'block' : 'none';
            if (isBaseline) setupJmsBaselineUI();
        }

        const compareBtn = document.getElementById('compareBtn');
        if (compareBtn && !compareBtn.disabled) {
            // Reset Button State (Handles the "Stop Listener" glitch)
            resetListenerBtn(); 
            
            switch(mode) {
                case 'PUBLISH': compareBtn.innerText = '🚀 Send JMS Message'; break;
                case 'BROWSE': compareBtn.innerText = '👁️ Browse Destination'; break;
                case 'CONSUME': compareBtn.innerText = '📥 Consume Message'; break;
                case 'LISTEN': compareBtn.innerText = '👂 Start Listener'; break;
                case 'PURGE': compareBtn.innerText = '🗑️ Purge Destination'; break;
                case 'CREATE': compareBtn.innerText = '➕ Create/Init'; break;
                case 'DELETE': compareBtn.innerText = '❌ Delete Destination'; break;
                case 'BASELINE': compareBtn.innerText = '📀 Replay & Compare'; break;
                default: compareBtn.innerText = '▶️ Process Action';
            }
        }
    };

    let discoveryTimeout = null;
    const handleDestDiscovery = () => {
        if (discoveryTimeout) clearTimeout(discoveryTimeout);
        discoveryTimeout = setTimeout(fetchDestinations, 500);
    };

    const fetchDestinations = async () => {
        try {
            const resp = await fetch('api/jms/destinations');
            if (resp.ok) {
                const dests = await resp.json();
                const datalist = document.getElementById('jmsDestSuggestions');
                if (datalist) {
                    const input = document.getElementById('jmsDestination');
                    const filter = input ? input.value.toLowerCase() : '';
                    
                    datalist.innerHTML = '';
                    let count = 0;
                    dests.forEach(d => {
                        if (!filter || d.toLowerCase().includes(filter)) {
                            const opt = document.createElement('option');
                            opt.value = d;
                            datalist.appendChild(opt);
                            count++;
                        }
                    });
                    if (count > 0) {
                         // Only log if explicit request or debug? 
                         // User requested "fetching the queue log entry".
                         // We'll log it if it was triggered by the Browse button logic (which calls this).
                    }
                }
                return dests; // Return for caller usage
            }
        } catch (e) { /* ignore discovery errors */ }
        return [];
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
        
        if (!destination) { 
            // User Request: If no destination, show available ones
            showToast('Fetching available destinations...', 'info');
            const foundDests = await fetchDestinations();
            
            if (foundDests && foundDests.length > 0) {
                 if (foundDests.length === 1) {
                     // Smart Browse: Only one found, so just browse it!
                     const singleDest = foundDests[0];
                     logActivity(`Discovery: Found single destination '${singleDest}'. Auto-browsing...`, 'success');
                     document.getElementById('jmsDestination').value = singleDest;
                     // Recursive call with value set
                     return handleBrowse();
                 } else {
                     const destStr = foundDests.join(', ');
                     logActivity(`Discovery: Found ${foundDests.length} destinations: ${destStr}`, 'success', destStr);
                     
                     // Render actionable list in Results
                     const container = document.getElementById('resultsContainer');
                     const blockId = 'jms_discovery_' + Date.now();
                     const block = document.createElement('div');
                     block.className = 'jms-result-block open';
                     block.id = blockId;
                     block.style.cssText = `margin-bottom:20px; border:1px solid #e9d8fd; border-radius:12px; overflow:hidden; background:white;`;
                     
                     const listHtml = foundDests.map(d => 
                        `<div style="padding:10px; border-bottom:1px solid #eee; display:flex; justify-content:space-between; align-items:center;">
                            <span style="font-weight:bold; color:#553c9a;">${d}</span>
                            <div style="display:flex; gap:5px;">
                                <button class="btn-secondary" onclick="navigator.clipboard.writeText('${d}'); showToast('Copied!', 'success')" style="font-size:0.8rem; padding:4px 8px;">📋</button>
                                <button class="btn-primary" onclick="document.getElementById('jmsDestination').value='${d}'; JmsModule.handleBrowse()" style="font-size:0.8rem; padding:4px 12px;">Browse ➞</button>
                            </div>
                        </div>`
                     ).join('');
                     
                     block.innerHTML = `
                        <div class="jms-block-header" style="background:#f3e8ff; padding:12px 20px; border-bottom:1px solid #e9d8fd;">
                            <strong style="color:#44337a;">🔎 Discovered Destinations (${foundDests.length})</strong>
                            <div style="font-size:0.75rem; color:#666;">Select one to browse</div>
                        </div>
                        <div class="jms-block-content" style="max-height:400px; overflow-y:auto;">
                            ${listHtml}
                        </div>
                     `;
                     
                     if(container.firstChild) container.insertBefore(block, container.firstChild);
                     else container.appendChild(block);
                     
                     showToast(`Found ${foundDests.length} destinations. Select from list below.`, 'success');
                 }
            } else {
                 logActivity('Discovery: No destinations found.', 'warning');
                 showToast('No destinations found or discovery not supported.', 'warning');
            }
            return; 
        }
        
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

    // Global selection clearing helper
    const clearSelectionInBlock = (blockId) => {
        const block = document.getElementById(blockId);
        if(!block) return;
        block.querySelectorAll('input[type="checkbox"]:checked').forEach(cb => {
            cb.checked = false;
            // manually trigger change event or just update state directly
            // since we can't easily access the 'm' data here without parsing,
            // we'll just rebuild 'selectedMsgCards' by filtering out items from this block?
            // Easier: just simulate click? No, infinite loop risk.
            // Let's just reset the global array for items in this block.
            // We need the ID.
        });
        // Simplest: Just clear global selection? 
        // User asked: "whenever user is selecting the messages and closing the event card ... then automatically selected messages should be unselected."
        // So we should find which messages were in this block and remove them.
        
        // This requires we know which IDs are in this block.
        // We can find them from the DOM.
        const msgIdsInBlock = Array.from(block.querySelectorAll('.msg-card')).map(card => card.getAttribute('data-msg-id'));
        selectedMsgCards = selectedMsgCards.filter(m => !msgIdsInBlock.includes(m.JMSMessageID));
        console.log('[JMS] Cleared selection for block', blockId);
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
                    <button class="btn-primary" onclick="event.stopPropagation(); JmsModule.compareSelected()" style="font-size:0.7rem; padding:4px 10px; border-radius:5px; background:#6b46c1;">✨ Compare Selected</button>
                    <button class="btn-secondary" onclick="event.stopPropagation(); JmsModule.captureJmsBaseline('${blockId}')" style="font-size:0.7rem; padding:4px 10px; border-radius:5px; background:#4c51bf; color:white;">💾 Save as Baseline</button>
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
                <div class="msg-card" data-msg-id="${m.JMSMessageID || ''}" data-raw='${JSON.stringify(m).replace(/'/g, "&#39;")}' style="border-left:5px solid var(--primary-color); padding:15px; background:#fdfaff; border-bottom:1px solid #e9d8fd; position:relative;">
                    <div style="position:absolute; right:10px; top:10px; z-index:10;">
                        <input type="checkbox" style="transform:scale(1.3); cursor:pointer;" onchange="JmsModule.toggleMsgSelection(this, ${JSON.stringify(m).replace(/"/g, '&quot;')})">
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px; border-bottom:1px dotted #d6bcfa; padding-right:30px; padding-bottom:5px;">
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
        // Prepend instead of append
        if(container.firstChild) {
            container.insertBefore(block, container.firstChild);
        } else {
            container.appendChild(block);
        }
        
        if (newMessages.length > 0) {
            block.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    };

    const toggleBlock = (id) => {
        const b = document.getElementById(id);
        if (b) {
            const wasOpen = b.classList.contains('open');
            b.classList.toggle('open');
            const icon = b.querySelector('.icon');
            if (icon) icon.innerText = !wasOpen ? '▲' : '▼';
            
            if (wasOpen) {
                // If closing, clear selections within this block
                clearSelectionInBlock(id);
            }
        }
    };

    const toggleMsgSelection = (checkbox, data) => {
        if (checkbox.checked) {
            selectedMsgCards.push(data);
            checkbox.closest('.msg-card').style.background = '#f0fff4';
            checkbox.closest('.msg-card').style.borderColor = '#48bb78';
        } else {
            selectedMsgCards = selectedMsgCards.filter(m => m.JMSMessageID !== data.JMSMessageID);
            checkbox.closest('.msg-card').style.background = '#fdfaff';
            checkbox.closest('.msg-card').style.borderColor = 'var(--primary-color)';
        }
        console.log('[JMS] Selected:', selectedMsgCards.length);
    };

    const compareSelected = async () => {
        if (selectedMsgCards.length !== 2) {
            showToast('Please select exactly 2 messages to compare.', 'warning');
            return;
        }

        const m1 = selectedMsgCards[0];
        const m2 = selectedMsgCards[1];

        logActivity(`Comparing Message ${m1.JMSMessageID} vs ${m2.JMSMessageID}`, 'info');
        
        // Prepare objects for comparison
        const obj1 = {
            headers: { ...m1.systemProperties, ...m1.customProperties },
            payload: m1.body
        };
        const obj2 = {
            headers: { ...m2.systemProperties, ...m2.customProperties },
            payload: m2.body
        };

        try {
            const resp = await fetch('api/jms/compare-selected', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    msg1: { headers: obj1.headers, payload: obj1.payload },
                    msg2: { headers: obj2.headers, payload: obj2.payload }
                })
            });
            const result = await resp.json();
            if (result.error) {
                showToast('Comparison failed: ' + result.error, 'error');
            } else {
                showToast('Comparison successful.', 'success');
                // Switch to Main view if needed, or just show summary
                // For JMS, we'll append it to the resultsContainer (where renderResults puts things)
                if (window.renderResults) {
                    window.renderResults([result]);
                }
            }
        } catch (e) {
            console.error(e);
        }
    };

    const filterResults = (blockId, query) => {
        const block = document.getElementById(blockId);
        if (!block) return;
        const q = query.toLowerCase();
        
        block.querySelectorAll('.msg-card').forEach(card => {
            const raw = card.getAttribute('data-raw'); // Use raw data to reset content if needed
            const origContent = card.innerHTML; // Can't easily restore innerHTML without re-rendering or saving state.
            // Simplified approach: Toggle display, and try to highlight visible text.
            // Highlighting inside a complex DOM is tricky.
            // A safer bet is to highlight only in the Payload <pre> or specific fields.
            
            // Always clear highlights first
            card.querySelectorAll('mark.highlight').forEach(m => {
                m.replaceWith(document.createTextNode(m.textContent)); // Flatten
                card.normalize(); // Merge text nodes
            });

            // Let's search in textContent
            if (!q) {
                card.style.display = 'block';
                return; 
            }

            const text = card.textContent.toLowerCase();
            if (text.includes(q)) {
                card.style.display = 'block';
                // Highlight Logic (Basic)
                // We'll traverse text nodes and wrap matches.
                // Note: This is expensive and can break HTML if not careful. 
                // We will only highlight in the Payload Pre and Property Values for safety.
                highlightInElement(card, query);
            } else {
                card.style.display = 'none';
            }
        });
    };
    
    const highlightInElement = (root, term) => {
        // Recursive text node traversal
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
        const nodes = [];
        while(walker.nextNode()) nodes.push(walker.currentNode);
        
        const regex = new RegExp(`(${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
        
        nodes.forEach(node => {
            if (node.parentNode.nodeName === 'MARK' || node.parentNode.nodeName === 'SCRIPT' || node.parentNode.nodeName === 'STYLE') return;
            if (node.nodeValue && regex.test(node.nodeValue)) {
                 const span = document.createElement('span');
                 span.innerHTML = node.nodeValue.replace(regex, '<mark class="highlight" style="background:#fefcbf; color:#744210; border-radius:3px; padding:0 2px;">$1</mark>');
                 node.parentNode.replaceChild(span, node);
            }
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
            case 'BASELINE': await handleBaselineReplay(); break;
            case 'CREATE': await handleCreateDest(); break;
            case 'DELETE': await handleDeleteDest(); break;
        }
    };

    const captureJmsBaseline = async (blockId) => {
        const block = document.getElementById(blockId);
        if (!block) return;
        const msgCards = block.querySelectorAll('.msg-card');
        if (msgCards.length === 0) return;
        
        const serviceName = prompt('Enter Service Name for this baseline:', 'JMS-Provider');
        if (!serviceName) return;
        const description = prompt('Enter Description:', 'Captured browse session');
        
        const messages = [];
        msgCards.forEach(card => {
            // Find the checkbox which has the data
            const cb = card.querySelector('input[type="checkbox"]');
            if (cb) {
                // We need to parse the data from the checkbox attribute
                // but wait, I can just use the data I passed to renderJmsResults
                // For simplicity, let's assume we can get it from the checkbox's parent logic
                // Actually, I'll update the checkbox to store the data in an attribute
            }
        });
        
        // Let's use a simpler approach: get from selected messages if any, 
        // OR get all from block.
        // Actually, the easiest is to just grab the bodies and headers from the DOM or data attributes.
        // I'll update renderJmsResults to store full data in a data-attribute.
        msgCards.forEach(card => {
            const raw = card.getAttribute('data-raw');
            if (raw) messages.push(JSON.parse(raw));
        });

        if (messages.length === 0) {
             showToast('No message data found to capture.', 'warning');
             return;
        }

        logActivity(`Capturing JMS Baseline: ${serviceName} with ${messages.length} msgs`, 'info');
        try {
            const resp = await fetch('api/jms/baselines/capture', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    serviceName, description,
                    messages: messages.map(m => ({
                        payload: m.body,
                        headers: { ...m.systemProperties, ...m.customProperties }
                    }))
                })
            });
            const data = await resp.json();
            if (data.success) {
                showToast(`Baseline Captured! RunID: ${data.runId}`, 'success');
                // Fill the replay fields automatically
                document.getElementById('jmsBaselineService').value = serviceName;
                document.getElementById('jmsBaselineDate').value = new Date().toISOString().split('T')[0];
                document.getElementById('jmsBaselineRunId').value = data.runId;
            } else {
                showToast('Capture failed: ' + data.error, 'error');
            }
        } catch (e) { console.error(e); }
    };

    const handleBaselineReplay = async () => {
        const serviceName = document.getElementById('jmsBaselineService').value;
        const date = document.getElementById('jmsBaselineDate').value;
        const runId = document.getElementById('jmsBaselineRunId').value;
        const destination = document.getElementById('jmsDestination').value;
        const destType = document.getElementById('jmsDestType').value;
        
        if (!date || !runId || !destination) {
            showToast('Date, RunID, and Destination Name are required for replay.', 'warning');
            return;
        }

        logActivity(`Replaying JMS Baseline: ${runId} to ${destination}`, 'info');
        try {
            const resp = await fetch('api/jms/baselines/replay', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({ serviceName, date, runId, destination, destType })
            });
            const results = await resp.json();
            if (results.error) {
                showToast('Replay failed: ' + results.error, 'error');
            } else {
                showToast(`Replayed ${results.length} iterations. Rendering parity...`, 'success');
                if (window.renderResults) {
                    window.renderResults(results);
                }
            }
        } catch (e) { console.error(e); }
    };

    // -- JMS Baseline Auto-Complete Logic --
    
    const setupJmsBaselineUI = () => {
        console.log('[JMS] Setting up Baseline UI...');
        const svcSelect = document.getElementById('jmsBaselineService');
        if (!svcSelect) return;
        
        // Always re-bind
        svcSelect.removeEventListener('change', loadJmsDates);
        svcSelect.addEventListener('change', loadJmsDates);

        const dateSelect = document.getElementById('jmsBaselineDate');
        if (dateSelect) {
            dateSelect.removeEventListener('change', loadJmsRuns);
            dateSelect.addEventListener('change', loadJmsRuns);
        }

        // Auto-fetch services immediately
        loadJmsServices();
    };

    const loadJmsServices = async () => {
        const workDir = document.getElementById('workingDirectory')?.value || '';
        const svcSelect = document.getElementById('jmsBaselineService');
        if (!svcSelect) return;

        try {
            const url = `api/baselines/services?type=JMS${workDir ? '&workDir=' + encodeURIComponent(workDir) : ''}`;
            const resp = await fetch(url);
            if (resp.ok) {
                const services = await resp.json();
                svcSelect.innerHTML = '<option value="">-- Select Service --</option>';
                services.forEach(s => {
                    const opt = document.createElement('option');
                    opt.value = opt.textContent = s;
                    svcSelect.appendChild(opt);
                });
                
                if (services.length > 0) {
                    svcSelect.value = services[0];
                    svcSelect.dispatchEvent(new Event('change'));
                }
            }
        } catch (e) { console.error('Failed to load JMS services', e); }
    };

    const loadJmsDates = async () => {
        const service = document.getElementById('jmsBaselineService').value;
        const dateSelect = document.getElementById('jmsBaselineDate');
        const workDir = document.getElementById('workingDirectory')?.value || '';
        if (!service || !dateSelect) return;

        try {
            const url = `api/baselines/dates/${encodeURIComponent(service)}?type=JMS${workDir ? '&workDir=' + encodeURIComponent(workDir) : ''}`;
            const resp = await fetch(url);
            if (resp.ok) {
                const dates = await resp.json();
                dateSelect.innerHTML = '<option value="">-- Select Date --</option>';
                dates.forEach(d => {
                    const opt = document.createElement('option');
                    opt.value = d;
                    // Format for display: yyyymmdd -> yyyy-mm-dd
                    if (d.length === 8) {
                        opt.textContent = `${d.substring(0,4)}-${d.substring(4,6)}-${d.substring(6,8)}`;
                    } else {
                        opt.textContent = d;
                    }
                    dateSelect.appendChild(opt);
                });
                
                if (dates.length > 0) {
                    dateSelect.value = dates[0];
                    dateSelect.disabled = false;
                    dateSelect.dispatchEvent(new Event('change'));
                }
            }
        } catch (e) { console.error(e); }
    };

    const loadJmsRuns = async () => {
        const service = document.getElementById('jmsBaselineService').value;
        const date = document.getElementById('jmsBaselineDate').value;
        const runSelect = document.getElementById('jmsBaselineRunId');
        const workDir = document.getElementById('workingDirectory')?.value || '';
        if (!service || !date || !runSelect) return;

        try {
            const url = `api/baselines/runs/${encodeURIComponent(service)}/${encodeURIComponent(date)}?type=JMS${workDir ? '&workDir=' + encodeURIComponent(workDir) : ''}`;
            const resp = await fetch(url);
            if (resp.ok) {
                const runs = await resp.json();
                runSelect.innerHTML = '<option value="">-- Select Run --</option>';
                runs.forEach(r => {
                    const opt = document.createElement('option');
                    opt.value = r.runId; 
                    const ts = r.timestamp ? r.timestamp.split('T')[1].substring(0,8) : '';
                    opt.textContent = `${r.runId} - ${r.description || ''} (${ts})`;
                    runSelect.appendChild(opt);
                });
                if (runs.length > 0) {
                    runSelect.value = runs[0].runId;
                    runSelect.disabled = false;
                }
            }
        } catch (e) { console.error(e); }
    };

    return {
        init,
        runAction,
        toggleBlock,
        filterResults,
        toggleMsgSelection,
        compareSelected,
        captureJmsBaseline,
        stopListener: handleStopListener
    };
})();

// Auto-init if loaded late? or waiting for main app?
// We'll let app.js call JmsModule.init() when ready or when the HTML is injected.
