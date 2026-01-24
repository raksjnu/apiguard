// Syntax highlighting and auto-format for payload editor
(function() {
    'use strict';

    function highlightPayload(textarea) {
        const value = textarea.value.trim();
        if (!value) return;

        // Detect format
        const isJson = value.startsWith('{') || value.startsWith('[');
        const isXml = value.startsWith('<');

        if (isJson) {
            try {
                const formatted = JSON.stringify(JSON.parse(value), null, 2);
                textarea.value = formatted;
                showFormatSuccess('JSON formatted successfully');
            } catch (e) {
                showFormatError('Invalid JSON: ' + e.message);
            }
        } else if (isXml) {
            try {
                const formatted = formatXml(value);
                textarea.value = formatted;
                showFormatSuccess('XML formatted successfully');
            } catch (e) {
                showFormatError('Invalid XML: ' + e.message);
            }
        }
    }

    function formatXml(xml) {
        const PADDING = '  ';
        const reg = /(>)(<)(\/*)/g;
        let formatted = '';
        let pad = 0;

        xml = xml.replace(reg, '$1\r\n$2$3');
        const lines = xml.split('\r\n');

        lines.forEach(line => {
            let indent = 0;
            if (line.match(/.+<\/\w[^>]*>$/)) {
                indent = 0;
            } else if (line.match(/^<\/\w/)) {
                if (pad !== 0) {
                    pad -= 1;
                }
            } else if (line.match(/^<\w([^>]*[^\/])?>.*$/)) {
                indent = 1;
            } else {
                indent = 0;
            }

            formatted += PADDING.repeat(pad) + line + '\r\n';
            pad += indent;
        });

        return formatted.trim();
    }

    function showFormatSuccess(message) {
        showToast(message, 'success');
    }

    function showFormatError(message) {
        showToast(message, 'error');
    }

    function showToast(message, type) {
        const existing = document.querySelector('.format-toast');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.className = 'format-toast';
        toast.textContent = message;
        toast.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            padding: 12px 20px;
            background: ${type === 'success' ? '#28a745' : '#dc3545'};
            color: white;
            border-radius: 6px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
            z-index: 10000;
            font-weight: 600;
            animation: slideIn 0.3s ease;
        `;

        document.body.appendChild(toast);
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    // Add format button
    function addFormatButton() {
        const payloadField = document.getElementById('payload');
        if (!payloadField) return;

        const container = payloadField.parentElement;
        const formatBtn = document.createElement('button');
        formatBtn.type = 'button';
        formatBtn.className = 'btn-secondary';
        formatBtn.innerHTML = '✨ Auto-Format';
        formatBtn.style.cssText = 'margin-top: 8px; padding: 8px 16px; font-size: 0.85rem;';
        
        formatBtn.addEventListener('click', () => highlightPayload(payloadField));
        
        // Insert after payload field
        const small = container.querySelector('small');
        if (small) {
            container.insertBefore(formatBtn, small);
        } else {
            container.appendChild(formatBtn);
        }
    }

    // Add CSS animation
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideIn {
            from { transform: translateX(400px); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }
        @keyframes slideOut {
            from { transform: translateX(0); opacity: 1; }
            to { transform: translateX(400px); opacity: 0; }
        }
    `;
    document.head.appendChild(style);

    // Initialize
    window.addEventListener('DOMContentLoaded', addFormatButton);

    // Expose globally
    window.payloadFormatter = {
        format: highlightPayload
    };
})();
