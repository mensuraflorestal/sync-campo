// SyncCampo - RS Florestal (Material Design 3 Android Edition)
const FILES_CACHE_NAME = 'sync-campo-files-v1';
const STORAGE_FILES_KEY = 'sync_campo_files_v1';
const STORAGE_CACHED_IDS_KEY = 'sync_campo_cached_ids_v1';
const STORAGE_LAST_SYNC_KEY = 'sync_campo_last_sync_v1';
const STORAGE_SERVER_URL_KEY = 'sync_campo_server_url';
const STORAGE_VIEW_MODE_KEY = 'sync_campo_view_mode';

function getApiUrl(path) {
  let custom = localStorage.getItem(STORAGE_SERVER_URL_KEY);
  if (!custom && window.NativeBridge && typeof window.NativeBridge.getServerUrl === 'function') {
    custom = window.NativeBridge.getServerUrl();
    if (custom) localStorage.setItem(STORAGE_SERVER_URL_KEY, custom);
  }
  if (custom && custom.trim()) {
    const base = custom.trim().replace(/\/+$/, '');
    return `${base}${path}`;
  }
  return path;
}

// Configura o worker do PDF.js para funcionar 100% offline
if (window.pdfjsLib) {
  window.pdfjsLib.GlobalWorkerOptions.workerSrc = 'vendor/pdfjs/pdf.worker.min.js';
}

function loadStoredFiles() {
  try {
    const raw = localStorage.getItem(STORAGE_FILES_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed.map(normalizeItem);
      }
    }
  } catch (e) {}
  return [];
}

function loadStoredCachedIds() {
  try {
    const raw = localStorage.getItem(STORAGE_CACHED_IDS_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return new Set(parsed);
    }
  } catch (e) {}
  return new Set();
}

function normalizeItem(item) {
  const isFolder = item.is_folder === true || item.mimeType === 'application/vnd.google-apps.folder';
  let parentId = item.parentId;
  if (!parentId) {
    const folderName = (item.folder_path || item.folder || '').trim();
    if (!folderName || folderName === 'Raiz') {
      parentId = 'root';
    } else {
      parentId = folderName;
    }
  }
  return {
    ...item,
    is_folder: isFolder,
    parentId: parentId || 'root'
  };
}

// Estado global do aplicativo
let state = {
  files: loadStoredFiles().map(normalizeItem),
  cachedFileIds: loadStoredCachedIds(),
  currentFolderId: 'root',
  folderHistory: [{ id: 'root', name: 'Meu Drive' }],
  viewMode: localStorage.getItem(STORAGE_VIEW_MODE_KEY) || 'grid',
  activeFilter: 'all',
  activeTab: 'files', // 'files' | 'offline' | 'sync'
  searchQuery: '',
  isSyncing: false,
  selectedFileForSheet: null,
  
  // Estado do leitor de PDF
  pdfDoc: null,
  pdfCurrentPage: 1,
  pdfTotalPages: 0,
  pdfScale: 1.2,
  currentObjectUrl: null
};

// Elementos da Interface
const dom = {
  // Top App Bar
  networkBadge: document.getElementById('network-badge'),
  networkText: document.getElementById('network-text'),
  searchInput: document.getElementById('search-input'),
  searchClearBtn: document.getElementById('search-clear-btn'),
  btnTopNav: document.getElementById('top-nav-box'),
  btnOpenServerSettings: document.getElementById('btn-open-server-settings'),
  serverOfflineBanner: document.getElementById('server-offline-banner'),
  btnBannerConnect: document.getElementById('btn-banner-connect'),
  demoBadge: document.getElementById('demo-badge'),
  demoText: document.getElementById('demo-text'),

  // Filtros & Navegação
  filterChips: document.querySelectorAll('.m3-chip'),
  filterContainer: document.querySelector('.m3-filter-chips-container'),
  subheadBar: document.querySelector('.m3-subhead-bar'),
  breadcrumbTrail: document.getElementById('breadcrumb-trail'),
  btnDriveBack: document.getElementById('btn-drive-back'),
  btnToggleView: document.getElementById('btn-toggle-view'),
  viewToggleIcon: document.getElementById('view-toggle-icon'),
  filesCounter: document.getElementById('files-counter'),

  // Área de Conteúdo
  driveContentArea: document.getElementById('drive-content-area'),
  driveFoldersSection: document.getElementById('drive-folders-section'),
  foldersContainer: document.getElementById('folders-container'),
  foldersCounter: document.getElementById('folders-counter'),
  driveFilesSection: document.getElementById('drive-files-section'),
  filesContainer: document.getElementById('files-container'),
  filesSectionCounter: document.getElementById('files-section-counter'),
  driveEmptyState: document.getElementById('drive-empty-state'),

  // Aba Central de Sincronização
  tabSyncView: document.getElementById('tab-sync-view'),
  statTotal: document.getElementById('stat-total-files'),
  statOffline: document.getElementById('stat-offline-files'),
  btnSyncHero: document.getElementById('btn-sync-all'),
  syncHeroIcon: document.getElementById('sync-icon'),
  syncHeroLabel: document.getElementById('sync-btn-label'),
  progressBox: document.getElementById('sync-progress-box'),
  progressFill: document.getElementById('sync-progress-fill'),
  progressStatus: document.getElementById('sync-progress-status'),
  progressPct: document.getElementById('sync-progress-pct'),
  btnOpenServerFromSync: document.getElementById('btn-open-server-from-sync'),
  btnOpenApkFromSync: document.getElementById('btn-open-apk-modal'),

  // Floating Action Button & Bottom Navigation
  fabSync: document.getElementById('m3-fab-sync'),
  bottomNavItems: document.querySelectorAll('.m3-nav-item'),

  // Bottom Sheet
  sheetBackdrop: document.getElementById('file-bottom-sheet'),
  sheetFileIcon: document.getElementById('sheet-file-icon'),
  sheetFileName: document.getElementById('sheet-file-title'),
  sheetFileMeta: document.getElementById('sheet-file-meta'),
  sheetActionOpen: document.getElementById('sheet-action-open'),
  sheetActionToggleOffline: document.getElementById('sheet-action-toggle-offline'),
  sheetOfflineIcon: document.getElementById('sheet-offline-icon'),
  sheetOfflineText: document.getElementById('sheet-offline-text'),
  sheetActionExternal: document.getElementById('sheet-action-external'),
  sheetActionClose: document.getElementById('sheet-action-close'),

  // Snackbar
  snackbar: document.getElementById('m3-snackbar'),
  snackbarText: document.getElementById('snackbar-text'),

  // Modal & Visualizador
  modal: document.getElementById('file-modal'),
  modalFileIcon: document.getElementById('modal-file-icon'),
  modalFilename: document.getElementById('modal-filename'),
  modalContent: document.getElementById('modal-content'),
  modalClose: document.getElementById('btn-close-modal'),
  modalBtnClose: document.getElementById('btn-modal-close'),
  modalBtnDownload: document.getElementById('btn-modal-download'),
  btnModalFullscreen: document.getElementById('btn-modal-fullscreen'),

  // Controles de PDF
  pdfControls: document.getElementById('pdf-controls'),
  pdfPrevPage: document.getElementById('pdf-prev-page'),
  pdfNextPage: document.getElementById('pdf-next-page'),
  pdfPageNum: document.getElementById('pdf-page-num'),
  pdfZoomIn: document.getElementById('pdf-zoom-in'),
  pdfZoomOut: document.getElementById('pdf-zoom-out'),
  pdfZoomFit: document.getElementById('pdf-zoom-fit')
};

