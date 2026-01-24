// Results filtering and export functionality
(function() {
    'use strict';

    let currentResults = [];
    let currentFilter = 'all';

    // Initialize filter tabs
    function initResultFilters() {
        document.querySelectorAll('.filter-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                // Update active state
                document.querySelectorAll('.filter-tab').forEach(t => {
                    t.style.background = '#e0e0e0';
                    t.style.color = '#666';
                    t.classList.remove('active');
                });
                tab.style.background = 'var(--primary-color)';
                tab.style.color = 'white';
                tab.classList.add('active');
                
                currentFilter = tab.dataset.filter;
                filterResults();
            });
        });

        // Search functionality
        const searchInput = document.getElementById('resultSearch');
        if (searchInput) {
            let searchTimeout;
            searchInput.addEventListener('input', () => {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(filterResults, 300);
            });
        }

        // Export buttons
        document.getElementById('exportJsonBtn')?.addEventListener('click', exportToJson);
        document.getElementById('exportCsvBtn')?.addEventListener('click', exportToCsv);
    }

    function filterResults() {
        const searchTerm = document.getElementById('resultSearch')?.value.toLowerCase() || '';
        const resultItems = document.querySelectorAll('.result-item');
        
        let visibleCount = 0;
        resultItems.forEach(item => {
            const status = item.dataset.status?.toLowerCase() || '';
            const text = item.textContent.toLowerCase();
            
            let matchesFilter = currentFilter === 'all' || status === currentFilter;
            let matchesSearch = !searchTerm || text.includes(searchTerm);
            
            if (matchesFilter && matchesSearch) {
                item.style.display = '';
                visibleCount++;
            } else {
                item.style.display = 'none';
            }
        });
        
        // Show "no results" message if needed
        if (visibleCount === 0 && resultItems.length > 0) {
            const container = document.getElementById('resultsContainer');
            let noResults = container.querySelector('.no-results-message');
            if (!noResults) {
                noResults = document.createElement('div');
                noResults.className = 'no-results-message empty-state';
                noResults.innerHTML = '<p style="color: #999;">No results match your filter/search criteria</p>';
                container.appendChild(noResults);
            }
            noResults.style.display = 'block';
        } else {
            const noResults = document.querySelector('.no-results-message');
            if (noResults) noResults.style.display = 'none';
        }
    }

    function updateCounts(results) {
        currentResults = results;
        
        const counts = {
            all: results.length,
            match: results.filter(r => r.status === 'MATCH').length,
            mismatch: results.filter(r => r.status === 'MISMATCH').length,
            error: results.filter(r => r.status === 'ERROR').length
        };
        
        document.getElementById('countAll').textContent = counts.all;
        document.getElementById('countMatch').textContent = counts.match;
        document.getElementById('countMismatch').textContent = counts.mismatch;
        document.getElementById('countError').textContent = counts.error;
        
        // Show/hide filter panel and export buttons
        const filterPanel = document.getElementById('resultFilters');
        const exportJsonBtn = document.getElementById('exportJsonBtn');
        const exportCsvBtn = document.getElementById('exportCsvBtn');
        
        if (results.length > 0) {
            filterPanel.style.display = 'block';
            exportJsonBtn.style.display = 'inline-block';
            exportCsvBtn.style.display = 'inline-block';
        } else {
            filterPanel.style.display = 'none';
            exportJsonBtn.style.display = 'none';
            exportCsvBtn.style.display = 'none';
        }
    }

    function exportToJson() {
        if (currentResults.length === 0) return;
        
        const dataStr = JSON.stringify(currentResults, null, 2);
        const blob = new Blob([dataStr], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `api-forge-results-${new Date().toISOString().slice(0,19).replace(/:/g,'-')}.json`;
        link.click();
        URL.revokeObjectURL(url);
    }

    function exportToCsv() {
        if (currentResults.length === 0) return;
        
        const headers = ['Iteration', 'Operation', 'Status', 'API1 URL', 'API2 URL', 'API1 Duration (ms)', 'API2 Duration (ms)', 'Differences'];
        const rows = currentResults.map((r, idx) => [
            idx + 1,
            r.operationName || '',
            r.status || '',
            r.api1?.url || '',
            r.api2?.url || '',
            r.api1?.duration || '',
            r.api2?.duration || '',
            r.differences ? r.differences.join('; ') : ''
        ]);
        
        const csvContent = [
            headers.join(','),
            ...rows.map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(','))
        ].join('\n');
        
        const blob = new Blob([csvContent], { type: 'text/csv' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `api-forge-results-${new Date().toISOString().slice(0,19).replace(/:/g,'-')}.csv`;
        link.click();
        URL.revokeObjectURL(url);
    }

    // Expose functions globally
    window.resultFilters = {
        init: initResultFilters,
        updateCounts: updateCounts,
        filter: filterResults
    };

    // Auto-initialize on load
    window.addEventListener('DOMContentLoaded', initResultFilters);
})();
