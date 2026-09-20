(() => {
    const WIDTH = 600;
    const HEIGHT = 600;
    const MAP_SUPPORTED_SIDO = new Set(['서울특별시']);

    const csrfToken = document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

    const tooltip = document.getElementById('tooltip');
    const unitSelect = document.getElementById('unit-select');
    const customRange = document.getElementById('custom-range');
    const fromDateInput = document.getElementById('from-date');
    const toDateInput = document.getElementById('to-date');
    const searchBtn = document.getElementById('search-btn');
    const sidoSelect = document.getElementById('sido-select');
    const mapContainer = document.getElementById('map');
    const mapUnavailable = document.getElementById('map-unavailable');

    let mapSelection = null;
    let colorScale = null;
    let regionDataByName = new Map();
    let mapInitialized = false;
    let currentSido = '서울특별시';

    function buildStatsQuery() {
        const unit = unitSelect.value;
        const params = new URLSearchParams({ unit, sido: currentSido });
        if (unit === 'CUSTOM') {
            params.set('from', fromDateInput.value);
            params.set('to', toDateInput.value);
        }
        return params.toString();
    }

    async function loadSidoMenu() {
        const response = await fetch('/api/regions/sido');
        const sidoList = await response.json();

        sidoSelect.innerHTML = sidoList
            .map((s) => `<option value="${s.name}">${s.name}</option>`)
            .join('');

        if (sidoList.length > 0) {
            currentSido = sidoList[0].name;
            sidoSelect.value = currentSido;
        }
    }

    async function refreshStats() {
        if (unitSelect.value === 'CUSTOM' && (!fromDateInput.value || !toDateInput.value)) {
            return;
        }

        const response = await fetch(`/api/stats/orders?${buildStatsQuery()}`);
        if (!response.ok) {
            console.error('통계 조회 실패', response.status);
            return;
        }
        const data = await response.json();

        regionDataByName = new Map(data.map((d) => [d.regionName, d]));

        if (mapInitialized) {
            const maxCount = Math.max(1, ...data.map((d) => d.count));
            colorScale.domain([0, maxCount]);
            mapSelection
                .transition().duration(250)
                .attr('fill', (d) => {
                    const count = regionDataByName.get(d.properties.name)?.count ?? 0;
                    return count === 0 ? '#eef1f6' : colorScale(count);
                });
        }

        renderTable(data);
    }

    function renderTable(data) {
        const tbody = document.getElementById('region-table-body');
        const sorted = [...data].sort((a, b) => b.count - a.count);
        tbody.innerHTML = sorted
            .map((d) => `<tr><td>${d.regionName}</td><td>${d.count.toLocaleString()}</td></tr>`)
            .join('');
    }

    function showTooltip(event, feature) {
        const name = feature.properties.name;
        const region = regionDataByName.get(name);
        const count = region?.count ?? 0;
        const breakdown = region?.byMerchantRegion ?? [];

        const breakdownHtml = breakdown.length > 0
            ? `<ul class="tooltip-breakdown">${breakdown
                .map((b) => `<li>${b.regionName} 가게: ${b.count.toLocaleString()}건</li>`)
                .join('')}</ul>`
            : '';

        tooltip.style.display = 'block';
        tooltip.style.left = `${event.clientX + 12}px`;
        tooltip.style.top = `${event.clientY + 12}px`;
        tooltip.innerHTML =
            `<div class="tooltip-title">${name} (주문자 기준): ${count.toLocaleString()}건</div>${breakdownHtml}`;
    }

    function hideTooltip() {
        tooltip.style.display = 'none';
    }

    async function initMap() {
        if (mapInitialized) return;

        const geojson = await d3.json('/data/seoul-districts.geojson');

        const svg = d3.select('#map')
            .append('svg')
            .attr('viewBox', `0 0 ${WIDTH} ${HEIGHT}`);

        const projection = d3.geoMercator().fitSize([WIDTH, HEIGHT], geojson);
        const path = d3.geoPath().projection(projection);
        colorScale = d3.scaleSequential(d3.interpolateBlues);

        mapSelection = svg.append('g')
            .selectAll('path')
            .data(geojson.features)
            .join('path')
            .attr('d', path)
            .attr('fill', '#eef1f6')
            .on('mousemove', showTooltip)
            .on('mouseleave', hideTooltip);

        mapInitialized = true;
    }

    function applySidoView() {
        const supported = MAP_SUPPORTED_SIDO.has(currentSido);
        mapUnavailable.style.display = supported ? 'none' : 'block';

        if (supported) {
            mapContainer.style.display = '';
            initMap().then(refreshStats);
        } else {
            mapContainer.style.display = 'none';
            refreshStats();
        }
    }

    const loadCountInput = document.getElementById('load-count');
    const loadDurationInput = document.getElementById('load-duration');
    const loadGenerateBtn = document.getElementById('load-generate-btn');
    const loadProgress = document.getElementById('load-progress');
    const loadProgressFill = document.getElementById('load-progress-fill');
    const simStatus = document.getElementById('sim-status');
    let pollTimer = null;

    async function startLoadGeneration() {
        const count = parseInt(loadCountInput.value, 10);
        const durationMinutes = parseInt(loadDurationInput.value, 10);
        if (!count || count <= 0) {
            simStatus.textContent = '건수는 1 이상의 숫자를 입력하세요.';
            return;
        }
        if (!durationMinutes || durationMinutes <= 0) {
            simStatus.textContent = '소요 시간(분)은 1 이상의 숫자를 입력하세요.';
            return;
        }
        if (!window.confirm(`${durationMinutes.toLocaleString()}분 동안 ${count.toLocaleString()}건을 생성하시겠습니까?`)) {
            return;
        }

        const response = await fetch('/api/simulation/load', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken,
            },
            body: JSON.stringify({ count, durationMinutes }),
        });

        if (!response.ok) {
            const body = await response.json().catch(() => null);
            simStatus.textContent = body?.message ?? '생성을 시작하지 못했습니다.';
            return;
        }

        setGeneratingUi(true);
        pollLoadStatus();
    }

    function setGeneratingUi(running) {
        loadCountInput.disabled = running;
        loadDurationInput.disabled = running;
        loadGenerateBtn.disabled = running;
        loadProgress.style.display = running ? 'block' : 'none';
    }

    function pollLoadStatus() {
        if (pollTimer) clearInterval(pollTimer);
        pollTimer = setInterval(async () => {
            const response = await fetch('/api/simulation/load');
            const status = await response.json();
            renderLoadStatus(status);

            if (!status.running) {
                clearInterval(pollTimer);
                pollTimer = null;
                setGeneratingUi(false);
                refreshStats();
            }
        }, 1000);
    }

    function renderLoadStatus(status) {
        const percentage = status.total > 0 ? Math.floor((status.completed / status.total) * 100) : 0;
        loadProgressFill.style.width = `${percentage}%`;

        const elapsedMinutes = Math.floor(status.elapsedSeconds / 60);
        const elapsedLabel = `${elapsedMinutes}분 경과 / ${status.durationMinutes}분 목표`;

        if (status.failedMessage) {
            simStatus.textContent = `생성 중 오류: ${status.failedMessage}`;
        } else if (status.running) {
            simStatus.textContent =
                `생성 중... ${status.completed.toLocaleString()} / ${status.total.toLocaleString()}건 (${percentage}%) · ${elapsedLabel}`;
        } else if (status.total > 0) {
            simStatus.textContent = `완료: ${status.completed.toLocaleString()}건 생성됨 (${elapsedLabel})`;
        }
    }

    const STATUS_LABELS = {
        RECEIVED: '접수',
        COOKING: '조리 중',
        DISPATCHED: '배차',
        COMPLETED: '완료',
    };

    function statusLabel(status) {
        return STATUS_LABELS[status] ?? status;
    }

    function formatEventTime(isoString) {
        const date = new Date(isoString);
        return date.toLocaleTimeString('ko-KR', { hour12: false });
    }

    async function refreshPipeline() {
        const status = await (await fetch('/api/pipeline/status')).json();
        document.getElementById('stat-received').textContent = status.received.toLocaleString();
        document.getElementById('stat-cooking').textContent = status.cooking.toLocaleString();
        document.getElementById('stat-dispatched').textContent = status.dispatched.toLocaleString();
        document.getElementById('stat-completed').textContent = status.completed.toLocaleString();
        document.getElementById('stat-riders-available').textContent = status.ridersAvailable.toLocaleString();
        document.getElementById('stat-riders-busy').textContent = status.ridersBusy.toLocaleString();

        const events = await (await fetch('/api/pipeline/events')).json();
        document.getElementById('pipeline-event-body').innerHTML = events
            .map((e) => `
                <tr>
                    <td>#${e.orderId}</td>
                    <td>${e.merchantName}</td>
                    <td>${e.riderName ?? '-'}</td>
                    <td>${e.fromStatus ? statusLabel(e.fromStatus) : '신규'} → ${statusLabel(e.toStatus)}</td>
                    <td>${formatEventTime(e.occurredAt)}</td>
                </tr>
            `)
            .join('');

        const dispatches = await (await fetch('/api/pipeline/dispatches')).json();
        document.getElementById('pipeline-dispatch-body').innerHTML = dispatches
            .map((d) => `
                <tr>
                    <td>${d.riderName}</td>
                    <td>#${d.orderId}</td>
                    <td>${d.merchantName}</td>
                    <td>${d.regionName}</td>
                    <td>${formatEventTime(d.dispatchedAt)}</td>
                </tr>
            `)
            .join('');
    }

    async function syncLoadStatusOnLoad() {
        const response = await fetch('/api/simulation/load');
        const status = await response.json();
        if (status.running) {
            setGeneratingUi(true);
            renderLoadStatus(status);
            pollLoadStatus();
        } else if (status.total > 0) {
            renderLoadStatus(status);
        }
    }

    sidoSelect.addEventListener('change', () => {
        currentSido = sidoSelect.value;
        applySidoView();
    });
    unitSelect.addEventListener('change', () => {
        customRange.style.display = unitSelect.value === 'CUSTOM' ? 'flex' : 'none';
        if (unitSelect.value !== 'CUSTOM') {
            refreshStats();
        }
    });
    searchBtn.addEventListener('click', refreshStats);
    loadGenerateBtn.addEventListener('click', startLoadGeneration);

    (async () => {
        await loadSidoMenu();
        applySidoView();
    })();
    syncLoadStatusOnLoad();
    refreshPipeline();
    setInterval(refreshStats, 15000);
    setInterval(refreshPipeline, 3000);
})();