// ==========================================
// 1. SNACKBAR NATIVO DO ANDROID
// ==========================================
let snackbarTimer = null;
function showSnackbar(text, duration = 3000) {
  if (!dom.snackbar) return;
  dom.snackbarText.textContent = text;
  dom.snackbar.classList.add('active');
  clearTimeout(snackbarTimer);
  snackbarTimer = setTimeout(() => {
    dom.snackbar.classList.remove('active');
  }, duration);
}

// ==========================================
// 2. MONITORAMENTO DE REDE E SERVIDOR CENTRAL
// ==========================================
function markServerConnected(message = 'Online') {
  if (dom.networkBadge) {
    dom.networkBadge.className = 'm3-network-badge online';
    dom.networkText.textContent = message;
  }
  if (dom.btnSyncHero) dom.btnSyncHero.disabled = false;
  if (dom.serverOfflineBanner) dom.serverOfflineBanner.style.display = 'none';
}

function markServerDisconnected(reason = 'Desconectado') {
  if (dom.networkBadge) {
    dom.networkBadge.className = 'm3-network-badge offline';
    dom.networkText.textContent = reason;
  }
  if (dom.serverOfflineBanner) dom.serverOfflineBanner.style.display = 'flex';
}

function updateNetworkStatus() {
  const isOnline = navigator.onLine;
  if (isOnline) {
    if (dom.btnSyncHero) dom.btnSyncHero.disabled = false;
  } else {
    markServerDisconnected('Modo Campo');
    if (dom.btnSyncHero) dom.btnSyncHero.disabled = true;
  }
}

window.addEventListener('online', () => {
  updateNetworkStatus();
  fetchStatusAndFiles();
});
window.addEventListener('offline', updateNetworkStatus);

// ==========================================
// 3. CARREGAMENTO DOS DADOS (API / DRIVE)
// ==========================================
async function fetchStatusAndFiles() {
  await refreshCachedFilesStatus();
  renderDriveView();

  if (!navigator.onLine) {
    markServerDisconnected('Modo Campo');
    return;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);
    const statusRes = await fetch(getApiUrl('/api/status'), { signal: controller.signal });
    clearTimeout(timeoutId);

    if (statusRes.ok) {
      const statusData = await statusRes.json();
      markServerConnected('Online');
      if (statusData.mode === 'demo_local') {
        if (dom.demoBadge) dom.demoBadge.style.display = 'flex';
        if (dom.demoText) dom.demoText.textContent = statusData.message;
      } else {
        if (dom.demoBadge) dom.demoBadge.style.display = 'none';
      }
    } else {
      markServerDisconnected('Servidor Erro');
    }

    const filesRes = await fetch(getApiUrl('/api/files'));
    if (filesRes.ok) {
      const data = await filesRes.json();
      if (Array.isArray(data.files) && data.files.length > 0) {
        state.files = data.files.map(normalizeItem);
        try {
          localStorage.setItem(STORAGE_FILES_KEY, JSON.stringify(state.files));
        } catch (e) {}
      }
    }
  } catch (err) {
    markServerDisconnected('Servidor Offline');
  }

  await refreshCachedFilesStatus();
  renderDriveView();
}

async function refreshCachedFilesStatus() {
  const savedIds = loadStoredCachedIds();
  savedIds.forEach(id => state.cachedFileIds.add(id));

  if ('caches' in window) {
    try {
      const cache = await caches.open(FILES_CACHE_NAME);
      const cachedRequests = await cache.keys();
      for (const req of cachedRequests) {
        const match = req.url.match(/\/api\/files\/([^\/]+)\/download/);
        if (match && match[1]) {
          state.cachedFileIds.add(decodeURIComponent(match[1]));
        } else {
          state.files.forEach(f => {
            if (!f.is_folder && (req.url.includes(encodeURIComponent(f.id)) || req.url.includes(f.id))) {
              state.cachedFileIds.add(f.id);
            }
          });
        }
      }
    } catch (e) {}
  }

  if (state.cachedFileIds.size > 0) {
    try {
      localStorage.setItem(STORAGE_CACHED_IDS_KEY, JSON.stringify([...state.cachedFileIds]));
    } catch (e) {}
  }

  const downloadable = state.files.filter(f => !f.is_folder && f.mimeType !== 'application/vnd.google-apps.folder');
  const folders = state.files.filter(f => f.is_folder || f.mimeType === 'application/vnd.google-apps.folder');
  const total = downloadable.length;
  const offline = state.cachedFileIds.size;

  if (dom.statTotal) dom.statTotal.textContent = `${total} arquivos (${folders.length} pastas)`;
  if (dom.statOffline) {
    dom.statOffline.textContent = total > 0 && offline >= total 
      ? `${offline} salvos (100% OK)` 
      : `${offline} salvos`;
  }
}

