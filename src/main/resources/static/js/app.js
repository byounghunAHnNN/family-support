/**
 * 화면 동작 — 내 상황(오른쪽 서랍) → POST /api/benefits/search → 카드 격자.
 * 검색어·판정 탭·카테고리 칩·정렬은 받아 온 결과 안에서 바로 거른다(서버를 다시 부르지 않음).
 * 입력값은 localStorage에만 둔다(서버 저장 없음). 저장소를 못 쓰는 환경(사생활 보호 모드 등)에서도 동작하게 try로 감싼다.
 */
(() => {
    const STORAGE_KEY = 'familySupport.profile';
    /** 관심 사업(별표) — 카드 id 목록. 내 상황처럼 이 브라우저에만 둔다. */
    const FAVORITE_KEY = 'familySupport.favorites';
    const STAGES = [
        ['PREPARING', '예비부모·난임'],
        ['PREGNANT', '임신 중'],
        ['BIRTH', '출산·입양'],
        ['INFANT', '영유아 자녀'],
        ['NEWLYWED', '신혼'],
    ];
    const FLAGS = [
        ['dualIncome', '맞벌이'], ['noHome', '무주택'], ['multiChild', '다자녀'], ['singleParent', '한부모'],
        ['disabled', '장애인 가구'], ['lowIncome', '기초·차상위'], ['multicultural', '다문화'],
    ];
    const TYPES = [
        ['CASH', '💰', '현금'], ['VOUCHER', '🎫', '바우처·이용권'], ['DISCOUNT', '🏷', '감면'],
        ['SERVICE', '🩺', '서비스'], ['GOODS', '📦', '현물·시설'], ['LOAN', '🏦', '융자'], ['ETC', '·', '기타'],
    ];
    const FIELDS = [
        ['PREGNANCY', '임신·출산'], ['CHILDCARE', '보육·돌봄'], ['HEALTH', '의료·건강'], ['LIVING', '생활·에너지'],
        ['HOUSING', '주거'], ['WORK', '일·휴가'], ['EDU', '교육·문화'], ['ETC', '기타'],
    ];
    const TYPE_MAP = Object.fromEntries(TYPES.map(([code, icon, label]) => [code, { icon, label }]));
    const FIELD_MAP = Object.fromEntries(FIELDS);

    const $ = (id) => document.getElementById(id);
    let regions = [];
    let items = [];
    const view = { status: 'ALL', keyword: '', sort: 'DEFAULT', types: new Set(), fields: new Set() };
    let pollTimer = null;
    let favorites = loadFavorites();

    /** HTML에 넣는 모든 외부 문자열은 esc를 거친다(공공 API 원문에 태그가 섞여 있을 수 있음). */
    function esc(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
    }

    /** 공식 링크는 http(s)만 허용(javascript: 등 차단). */
    function safeUrl(url) {
        return /^https?:\/\//i.test(url || '') ? url : null;
    }

    /** 원문 첫 줄(앞의 ○·- 같은 기호 제거). */
    function firstLine(text) {
        return (text || '').split('\n').map((s) => s.replace(/^[\s○●ㅇ\-·•*]+/, '').trim()).find((s) => s) || '';
    }

    function numberOrNull(id) {
        const v = $(id).value.trim();
        return v === '' ? null : Number(v);
    }

    // ── 입력 저장·복원 ───────────────────────────────────────
    function readForm() {
        const profile = {
            sido: $('sido').value,
            sigungu: $('sigungu').value || null,
            stages: STAGES.map(([code]) => code).filter((code) => $('stage-' + code).checked),
            parentAge: numberOrNull('parentAge'),
            childAge: numberOrNull('childAge'),
            householdSize: numberOrNull('householdSize'),
            annualIncomeManwon: numberOrNull('income'),
        };
        FLAGS.forEach(([id]) => { profile[id] = $(id).checked; });
        return profile;
    }

    function saveForm(profile) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
        } catch (e) {
            // 저장 불가 환경 — 다음 방문 때 다시 입력하면 된다
        }
    }

    /** 입력 지우기 — 브라우저에 저장된 내 상황을 지우고 칸·결과를 비운다(서버에는 원래 남는 것이 없다). */
    function clearProfile() {
        if (!confirm('입력한 내 상황을 지울까요? 이 브라우저에 저장된 값도 함께 지워집니다.')) return;
        try {
            localStorage.removeItem(STORAGE_KEY);
        } catch (e) {
            // 저장 불가 환경 — 칸만 비운다
        }
        $('profileForm').reset();
        fillSigungu(null);
        items = [];
        $('profileSummary').innerHTML = '';
        $('incomeBadge').classList.add('d-none');
        $('footnote').textContent = '';
        render();
        $('count').textContent = '';
        showNotice('입력을 지웠습니다. 거주 지역과 가구 상황을 다시 입력해 주세요.', 'info');
    }

    function loadFavorites() {
        try {
            return new Set(JSON.parse(localStorage.getItem(FAVORITE_KEY) || '[]'));
        } catch (e) {
            return new Set();
        }
    }

    function toggleFavorite(id) {
        favorites.has(id) ? favorites.delete(id) : favorites.add(id);
        try {
            localStorage.setItem(FAVORITE_KEY, JSON.stringify([...favorites]));
        } catch (e) {
            // 저장 불가 환경 — 이번 화면에서만 유지
        }
        render();
    }

    function loadSaved() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
        } catch (e) {
            return null;
        }
    }

    function fillForm(p) {
        if (!p) return;
        $('sido').value = p.sido || '';
        fillSigungu(p.sigungu);
        STAGES.forEach(([code]) => { $('stage-' + code).checked = (p.stages || []).includes(code); });
        $('parentAge').value = p.parentAge ?? '';
        $('childAge').value = p.childAge ?? '';
        $('householdSize').value = p.householdSize ?? '';
        $('income').value = p.annualIncomeManwon ?? '';
        FLAGS.forEach(([id]) => { $(id).checked = !!p[id]; });
    }

    /** 머리의 한 줄 요약 — 서랍을 열지 않아도 어떤 조건으로 보는지 알 수 있게. */
    function renderProfileSummary(p) {
        const parts = [];
        if (p.sido) parts.push(p.sido.replace(/(특별시|광역시|특별자치시|특별자치도|도)$/, '') + (p.sigungu ? ' ' + p.sigungu : ''));
        const stageLabels = STAGES.filter(([code]) => (p.stages || []).includes(code)).map(([, label]) => label);
        if (stageLabels.length) parts.push(stageLabels.join('·'));
        const ages = [p.parentAge != null ? p.parentAge + '세' : '', p.childAge != null ? '아이 ' + p.childAge + '세' : '']
            .filter(Boolean).join(' · ');
        if (ages) parts.push(ages);
        if (p.householdSize != null || p.annualIncomeManwon != null) {
            parts.push([p.householdSize != null ? p.householdSize + '인' : '',
                p.annualIncomeManwon != null ? '연 ' + Number(p.annualIncomeManwon).toLocaleString() + '만' : '']
                .filter(Boolean).join(' · '));
        }
        const flags = FLAGS.filter(([id]) => p[id]).map(([, label]) => label);
        if (flags.length) parts.push(flags.join(' · '));
        $('profileSummary').innerHTML = parts.map((t) => `<span class="badge">${esc(t)}</span>`).join('');
    }

    // ── 선택 목록 ───────────────────────────────────────────
    function renderStages() {
        $('stages').innerHTML = STAGES.map(([code, label]) =>
            `<input type="checkbox" class="btn-check" id="stage-${code}" autocomplete="off">
             <label class="btn btn-outline-secondary btn-sm" for="stage-${code}">${esc(label)}</label>`).join('');
    }

    function fillSido() {
        $('sido').innerHTML = '<option value="">선택</option>'
            + regions.map((r) => `<option value="${esc(r.name)}">${esc(r.name)}</option>`).join('');
    }

    function fillSigungu(selected) {
        const sido = regions.find((r) => r.name === $('sido').value);
        const list = sido ? sido.sigungus : [];
        $('sigungu').innerHTML = '<option value="">전체(선택 안 함)</option>'
            + list.map((s) => `<option value="${esc(s)}">${esc(s)}</option>`).join('');
        $('sigungu').value = list.includes(selected) ? selected : '';
    }

    // ── 검색 ────────────────────────────────────────────────
    async function search() {
        const profile = readForm();
        if (!profile.sido) {
            showNotice('거주 시·도를 선택해 주세요.', 'warning');
            openProfile();
            return;
        }
        saveForm(profile);
        renderProfileSummary(profile);
        $('grid').innerHTML = '<p class="text-muted">찾는 중…</p>';
        try {
            renderResult(await Api.post('/api/benefits/search', profile));
        } catch (e) {
            $('grid').innerHTML = '';
            showNotice(e.message, 'danger');
        }
    }

    function renderResult(data) {
        hideNotice();
        if (data.totalBenefits === 0) {
            showNotice('아직 지원사업을 모으는 중입니다. 첫 수집은 몇 분 걸립니다 — 끝나면 자동으로 다시 찾습니다.', 'info');
            startPolling();
        }
        // 기본 목록(조건 일치·확인 필요) 뒤에 제외된 사업(내 시·도·전국)을 붙여 두고 탭으로 나눠 본다
        items = data.items.concat(data.excluded || []);
        $('incomeBadge').textContent = data.medianIncomePercent != null ? `내 소득 ≈ 중위 ${data.medianIncomePercent}%` : '';
        $('incomeBadge').classList.toggle('d-none', data.medianIncomePercent == null);
        $('footnote').textContent = data.lastCollectedAt
            ? `마지막 수집 ${data.lastCollectedAt} · 저장된 사업 ${data.totalBenefits}건 · 출처: 정부24, 복지로. `
              + '같은 사업이 두 출처에 있으면 카드 한 장으로 묶었습니다. 신청 전 반드시 공식 안내를 확인하세요.'
            : '';
        render();
    }

    // ── 거르기·표시 ─────────────────────────────────────────
    function searchableText(item) {
        return [item.name, item.organization, ...item.sources.flatMap((s) => [s.summary, s.targetText, s.supportText])]
            .join(' ').replace(/\s+/g, '').toLowerCase();
    }

    /** '전체' 탭은 제외된 사업을 넣지 않는다. */
    const statusOk = (i, status) => (status === 'ALL' ? i.status !== 'EXCLUDED'
        : status === 'FAVORITE' ? favorites.has(i.id) : i.status === status);
    const typeOk = (i) => !view.types.size || i.supportTypes.some((t) => view.types.has(t));
    const fieldOk = (i) => !view.fields.size || i.fields.some((f) => view.fields.has(f));

    function keywordList() {
        const needle = view.keyword.replace(/\s+/g, '').toLowerCase();
        return items.filter((i) => !needle || searchableText(i).includes(needle));
    }

    /** 탭 옆 건수 — 지금 걸린 검색어·칩 기준으로 다시 센다(탭을 눌렀을 때 보일 건수와 같게). */
    function renderTabCounts(byKeyword) {
        const shown = byKeyword.filter((i) => typeOk(i) && fieldOk(i));
        $('matchedCount').textContent = shown.filter((i) => i.status === 'MATCHED').length;
        $('checkCount').textContent = shown.filter((i) => i.status === 'CHECK').length;
        $('excludedCount').textContent = shown.filter((i) => i.status === 'EXCLUDED').length;
        $('favoriteCount').textContent = shown.filter((i) => favorites.has(i.id)).length;
    }

    function render() {
        $('chipRows').classList.toggle('d-none', items.length === 0);
        const byKeyword = keywordList();
        renderTabCounts(byKeyword);
        const base = byKeyword.filter((i) => statusOk(i, view.status));
        renderChips('typeChips', TYPES.map(([code, icon, label]) => [code, icon + ' ' + label]),
            view.types, base.filter(fieldOk), 'supportTypes');
        renderChips('fieldChips', FIELDS, view.fields, base.filter(typeOk), 'fields');
        let list = base.filter((i) => typeOk(i) && fieldOk(i));
        if (view.sort === 'NAME') {
            list = [...list].sort((a, b) => a.name.localeCompare(b.name, 'ko'));
        }
        $('count').textContent = list.length + '건';
        renderSearchHint(byKeyword, list);
        if (list.length === 0) {
            if (view.status === 'FAVORITE') {
                $('grid').innerHTML = '<p class="text-muted">별표(☆)를 눌러 관심 사업을 모아 둘 수 있습니다. 이 브라우저에만 저장됩니다.</p>';
                return;
            }
            const hidden = view.status === 'EXCLUDED' ? 0
                : byKeyword.filter((i) => i.status === 'EXCLUDED' && typeOk(i) && fieldOk(i)).length;
            $('grid').innerHTML = !items.length ? ''
                : hidden ? `<p class="text-muted">내 조건에 맞는 사업이 없습니다. 내 조건으로는 해당하지 않는 사업이 ${hidden}건 있습니다 — `
                    + '<button type="button" class="btn btn-link p-0 align-baseline" data-goto="EXCLUDED">“제외” 탭에서 이유 보기</button></p>'
                : '<p class="text-muted">조건에 맞는 사업이 없습니다. 칩·검색어를 풀거나 내 상황을 바꿔 보세요.</p>';
            return;
        }
        $('grid').innerHTML = list.map(card).join('');
    }

    /** 검색어에 맞는데 지금 탭·칩 때문에 안 보이는 사업이 있으면 알려 준다. '관심' 탭은 일부러 고른 것이라 띄우지 않는다. */
    function renderSearchHint(byKeyword, list) {
        const hint = $('searchHint');
        const hidden = byKeyword.length - list.length;
        if (!view.keyword || hidden <= 0 || view.status === 'FAVORITE') {
            hint.classList.add('d-none');
            return;
        }
        const shown = new Set(list);
        const excludedOnly = view.status !== 'EXCLUDED'
            && byKeyword.filter((i) => !shown.has(i)).every((i) => i.status === 'EXCLUDED');
        hint.innerHTML = `🔍 '${esc(view.keyword)}' ${byKeyword.length}건 중 <strong>${list.length}건</strong>만 보입니다 — `
            + (excludedOnly
                ? `<button type="button" class="btn btn-link btn-sm p-0 align-baseline" data-goto="EXCLUDED">제외 탭에 ${hidden}건</button>`
                : `탭·칩 때문에 ${hidden}건이 가려져 있습니다. `
                    + '<button type="button" class="btn btn-link btn-sm p-0 align-baseline" data-reset-filters>탭·칩 풀기</button>');
        hint.classList.remove('d-none');
    }

    /** 칩마다 건수를 붙인다. 건수가 0이고 선택도 안 된 칩은 숨긴다. */
    function renderChips(targetId, options, selected, pool, prop) {
        $(targetId).innerHTML = options.map(([code, label]) => {
            const n = pool.filter((i) => i[prop].includes(code)).length;
            if (!n && !selected.has(code)) return '';
            return `<button type="button" class="filter-chip ${selected.has(code) ? 'on' : ''}" data-code="${code}">`
                + `${esc(label)}<span class="n">${n}</span></button>`;
        }).join('');
    }

    /** 원문 등록·수정 30일 이내 표시. */
    function freshBadge(freshness) {
        if (freshness === 'NEW') return '<span class="fresh new" title="원문 등록 30일 이내">NEW</span>';
        if (freshness === 'UPDATED') return '<span class="fresh upd" title="원문 수정 30일 이내">변경</span>';
        return '';
    }

    function card(item) {
        const lead = item.sources[0];
        const url = safeUrl(lead.detailUrl);
        const type = TYPE_MAP[item.supportTypes[0]] || TYPE_MAP.ETC;
        const mark = item.status === 'EXCLUDED' ? '✕' : '?';
        const reasons = item.status === 'MATCHED'
            ? '<div class="breason ok clamp1">✓ 지역·상황·나이·소득 조건에 맞습니다</div>'
            : item.reasons.slice(0, 2).map((r) => `<div class="breason clamp1 ${item.status === 'EXCLUDED' ? 'no' : ''}"`
                + ` title="${esc(r)}">${mark} ${esc(r)}</div>`).join('');
        const sources = item.sources.map((s) => s.sourceLabel).filter((v, i, a) => a.indexOf(v) === i).join('·');
        return `
        <article class="bcard ${esc(item.status)}">
            <div class="meta">
                <span class="status ${esc(item.status)}">${esc(item.statusLabel)}</span>
                <span class="badge text-bg-light border">${esc(item.regionLabel)}</span>
                <span class="type-badge">${type.icon} ${esc(type.label)}</span>
                <span class="source">${esc(sources)}</span>
                <button type="button" class="fav ${favorites.has(item.id) ? 'on' : ''}" data-fav="${esc(item.id)}"
                    title="관심 사업" aria-label="관심 사업 표시">${favorites.has(item.id) ? '★' : '☆'}</button>
            </div>
            <h3 class="bname clamp2" title="${esc(item.name)}">${freshBadge(item.freshness)}${esc(item.name)}</h3>
            <div class="bsupport clamp2">${esc(firstLine(lead.supportText) || firstLine(lead.summary))}</div>
            <div class="bperiod clamp1 ${esc(item.periodStatus)}" title="${esc(lead.applyPeriod || '')}">📅 ${esc(item.periodLabel)}</div>
            ${reasons}
            <div class="bactions">
                <button type="button" class="btn btn-outline-secondary" data-detail="${esc(item.id)}">상세</button>
                ${url ? `<a class="btn btn-brand" href="${esc(url)}" target="_blank" rel="noopener noreferrer">공식 안내</a>` : ''}
            </div>
        </article>`;
    }

    function selectTab(status) {
        view.status = status;
        $('tabs').querySelectorAll('.nav-link').forEach((b) => b.classList.toggle('active', b.dataset.filter === status));
        render();
    }

    // ── 상세 팝업 ───────────────────────────────────────────
    function openDetail(id) {
        const item = items.find((i) => i.id === id);
        if (!item) return;
        const type = (code) => (TYPE_MAP[code] || TYPE_MAP.ETC);
        $('detailMeta').innerHTML = `<span class="status ${esc(item.status)}">${esc(item.statusLabel)}</span>
            <span class="badge text-bg-light border">${esc(item.regionLabel)}</span>
            <span class="text-muted">${esc(item.organization)}</span>`;
        $('detailName').textContent = item.name;
        const reasons = item.reasons.length ? item.reasons : ['지역·상황·나이·소득 조건에 맞습니다.'];
        const categories = `지원 방식: ${item.supportTypes.map((c) => type(c).icon + ' ' + type(c).label).join(', ')}`
            + ` · 분야: ${item.fields.map((c) => FIELD_MAP[c] || c).join(', ')}`
            + (item.targetGroups.length ? ` · 특정 대상: <strong>${esc(item.targetGroups.join(', '))}</strong>` : '');
        const tabs = item.sources.length > 1
            ? `<ul class="nav nav-tabs mt-3" role="tablist">${item.sources.map((s, i) =>
                `<li class="nav-item"><button class="nav-link ${i === 0 ? 'active' : ''}" type="button" data-bs-toggle="tab"
                    data-bs-target="#src-${i}">${esc(s.sourceLabel)} 원문</button></li>`).join('')}</ul>` : '';
        const panes = item.sources.map((s, i) => `
            <div class="tab-pane ${i === 0 ? 'show active' : ''}" id="src-${i}">${sourceSections(s)}</div>`).join('');
        $('detailBody').innerHTML = `
            <h3>판정 이유</h3><ul class="mb-0">${reasons.map((r) => `<li>${esc(r)}</li>`).join('')}</ul>
            <h3>대상 상황</h3>${item.stages.map((s) => `<span class="chip">${esc(s)}</span>`).join('')}
            <h3>분류</h3><div class="small">${categories}</div>
            ${tabs}<div class="tab-content">${panes}</div>`;
        const url = safeUrl(item.sources[0].detailUrl);
        $('detailLink').classList.toggle('d-none', !url);
        $('detailLink').href = url || '#';
        bootstrap.Modal.getOrCreateInstance($('detailModal')).show();
    }

    function sourceSections(s) {
        const rows = [
            ['요약', s.summary], ['지원 대상', s.targetText], ['선정 기준', s.criteriaText], ['지원 내용', s.supportText],
            ['신청 방법', s.applyMethod], ['신청 기한', s.applyPeriod], ['문의', s.contact],
            ['출처', [s.sourceLabel, s.organization].filter(Boolean).join(' · ')], ['원문 수정일', s.sourceModified],
        ].filter(([, v]) => v);
        const link = safeUrl(s.detailUrl);
        return rows.map(([k, v]) => `<h3>${esc(k)}</h3><div class="txt">${esc(v)}</div>`).join('')
            + (link ? `<h3>공식 안내</h3><a href="${esc(link)}" target="_blank" rel="noopener noreferrer">${esc(link)}</a>` : '');
    }

    // ── 알림·서랍 ───────────────────────────────────────────
    function showNotice(message, kind) {
        const n = $('notice');
        n.className = 'alert alert-' + kind;
        n.textContent = message;
    }

    function hideNotice() {
        $('notice').className = 'alert d-none';
    }

    function openProfile() {
        bootstrap.Offcanvas.getOrCreateInstance($('profilePanel')).show();
    }

    /** 첫 수집 중이면 10초마다 상태를 보고, 끝나면 다시 검색한다. */
    function startPolling() {
        if (pollTimer) return;
        pollTimer = setInterval(async () => {
            try {
                const status = await Api.get('/api/collect/status');
                if (status.status !== 'RUNNING' && status.status !== 'NONE') {
                    clearInterval(pollTimer);
                    pollTimer = null;
                    search();
                }
            } catch (e) {
                // 다음 주기에 다시 시도
            }
        }, 10000);
    }

    // ── 시작 ───────────────────────────────────────────────
    function bindEvents() {
        $('sido').addEventListener('change', () => fillSigungu(null));
        $('clearProfile').addEventListener('click', clearProfile);
        $('profileForm').addEventListener('submit', (e) => {
            e.preventDefault();
            bootstrap.Offcanvas.getOrCreateInstance($('profilePanel')).hide();
            search();
        });
        $('tabs').addEventListener('click', (e) => {
            const button = e.target.closest('[data-filter]');
            if (button) selectTab(button.dataset.filter);
        });
        $('keyword').addEventListener('input', (e) => {
            view.keyword = e.target.value.trim();
            render();
        });
        $('searchHint').addEventListener('click', (e) => {
            if (e.target.closest('[data-reset-filters]')) {
                view.types.clear();
                view.fields.clear();
                selectTab('ALL');
                return;
            }
            const goto = e.target.closest('[data-goto]');
            if (goto) selectTab(goto.dataset.goto);
        });
        $('sort').addEventListener('change', (e) => {
            view.sort = e.target.value;
            render();
        });
        [['typeChips', view.types], ['fieldChips', view.fields]].forEach(([id, set]) => {
            $(id).addEventListener('click', (e) => {
                const chip = e.target.closest('[data-code]');
                if (!chip) return;
                set.has(chip.dataset.code) ? set.delete(chip.dataset.code) : set.add(chip.dataset.code);
                render();
            });
        });
        $('grid').addEventListener('click', (e) => {
            const fav = e.target.closest('[data-fav]');
            if (fav) {
                toggleFavorite(fav.dataset.fav);
                return;
            }
            const button = e.target.closest('[data-detail]');
            if (button) openDetail(button.dataset.detail);
            const goto = e.target.closest('[data-goto]');
            if (goto) selectTab(goto.dataset.goto);
        });
    }

    async function init() {
        renderStages();
        bindEvents();
        try {
            regions = (await Api.get('/api/benefits/regions')).sidos;
        } catch (e) {
            showNotice(e.message, 'danger');
        }
        fillSido();
        const saved = loadSaved();
        fillForm(saved);
        if (saved && saved.sido) {
            search();
        } else {
            showNotice('오른쪽 위 "내 상황"에서 거주 지역과 가구 상황을 입력해 주세요.', 'info');
            openProfile();
        }
    }

    init();
})();
