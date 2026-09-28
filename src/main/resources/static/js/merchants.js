(() => {
    const keywordInput = document.getElementById('filter-keyword');
    const sidoSelect = document.getElementById('filter-sido');
    const regionSelect = document.getElementById('filter-region');
    const statusSelect = document.getElementById('filter-status');
    const cookingSelect = document.getElementById('filter-cooking');
    const searchBtn = document.getElementById('filter-search-btn');
    const resetBtn = document.getElementById('filter-reset-btn');
    const tableBody = document.getElementById('merchants-table-body');
    const summary = document.getElementById('result-summary');
    const pageInfo = document.getElementById('page-info');
    const prevBtn = document.getElementById('prev-page-btn');
    const nextBtn = document.getElementById('next-page-btn');

    const PAGE_SIZE = 20;
    let currentPage = 0;
    let totalPages = 0;
    let requestSeq = 0;
    let allRegions = [];
    let sidoCounts = new Map();

    // 공공데이터 원본 텍스트가 그대로 들어오므로 innerHTML에 넣기 전에 반드시 이스케이프한다.
    function esc(value) {
        return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
        }[ch]));
    }

    function cell(value) {
        return value === null || value === undefined || value === '' ? '-' : esc(value);
    }

    function fillSelect(select, options) {
        options.forEach((o) => {
            const opt = document.createElement('option');
            opt.value = o.value;
            opt.textContent = `${o.label} (${Number(o.count).toLocaleString()})`;
            if (Number(o.count) === 0) opt.classList.add('empty-option');
            select.appendChild(opt);
        });
    }

    // 시/도를 고르면 그 시/도에 속한 시/군/구만 보여준다. 시/도를 고르지 않으면 비활성화.
    function populateRegions() {
        regionSelect.length = 1;
        regionSelect.value = '';
        const sido = sidoSelect.value;
        regionSelect.disabled = !sido;
        if (!sido) return;
        fillSelect(regionSelect, allRegions.filter((r) => r.sido === sido));
    }

    async function loadFacets() {
        const response = await fetch('/api/merchants/facets');
        if (!response.ok) return;
        const facets = await response.json();
        allRegions = facets.regions;
        sidoCounts = new Map(facets.sidos.map((o) => [o.value, Number(o.count)]));
        fillSelect(sidoSelect, facets.sidos);
        fillSelect(statusSelect, facets.businessStatuses);
        fillSelect(cookingSelect, facets.cookingMinutes);
    }

    // 선택한 지역 자체에 가게가 하나도 없는 경우와, 다른 조건(검색어 등) 때문에 결과가 비는 경우를 구분해서 안내한다.
    function emptyMessage() {
        const sido = sidoSelect.value;
        const region = regionSelect.value;
        if (region) {
            const found = allRegions.find((r) => r.sido === sido && r.value === region);
            if (found && Number(found.count) === 0) {
                return { title: `${sido} ${region}에는 등록된 가게가 없습니다.`, sub: '이 지역의 가게 데이터가 아직 없습니다.' };
            }
        } else if (sido && sidoCounts.get(sido) === 0) {
            return { title: `${sido}에는 등록된 가게가 없습니다.`, sub: '이 지역의 가게 데이터가 아직 없습니다.' };
        }
        return { title: '조건에 맞는 가게가 없습니다.', sub: '검색어나 카테고리를 바꿔 보세요.' };
    }

    function buildQuery(page) {
        const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
        if (keywordInput.value.trim()) params.set('keyword', keywordInput.value.trim());
        if (sidoSelect.value) params.set('sido', sidoSelect.value);
        if (regionSelect.value) params.set('region', regionSelect.value);
        if (statusSelect.value) params.set('businessStatus', statusSelect.value);
        if (cookingSelect.value) params.set('cookingMinutes', cookingSelect.value);
        return params.toString();
    }

    async function loadPage(page) {
        // 조회를 연달아 눌렀을 때 늦게 도착한 이전 응답이 최신 결과를 덮어쓰지 않도록 한다.
        const seq = ++requestSeq;
        const response = await fetch(`/api/merchants?${buildQuery(page)}`);
        if (seq !== requestSeq) return;
        if (!response.ok) {
            tableBody.innerHTML = '<tr><td colspan="10">조회 중 오류가 발생했습니다.</td></tr>';
            return;
        }
        const data = await response.json();
        currentPage = data.page;
        totalPages = data.totalPages;

        if (data.content.length === 0) {
            const msg = emptyMessage();
            tableBody.innerHTML = `
                <tr class="empty-row">
                    <td colspan="10">
                        <div class="empty-state">
                            <img src="/img/empty-store.svg" alt="" width="160" height="130">
                            <p class="empty-title">${esc(msg.title)}</p>
                            <p class="empty-sub">${esc(msg.sub)}</p>
                        </div>
                    </td>
                </tr>`;
        } else {
            tableBody.innerHTML = data.content
                .map((m) => `
                    <tr>
                        <td>#${m.id}</td>
                        <td>${esc(m.name)}</td>
                        <td>${esc(m.sidoName)} ${esc(m.regionName)}</td>
                        <td>${cell(m.businessStatusName)}</td>
                        <td>${cell(m.roadAddress)}</td>
                        <td>${cell(m.phone)}</td>
                        <td>${cell(m.licenseDate)}</td>
                        <td>${m.cookingMinutes}</td>
                        <td>${m.maxConcurrentCooking}</td>
                        <td>${Number(m.orderCount).toLocaleString()}건</td>
                    </tr>
                `)
                .join('');
        }

        summary.textContent = `조건에 맞는 가게 ${data.totalElements.toLocaleString()}곳`;
        pageInfo.textContent = totalPages === 0
            ? '총 0건'
            : `${currentPage + 1} / ${totalPages.toLocaleString()} 페이지`;
        prevBtn.disabled = currentPage <= 0;
        nextBtn.disabled = currentPage >= totalPages - 1;
    }

    function reset() {
        keywordInput.value = '';
        [sidoSelect, statusSelect, cookingSelect].forEach((s) => { s.value = ''; });
        populateRegions();
        loadPage(0);
    }

    searchBtn.addEventListener('click', () => loadPage(0));
    resetBtn.addEventListener('click', reset);
    keywordInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') loadPage(0);
    });
    sidoSelect.addEventListener('change', () => {
        populateRegions();
        loadPage(0);
    });
    [regionSelect, statusSelect, cookingSelect].forEach((s) => {
        s.addEventListener('change', () => loadPage(0));
    });
    prevBtn.addEventListener('click', () => {
        if (currentPage > 0) loadPage(currentPage - 1);
    });
    nextBtn.addEventListener('click', () => {
        if (currentPage < totalPages - 1) loadPage(currentPage + 1);
    });

    loadFacets();
    loadPage(0);
})();