// ==========================================
// 4. SINCRONIZAÇÃO EM SEGUNDO PLANO
// ==========================================
async function syncAllFiles() {
  if (state.isSyncing) return;
  if (!navigator.onLine) {
    showSnackbar('⚠️ Conecte ao Wi-Fi para sincronizar arquivos.');
    return;
  }

  const files = state.files.filter(f => !f.is_folder && f.mimeType !== 'application/vnd.google-apps.folder');
  if (files.length === 0) {
    await fetchStatusAndFiles();
  }

  state.isSyncing = true;
  if (dom.btnSyncHero) dom.btnSyncHero.disabled = true;
  if (dom.syncHeroIcon) dom.syncHeroIcon.className = 'sync-icon-spin';
  if (dom.fabSync) {
    const icon = dom.fabSync.querySelector('.m3-fab-icon');
    if (icon) icon.className = 'm3-fab-icon sync-icon-spin';
  }
  if (dom.progressBox) dom.progressBox.classList.add('active');

  showSnackbar('🔄 Sincronizando documentos para o celular...');

  const total = files.length;
  let downloadedCount = 0;

  try {
    const cache = await caches.open(FILES_CACHE_NAME);

    for (let i = 0; i < total; i++) {
      const file = files[i];
      const downloadUrl = `/api/files/${encodeURIComponent(file.id)}/download`;
      const fullUrl = new URL(downloadUrl, window.location.origin).href;
      
      const pct = Math.round(((i) / total) * 100);
      if (dom.progressFill) dom.progressFill.style.width = `${pct}%`;
      if (dom.progressPct) dom.progressPct.textContent = `${pct}%`;
      if (dom.progressStatus) dom.progressStatus.textContent = `[${i + 1}/${total}] ${file.name}`;

      try {
        const response = await fetch(getApiUrl(downloadUrl));
        if (response.ok) {
          await cache.put(downloadUrl, response.clone());
          await cache.put(fullUrl, response.clone());
          state.cachedFileIds.add(file.id);
          downloadedCount++;
        }
      } catch (fileErr) {
        console.error(`Erro ao baixar ${file.name}:`, fileErr);
      }
    }

    try {
      localStorage.setItem(STORAGE_FILES_KEY, JSON.stringify(state.files));
      localStorage.setItem(STORAGE_CACHED_IDS_KEY, JSON.stringify([...state.cachedFileIds]));
      localStorage.setItem(STORAGE_LAST_SYNC_KEY, new Date().toISOString());
    } catch (e) {}

    showSnackbar(`✓ Sincronização concluída! ${downloadedCount} arquivos prontos offline.`);
  } catch (err) {
    showSnackbar('❌ Erro durante a sincronização.');
  } finally {
    state.isSyncing = false;
    if (dom.btnSyncHero) dom.btnSyncHero.disabled = false;
    if (dom.syncHeroIcon) dom.syncHeroIcon.className = '';
    if (dom.fabSync) {
      const icon = dom.fabSync.querySelector('.m3-fab-icon');
      if (icon) icon.className = 'm3-fab-icon';
    }
    if (dom.progressBox) dom.progressBox.classList.remove('active');
    await refreshCachedFilesStatus();
    renderDriveView();
  }
}

dom.btnSyncHero?.addEventListener('click', syncAllFiles);
dom.fabSync?.addEventListener('click', syncAllFiles);

// ==========================================
// 5. NAVEGAÇÃO ENTRE ABAS DA BOTTOM BAR
// ==========================================
function switchTab(tab) {
  state.activeTab = tab;
  dom.bottomNavItems.forEach(item => {
    item.classList.toggle('active', item.dataset.nav === tab);
  });

  if (tab === 'files') {
    dom.driveContentArea.style.display = 'flex';
    dom.tabSyncView.style.display = 'none';
    dom.filterContainer.style.display = 'block';
    dom.subheadBar.style.display = 'flex';
    dom.fabSync.style.display = 'flex';
    
    // Reseta filtro offline se estava na aba offline
    if (state.activeFilter === 'offline') {
      state.activeFilter = 'all';
      updateFilterChipsUi();
    }
    renderDriveView();
  } else if (tab === 'offline') {
    dom.driveContentArea.style.display = 'flex';
    dom.tabSyncView.style.display = 'none';
    dom.filterContainer.style.display = 'block';
    dom.subheadBar.style.display = 'flex';
    dom.fabSync.style.display = 'flex';

    state.activeFilter = 'offline';
    updateFilterChipsUi();
    renderDriveView();
  } else if (tab === 'sync') {
    dom.driveContentArea.style.display = 'none';
    dom.tabSyncView.style.display = 'flex';
    dom.filterContainer.style.display = 'none';
    dom.subheadBar.style.display = 'none';
    dom.fabSync.style.display = 'none';
    refreshCachedFilesStatus();
  }
}

dom.bottomNavItems.forEach(item => {
  item.addEventListener('click', () => {
    switchTab(item.dataset.nav);
  });
});

// ==========================================
// 6. GESTÃO DE PASTAS E NAVEGAÇÃO DRIVE
// ==========================================
function getFolderStats(folderId) {
  const children = state.files.filter(f => f.parentId === folderId);
  const folders = children.filter(f => f.is_folder || f.mimeType === 'application/vnd.google-apps.folder');
  const files = children.filter(f => !f.is_folder && f.mimeType !== 'application/vnd.google-apps.folder');
  const offlineFiles = files.filter(f => state.cachedFileIds.has(f.id));
  return {
    foldersCount: folders.length,
    filesCount: files.length,
    offlineCount: offlineFiles.length,
    allOffline: files.length > 0 && offlineFiles.length === files.length
  };
}

function countMatchingFilesInFolder(folder, filterType) {
  let count = 0;
  function walk(fId) {
    const children = state.files.filter(f => f.parentId === fId);
    children.forEach(item => {
      const isF = item.is_folder || item.mimeType === 'application/vnd.google-apps.folder';
      if (isF) {
        walk(item.id);
      } else if (fileMatchesFilter(item, filterType)) {
        count++;
      }
    });
  }
  walk(folder.id);
  return count;
}

function folderMatchesFilter(folder, filterType) {
  if (filterType === 'all' || filterType === 'folders') return true;
  return countMatchingFilesInFolder(folder, filterType) > 0;
}

function fileMatchesFilter(file, filterType) {
  if (file.is_folder || file.mimeType === 'application/vnd.google-apps.folder') return false;
  if (filterType === 'all') return true;
  if (filterType === 'offline') return state.cachedFileIds.has(file.id);

  const name = (file.name || '').toLowerCase();
  const mime = (file.mimeType || '').toLowerCase();

  if (filterType === 'pdf') {
    return name.endsWith('.pdf') || mime.includes('pdf');
  }
  if (filterType === 'sheets') {
    return name.endsWith('.xlsx') || name.endsWith('.xls') || name.endsWith('.csv') || mime.includes('spreadsheet') || mime.includes('excel') || mime.includes('csv');
  }
  if (filterType === 'images') {
    return name.endsWith('.png') || name.endsWith('.jpg') || name.endsWith('.jpeg') || name.endsWith('.svg') || mime.includes('image');
  }
  return true;
}

