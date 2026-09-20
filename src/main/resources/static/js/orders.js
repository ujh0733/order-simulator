(() => {
    const STATUS_LABELS = {
        RECEIVED: '접수',
        COOKING: '조리 중',
        DISPATCHED: '배차',
        COMPLETED: '완료',
    };

    const keywordInput = document.getElementById('filter-keyword');
    const statusSelect = document.getElementById('filter-status');
    const fromInput = document.getElementById('filter-from');
    const toInput = document.getElementById('filter-to');
    const searchBtn = document.getElementById('filter-search-btn');
    const tableBody = document.getElementById('orders-table-body');
    const pageInfo = document.getElementById('page-info');
    const prevBtn = document.getElementById('prev-page-btn');
    const nextBtn = document.getElementById('next-page-btn');

    const PAGE_SIZE = 20;
    let currentPage = 0;
    let totalPages = 0;

    function statusLabel(status) {
        return STATUS_LABELS[status] ?? status;
    }

    function formatDateTime(isoString) {
        if (!isoString) return '-';
        const date = new Date(isoString);
        return date.toLocaleString('ko-KR', { hour12: false });
    }

    function buildQuery(page) {
        const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
        if (keywordInput.value.trim()) params.set('keyword', keywordInput.value.trim());
        if (statusSelect.value) params.set('status', statusSelect.value);
        if (fromInput.value) params.set('from', fromInput.value);
        if (toInput.value) params.set('to', toInput.value);
        return params.toString();
    }

    async function loadPage(page) {
        const response = await fetch(`/api/orders?${buildQuery(page)}`);
        if (!response.ok) {
            tableBody.innerHTML = '<tr><td colspan="9">조회 중 오류가 발생했습니다.</td></tr>';
            return;
        }
        const data = await response.json();
        currentPage = data.page;
        totalPages = data.totalPages;

        if (data.content.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="9">조건에 맞는 주문이 없습니다.</td></tr>';
        } else {
            tableBody.innerHTML = data.content
                .map((o) => `
                    <tr>
                        <td>#${o.id}</td>
                        <td>${o.userName} (${o.userPhone})</td>
                        <td>${o.merchantName}</td>
                        <td>${o.regionName}</td>
                        <td>${o.riderName ?? '-'}</td>
                        <td>${statusLabel(o.status)}</td>
                        <td>${o.estimatedDeliveryMinutes}</td>
                        <td>${formatDateTime(o.orderedAt)}</td>
                        <td>${formatDateTime(o.completedAt)}</td>
                    </tr>
                `)
                .join('');
        }

        const totalLabel = data.totalElements.toLocaleString();
        pageInfo.textContent = totalPages === 0
            ? `총 0건`
            : `${currentPage + 1} / ${totalPages} 페이지 (총 ${totalLabel}건)`;
        prevBtn.disabled = currentPage <= 0;
        nextBtn.disabled = currentPage >= totalPages - 1;
    }

    searchBtn.addEventListener('click', () => loadPage(0));
    keywordInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') loadPage(0);
    });
    prevBtn.addEventListener('click', () => {
        if (currentPage > 0) loadPage(currentPage - 1);
    });
    nextBtn.addEventListener('click', () => {
        if (currentPage < totalPages - 1) loadPage(currentPage + 1);
    });

    loadPage(0);
})();
