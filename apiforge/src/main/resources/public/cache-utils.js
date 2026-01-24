// Auto-populate and caching utilities for API Forge
(function() {
    'use strict';

    const CACHE_KEY = 'apiForge_formCache';
    const SOAP_DEFAULTS = {
        operationName: 'OperationName',
        url1: 'https://api1.example.com/soap',
        url2: 'https://api2.example.com/soap',
        method: 'POST',
        payload: `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
    <soapenv:Body>
        <Placeholder>{{token}}</Placeholder>
    </soapenv:Body>
</soapenv:Envelope>`,
        headers: [
            { name: 'Content-Type', value: 'text/xml;charset=UTF-8' }
        ],
        tokens: []
    };

    const REST_DEFAULTS = {
        operationName: 'operationName',
        url1: 'https://api1.example.com/rest',
        url2: 'https://api2.example.com/rest',
        method: 'POST',
        payload: `{
  "id": "{{id}}"
}`,
        headers: [
            { name: 'Content-Type', value: 'application/json' }
        ],
        tokens: []
    };

    // Save form to cache
    function saveFormToCache() {
        try {
            const formData = {
                testType: document.getElementById('testType')?.value,
                operationName: document.getElementById('operationName')?.value,
                url1: document.getElementById('url1')?.value,
                url2: document.getElementById('url2')?.value,
                method: document.getElementById('method')?.value,
                payload: document.getElementById('payload')?.value,
                ignoredFields: document.getElementById('ignoredFields')?.value,
                ignoreHeaders: document.getElementById('ignoreHeaders')?.checked,
                enableAuth: document.getElementById('enableAuth')?.checked,
                clientId: document.getElementById('clientId')?.value,
                clientSecret: document.getElementById('clientSecret')?.value,
                iterationController: document.getElementById('iterationController')?.value,
                maxIterations: document.getElementById('maxIterations')?.value,
                headers: getTableData('headersTable'),
                tokens: getTableData('tokensTable'),
                timestamp: new Date().toISOString()
            };
            localStorage.setItem(CACHE_KEY, JSON.stringify(formData));
        } catch (e) {
            console.warn('Failed to save form cache:', e);
        }
    }

    // Load form from cache
    function loadFormFromCache() {
        try {
            const cached = localStorage.getItem(CACHE_KEY);
            if (!cached) return false;
            
            const formData = JSON.parse(cached);
            
            // Check if cache is recent (within 7 days)
            const cacheAge = Date.now() - new Date(formData.timestamp).getTime();
            if (cacheAge > 7 * 24 * 60 * 60 * 1000) {
                localStorage.removeItem(CACHE_KEY);
                return false;
            }
            
            // Restore form fields
            if (formData.testType) document.getElementById('testType').value = formData.testType;
            if (formData.operationName) document.getElementById('operationName').value = formData.operationName;
            if (formData.url1) document.getElementById('url1').value = formData.url1;
            if (formData.url2) document.getElementById('url2').value = formData.url2;
            if (formData.method) document.getElementById('method').value = formData.method;
            if (formData.payload) document.getElementById('payload').value = formData.payload;
            if (formData.ignoredFields) document.getElementById('ignoredFields').value = formData.ignoredFields;
            if (formData.ignoreHeaders !== undefined) document.getElementById('ignoreHeaders').checked = formData.ignoreHeaders;
            if (formData.enableAuth !== undefined) document.getElementById('enableAuth').checked = formData.enableAuth;
            if (formData.clientId) document.getElementById('clientId').value = formData.clientId;
            if (formData.clientSecret) document.getElementById('clientSecret').value = formData.clientSecret;
            if (formData.iterationController) document.getElementById('iterationController').value = formData.iterationController;
            if (formData.maxIterations) document.getElementById('maxIterations').value = formData.maxIterations;
            
            // Restore headers
            if (formData.headers && formData.headers.length > 0) {
                const tbody = document.querySelector('#headersTable tbody');
                if (tbody) {
                    tbody.innerHTML = '';
                    formData.headers.forEach(h => {
                        const row = tbody.insertRow();
                        row.innerHTML = `
                            <td><input type="text" class="key-input" value="${escapeHtml(h.name)}"></td>
                            <td><input type="text" class="value-input" value="${escapeHtml(h.value)}"></td>
                            <td><button type="button" class="btn-remove" onclick="this.closest('tr').remove()">×</button></td>
                        `;
                    });
                }
            }
            
            // Restore tokens
            if (formData.tokens && formData.tokens.length > 0) {
                const tbody = document.querySelector('#tokensTable tbody');
                if (tbody) {
                    tbody.innerHTML = '';
                    formData.tokens.forEach(t => {
                        const row = tbody.insertRow();
                        row.innerHTML = `
                            <td><input type="text" class="key-input" value="${escapeHtml(t.name)}"></td>
                            <td><input type="text" class="value-input" value="${escapeHtml(t.values)}"></td>
                            <td><button type="button" class="btn-remove" onclick="this.closest('tr').remove()">×</button></td>
                        `;
                    });
                }
            }
            
            return true;
        } catch (e) {
            console.warn('Failed to load form cache:', e);
            return false;
        }
    }

    // Get table data
    function getTableData(tableId) {
        const tbody = document.querySelector(`#${tableId} tbody`);
        if (!tbody) return [];
        
        const data = [];
        tbody.querySelectorAll('tr').forEach(tr => {
            const keyInput = tr.querySelector('.key-input');
            const valInput = tr.querySelector('.value-input');
            if (keyInput && valInput && keyInput.value.trim()) {
                data.push({
                    name: keyInput.value.trim(),
                    value: valInput.value.trim()
                });
            }
        });
        return data;
    }

    // Apply defaults based on type
    function applyDefaults(type) {
        const defaults = type === 'SOAP' ? SOAP_DEFAULTS : REST_DEFAULTS;
        
        document.getElementById('operationName').value = defaults.operationName;
        document.getElementById('url1').value = defaults.url1;
        document.getElementById('url2').value = defaults.url2;
        document.getElementById('method').value = defaults.method;
        document.getElementById('payload').value = defaults.payload;
        
        // Set headers
        const headersTable = document.querySelector('#headersTable tbody');
        if (headersTable) {
            headersTable.innerHTML = '';
            defaults.headers.forEach(h => {
                const row = headersTable.insertRow();
                row.innerHTML = `
                    <td><input type="text" class="key-input" value="${h.name}" placeholder="Header Name"></td>
                    <td><input type="text" class="value-input" value="${h.value}" placeholder="Value"></td>
                    <td><button type="button" class="btn-remove" onclick="this.closest('tr').remove()">×</button></td>
                `;
            });
        }
        
        // Set tokens
        const tokensTable = document.querySelector('#tokensTable tbody');
        if (tokensTable) {
            tokensTable.innerHTML = '';
            defaults.tokens.forEach(t => {
                const row = tokensTable.insertRow();
                row.innerHTML = `
                    <td><input type="text" class="key-input" value="${t.name}" placeholder="Token Name"></td>
                    <td><input type="text" class="value-input" value="${t.values}" placeholder="Values (semicolon separated)"></td>
                    <td><button type="button" class="btn-remove" onclick="this.closest('tr').remove()">×</button></td>
                `;
            });
        }
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Initialize on page load
    window.addEventListener('DOMContentLoaded', () => {
        // Try to load from cache first
        const cacheLoaded = loadFormFromCache();
        
        // If no cache, apply defaults based on current type
        if (!cacheLoaded) {
            const testType = document.getElementById('testType')?.value || 'SOAP';
            applyDefaults(testType);
        }
        
        // Save form on changes (debounced)
        let saveTimeout;
        const debouncedSave = () => {
            clearTimeout(saveTimeout);
            saveTimeout = setTimeout(saveFormToCache, 1000);
        };
        
        // Add change listeners
        document.querySelectorAll('input, select, textarea').forEach(el => {
            el.addEventListener('change', debouncedSave);
            el.addEventListener('input', debouncedSave);
        });
        
        // Method change listener - clear payload for GET
        const methodSelect = document.getElementById('method');
        if (methodSelect) {
            methodSelect.addEventListener('change', (e) => {
                const payloadField = document.getElementById('payload');
                if (payloadField && e.target.value === 'GET') {
                    if (confirm('GET requests typically don\'t have a payload. Clear the payload field?')) {
                        payloadField.value = '';
                    }
                }
            });
        }
        
        // Save before unload
        window.addEventListener('beforeunload', saveFormToCache);
    });

    // Expose functions globally
    window.apiForgeCache = {
        save: saveFormToCache,
        load: loadFormFromCache,
        applyDefaults: applyDefaults,
        clearCache: () => localStorage.removeItem(CACHE_KEY)
    };
})();