function renderBreadcrumbs() {
  dom.breadcrumbTrail.innerHTML = '';

  const canGoBack = state.folderHistory.length > 1 || state.searchQuery || state.activeFilter !== 'all';
  dom.btnDriveBack.style.display = canGoBack ? 'flex' : 'none';

  state.folderHistory.forEach((item, index) => {
    const isLast = index === state.folderHistory.length - 1;

    const crumb = document.createElement('span');
    crumb.className = `crumb ${isLast && !state.searchQuery ? 'active' : ''}`;
    crumb.textContent = item.name;
    crumb.addEventListener('click', () => {
      jumpToBreadcrumbIndex(index);
    });
    dom.breadcrumbTrail.appendChild(crumb);

    if (!isLast) {
      const sep = document.createElement('span');
      sep.className = 'crumb-sep';
      sep.textContent = '›';
      dom.breadcrumbTrail.appendChild(sep);
    }
  });

  if (state.searchQuery) {
    const sep = document.createElement('span');
    sep.className = 'crumb-sep';
    sep.textContent = '›';
    dom.breadcrumbTrail.appendChild(sep);

    const searchCrumb = document.createElement('span');
    searchCrumb.className = 'crumb active';
    searchCrumb.textContent = `Busca: "${state.searchQuery}"`;
    dom.breadcrumbTrail.appendChild(searchCrumb);
  }

  setTimeout(() => {
    dom.breadcrumbTrail.scrollLeft = dom.breadcrumbTrail.scrollWidth;
  }, 10);
}

function navigateToFolder(folder) {
  if (state.searchQuery) {
    state.searchQuery = '';
    dom.searchInput.value = '';
    dom.searchClearBtn.style.display = 'none';
  }
  state.currentFolderId = folder.id;
  state.folderHistory.push({ id: folder.id, name: folder.name });
  renderDriveView();
}

function navigateBack() {
  if (state.searchQuery) {
    state.searchQuery = '';
    dom.searchInput.value = '';
    dom.searchClearBtn.style.display = 'none';
    renderDriveView();
    return;
  }
  if (state.activeFilter !== 'all' && state.currentFolderId === 'root') {
    state.activeFilter = 'all';
    updateFilterChipsUi();
    renderDriveView();
    return;
  }
  if (state.folderHistory.length > 1) {
    state.folderHistory.pop();
    const parent = state.folderHistory[state.folderHistory.length - 1];
    state.currentFolderId = parent ? parent.id : 'root';
    renderDriveView();
  }
}

dom.btnDriveBack?.addEventListener('click', navigateBack);

function jumpToBreadcrumbIndex(index) {
  if (index >= 0 && index < state.folderHistory.length) {
    state.folderHistory = state.folderHistory.slice(0, index + 1);
    const target = state.folderHistory[index];
    state.currentFolderId = target.id;
    renderDriveView();
  }
}

// ==========================================
// 7. CRIAÇÃO DE CARDS DE PASTA E ARQUIVO M3
// ==========================================
function getFileTypeInfo(name, mimeType) {
  const n = (name || '').toLowerCase();
  const m = (mimeType || '').toLowerCase();

  if (n.endsWith('.pdf') || m.includes('pdf')) {
    return { icon: '📄', cssClass: 'pdf', label: 'PDF' };
  }
  if (n.endsWith('.xlsx') || n.endsWith('.xls') || n.endsWith('.csv') || m.includes('spreadsheet') || m.includes('excel')) {
    return { icon: '📊', cssClass: 'sheet', label: 'Planilha' };
  }
  if (n.endsWith('.png') || n.endsWith('.jpg') || n.endsWith('.jpeg') || n.endsWith('.svg') || m.includes('image')) {
    return { icon: '🗺️', cssClass: 'img', label: 'Mapa' };
  }
  return { icon: '📄', cssClass: 'default', label: 'Documento' };
}

function createFolderItem(folder) {
  const card = document.createElement('div');
  card.className = 'm3-folder-item';
  card.title = `Abrir pasta ${folder.name}`;

  const stats = getFolderStats(folder.id);
  let subText = '';
  if (stats.foldersCount > 0 && stats.filesCount > 0) {
    subText = `${stats.foldersCount} past., ${stats.filesCount} arq.`;
  } else if (stats.filesCount > 0) {
    subText = `${stats.filesCount} ${stats.filesCount === 1 ? 'arquivo' : 'arquivos'}`;
    if (stats.allOffline) subText += ' • ✓ Offline';
  } else if (stats.foldersCount > 0) {
    subText = `${stats.foldersCount} ${stats.foldersCount === 1 ? 'pasta' : 'pastas'}`;
  } else {
    subText = 'Vazia';
  }

  card.innerHTML = `
    <div class="m3-folder-icon-wrap">📁</div>
    <div class="m3-folder-info">
      <div class="m3-folder-name">${escapeHtml(folder.name)}</div>
      <div class="m3-folder-sub">${subText}</div>
    </div>
    <div class="m3-folder-arrow">›</div>
  `;

  card.addEventListener('click', () => {
    navigateToFolder(folder);
  });

  return card;
}

function createFileItem(file) {
  const isCached = state.cachedFileIds.has(file.id);
  const typeInfo = getFileTypeInfo(file.name, file.mimeType);

  const card = document.createElement('div');
  card.className = 'm3-file-card';
  card.title = file.name;

  const dateStr = formatDate(file.modifiedTime);
  const sizeStr = file.size_formatted || '--';

  if (state.viewMode === 'grid') {
    card.innerHTML = `
      <div class="m3-file-top">
        <div class="m3-file-icon-box ${typeInfo.cssClass}">
          <span>${typeInfo.icon}</span>
          ${isCached ? '<div class="m3-offline-pin">✓</div>' : ''}
        </div>
        <button class="m3-btn-more-options" title="Mais opções" data-file-id="${file.id}">⋮</button>
      </div>
      <div class="m3-file-bottom">
        <div class="m3-file-name" title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</div>
        <div class="m3-file-meta">
          <span>${sizeStr}</span>
          <span>•</span>
          <span>${dateStr}</span>
        </div>
      </div>
    `;
  } else {
    // Modo Lista
    card.innerHTML = `
      <div class="m3-file-top">
        <div class="m3-file-icon-box ${typeInfo.cssClass}">
          <span>${typeInfo.icon}</span>
          ${isCached ? '<div class="m3-offline-pin">✓</div>' : ''}
        </div>
      </div>
      <div class="m3-file-bottom">
        <div class="m3-file-name" title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</div>
        <div class="m3-file-meta">
          <span>${sizeStr}</span>
          <span>•</span>
          <span>${dateStr}</span>
          <span class="m3-file-status-badge ${isCached ? 'm3-status-pill-offline' : 'm3-status-pill-cloud'}">
            ${isCached ? '✓ Salvo' : '☁ Nuvem'}
          </span>
        </div>
      </div>
      <button class="m3-btn-more-options" title="Mais opções" data-file-id="${file.id}">⋮</button>
    `;
  }

  // Toque no botão de 3 pontinhos abre a Bottom Sheet
  const btnMore = card.querySelector('.m3-btn-more-options');
  btnMore.addEventListener('click', (e) => {
    e.stopPropagation();
    openFileBottomSheet(file);
  });

  // Toque no cartão abre o arquivo diretamente (comportamento nativo Android)
  card.addEventListener('click', () => {
    openFile(file);
  });

  return card;
}

// ==========================================
// 8. RENDERIZAÇÃO DA VISUALIZAÇÃO DRIVE
// ==========================================
function renderDriveView() {
  renderBreadcrumbs();
  updateViewModeUi();

  dom.foldersContainer.innerHTML = '';
  dom.filesContainer.innerHTML = '';

  let foldersToRender = [];
  let filesToRender = [];

  const query = state.searchQuery.toLowerCase().trim();
  const filter = state.activeFilter;

  if (query) {
    state.files.forEach(item => {
      const isFolder = item.is_folder === true || item.mimeType === 'application/vnd.google-apps.folder';
      const nameMatches = item.name.toLowerCase().includes(query) || (item.folder_path && item.folder_path.toLowerCase().includes(query));
      if (!nameMatches) return;

      if (isFolder) {
        if (folderMatchesFilter(item, filter)) foldersToRender.push(item);
      } else {
        if (fileMatchesFilter(item, filter)) filesToRender.push(item);
      }
    });
    dom.filesCounter.textContent = `${foldersToRender.length + filesToRender.length} resultados`;
  } else if (filter !== 'all') {
    if (filter === 'folders') {
      foldersToRender = state.files.filter(f => (f.is_folder || f.mimeType === 'application/vnd.google-apps.folder'));
    } else {
      filesToRender = state.files.filter(f => fileMatchesFilter(f, filter));
      const foldersWithMatches = state.files.filter(f => (f.is_folder || f.mimeType === 'application/vnd.google-apps.folder') && folderMatchesFilter(f, filter));
      foldersToRender = foldersWithMatches;
    }
    dom.filesCounter.textContent = `${foldersToRender.length + filesToRender.length} itens`;
  } else {
    // Navegação normal na pasta atual
    state.files.forEach(item => {
      if (item.parentId === state.currentFolderId) {
        if (item.is_folder || item.mimeType === 'application/vnd.google-apps.folder') {
          foldersToRender.push(item);
        } else {
          filesToRender.push(item);
        }
      }
    });
    dom.filesCounter.textContent = `${foldersToRender.length + filesToRender.length} itens`;
  }

  // Ordenação alfabética
  foldersToRender.sort((a, b) => a.name.localeCompare(b.name));
  filesToRender.sort((a, b) => a.name.localeCompare(b.name));

  // Renderiza Seção de Pastas
  if (foldersToRender.length > 0) {
    dom.driveFoldersSection.style.display = 'block';
    dom.foldersCounter.textContent = foldersToRender.length;
    foldersToRender.forEach(folder => {
      dom.foldersContainer.appendChild(createFolderItem(folder));
    });
  } else {
    dom.driveFoldersSection.style.display = 'none';
  }

  // Renderiza Seção de Arquivos
  if (filesToRender.length > 0) {
    dom.driveFilesSection.style.display = 'block';
    dom.filesSectionCounter.textContent = filesToRender.length;
    filesToRender.forEach(file => {
      dom.filesContainer.appendChild(createFileItem(file));
    });
  } else {
    dom.driveFilesSection.style.display = 'none';
  }

  // Estado Vazio
  if (foldersToRender.length === 0 && filesToRender.length === 0) {
    dom.driveEmptyState.style.display = 'block';
    const h3 = dom.driveEmptyState.querySelector('.m3-empty-title');
    const p = dom.driveEmptyState.querySelector('.m3-empty-desc');
    if (state.searchQuery) {
      h3.textContent = 'Nenhum resultado';
      p.textContent = `Nenhum arquivo ou pasta encontrado para "${escapeHtml(state.searchQuery)}".`;
    } else if (state.activeFilter === 'offline') {
      h3.textContent = 'Nenhum documento offline';
      p.textContent = 'Toque em "Sincronizar" para salvar os documentos no celular.';
    } else {
      h3.textContent = 'Esta pasta está vazia';
      p.textContent = 'Nenhum documento adicionado aqui ainda.';
    }
  } else {
    dom.driveEmptyState.style.display = 'none';
  }
}

// ==========================================
// 9. BOTTOM SHEET DE AÇÕES NATIVA DO ANDROID
// ==========================================
function openFileBottomSheet(file) {
  state.selectedFileForSheet = file;
  const isCached = state.cachedFileIds.has(file.id);
  const typeInfo = getFileTypeInfo(file.name, file.mimeType);

  dom.sheetFileIcon.textContent = typeInfo.icon;
  dom.sheetFileName.textContent = file.name;
  dom.sheetFileMeta.textContent = `${file.size_formatted || '--'} • ${formatDate(file.modifiedTime)} • ${isCached ? 'Salvo no aparelho' : 'Disponível na nuvem'}`;

  if (isCached) {
    dom.sheetOfflineIcon.textContent = '🗑️';
    dom.sheetOfflineText.textContent = 'Remover do Celular (Liberar espaço)';
  } else {
    dom.sheetOfflineIcon.textContent = '📥';
    dom.sheetOfflineText.textContent = 'Salvar no Celular (Disponibilizar Offline)';
  }

  dom.sheetBackdrop.style.display = 'flex';
}

function closeFileBottomSheet() {
  dom.sheetBackdrop.style.display = 'none';
  state.selectedFileForSheet = null;
}

dom.sheetActionClose?.addEventListener('click', closeFileBottomSheet);
dom.sheetBackdrop?.addEventListener('click', (e) => {
  if (e.target === dom.sheetBackdrop) closeFileBottomSheet();
});

dom.sheetActionOpen?.addEventListener('click', () => {
  const file = state.selectedFileForSheet;
  closeFileBottomSheet();
  if (file) openFile(file);
});

dom.sheetActionToggleOffline?.addEventListener('click', async () => {
  const file = state.selectedFileForSheet;
  if (!file) return;

  const isCached = state.cachedFileIds.has(file.id);
  const downloadUrl = `/api/files/${encodeURIComponent(file.id)}/download`;
  const fullUrl = new URL(downloadUrl, window.location.origin).href;

  if (isCached) {
    // Remover do cache
    if ('caches' in window) {
      try {
        const cache = await caches.open(FILES_CACHE_NAME);
        await cache.delete(downloadUrl);
        await cache.delete(fullUrl);
      } catch (e) {}
    }
    state.cachedFileIds.delete(file.id);
    try {
      localStorage.setItem(STORAGE_CACHED_IDS_KEY, JSON.stringify([...state.cachedFileIds]));
    } catch (e) {}
    closeFileBottomSheet();
    showSnackbar(`🗑️ Arquivo "${file.name}" removido do aparelho.`);
    await refreshCachedFilesStatus();
    renderDriveView();
  } else {
    // Baixar para o cache
    closeFileBottomSheet();
    showSnackbar(`📥 Baixando "${file.name}" para uso offline...`);
    try {
      const res = await fetch(getApiUrl(downloadUrl));
      if (res.ok) {
        if ('caches' in window) {
          const cache = await caches.open(FILES_CACHE_NAME);
          await cache.put(downloadUrl, res.clone());
          await cache.put(fullUrl, res.clone());
        }
        state.cachedFileIds.add(file.id);
        try {
          localStorage.setItem(STORAGE_CACHED_IDS_KEY, JSON.stringify([...state.cachedFileIds]));
        } catch (e) {}
        showSnackbar(`✓ "${file.name}" disponível offline!`);
        await refreshCachedFilesStatus();
        renderDriveView();
      } else {
        showSnackbar(`❌ Falha ao baixar arquivo.`);
      }
    } catch (e) {
      showSnackbar(`❌ Erro ao baixar arquivo.`);
    }
  }
});

dom.sheetActionExternal?.addEventListener('click', () => {
  const file = state.selectedFileForSheet;
  closeFileBottomSheet();
  if (file) {
    const downloadUrl = `/api/files/${encodeURIComponent(file.id)}/download`;
    window.open(getApiUrl(downloadUrl), '_blank');
  }
});

// ==========================================
// 10. VISUALIZADOR DE ARQUIVOS (PDF / FOTO / TXT)
// ==========================================
async function openFile(file) {
  const downloadUrl = `/api/files/${encodeURIComponent(file.id)}/download`;
  const typeInfo = getFileTypeInfo(file.name, file.mimeType);

  dom.modalFilename.textContent = file.name;
  dom.modalFileIcon.textContent = typeInfo.icon;
  dom.modalContent.innerHTML = '<div style="text-align:center; padding: 40px; color: var(--text-muted);">Carregando documento...</div>';
  dom.pdfControls.style.display = 'none';
  dom.modal.classList.add('active');

  dom.modalBtnDownload.href = downloadUrl;
  dom.modalBtnDownload.download = file.name;

  try {
    let blob;
    if ('caches' in window) {
      const cache = await caches.open(FILES_CACHE_NAME);
      const fullUrl = new URL(downloadUrl, window.location.origin).href;
      let cachedRes = await cache.match(downloadUrl) || await cache.match(fullUrl);
      if (!cachedRes) {
        const keys = await cache.keys();
        for (const req of keys) {
          if (req.url.includes(encodeURIComponent(file.id)) || req.url.includes(file.id)) {
            cachedRes = await cache.match(req);
            break;
          }
        }
      }
      if (cachedRes) blob = await cachedRes.blob();
    }

    if (!blob) {
      if (!navigator.onLine) {
        dom.modalContent.innerHTML = `
          <div style="text-align: center; padding: 40px 20px; color: var(--warning);">
            <p style="font-size: 2.5rem; margin-bottom: 12px;">⚠️</p>
            <p><strong>Documento não sincronizado para o aparelho.</strong></p>
            <p style="font-size: 0.85rem; color: var(--text-muted); margin-top: 8px;">
              Conecte à internet na base para sincronizar este arquivo.
            </p>
          </div>
        `;
        return;
      }
      const netRes = await fetch(getApiUrl(downloadUrl));
      if (netRes.ok) {
        blob = await netRes.blob();
        const cache = await caches.open(FILES_CACHE_NAME);
        await cache.put(downloadUrl, netRes.clone());
        state.cachedFileIds.add(file.id);
        refreshCachedFilesStatus();
      } else {
        throw new Error('Falha ao baixar arquivo');
      }
    }

    if (state.currentObjectUrl) {
      URL.revokeObjectURL(state.currentObjectUrl);
    }
    state.currentObjectUrl = URL.createObjectURL(blob);
    dom.modalBtnDownload.href = state.currentObjectUrl;

    dom.btnModalFullscreen.onclick = () => {
      window.open(state.currentObjectUrl, '_blank');
    };

    const mime = (blob.type || file.mimeType || '').toLowerCase();
    const name = file.name.toLowerCase();

    if (mime.includes('pdf') || name.endsWith('.pdf')) {
      await renderPdfDocument(blob);
    } else if (mime.includes('image') || name.endsWith('.png') || name.endsWith('.jpg') || name.endsWith('.svg')) {
      dom.modalContent.innerHTML = `
        <div style="display:flex; justify-content:center; align-items:center; width:100%; height:100%;">
          <img src="${state.currentObjectUrl}" alt="${escapeHtml(file.name)}" style="max-height: 80vh; max-width: 100%; object-fit: contain;">
        </div>
      `;
    } else if (mime.includes('text') || name.endsWith('.txt') || name.endsWith('.csv')) {
      const text = await blob.text();
      dom.modalContent.innerHTML = `<pre>${escapeHtml(text)}</pre>`;
    } else {
      dom.modalContent.innerHTML = `
        <div style="text-align: center; padding: 40px 20px;">
          <p style="font-size: 3.5rem; margin-bottom: 14px;">📄</p>
          <p style="font-size: 1.1rem; font-weight: 700; margin-bottom: 8px;">${escapeHtml(file.name)}</p>
          <p style="color: var(--text-muted); font-size: 0.88rem; margin-bottom: 24px;">
            Este documento pode ser aberto diretamente no aplicativo leitor do aparelho.
          </p>
          <a href="${state.currentObjectUrl}" download="${escapeHtml(file.name)}" class="btn-modal primary" style="display:inline-block; text-decoration:none;">
            Abrir no Celular
          </a>
        </div>
      `;
    }
  } catch (err) {
    dom.modalContent.innerHTML = `
      <div style="text-align: center; padding: 40px 20px; color: var(--danger);">
        <p>Erro ao abrir o documento. Tente sincronizar novamente.</p>
      </div>
    `;
  }
}

async function renderPdfDocument(blob) {
  if (!window.pdfjsLib) {
    dom.modalContent.innerHTML = `
      <div style="text-align: center; padding: 40px 20px;">
        <button onclick="window.open('${state.currentObjectUrl}', '_blank')" class="btn-modal primary">
          ↗ Abrir Documento em Tela Cheia
        </button>
      </div>
    `;
    return;
  }

  try {
    const arrayBuffer = await blob.arrayBuffer();
    const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer });
    state.pdfDoc = await loadingTask.promise;
    state.pdfTotalPages = state.pdfDoc.numPages;
    state.pdfCurrentPage = 1;

    dom.pdfControls.style.display = 'flex';
    updatePdfPageIndicator();
    await renderPdfPage(state.pdfCurrentPage);
  } catch (err) {
    dom.modalContent.innerHTML = `
      <div style="text-align: center; padding: 40px 20px;">
        <p style="color: var(--warning); margin-bottom: 16px;">Não foi possível carregar a prévia do PDF.</p>
        <button onclick="window.open('${state.currentObjectUrl}', '_blank')" class="btn-modal primary">
          Abrir no Navegador / Leitor
        </button>
      </div>
    `;
  }
}

async function renderPdfPage(num) {
  if (!state.pdfDoc) return;
  try {
    const page = await state.pdfDoc.getPage(num);
    const viewport = page.getViewport({ scale: state.pdfScale });

    dom.modalContent.innerHTML = '';
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    canvas.height = viewport.height;
    canvas.width = viewport.width;
    canvas.style.maxWidth = '100%';
    canvas.style.height = 'auto';
    canvas.style.display = 'block';
    canvas.style.margin = '0 auto';

    dom.modalContent.appendChild(canvas);
    await page.render({ canvasContext: ctx, viewport: viewport }).promise;
    updatePdfPageIndicator();
  } catch (e) {}
}

function updatePdfPageIndicator() {
  if (dom.pdfPageNum) {
    dom.pdfPageNum.textContent = `Pág ${state.pdfCurrentPage} / ${state.pdfTotalPages}`;
  }
}

dom.pdfPrevPage?.addEventListener('click', () => {
  if (state.pdfCurrentPage > 1) {
    state.pdfCurrentPage--;
    renderPdfPage(state.pdfCurrentPage);
  }
});

dom.pdfNextPage?.addEventListener('click', () => {
  if (state.pdfCurrentPage < state.pdfTotalPages) {
    state.pdfCurrentPage++;
    renderPdfPage(state.pdfCurrentPage);
  }
});

dom.pdfZoomIn?.addEventListener('click', () => {
  state.pdfScale = Math.min(state.pdfScale + 0.25, 3.0);
  renderPdfPage(state.pdfCurrentPage);
});

dom.pdfZoomOut?.addEventListener('click', () => {
  state.pdfScale = Math.max(state.pdfScale - 0.25, 0.6);
  renderPdfPage(state.pdfCurrentPage);
});

dom.pdfZoomFit?.addEventListener('click', () => {
  state.pdfScale = 1.0;
  renderPdfPage(state.pdfCurrentPage);
});

function closeModal() {
  dom.modal.classList.remove('active');
  if (state.currentObjectUrl) {
    URL.revokeObjectURL(state.currentObjectUrl);
    state.currentObjectUrl = null;
  }
  state.pdfDoc = null;
}

dom.modalClose?.addEventListener('click', closeModal);
dom.modalBtnClose?.addEventListener('click', closeModal);
dom.modal?.addEventListener('click', (e) => {
  if (e.target === dom.modal) closeModal();
});

// ==========================================
// 11. CONTROLE DE MODO DE VISUALIZAÇÃO & FILTROS
// ==========================================
function updateViewModeUi() {
  const isGrid = state.viewMode === 'grid';
  dom.driveContentArea.className = `m3-drive-view ${isGrid ? 'view-grid' : 'view-list'}`;
  dom.viewToggleIcon.textContent = isGrid ? '⊞' : '☰';
  dom.btnToggleView.title = isGrid ? 'Mudar para Lista' : 'Mudar para Grade';
}

dom.btnToggleView?.addEventListener('click', () => {
  state.viewMode = state.viewMode === 'grid' ? 'list' : 'grid';
  localStorage.setItem(STORAGE_VIEW_MODE_KEY, state.viewMode);
  updateViewModeUi();
  renderDriveView();
});

function updateFilterChipsUi() {
  dom.filterChips.forEach(chip => {
    chip.classList.toggle('active', chip.dataset.filter === state.activeFilter);
  });
}

dom.filterChips.forEach(chip => {
  chip.addEventListener('click', () => {
    state.activeFilter = chip.dataset.filter;
    updateFilterChipsUi();
    renderDriveView();
  });
});

// Busca no Drive
let searchDebounceTimer = null;
dom.searchInput?.addEventListener('input', (e) => {
  const val = e.target.value;
  dom.searchClearBtn.style.display = val.length > 0 ? 'flex' : 'none';
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(() => {
    state.searchQuery = val;
    renderDriveView();
  }, 200);
});

dom.searchClearBtn?.addEventListener('click', () => {
  dom.searchInput.value = '';
  dom.searchClearBtn.style.display = 'none';
  state.searchQuery = '';
  renderDriveView();
});

// ==========================================
// 12. CONFIGURAÇÃO DO SERVIDOR & QR CODE
// ==========================================
const serverSettingsDom = {
  modal: document.getElementById('server-settings-modal'),
  btnOpen: document.getElementById('btn-open-server-settings'),
  btnClose: document.getElementById('btn-close-server-settings'),
  inputUrl: document.getElementById('server-url-input'),
  btnSave: document.getElementById('btn-save-server-url'),
  btnReset: document.getElementById('btn-reset-server-url'),
  btnScan: document.getElementById('btn-scan-qr'),
  qrReader: document.getElementById('qr-reader'),
  resultBox: document.getElementById('server-test-result')
};

let html5QrScanner = null;

function cleanServerUrl(input) {
  if (!input) return '';
  let str = input.trim();
  str = str.replace(/\/+sync_campo\.apk(\?.*)?$/i, '');
  str = str.replace(/\/+api\/.*$/i, '');
  if (!str.startsWith('http://') && !str.startsWith('https://')) {
    str = 'https://' + str;
  }
  try {
    const url = new URL(str);
    return url.origin;
  } catch (e) {
    return str.replace(/\/+$/, '');
  }
}

function openServerSettings() {
  const current = localStorage.getItem(STORAGE_SERVER_URL_KEY) || '';
  if (serverSettingsDom.inputUrl) serverSettingsDom.inputUrl.value = current;
  if (serverSettingsDom.resultBox) serverSettingsDom.resultBox.style.display = 'none';
  if (serverSettingsDom.modal) serverSettingsDom.modal.style.display = 'flex';
}

function closeServerSettings() {
  if (html5QrScanner) {
    html5QrScanner.stop().catch(() => {});
    html5QrScanner = null;
  }
  if (serverSettingsDom.qrReader) serverSettingsDom.qrReader.style.display = 'none';
  if (serverSettingsDom.modal) serverSettingsDom.modal.style.display = 'none';
}

async function saveAndTestServerUrl(rawUrl) {
  const resultBox = serverSettingsDom.resultBox;
  resultBox.style.display = 'block';
  resultBox.style.background = 'rgba(255, 255, 255, 0.08)';
  resultBox.style.color = 'var(--text-main)';
  resultBox.textContent = 'Testando conexão com o servidor...';

  const clean = cleanServerUrl(rawUrl);

  if (!clean) {
    localStorage.removeItem(STORAGE_SERVER_URL_KEY);
    if (window.NativeBridge && typeof window.NativeBridge.setServerUrl === 'function') {
      window.NativeBridge.setServerUrl('');
    }
    resultBox.style.background = 'rgba(34, 197, 94, 0.15)';
    resultBox.style.color = '#4ade80';
    resultBox.textContent = '✓ Configuração restaurada para o padrão!';
    setTimeout(() => {
      closeServerSettings();
      fetchStatusAndFiles();
    }, 1000);
    return;
  }

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);
    const testRes = await fetch(`${clean}/api/status`, { signal: controller.signal });
    clearTimeout(timeoutId);

    if (testRes.ok) {
      localStorage.setItem(STORAGE_SERVER_URL_KEY, clean);
      if (window.NativeBridge && typeof window.NativeBridge.setServerUrl === 'function') {
        window.NativeBridge.setServerUrl(clean);
      }
      resultBox.style.background = 'rgba(34, 197, 94, 0.15)';
      resultBox.style.color = '#4ade80';
      resultBox.textContent = '✓ Conectado com sucesso à Central!';
      setTimeout(() => {
        closeServerSettings();
        fetchStatusAndFiles();
      }, 1000);
    } else {
      resultBox.style.background = 'rgba(239, 68, 68, 0.15)';
      resultBox.style.color = '#ef4444';
      resultBox.textContent = `⚠️ Erro (${testRes.status}) ao conectar.`;
    }
  } catch (err) {
    resultBox.style.background = 'rgba(239, 68, 68, 0.15)';
    resultBox.style.color = '#ef4444';
    resultBox.textContent = '❌ Não foi possível alcançar o servidor. Verifique se o PC está com o run.py ativo.';
  }
}

serverSettingsDom.btnOpen?.addEventListener('click', openServerSettings);
serverSettingsDom.btnClose?.addEventListener('click', closeServerSettings);
dom.networkBadge?.addEventListener('click', openServerSettings);
dom.btnBannerConnect?.addEventListener('click', openServerSettings);
dom.btnOpenServerFromSync?.addEventListener('click', openServerSettings);

serverSettingsDom.btnSave?.addEventListener('click', () => {
  saveAndTestServerUrl(serverSettingsDom.inputUrl.value);
});

serverSettingsDom.btnReset?.addEventListener('click', () => {
  saveAndTestServerUrl('');
});

serverSettingsDom.btnScan?.addEventListener('click', () => {
  if (typeof Html5Qrcode === 'undefined') {
    alert('Aguarde 2 segundos para o leitor carregar...');
    return;
  }
  const readerDiv = serverSettingsDom.qrReader;
  readerDiv.style.display = 'block';
  if (!html5QrScanner) html5QrScanner = new Html5Qrcode('qr-reader');

  html5QrScanner.start(
    { facingMode: 'environment' },
    { fps: 10, qrbox: { width: 250, height: 250 } },
    (decodedText) => {
      if (html5QrScanner) {
        html5QrScanner.stop().catch(() => {});
        html5QrScanner = null;
      }
      readerDiv.style.display = 'none';
      if (serverSettingsDom.inputUrl) serverSettingsDom.inputUrl.value = decodedText;
      saveAndTestServerUrl(decodedText);
    },
    () => {}
  ).catch(err => {
    alert('Não foi possível abrir a câmera para escanear.');
    readerDiv.style.display = 'none';
  });
});

// ==========================================
// 13. MODAL DE DOWNLOAD DO APK
// ==========================================
const apkModalDom = {
  modal: document.getElementById('apk-download-modal'),
  btnOpenSync: document.getElementById('btn-open-apk-modal'),
  btnClose: document.getElementById('btn-close-apk-modal'),
  qrImg: document.getElementById('apk-qr-img'),
  linkDirect: document.getElementById('link-direct-apk'),
  urlDisplay: document.getElementById('apk-url-display'),
  btnCopy: document.getElementById('btn-copy-apk-link'),
  linkOpenFromSettings: document.getElementById('link-open-apk-from-settings')
};

function openApkModal() {
  const custom = localStorage.getItem(STORAGE_SERVER_URL_KEY);
  const clean = custom && custom.trim() ? custom.trim().replace(/\/+$/, '') : window.location.origin;
  const apkDownloadUrl = `${clean}/sync_campo.apk`;

  if (apkModalDom.urlDisplay) apkModalDom.urlDisplay.value = apkDownloadUrl;
  if (apkModalDom.linkDirect) apkModalDom.linkDirect.href = apkDownloadUrl;
  if (apkModalDom.qrImg) apkModalDom.qrImg.src = getApiUrl(`/api/qr/apk?url=${encodeURIComponent(clean)}&t=${Date.now()}`);
  if (apkModalDom.modal) apkModalDom.modal.style.display = 'flex';
}

function closeApkModal() {
  if (apkModalDom.modal) apkModalDom.modal.style.display = 'none';
}

apkModalDom.btnOpenSync?.addEventListener('click', openApkModal);
apkModalDom.linkOpenFromSettings?.addEventListener('click', (e) => {
  e.preventDefault();
  closeServerSettings();
  openApkModal();
});
apkModalDom.btnClose?.addEventListener('click', closeApkModal);
apkModalDom.modal?.addEventListener('click', (e) => {
  if (e.target === apkModalDom.modal) closeApkModal();
});

apkModalDom.btnCopy?.addEventListener('click', async () => {
  if (apkModalDom.urlDisplay && apkModalDom.urlDisplay.value) {
    try {
      await navigator.clipboard.writeText(apkModalDom.urlDisplay.value);
      showSnackbar('✓ Link do APK copiado para a área de transferência!');
    } catch (e) {
      apkModalDom.urlDisplay.select();
      document.execCommand('copy');
      showSnackbar('✓ Link do APK copiado!');
    }
  }
});

// Funções utilitárias
function formatDate(isoStr) {
  if (!isoStr) return '';
  try {
    const d = new Date(isoStr);
    const day = String(d.getDate()).padStart(2, '0');
    const month = String(d.getMonth() + 1).padStart(2, '0');
    return `${day}/${month}`;
  } catch (e) {
    return '';
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// INICIALIZAÇÃO
updateNetworkStatus();
fetchStatusAndFiles();
