'use strict';

/* global ICONS */
const api = window.lumina;
const IS_MAC = api.platform === 'darwin';
/** Names that differ per platform: the Mac keyboard's ⇧ and the Finder vs. Windows' wording. */
const WORDS = IS_MAC
  ? { showFolder: 'Show in Finder', minimize: 'Keep in Dock' }
  : { showFolder: 'Open folder', minimize: 'Minimize' };
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const state = {
  page: 'home',
  info: null,
  settings: null,
  accounts: { selected: null, accounts: [] },
  game: 'idle', // idle | preparing | running | error
  extra: null, // the second game: { state: 'preparing' | 'running', accountId, name, type, uuid }
  profiles: { selected: 'default', profiles: [], versions: [] },
  modProfile: 'default', // profile shown on the mods page
  modTab: 'installed',
  shaders: [],
  search: { query: '', sort: 'relevance', offset: 0, total: 0, loading: false, token: 0 },
  installed: [],
  core: [],
  installing: new Set(),
  logLines: [],
  inWorld: false, // the game entered a world or server since it started
  update: null,
  cosmetics: null,
  cosTab: 'capes',
};

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

async function call(promise) {
  const res = await promise;
  if (!res.ok) {
    const err = new Error(res.error);
    err.code = res.code;
    err.url = res.url;
    throw err;
  }
  return res.data;
}

function svg(name) {
  const body = ICONS[name] || '';
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;
}

function hydrateIcons(root = document) {
  for (const el of $$('i[data-icon]', root)) {
    if (el.dataset.ready === el.dataset.icon) continue;
    el.innerHTML = svg(el.dataset.icon);
    el.dataset.ready = el.dataset.icon;
  }
}

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

function formatCount(n) {
  if (n >= 1e9) return (n / 1e9).toFixed(1).replace('.0', '') + 'B';
  if (n >= 1e6) return (n / 1e6).toFixed(1).replace('.0', '') + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(1).replace('.0', '') + 'k';
  return String(n);
}

function toast(message, type = 'info', timeout = 4200) {
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  const icon = type === 'error' ? 'triangle-alert' : type === 'success' ? 'circle-check' : 'info';
  el.innerHTML = `<i data-icon="${icon}"></i><span>${esc(message)}</span>`;
  $('#toasts').appendChild(el);
  hydrateIcons(el);
  setTimeout(() => {
    el.classList.add('out');
    setTimeout(() => el.remove(), 320);
  }, timeout);
}

function debounce(fn, ms) {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), ms);
  };
}

// ---------------------------------------------------------------------------
// navigation
// ---------------------------------------------------------------------------

function showPage(page) {
  state.page = page;
  $$('.nav-item').forEach((b) => b.classList.toggle('active', b.dataset.page === page));
  moveNavIndicator();
  $$('.page').forEach((p) => p.classList.toggle('active', p.dataset.page === page));
  if (page === 'mods') refreshModTab();
  if (page === 'cosmetics') loadCosmetics();
  // the 3D preview only renders while its page is open
  if (preview3d.viewer) preview3d.viewer.renderPaused = page !== 'cosmetics';
  syncLobbyPause();
}

function moveNavIndicator() {
  const active = $('.nav-item.active');
  const indicator = $('.nav-indicator');
  indicator.style.width = `${active.offsetWidth}px`;
  indicator.style.transform = `translateX(${active.offsetLeft}px)`;
}

function setupNav() {
  moveNavIndicator();
  // the widths change once the font has loaded
  document.fonts.ready.then(moveNavIndicator);
  $$('.nav-item').forEach((b) => b.addEventListener('click', () => showPage(b.dataset.page)));
  $$('[data-goto]').forEach((c) => c.addEventListener('click', () => showPage(c.dataset.goto)));
  $$('[data-win]').forEach((b) => b.addEventListener('click', () => api.window[b.dataset.win]()));
}

// ---------------------------------------------------------------------------
// accounts
// ---------------------------------------------------------------------------

/** Faces cut from the account's current skin (uuid -> data url); mc-heads is only the first guess. */
const faces = {};

function avatarUrl(acc) {
  if (!acc) return 'https://mc-heads.net/avatar/MHF_Steve/64';
  if (acc.type === 'msa' && faces[acc.uuid]) return faces[acc.uuid].face;
  return `https://mc-heads.net/avatar/${encodeURIComponent(acc.type === 'msa' ? acc.uuid : acc.name)}/64`;
}

/** Draws the 8x8 face plus the hat layer of a skin image (any resolution) into a 64x64 picture. */
function drawFace(skinData) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const k = img.width / 64;
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 64;
      const g = canvas.getContext('2d');
      g.imageSmoothingEnabled = false;
      g.drawImage(img, 8 * k, 8 * k, 8 * k, 8 * k, 0, 0, 64, 64);
      if (img.height >= img.width) g.drawImage(img, 40 * k, 8 * k, 8 * k, 8 * k, 0, 0, 64, 64);
      resolve(canvas.toDataURL());
    };
    img.onerror = reject;
    img.src = skinData;
  });
}

let skinCheckedAt = 0;
const skinRequested = new Set();

/** Reloads the selected account's skin, so a skin changed in game or on minecraft.net shows up here. */
async function refreshSkin(force = false) {
  const acc = state.accounts.accounts.find((a) => a.id === state.accounts.selected);
  if (!acc || acc.type !== 'msa') return;
  if (!force && Date.now() - skinCheckedAt < 30000) return;
  skinCheckedAt = Date.now();
  await loadSkinOf(acc);
}

/** Fetches one account's current skin from Mojang and redraws wherever its face or skin shows. */
async function loadSkinOf(acc) {
  try {
    const skin = await call(api.accounts.skin(acc.uuid));
    const known = faces[acc.uuid];
    if (!skin || (known && known.url === skin.url && known.skin.slim === skin.slim)) return;
    faces[acc.uuid] = { url: skin.url, face: await drawFace(skin.data), skin };
    saveSkin(acc.uuid, skin);
    showFaces(acc.id === state.accounts.selected);
  } catch {
    /* offline or rate limited: keep the current picture */
  }
}

/** Redraws the faces; the chip (and with it the start page character) only when the selected account changed. */
function showFaces(selectedChanged) {
  if (selectedChanged) renderAccountChip();
  if (!$('#accounts-modal').hidden) renderAccountList();
  if (state.extra) renderExtra();
  if (selectedChanged && preview3d.viewer) loadPreviewSkin();
}

/**
 * Every account's face is ready before the account list opens: at once from the last known skin,
 * then fresh from Mojang one account after the other. Offline accounts' pictures land in the cache.
 */
async function preloadFaces() {
  let selectedChanged = false;
  let any = false;
  for (const acc of state.accounts.accounts) {
    if (acc.type !== 'msa') {
      new Image().src = avatarUrl(acc);
      continue;
    }
    const saved = !faces[acc.uuid] && savedSkin(acc.uuid);
    if (!saved || !saved.data) continue;
    try {
      faces[acc.uuid] = { url: saved.url, face: await drawFace(saved.data), skin: saved };
      any = true;
      if (acc.id === state.accounts.selected) selectedChanged = true;
    } catch {
      /* a broken saved skin: Mojang's comes next */
    }
  }
  if (any) showFaces(selectedChanged);
  for (const acc of state.accounts.accounts) {
    if (acc.type !== 'msa' || skinRequested.has(acc.uuid)) continue;
    skinRequested.add(acc.uuid);
    await loadSkinOf(acc);
  }
}

function setupSkinRefresh() {
  // every few minutes, whenever the launcher comes back to the front, and after playing (Skin Changer)
  setInterval(() => refreshSkin(true), 5 * 60 * 1000);
  window.addEventListener('focus', () => refreshSkin());
  api.on('game:state', (s) => {
    if (s.state !== 'stopped') return;
    refreshSkin(true);
    // Mojang's session server can take a minute to hand out a new skin
    setTimeout(() => refreshSkin(true), 70000);
  });
}

function renderAccountChip() {
  const acc = state.accounts.accounts.find((a) => a.id === state.accounts.selected);
  $('#account-name').textContent = acc ? acc.name : 'Not signed in';
  $('#account-type').textContent = acc ? (acc.type === 'msa' ? 'Microsoft account' : 'Offline') : 'Add account';
  $('#account-avatar').src = avatarUrl(acc);
  renderLobby(acc);
  // a newly selected or added account loads its real skin once right away
  if (acc && acc.type === 'msa' && !faces[acc.uuid] && !skinRequested.has(acc.uuid)) {
    skinRequested.add(acc.uuid);
    loadSkinOf(acc);
  }
}

// ---------------------------------------------------------------------------
// start page: your character in 3D
// ---------------------------------------------------------------------------

/** The character (lobby.js); null when WebGL is missing, then the logo stands alone on the stage. */
let lobby = null;
let lobbyAccount = 0;

/** The last skin of an account, so the character is there at once and also without a connection. */
function savedSkin(uuid) {
  try {
    return JSON.parse(localStorage.getItem(`lumina.skin.${uuid}`));
  } catch {
    return null;
  }
}

function saveSkin(uuid, skin) {
  try {
    localStorage.setItem(`lumina.skin.${uuid}`, JSON.stringify(skin));
  } catch {
    /* no room: it is only a head start */
  }
}

/** Skin of the selected account; Steve for offline accounts or without an account. */
async function renderLobby(acc) {
  if (!lobby) return;
  const turn = ++lobbyAccount;
  const known = acc && acc.type === 'msa' ? (faces[acc.uuid] && faces[acc.uuid].skin) || savedSkin(acc.uuid) : null;
  const skin = known || (await call(api.cosmetics.skin()).catch(() => null));
  if (skin && turn === lobbyAccount) lobby.setSkin(skin).catch(() => {});
}

/** The cape and particle effect picked on the Cosmetics page or in game. */
async function loadLobbyCosmetics() {
  if (!lobby) return;
  const worn = await call(api.cosmetics.worn()).catch(() => null);
  if (!worn) return;
  lobby.particles.set(worn.effect, worn.tint, worn.density);
  applyPreviewCape(worn.cape, lobby);
}

/**
 * Only renders while it can be seen. While a game runs it only moves when you have the launcher in
 * front: in the background it would cost the game frames.
 */
function syncLobbyPause() {
  const playing = state.game === 'running' || !!state.extra;
  const inFront = document.hasFocus() && document.visibilityState === 'visible';
  if (lobby) lobby.setPaused(state.page !== 'home' || (playing && !inFront) || !$('#welcome').hidden);
}

function setupLobby() {
  window.addEventListener('focus', syncLobbyPause);
  window.addEventListener('blur', syncLobbyPause);
  document.addEventListener('visibilitychange', syncLobbyPause);
  try {
    lobby = new window.LuminaLobby.Lobby($('#lobby'), $('#lobby-stage'), window.skinview3d, window.LuminaParticles.Preview);
    loadLobbyCosmetics();
  } catch {
    lobby = null;
    $('#lobby-stage').replaceChildren();
  }
}

function renderAccountList() {
  const list = $('#account-list');
  if (!state.accounts.accounts.length) {
    list.innerHTML = `<div class="muted" style="font-size:13px;padding:4px 2px 6px">No account yet. Sign in with the Microsoft account that owns Minecraft.</div>`;
  } else {
    list.innerHTML = state.accounts.accounts.map((a) => `
      <div class="account-row ${a.id === state.accounts.selected ? 'selected' : ''}" data-id="${esc(a.id)}">
        <img class="avatar" src="${esc(avatarUrl(a))}" alt="" />
        <div class="meta"><div class="n">${esc(a.name)}</div><div class="t">${a.type === 'msa' ? 'Microsoft' : 'Offline · singleplayer only'}</div></div>
        <i data-icon="check" class="check"></i>
        <button class="icon-btn danger" data-remove="${esc(a.id)}" title="Remove"><i data-icon="log-out"></i></button>
      </div>`).join('');
  }
  hydrateIcons(list);
  for (const row of $$('.account-row', list)) {
    row.addEventListener('click', async (e) => {
      if (e.target.closest('[data-remove]')) return;
      state.accounts = await call(api.accounts.select(row.dataset.id));
      renderAccountChip();
      renderAccountList();
    });
  }
  for (const btn of $$('[data-remove]', list)) {
    btn.addEventListener('click', async () => {
      state.accounts = await call(api.accounts.remove(btn.dataset.remove));
      renderAccountChip();
      renderAccountList();
    });
  }
}

function openAccounts() {
  renderAccountList();
  $('#accounts-modal').hidden = false;
}

/** The first start of a new install: nothing but the logo and the way in. */
function showWelcome() {
  document.documentElement.classList.add('welcoming');
  $('#welcome').hidden = false;
  syncLobbyPause();
}

async function closeWelcome() {
  document.documentElement.classList.remove('welcoming');
  $('#welcome').hidden = true;
  state.settings = await call(api.settings.set({ welcomed: true }));
  showPage('home');
}

function setupWelcome() {
  $('#welcome-skip').addEventListener('click', closeWelcome);
  $('#welcome-signin').addEventListener('click', async () => {
    const btn = $('#welcome-signin');
    btn.disabled = true;
    $('#welcome-signin-text').textContent = 'Signing in…';
    try {
      state.accounts = await call(api.accounts.addMicrosoft());
      renderAccountChip();
      await closeWelcome();
      toast('Signed in as ' + state.accounts.accounts.find((a) => a.id === state.accounts.selected).name, 'success');
    } catch (e) {
      if (!/closed|cancel/i.test(e.message)) toast(e.message, 'error', 6000);
    } finally {
      btn.disabled = false;
      $('#welcome-signin-text').textContent = 'Sign in with Microsoft';
    }
  });
  // the game can switch accounts while it runs
  api.on('accounts:changed', (accounts) => {
    state.accounts = accounts;
    renderAccountChip();
    if (!$('#accounts-modal').hidden) renderAccountList();
    preloadFaces();
  });
}

function setupAccounts() {
  $('#account-chip').addEventListener('click', openAccounts);
  $$('#accounts-modal [data-close]').forEach((el) => el.addEventListener('click', () => ($('#accounts-modal').hidden = true)));
  $('#add-microsoft').addEventListener('click', async () => {
    const btn = $('#add-microsoft');
    btn.disabled = true;
    try {
      state.accounts = await call(api.accounts.addMicrosoft());
      renderAccountChip();
      renderAccountList();
      toast('Signed in as ' + state.accounts.accounts.find((a) => a.id === state.accounts.selected).name, 'success');
    } catch (e) {
      if (!/closed|cancel/i.test(e.message)) toast(e.message, 'error', 6000);
    } finally {
      btn.disabled = false;
    }
  });
  $('#add-offline').addEventListener('click', async () => {
    try {
      state.accounts = await call(api.accounts.addOffline($('#offline-name').value.trim()));
      $('#offline-name').value = '';
      renderAccountChip();
      renderAccountList();
    } catch (e) {
      toast(e.message, 'error', 6000);
    }
  });
}

// ---------------------------------------------------------------------------
// play
// ---------------------------------------------------------------------------

function setGameState(s, detail = {}) {
  state.game = s;
  const btn = $('#play-btn');
  const text = $('#play-text');
  const icon = $('#play-icon');
  btn.classList.toggle('busy', s === 'preparing');
  btn.classList.toggle('running', s === 'running');
  btn.disabled = s === 'preparing';
  $('#play-status').classList.toggle('visible', s !== 'idle');
  $('#version-btn').disabled = s !== 'idle' && s !== 'error';
  if (s === 'preparing') {
    text.textContent = 'Preparing';
    icon.dataset.icon = 'loader-circle';
    icon.classList.add('spin');
  } else if (s === 'running') {
    text.textContent = 'Running';
    icon.dataset.icon = 'square';
    icon.classList.remove('spin');
    $('#play-fill').style.width = '0%';
    $('#stage').textContent = 'Minecraft is running';
    $('#stage-sub').textContent = '';
  } else {
    text.textContent = 'Play';
    icon.dataset.icon = 'play';
    icon.classList.remove('spin');
    $('#play-fill').style.width = '0%';
    if (s === 'error') {
      $('#stage').textContent = 'Could not start';
      $('#stage-sub').textContent = detail.error || '';
    } else if (detail.crashed) {
      showCrash(detail);
    }
  }
  hydrateIcons(btn);
  renderExtra();
  if (state.update) renderUpdate(state.update);
  renderCosLock();
  syncLobbyPause();
  // the game may have changed the cosmetics while it ran
  if (s === 'idle' && state.page === 'cosmetics') loadCosmetics();
  if (s === 'idle') loadLobbyCosmetics();
}

async function play() {
  if (state.game === 'running') {
    // only a world has something to lose; while the game still loads, stopping it needs no question
    if (!state.inWorld || confirm('Stop Minecraft?')) await call(api.game.kill());
    return;
  }
  if (state.game === 'preparing') return;
  if (!state.accounts.selected) {
    openAccounts();
    toast('Sign in first', 'info');
    return;
  }
  if (state.extra && state.extra.accountId === state.accounts.selected) {
    toast('This account is already playing', 'info');
    return;
  }
  state.logLines = [];
  state.inWorld = false;
  $('#console-body').textContent = '';
  closePickers();
  setGameState('preparing');
  $('#stage').textContent = 'Checking files…';
  $('#stage-sub').textContent = '';
  try {
    await call(api.game.launch());
  } catch (e) {
    setGameState('error', { error: e.message });
    toast(e.message, 'error', 7000);
  }
}

// A second game next to the first, with another account (e.g. to see each other's cosmetics).
// The small button next to Play: while a game runs it starts one, while the second runs it stops it.

function renderExtra() {
  const extra = state.extra;
  const picker = $('#extra-picker');
  picker.hidden = !extra && state.game !== 'running';
  if (picker.hidden) closeExtraMenu();
  const btn = $('#extra-btn');
  const icon = $('#extra-icon');
  const face = $('#extra-face');
  btn.classList.toggle('running', !!extra && extra.state === 'running');
  btn.disabled = !!extra && extra.state === 'preparing';
  face.hidden = !extra || extra.state !== 'running';
  icon.hidden = !face.hidden;
  if (!extra) {
    icon.dataset.icon = 'user-plus';
    icon.classList.remove('spin');
    btn.title = 'Play with another account';
  } else if (extra.state === 'preparing') {
    icon.dataset.icon = 'loader-circle';
    icon.classList.add('spin');
    btn.title = `Starting ${extra.name}`;
  } else {
    face.src = avatarUrl(extra);
    btn.title = `Stop ${extra.name}'s game`;
  }
  hydrateIcons(btn);
  syncLobbyPause();
}

function closeExtraMenu() {
  $('#extra-menu').hidden = true;
  $('#extra-picker').classList.remove('open');
}

function openExtraMenu() {
  const menu = $('#extra-menu');
  // the account the first game uses is the selected one (switching in the game selects it too)
  const others = state.accounts.accounts.filter((a) => a.id !== state.accounts.selected);
  const row = (a) => `
    <button class="vrow" data-extra-account="${esc(a.id)}">
      <img class="vrow-face" src="${esc(avatarUrl(a))}" alt="">
      <span class="ptext"><span class="n">${esc(a.name)}</span><span class="t">${a.type === 'msa' ? 'Microsoft account' : 'Offline'}</span></span>
    </button>`;
  menu.innerHTML = `
    <div class="version-head">Play with another account</div>
    ${others.map(row).join('')}
    ${others.length ? '<div class="version-sep"></div>' : ''}
    <button class="vrow vrow-new" data-extra-add>
      <i data-icon="plus" class="vrow-plus"></i>
      <span class="ptext"><span class="n">Add account</span></span>
    </button>`;
  hydrateIcons(menu);
  for (const el of $$('[data-extra-account]', menu)) {
    el.addEventListener('click', () => {
      closeExtraMenu();
      playExtra(el.dataset.extraAccount);
    });
  }
  $('[data-extra-add]', menu).addEventListener('click', () => {
    closeExtraMenu();
    openAccounts();
  });
  menu.hidden = false;
  $('#extra-picker').classList.add('open');
}

async function playExtra(accountId) {
  if (state.extra) return;
  const acc = state.accounts.accounts.find((a) => a.id === accountId);
  if (!acc) return;
  state.extra = { state: 'preparing', accountId, name: acc.name, type: acc.type, uuid: acc.uuid };
  renderExtra();
  try {
    await call(api.game.launchExtra(accountId));
  } catch (e) {
    state.extra = null;
    renderExtra();
    toast(e.message, 'error', 7000);
  }
}

async function onExtraClick(e) {
  e.stopPropagation();
  const extra = state.extra;
  if (extra && extra.state === 'running') {
    if (confirm(`Stop ${extra.name}'s game?`)) await call(api.game.kill('extra'));
    return;
  }
  if (extra) return;
  if ($('#extra-menu').hidden) {
    closePickers();
    openExtraMenu();
  } else {
    closeExtraMenu();
  }
}

function setupExtra() {
  $('#extra-btn').addEventListener('click', onExtraClick);
  document.addEventListener('click', (e) => {
    if (!e.target.closest('#extra-picker')) closeExtraMenu();
  });
  api.on('game:extra', (s) => {
    if (s.state === 'preparing' || s.state === 'running') {
      state.extra = { state: s.state, accountId: s.accountId, name: s.name, type: s.type, uuid: s.uuid };
    } else {
      state.extra = null;
      if (s.crashed) toast(`${s.name}'s game crashed`, 'error', 7000);
    }
    renderExtra();
  });
}

/** What the game logs when it enters a singleplayer world or connects to a server. */
const WORLD_LOG = /Starting integrated minecraft server|Connecting to \S+, \d+/;

function logLineElement(line) {
  const el = document.createElement('div');
  if (/\b(ERROR|FATAL|Exception)\b/.test(line)) el.className = 'err';
  else if (/\bWARN\b/.test(line)) el.className = 'warn';
  el.textContent = line;
  return el;
}

function showCrash(detail) {
  const hasLog = state.logLines.length > 0;
  $('#crash-error').textContent = detail.error || '';
  $('#crash-error').hidden = !detail.error;
  $('#crash-log').hidden = true;
  $('#crash-view').disabled = !hasLog;
  $('#crash-copy').disabled = !hasLog;
  $('#crash-view span').textContent = 'View log';
  $('#crash-modal .modal-card').classList.remove('log-open');
  $('#crash-modal').hidden = false;
}

function toggleCrashLog() {
  const log = $('#crash-log');
  const open = log.hidden;
  $('#crash-modal .modal-card').classList.toggle('log-open', open);
  $('#crash-view span').textContent = open ? 'Hide log' : 'View log';
  log.hidden = !open;
  if (open) {
    log.replaceChildren(...state.logLines.map(logLineElement));
    // the reason for a crash is at the end
    log.scrollTop = log.scrollHeight;
  }
}

function copyLog() {
  navigator.clipboard.writeText(state.logLines.join('\n')).then(
    () => toast('Log copied', 'success', 2000),
    () => toast('Could not copy the log', 'error'),
  );
}

function appendLog(lines) {
  const body = $('#console-body');
  const atBottom = body.scrollTop + body.clientHeight >= body.scrollHeight - 20;
  const frag = document.createDocumentFragment();
  for (const line of lines) {
    state.logLines.push(line);
    if (WORLD_LOG.test(line)) state.inWorld = true;
    frag.appendChild(logLineElement(line));
  }
  body.appendChild(frag);
  while (body.childNodes.length > 600) body.removeChild(body.firstChild);
  if (state.logLines.length > 2000) state.logLines.splice(0, state.logLines.length - 2000);
  if (atBottom) body.scrollTop = body.scrollHeight;
}

function setupPlay() {
  $('#play-btn').addEventListener('click', play);
  api.on('game:progress', (p) => {
    if (state.game !== 'preparing') return;
    $('#stage').textContent = p.stage || 'Preparing…';
    if (typeof p.progress === 'number') {
      $('#play-fill').style.width = `${Math.round(p.progress * 100)}%`;
      $('#stage-sub').textContent = `${Math.round(p.progress * 100)}%`;
    }
  });
  api.on('game:state', (s) => setGameState(s.state === 'stopped' ? 'idle' : s.state, s));
  api.on('game:log', appendLog);
  $('#console-toggle').addEventListener('click', () => $('#console').classList.toggle('collapsed'));
  $('#console-copy').addEventListener('click', copyLog);
  $('#crash-copy').addEventListener('click', copyLog);
  $('#crash-view').addEventListener('click', toggleCrashLog);
  $$('#crash-modal [data-close]').forEach((el) => el.addEventListener('click', () => ($('#crash-modal').hidden = true)));
  $('#console-folder').addEventListener('click', () => api.app.openFolder('logs'));
}

// ---------------------------------------------------------------------------
// profiles (version picker)
// ---------------------------------------------------------------------------

function profileById(id) {
  return state.profiles.profiles.find((p) => p.id === id) || state.profiles.profiles[0];
}

/** Newest Minecraft version first; profiles of the same version keep their order. */
function newestFirst(list) {
  const rank = (v) => state.profiles.versions.indexOf(v);
  return [...list].sort((a, b) => rank(b.mcVersion) - rank(a.mcVersion));
}

function versionLabel(p) {
  return `Lumina ${p.mcVersion}`;
}

/** Lumina's own profiles are just "Lumina <version>"; your own show their name and the version. */
function profileText(p) {
  return `<span class="ptext"><span class="n">${esc(p.name)}</span>${p.builtin ? '' : `<span class="t">${esc(versionLabel(p))}</span>`}</span>`;
}

/**
 * The version picker exists twice: on the start page it chooses what "Spielen" starts, on the Mods
 * page whose mods you see. Both list the same profiles and can create new ones.
 */
const pickers = {
  home: {
    root: '#version-picker',
    menu: '#version-menu',
    selected: () => state.profiles.selected,
    select: async (id) => setProfiles(await call(api.profiles.select(id))),
  },
  mods: {
    root: '#mod-picker',
    menu: '#mod-picker-menu',
    selected: () => state.modProfile,
    select: async (id) => {
      state.modProfile = id;
      renderModPicker();
      refreshModTab();
    },
  },
};

function setProfiles(data) {
  state.profiles = data;
  if (!data.profiles.some((p) => p.id === state.modProfile)) state.modProfile = data.selected;
  const current = profileById(data.selected);
  $('#version-name').textContent = current ? current.name : 'Lumina';
  $('#version-sub').textContent = current && !current.builtin ? versionLabel(current) : '';
  $('#version-sub').hidden = !current || current.builtin;
  renderModPicker();
}

function renderModPicker() {
  const p = profileById(state.modProfile);
  $('#mod-picker-btn').innerHTML = `${profileText(p)}<i data-icon="chevron-down" class="chev"></i>`;
  hydrateIcons($('#mod-picker-btn'));
}

function renderPickerMenu(key) {
  const picker = pickers[key];
  const menu = $(picker.menu);
  const selected = picker.selected();
  const row = (p) => `
    <div class="vrow ${p.id === selected ? 'selected' : ''}" data-profile="${esc(p.id)}">
      ${profileText(p)}
      ${p.builtin ? '' : `<button class="vrow-remove" data-remove-profile="${esc(p.id)}" title="Delete profile"><i data-icon="trash-2"></i></button>`}
      <i data-icon="check" class="vrow-check"></i>
    </div>`;
  const own = newestFirst(state.profiles.profiles.filter((p) => !p.builtin));
  menu.innerHTML = `
    <div class="version-head">Versions</div>
    ${newestFirst(state.profiles.profiles.filter((p) => p.builtin)).map(row).join('')}
    ${own.length ? `<div class="version-head sub">Your profiles</div>${own.map(row).join('')}` : ''}
    <div class="version-sep"></div>
    <button class="vrow vrow-new" data-new-profile>
      <i data-icon="plus" class="vrow-plus"></i>
      <span class="ptext"><span class="n">New profile</span></span>
    </button>`;
  hydrateIcons(menu);
  for (const el of $$('.vrow[data-profile]', menu)) {
    el.addEventListener('click', async (e) => {
      if (e.target.closest('[data-remove-profile]')) return;
      closePickers();
      await picker.select(el.dataset.profile);
    });
  }
  for (const btn of $$('[data-remove-profile]', menu)) {
    btn.addEventListener('click', async () => {
      const p = profileById(btn.dataset.removeProfile);
      if (!confirm(`Delete profile "${p.name}" and its mods? Worlds are kept.`)) return;
      setProfiles(await call(api.profiles.remove(p.id)));
      renderPickerMenu(key);
      if (state.page === 'mods') refreshModTab();
      toast('Profile deleted', 'success', 2000);
    });
  }
  $('[data-new-profile]', menu).addEventListener('click', () => {
    closePickers();
    openProfileModal(key);
  });
}

function togglePicker(key) {
  const picker = pickers[key];
  const open = $(picker.menu).hidden;
  closePickers();
  if (!open) return;
  renderPickerMenu(key);
  $(picker.menu).hidden = false;
  $(picker.root).classList.add('open');
}

function closePickers() {
  for (const picker of Object.values(pickers)) {
    $(picker.menu).hidden = true;
    $(picker.root).classList.remove('open');
  }
}

/** Where the new profile modal was opened from; from the Mods page the new profile is shown there. */
let profileModalFrom = 'home';

function openProfileModal(from = 'home') {
  profileModalFrom = from;
  $('#profile-version').innerHTML = [...state.profiles.versions].reverse().map((v) => `<option value="${esc(v)}">Lumina ${esc(v)}</option>`).join('');
  $('#profile-name').value = '';
  $('#profile-modal').hidden = false;
  setTimeout(() => $('#profile-name').focus(), 50);
}

function setupProfiles() {
  for (const key of Object.keys(pickers)) {
    $(`${pickers[key].root} > button`).addEventListener('click', (e) => {
      e.stopPropagation();
      togglePicker(key);
    });
  }
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.version-picker')) closePickers();
  });
  $$('#profile-modal [data-close]').forEach((el) => el.addEventListener('click', () => ($('#profile-modal').hidden = true)));
  const create = async () => {
    try {
      const data = await call(api.profiles.create({ name: $('#profile-name').value, mcVersion: $('#profile-version').value }));
      if (profileModalFrom === 'mods') state.modProfile = data.selected;
      setProfiles(data);
      $('#profile-modal').hidden = true;
      if (profileModalFrom === 'mods') refreshModTab();
      toast(`Profile "${profileById(state.profiles.selected).name}" created`, 'success');
    } catch (e) {
      toast(e.message, 'error');
    }
  };
  $('#profile-create').addEventListener('click', create);
  $('#profile-name').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') create();
  });
}

/** Asks which profile a mod goes into. Resolves to a profile id, or null when cancelled. */
function pickProfile(modName) {
  return new Promise((resolve) => {
    const modal = $('#target-modal');
    const list = $('#target-list');
    $('#target-title').textContent = `Install ${modName}`;
    list.innerHTML = newestFirst(state.profiles.profiles).map((p) => `
      <button class="target-row ${p.id === state.modProfile ? 'preferred' : ''}" data-target="${esc(p.id)}">
        <span class="target-icon"><i data-icon="layers"></i></span>
        <span class="meta"><span class="n">${esc(p.name)}</span><span class="t">${esc(versionLabel(p))}</span></span>
        <i data-icon="download" class="go"></i>
      </button>`).join('');
    hydrateIcons(list);
    const done = (value) => {
      modal.hidden = true;
      resolve(value);
    };
    for (const row of $$('[data-target]', list)) row.addEventListener('click', () => done(row.dataset.target));
    for (const el of $$('[data-close]', modal)) el.onclick = () => done(null);
    modal.hidden = false;
  });
}

// ---------------------------------------------------------------------------
// mods
// ---------------------------------------------------------------------------


function setModTab(tab) {
  // mods and shaders are different searches
  if ((tab === 'shaders') !== (state.modTab === 'shaders')) {
    state.search.query = '';
    $('#mod-search').value = '';
  }
  state.modTab = tab;
  const tabs = $$('#mod-tabs .tab');
  const active = tabs.find((t) => t.dataset.tab === tab);
  tabs.forEach((t) => t.classList.toggle('active', t === active));
  const ind = $('#mod-tabs .tab-indicator');
  ind.style.width = `${active.offsetWidth}px`;
  ind.style.transform = `translateX(${active.offsetLeft}px)`;
  $('#mod-toolbar').classList.toggle('hidden', tab === 'installed');
  $('#mod-search').placeholder = tab === 'shaders' ? 'Search shaders' : 'Search Modrinth';
  $('#mod-title').textContent = tab === 'shaders' ? 'Shaders' : 'Mods';
  $('#mod-count').style.visibility = tab === 'shaders' ? 'hidden' : '';
  refreshModTab();
}

async function refreshModTab() {
  if (state.page !== 'mods') return;
  if (state.modTab === 'installed') return renderInstalled();
  state.search.offset = 0;
  return runSearch(false);
}

async function loadInstalled() {
  try {
    state.installed = await call(api.mods.installed(state.modProfile));
  } catch {
    state.installed = [];
  }
  const count = state.installed.length;
  $('#mod-count').textContent = `${count} ${count === 1 ? 'mod' : 'mods'} installed`;
}

function installedKey(source, id) {
  return state.installed.some((m) => m.source === source && String(m.projectId) === String(id));
}

async function renderInstalled() {
  await loadInstalled();
  if (state.modTab !== 'installed') return;
  const list = $('#mod-list');
  const user = state.installed;
  const canMigrate = user.some((m) => !m.dependency) && state.profiles.profiles.length > 1;
  let html = `<div class="section-title"><span class="section-actions">${canMigrate ? `<button class="btn-ghost" id="migrate-mods" title="Move mods to another version"><i data-icon="arrow-right-left"></i>Move</button>` : ''}<button class="btn-ghost" id="open-mods-folder"><i data-icon="folder-open"></i>${WORDS.showFolder}</button></span></div>`;
  if (!user.length) {
    html += `<div class="empty"><div class="empty-icon"><i data-icon="puzzle"></i></div><h4>No mods of your own yet</h4><button class="btn btn-primary" id="browse-modrinth"><i data-icon="search"></i>Search Modrinth</button></div>`;
  } else {
    html += user.map((m) => `
      <div class="mod-item" data-file="${esc(m.file)}">
        ${m.icon ? `<img class="mod-icon" src="${esc(m.icon)}" alt="" />` : `<div class="mod-icon"><i data-icon="package"></i></div>`}
        <div class="mod-info">
          <div class="mod-title">${esc(m.name)} <span class="mod-by">${esc(m.version || '')}</span></div>
          <div class="mod-meta"><span class="chip">${m.source === 'local' ? 'Manual' : m.source === 'modrinth' ? 'Modrinth' : 'CurseForge'}</span>${m.dependency ? '<span>Dependency</span>' : ''}<span>${esc(m.file)}</span></div>
        </div>
        <div class="mod-actions">
          <label class="switch" title="Enabled"><input type="checkbox" data-toggle="${esc(m.file)}" ${m.enabled ? 'checked' : ''} /><span></span></label>
          <button class="icon-btn danger" data-delete="${esc(m.file)}" title="Remove"><i data-icon="trash-2"></i></button>
        </div>
      </div>`).join('');
  }
  list.innerHTML = html;
  $('#list-footer').innerHTML = '';
  hydrateIcons(list);
  const browse = $('#browse-modrinth');
  if (browse) browse.addEventListener('click', () => setModTab('modrinth'));
  $('#open-mods-folder').addEventListener('click', () => api.app.openFolder('mods', state.modProfile));
  if (canMigrate) $('#migrate-mods').addEventListener('click', () => openMigrate(state.modProfile));
  for (const t of $$('[data-toggle]', list)) {
    t.addEventListener('change', async () => {
      await call(api.mods.setEnabled(state.modProfile, t.dataset.toggle, t.checked));
      await loadInstalled();
    });
  }
  for (const b of $$('[data-delete]', list)) {
    b.addEventListener('click', async () => {
      const item = b.closest('.mod-item');
      item.style.opacity = '0.4';
      await call(api.mods.remove(state.modProfile, b.dataset.delete));
      toast('Mod removed', 'success', 2000);
      renderInstalled();
    });
  }
}

// ---------------------------------------------------------------------------
// moving mods to another version
// ---------------------------------------------------------------------------

const MIGRATE_STATUS = {
  available: { icon: 'circle-check', cls: 'ok', text: (i) => `Available${i.targetVersion ? ` · ${i.targetVersion}` : ''}` },
  installed: { icon: 'circle-check', cls: 'dim', text: () => 'Already installed' },
  missing: { icon: 'circle-x', cls: 'bad', text: (i, to) => (i.missingDependency ? `Needs ${i.missingDependency}, missing for ${to.mcVersion}` : `Not available for ${to.mcVersion}`) },
  unknown: { icon: 'circle-help', cls: 'dim', text: () => 'Not found on Modrinth' },
};

const migration = { from: null, to: null, plan: null };

function modsCount(n) {
  return n === 1 ? '1 mod' : `${n} mods`;
}

function openMigrate(fromId) {
  Object.assign(migration, { from: fromId, to: null, plan: null });
  $('#migrate-title').textContent = 'Move mods';
  renderMigrateTargets();
  $('#migrate-modal').hidden = false;
}

function closeMigrate() {
  $('#migrate-modal').hidden = true;
  migration.to = null;
}

function renderMigrateTargets() {
  const body = $('#migrate-body');
  const targets = newestFirst(state.profiles.profiles.filter((p) => p.id !== migration.from));
  body.innerHTML = `<div class="target-list">${targets.map((p) => `
    <button class="target-row" data-migrate-to="${esc(p.id)}">${profileText(p)}<i data-icon="arrow-right-left" class="go"></i></button>`).join('')}</div>`;
  hydrateIcons(body);
  for (const b of $$('[data-migrate-to]', body)) b.addEventListener('click', () => checkMigration(b.dataset.migrateTo));
}

async function checkMigration(toId) {
  migration.to = toId;
  const from = profileById(migration.from);
  const to = profileById(toId);
  $('#migrate-title').textContent = `Move to ${to.name}`;
  const body = $('#migrate-body');
  body.innerHTML = `<div class="migrate-wait"><i data-icon="loader-circle" class="spin"></i><span>Checking which mods exist for ${esc(versionLabel(to))}…</span></div>`;
  hydrateIcons(body);
  let plan;
  try {
    plan = await call(api.mods.migrationPlan(migration.from, toId));
  } catch (e) {
    if (migration.to !== toId) return;
    body.innerHTML = `<div class="migrate-summary none"><i data-icon="circle-x"></i><span>${esc(e.message)}</span></div><div class="migrate-actions"><button class="btn" data-migrate-back>Back</button></div>`;
    hydrateIcons(body);
    $('[data-migrate-back]', body).addEventListener('click', renderMigrateTargets);
    return;
  }
  if (migration.to !== toId) return;
  migration.plan = plan;
  const todo = plan.items.filter((i) => i.status === 'available');
  const blocked = plan.items.filter((i) => i.status !== 'available' && i.status !== 'installed');
  // everything exists for the other version: no questions, just move it over
  if (todo.length && !blocked.length) return runMigration(todo);
  renderMigrationPlan();
}

function renderMigrationPlan() {
  const to = profileById(migration.to);
  const items = migration.plan.items;
  const todo = items.filter((i) => i.status === 'available');
  const blocked = items.filter((i) => i.status !== 'available' && i.status !== 'installed');
  let summary;
  if (todo.length) summary = `${todo.length} of ${todo.length + blocked.length} mods are available for ${versionLabel(to)}`;
  else if (blocked.length) summary = `None of these mods are available for ${versionLabel(to)}`;
  else summary = `All mods are already in ${to.name}`;
  const row = (i) => {
    const st = MIGRATE_STATUS[i.status] || MIGRATE_STATUS.unknown;
    return `<div class="migrate-item ${st.cls}">
      ${i.icon ? `<img class="mi-icon" src="${esc(i.icon)}" alt="" />` : `<span class="mi-icon"><i data-icon="package"></i></span>`}
      <span class="mi-name">${esc(i.name)}</span>
      <span class="mi-status"><i data-icon="${st.icon}"></i>${esc(st.text(i, to))}</span>
    </div>`;
  };
  const body = $('#migrate-body');
  body.innerHTML = `
    <div class="migrate-summary ${todo.length ? '' : 'none'}"><i data-icon="${todo.length ? 'circle-check' : 'circle-x'}"></i><span>${esc(summary)}</span></div>
    <div class="migrate-list">${items.map(row).join('')}</div>
    <div class="migrate-actions">
      <button class="btn" data-migrate-back>Andere Version</button>
      ${todo.length ? `<button class="btn btn-primary" data-migrate-run><i data-icon="arrow-right-left"></i>Move ${modsCount(todo.length)}</button>` : ''}
    </div>`;
  hydrateIcons(body);
  $('[data-migrate-back]', body).addEventListener('click', () => {
    migration.to = null;
    $('#migrate-title').textContent = 'Move mods';
    renderMigrateTargets();
  });
  const run = $('[data-migrate-run]', body);
  if (run) run.addEventListener('click', () => runMigration(todo));
}

async function runMigration(todo) {
  const toId = migration.to;
  const to = profileById(toId);
  const body = $('#migrate-body');
  body.innerHTML = `
    <div class="migrate-wait"><i data-icon="loader-circle" class="spin"></i><span id="migrate-stage">Moving ${esc(modsCount(todo.length))} to ${esc(to.name)}…</span></div>
    <div class="migrate-bar"><span id="migrate-fill"></span></div>`;
  hydrateIcons(body);
  let res;
  try {
    res = await call(api.mods.migrate(toId, todo));
  } catch (e) {
    res = { done: [], failed: todo.map((i) => ({ name: i.name, error: e.message })) };
  }
  if (state.page === 'mods' && state.modProfile === toId) refreshModTab();
  if (migration.to !== toId) {
    toast(`Moved ${modsCount(res.done.length)} to ${to.name}`, res.failed.length ? 'error' : 'success');
    return;
  }
  const skipped = migration.plan.items.filter((i) => i.status !== 'available' && i.status !== 'installed');
  const notes = [
    ...skipped.map((i) => `${i.name}: ${(MIGRATE_STATUS[i.status] || MIGRATE_STATUS.unknown).text(i, to)}`),
    ...res.failed.map((f) => `${f.name}: ${f.error}`),
  ];
  body.innerHTML = `
    <div class="migrate-done">
      <div class="empty-icon"><i data-icon="${res.done.length ? 'circle-check' : 'circle-x'}"></i></div>
      <h4>${res.done.length ? `Moved ${esc(modsCount(res.done.length))} to ${esc(to.name)}` : 'Nothing moved'}</h4>
      ${notes.length ? `<p>Not moved:</p><ul>${notes.map((n) => `<li>${esc(n)}</li>`).join('')}</ul>` : '<p>Dependencies were installed automatically.</p>'}
    </div>
    <div class="migrate-actions">
      <button class="btn" data-migrate-close>Close</button>
      <button class="btn btn-primary" data-migrate-show>Open ${esc(to.name)}</button>
    </div>`;
  hydrateIcons(body);
  $('[data-migrate-close]', body).addEventListener('click', closeMigrate);
  $('[data-migrate-show]', body).addEventListener('click', () => {
    closeMigrate();
    pickers.mods.select(toId);
  });
}

function setupMigrate() {
  $$('#migrate-modal [data-close]').forEach((el) => el.addEventListener('click', closeMigrate));
  api.on('mods:progress', (p) => {
    if (p.id !== 'migrate' || !$('#migrate-stage')) return;
    $('#migrate-stage').textContent = `${p.stage}… (${p.index + 1}/${p.total})`;
    $('#migrate-fill').style.width = `${Math.round((p.index / p.total) * 100)}%`;
  });
}

function skeletons(n) {
  return Array.from({ length: n }, () => '<div class="skeleton"></div>').join('');
}

async function runSearch(append) {
  if (state.modTab === 'shaders') return runShaderSearch(append);
  const token = ++state.search.token;
  const list = $('#mod-list');
  const footer = $('#list-footer');
  if (!append) {
    list.innerHTML = skeletons(6);
    footer.innerHTML = '';
  } else {
    footer.innerHTML = `<button class="btn" disabled><i data-icon="loader-circle" class="spin"></i>Loading…</button>`;
    hydrateIcons(footer);
  }
  try {
    const res = await call(api.mods.search({ query: state.search.query, offset: state.search.offset, sort: state.search.sort, profileId: state.modProfile }));
    if (token !== state.search.token) return;
    state.search.total = res.total;
    const html = res.hits.map((m) => modCard(m)).join('');
    if (append) list.insertAdjacentHTML('beforeend', html);
    else list.innerHTML = html || `<div class="empty"><div class="empty-icon"><i data-icon="search"></i></div><h4>Nothing found</h4><p>No Fabric mods for ${esc(profileById(state.modProfile).mcVersion)} match "${esc(state.search.query)}"</p></div>`;
    hydrateIcons(list);
    bindModCards(list);
    state.search.offset += res.hits.length;
    footer.innerHTML = state.search.offset < state.search.total ? `<button class="btn" id="load-more">Load more</button>` : '';
    const more = $('#load-more');
    if (more) more.addEventListener('click', () => runSearch(true));
  } catch (e) {
    if (token !== state.search.token) return;
    list.innerHTML = `<div class="empty"><div class="empty-icon"><i data-icon="triangle-alert"></i></div><h4>Search failed</h4><p>${esc(e.message)}</p><button class="btn" id="retry"><i data-icon="refresh-cw"></i>Try again</button></div>`;
    hydrateIcons(list);
    $('#retry').addEventListener('click', () => runSearch(false));
  }
}

const LOADERS = new Set(['fabric', 'quilt', 'forge', 'neoforge', 'babric', 'legacy-fabric', 'ornithe', 'bta-babric', 'liteloader', 'modloader', 'rift', 'nilloader', 'java-agent']);

function coreEntry(m) {
  return m.source === 'modrinth' ? state.core.find((c) => c.project === m.id) : null;
}

function modCard(m) {
  const core = coreEntry(m);
  const coreActive = core && (core.required || (core.setting && state.settings && state.settings[core.setting]));
  const installed = installedKey(m.source, m.id) || coreActive;
  const busy = state.installing.has(`${m.source}:${m.id}`);
  const cats = (m.categories || []).filter((c) => !LOADERS.has(String(c).toLowerCase())).slice(0, 2);
  return `
    <div class="mod-item" data-source="${m.source}" data-id="${esc(m.id)}">
      ${m.icon ? `<img class="mod-icon" src="${esc(m.icon)}" alt="" loading="lazy" />` : `<div class="mod-icon"><i data-icon="package"></i></div>`}
      <div class="mod-info">
        <div class="mod-title"><a data-url="${esc(m.url)}">${esc(m.name)}</a> <span class="mod-by">by ${esc(m.author)}</span></div>
        <div class="mod-desc">${esc(m.description)}</div>
        <div class="mod-meta"><span><i data-icon="arrow-down-to-line"></i>${formatCount(m.downloads)}</span>${cats.map((c) => `<span class="chip">${esc(c)}</span>`).join('')}</div>
      </div>
      <div class="mod-actions">
        <button class="btn ${installed ? 'installed' : 'btn-primary'}" data-install ${installed || busy ? 'disabled' : ''}>
          ${busy ? '<i data-icon="loader-circle" class="spin"></i>Installing' : coreActive ? '<i data-icon="shield-check"></i>Included in Lumina' : installed ? '<i data-icon="check"></i>Installed' : core ? '<i data-icon="sparkles"></i>Enable in settings' : '<i data-icon="download"></i>Install'}
        </button>
      </div>
    </div>`;
}

function bindModCards(root) {
  for (const a of $$('a[data-url]', root)) {
    a.addEventListener('click', () => api.app.openExternal(a.dataset.url));
  }
  for (const btn of $$('[data-install]', root)) {
    if (btn.dataset.bound) continue;
    btn.dataset.bound = '1';
    btn.addEventListener('click', async () => {
      const item = btn.closest('.mod-item');
      const source = item.dataset.source, id = item.dataset.id;
      const key = `${source}:${id}`;
      const core = coreEntry({ source, id });
      if (core && core.setting) {
        // optional Lumina mods (Iris) are switched on through the launcher settings
        state.settings = await call(api.settings.set({ [core.setting]: true }));
        const box = $('#set-' + core.setting);
        if (box) box.checked = true;
        btn.className = 'btn installed';
        btn.disabled = true;
        btn.innerHTML = '<i data-icon="shield-check"></i>Included in Lumina';
        hydrateIcons(btn);
        toast(`${core.name} will be installed on the next launch`, 'success');
        return;
      }
      const modName = $('.mod-title a', item).textContent;
      const profileId = await pickProfile(modName);
      if (!profileId) return;
      const target = profileById(profileId);
      state.installing.add(key);
      btn.disabled = true;
      btn.innerHTML = '<i data-icon="loader-circle" class="spin"></i>Installing';
      hydrateIcons(btn);
      try {
        const installed = await call(api.mods.install({ id, profileId }));
        await loadInstalled();
        const here = profileId === state.modProfile;
        btn.className = here ? 'btn installed' : 'btn btn-primary';
        btn.disabled = here;
        btn.innerHTML = here ? '<i data-icon="check"></i>Installed' : '<i data-icon="download"></i>Install';
        const name = installed.length ? installed[installed.length - 1].name : modName;
        const deps = installed.length > 1 ? ` + ${installed.length - 1} ${installed.length === 2 ? 'dependency' : 'dependencies'}` : '';
        toast(`${name}${deps} installed in "${target.name}"`, 'success');
      } catch (e) {
        btn.disabled = false;
        btn.className = 'btn btn-primary';
        btn.innerHTML = '<i data-icon="download"></i>Install';
        if (e.code === 'ALREADY_CORE') {
          btn.className = 'btn installed';
          btn.innerHTML = '<i data-icon="check"></i>Included';
          btn.disabled = true;
        }
        toast(e.message, e.code === 'ALREADY_CORE' ? 'info' : 'error', 6000);
      } finally {
        state.installing.delete(key);
        hydrateIcons(btn);
      }
    });
  }
}

// ---------------------------------------------------------------------------
// shaders (one folder shared by every Minecraft version)
// ---------------------------------------------------------------------------

function shaderChip(s) {
  return `
    <div class="shader-chip" data-shader-file="${esc(s.file)}">
      ${s.icon ? `<img class="shader-chip-icon" src="${esc(s.icon)}" alt="" />` : '<span class="shader-chip-icon"><i data-icon="sparkles"></i></span>'}
      <span class="shader-chip-name" title="${esc(s.file)}">${esc(s.name)}</span>
      <button class="icon-btn small danger" data-shader-remove title="Remove"><i data-icon="trash-2"></i></button>
    </div>`;
}

/** The installed packs above the results; hidden while searching. */
async function renderInstalledShaders() {
  state.shaders = await call(api.shaders.installed()).catch(() => []);
  const box = $('#shader-installed');
  if (!box) return;
  if (state.search.query || !state.shaders.length) {
    box.innerHTML = '';
    return;
  }
  box.innerHTML = `
    <div class="section-title"><span>Installiert · ${state.shaders.length}</span><span class="section-actions"><button class="btn-ghost" id="open-shader-folder"><i data-icon="folder-open"></i>${WORDS.showFolder}</button></span></div>
    <div class="shader-chips">${state.shaders.map(shaderChip).join('')}</div>`;
  hydrateIcons(box);
  $('#open-shader-folder').addEventListener('click', () => api.app.openFolder('shaderpacks', state.modProfile));
  for (const b of $$('[data-shader-remove]', box)) {
    b.addEventListener('click', async () => {
      const chip = b.closest('.shader-chip');
      chip.style.opacity = '0.4';
      await call(api.shaders.remove(chip.dataset.shaderFile));
      toast('Shader removed', 'success', 2000);
      await renderInstalledShaders();
      syncShaderButtons();
    });
  }
}

function shaderButton(id) {
  if (state.installing.has(`shader:${id}`)) return { cls: '', icon: 'loader-circle', spin: true, title: 'Downloading', off: true };
  if (state.shaders.some((s) => s.projectId === id)) return { cls: 'installed', icon: 'check', title: 'Installed', off: true };
  return { cls: '', icon: 'download', title: 'Install', off: false };
}

function shaderButtonHtml(id) {
  const b = shaderButton(id);
  return `<button class="shader-get ${b.cls}" data-shader-install title="${b.title}" ${b.off ? 'disabled' : ''}><i data-icon="${b.icon}" ${b.spin ? 'class="spin"' : ''}></i></button>`;
}

function syncShaderButtons() {
  for (const card of $$('.shader-card')) {
    const b = shaderButton(card.dataset.id);
    const btn = $('[data-shader-install]', card);
    btn.className = `shader-get ${b.cls}`;
    btn.title = b.title;
    btn.disabled = b.off;
    btn.innerHTML = `<i data-icon="${b.icon}" ${b.spin ? 'class="spin"' : ''}></i>`;
    hydrateIcons(btn);
  }
}

function shaderCard(m) {
  const shot = m.image || m.icon;
  return `
    <div class="shader-card" data-id="${esc(m.id)}">
      <div class="shader-shot">${shot ? `<img src="${esc(shot)}" alt="" loading="lazy" />` : '<i data-icon="image"></i>'}</div>
      <div class="shader-body">
        <div class="shader-text">
          <a class="shader-name" data-url="${esc(m.url)}" title="${esc(m.name)}">${esc(m.name)}</a>
          <div class="shader-meta"><i data-icon="arrow-down-to-line"></i>${formatCount(m.downloads)}<span class="shader-author">${esc(m.author)}</span></div>
        </div>
        ${shaderButtonHtml(m.id)}
      </div>
    </div>`;
}

function bindShaderCards(root) {
  for (const card of $$('.shader-card', root)) {
    if (card.dataset.bound) continue;
    card.dataset.bound = '1';
    $('[data-url]', card).addEventListener('click', (e) => api.app.openExternal(e.currentTarget.dataset.url));
    $('[data-shader-install]', card).addEventListener('click', async () => {
      const id = card.dataset.id;
      state.installing.add(`shader:${id}`);
      syncShaderButtons();
      try {
        const res = await call(api.shaders.install({ id, profileId: state.modProfile }));
        // shaders need Iris: switch it on instead of leaving a pack nothing can load
        if (state.settings && !state.settings.shaders) {
          state.settings = await call(api.settings.set({ shaders: true }));
          const box = $('#set-shaders');
          if (box) box.checked = true;
        }
        toast(`${res.name} installed`, 'success');
      } catch (e) {
        toast(e.message, 'error', 6000);
      }
      state.installing.delete(`shader:${id}`);
      await renderInstalledShaders();
      syncShaderButtons();
    });
  }
}

async function runShaderSearch(append) {
  const token = ++state.search.token;
  const list = $('#mod-list');
  const footer = $('#list-footer');
  if (append) {
    footer.innerHTML = '<button class="btn" disabled><i data-icon="loader-circle" class="spin"></i>Loading…</button>';
    hydrateIcons(footer);
  } else {
    const version = versionLabel(profileById(state.modProfile));
    list.innerHTML = `
      <div id="shader-installed"></div>
      <div class="section-title"><span>For ${esc(version)}</span></div>
      <div class="shader-grid">${Array.from({ length: 6 }, () => '<div class="skeleton shader-skeleton"></div>').join('')}</div>`;
    footer.innerHTML = '';
    await renderInstalledShaders();
    if (token !== state.search.token) return;
  }
  try {
    const res = await call(api.shaders.search({ query: state.search.query, offset: state.search.offset, sort: state.search.sort, profileId: state.modProfile }));
    if (token !== state.search.token) return;
    state.search.total = res.total;
    const grid = $('.shader-grid', list);
    const html = res.hits.map(shaderCard).join('');
    if (append) grid.insertAdjacentHTML('beforeend', html);
    else grid.innerHTML = html;
    if (!append && !html) grid.outerHTML = `<div class="empty"><div class="empty-icon"><i data-icon="search"></i></div><h4>Nothing found</h4><p>No shaders for ${esc(versionLabel(profileById(state.modProfile)))} match "${esc(state.search.query)}"</p></div>`;
    hydrateIcons(list);
    bindShaderCards(list);
    state.search.offset += res.hits.length;
    footer.innerHTML = state.search.offset < state.search.total ? '<button class="btn" id="load-more">Load more</button>' : '';
    const more = $('#load-more');
    if (more) more.addEventListener('click', () => runSearch(true));
  } catch (e) {
    if (token !== state.search.token) return;
    list.innerHTML = `<div class="empty"><div class="empty-icon"><i data-icon="triangle-alert"></i></div><h4>Search failed</h4><p>${esc(e.message)}</p><button class="btn" id="retry"><i data-icon="refresh-cw"></i>Try again</button></div>`;
    hydrateIcons(list);
    $('#retry').addEventListener('click', () => runSearch(false));
  }
}

function setupMods() {
  $$('#mod-tabs .tab').forEach((t) => t.addEventListener('click', () => setModTab(t.dataset.tab)));
  const doSearch = debounce(() => {
    state.search.query = $('#mod-search').value.trim();
    state.search.offset = 0;
    runSearch(false);
  }, 320);
  $('#mod-search').addEventListener('input', doSearch);
  $('#mod-sort').addEventListener('change', () => {
    state.search.sort = $('#mod-sort').value;
    state.search.offset = 0;
    runSearch(false);
  });
  // set the initial tab without triggering a load; the indicator is measured once laid out
  const first = $('#mod-tabs .tab.active');
  const ind = $('#mod-tabs .tab-indicator');
  setTimeout(() => {
    ind.style.width = `${first.offsetWidth}px`;
    ind.style.transform = `translateX(${first.offsetLeft}px)`;
  }, 0);
}

// ---------------------------------------------------------------------------
// settings
// ---------------------------------------------------------------------------

function memoryLabel(mb) {
  return `${(mb / 1024).toFixed(mb % 1024 ? 1 : 0)} GB`;
}

function updateRange(input) {
  const p = ((input.value - input.min) / (input.max - input.min)) * 100;
  input.style.setProperty('--p', `${p}%`);
}

async function saveSettings(partial) {
  try {
    state.settings = await call(api.settings.set(partial));
    $('#console').classList.toggle('visible', !!state.settings.showConsole);
  } catch (e) {
    toast(e.message, 'error');
  }
}

function setupSettings() {
  const s = state.settings;
  const mem = $('#set-memory');
  const maxMem = Math.max(2048, Math.floor((state.info.totalMemoryMb - 1536) / 512) * 512);
  mem.max = String(maxMem);
  mem.value = String(Math.min(s.memoryMb, maxMem));
  $('#set-memory-value').textContent = memoryLabel(s.memoryMb);
  updateRange(mem);
  mem.addEventListener('input', () => {
    $('#set-memory-value').textContent = memoryLabel(Number(mem.value));
    updateRange(mem);
  });
  mem.addEventListener('change', () => saveSettings({ memoryMb: Number(mem.value) }));

  const bindValue = (sel, key, transform = (v) => v) => {
    const el = $(sel);
    el.value = s[key] ?? '';
    el.addEventListener('change', () => saveSettings({ [key]: transform(el.value) }));
  };
  const bindCheck = (sel, key, after) => {
    const el = $(sel);
    el.checked = !!s[key];
    el.addEventListener('change', async () => {
      await saveSettings({ [key]: el.checked });
      if (after) after(el.checked);
    });
  };
  bindValue('#set-width', 'width', Number);
  bindValue('#set-height', 'height', Number);
  bindValue('#set-autojoin', 'autoJoin', (v) => v.trim());
  bindValue('#set-java', 'javaPath', (v) => v.trim());
  bindValue('#set-jvm', 'jvmArgs', (v) => v.trim());
  bindValue('#set-after', 'afterLaunch');
  bindCheck('#set-fullscreen', 'fullscreen');
  bindCheck('#set-console', 'showConsole');
  bindCheck('#set-shaders', 'shaders', (on) => toast(on ? 'Iris will be installed on the next launch' : 'Iris will be removed on the next launch', 'info'));
  $$('[data-folder]').forEach((b) => b.addEventListener('click', () => api.app.openFolder(b.dataset.folder, state.profiles.selected)));
  $('#about').innerHTML = `LuminaClient ${esc(state.info.version)} · Lumina ${esc(state.info.mcVersion)}<br>Data: ${esc(state.info.dataDir)}`;
  $('#console').classList.toggle('visible', !!s.showConsole);
}

// ---------------------------------------------------------------------------
// cosmetics
// ---------------------------------------------------------------------------

/** An effect's icon like the Lumina menu draws it: the game's particle sprite, tinted (data URL, cached). */
const fxIcons = {};

function fxIcon(effect) {
  const color = fxColor(effect);
  const key = `${effect.sprite}${color}`;
  if (!fxIcons[key]) {
    const sprite = window.LuminaParticles.paint(effect.sprite);
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = sprite.width;
    const g = canvas.getContext('2d');
    // sprite x color, keeping the sprite's alpha
    g.drawImage(sprite, 0, 0);
    g.globalCompositeOperation = 'multiply';
    g.fillStyle = color;
    g.fillRect(0, 0, canvas.width, canvas.height);
    g.globalCompositeOperation = 'destination-in';
    g.drawImage(sprite, 0, 0);
    fxIcons[key] = canvas.toDataURL();
  }
  return fxIcons[key];
}

function fxColor(effect) {
  return effect.tinted ? state.cosmetics.tint : effect.color;
}

/** Puts the cape texture onto its .cape-face elements; textures with stacked frames show the first one. */
function paintCapes(root) {
  for (const el of $$('[data-cape-face]', root)) {
    const cape = state.cosmetics.capes.find((c) => c.id === el.dataset.capeFace);
    if (!cape) continue;
    el.style.backgroundImage = `url("${cape.data}")`;
    const img = new Image();
    img.onload = () => {
      // LabyMod layout (22:17): only the part of the 64x32 canvas that holds the cape
      if (img.width * 17 === img.height * 22) {
        el.style.backgroundSize = '220% 106.25%';
        return;
      }
      const frames = Math.max(1, Math.round(img.height / (img.width / 2)));
      if (frames > 1) el.style.backgroundSize = `640% ${200 * frames}%`;
    };
    img.src = cape.data;
  }
}

/** The 3D player of the preview (skinview3d); null until the page is first shown, or when WebGL is missing. */
const preview3d = { viewer: null, particles: null, failed: false, skin: null, skinTurn: 0, capeId: null, frames: null, timer: null, token: 0 };

function ensureViewer() {
  if (preview3d.viewer || preview3d.failed) return preview3d.viewer;
  const stage = $('#cos-stage');
  try {
    const canvas = document.createElement('canvas');
    canvas.className = 'cos-canvas';
    stage.prepend(canvas);
    const viewer = new window.skinview3d.SkinViewer({ canvas, width: stage.clientWidth, height: stage.clientHeight });
    viewer.fov = 38;
    viewer.zoom = 0.72;
    // a calm turn (radians per second): one round in about 18 seconds
    viewer.autoRotate = true;
    viewer.autoRotateSpeed = 0.35;
    viewer.animation = new window.skinview3d.IdleAnimation();
    viewer.controls.enableZoom = false;
    viewer.controls.enablePan = false;
    // seen from behind at an angle first, so the cape shows
    viewer.playerWrapper.rotation.y = Math.PI * 0.8;
    viewer.playerObject.visible = false;
    // the effect's particles, exactly as the game simulates and draws them
    preview3d.particles = new window.LuminaParticles.Preview(viewer, window.skinview3d);
    preview3d.viewer = viewer;
    // built ahead of time at launcher start: it only renders once its page is open
    viewer.renderPaused = state.page !== 'cosmetics';
    placeCosFloor(viewer);
  } catch {
    preview3d.failed = true;
    $('.cos-canvas', stage)?.remove();
  }
  return preview3d.viewer;
}

/** Puts the ring under the preview's feet, the way lobby.js does it on the start page. */
function placeCosFloor(viewer) {
  viewer.controls.update();
  viewer.camera.updateMatrixWorld();
  const height = $('#cos-stage').clientHeight;
  const screenY = (y) => ((1 - viewer.playerObject.position.clone().set(0, y, 0).project(viewer.camera).y) / 2) * height;
  const feet = screenY(-16);
  const preview = $('#cos-preview');
  preview.style.setProperty('--feet', `${feet.toFixed(1)}px`);
  preview.style.setProperty('--unit', `${((feet - screenY(16)) / 32).toFixed(2)}px`);
}

/** The preview's skin without asking Mojang: the selected account's last known one, or Steve from last time. */
function knownPreviewSkin() {
  const acc = state.accounts.accounts.find((a) => a.id === state.accounts.selected);
  if (acc && acc.type === 'msa') return (faces[acc.uuid] && faces[acc.uuid].skin) || savedSkin(acc.uuid);
  return savedSkin('steve');
}

async function wearPreviewSkin(viewer, skin) {
  if (!preview3d.skin || preview3d.skin.url !== skin.url || preview3d.skin.slim !== skin.slim) {
    await viewer.loadSkin(skin.data, { model: skin.slim ? 'slim' : 'default' });
    preview3d.skin = skin;
  }
  viewer.playerObject.visible = true;
}

/**
 * Loads the account's skin into the 3D player (Steve without a Microsoft account): the known one at
 * once, so the player is there together with its cape, then the current one from Mojang.
 */
async function loadPreviewSkin() {
  const viewer = ensureViewer();
  if (!viewer) return;
  const turn = ++preview3d.skinTurn;
  const known = knownPreviewSkin();
  if (known && known.data) {
    await wearPreviewSkin(viewer, known).catch(() => {});
    if (state.cosmetics) renderCosPreview();
  }
  try {
    const skin = await call(api.cosmetics.skin());
    // Steve only stands in when Mojang can't be reached: then the known skin stays
    const standIn = skin && skin.url === 'steve' && known && known.url !== 'steve';
    if (skin && turn === preview3d.skinTurn && !standIn) {
      await wearPreviewSkin(viewer, skin);
      if (skin.url === 'steve') saveSkin('steve', skin);
    }
  } catch {
    /* no skin: the preview keeps what it has, or the flat picture */
  }
  if (state.cosmetics) renderCosPreview();
}

/** All frames of a cape as 2:1 canvases (APNG/GIF via ImageDecoder, frames stacked in a PNG), at most 512 px wide. */
async function capeFrames(cape) {
  const type = cape.data.slice(5, cape.data.indexOf(';'));
  const frames = [];
  const delays = [];
  const canvasFor = (w) => {
    const c = document.createElement('canvas');
    c.width = Math.min(512, w);
    c.height = c.width / 2;
    return c;
  };
  if (type !== 'image/png' && 'ImageDecoder' in window) {
    try {
      const bytes = Uint8Array.from(atob(cape.data.slice(cape.data.indexOf(',') + 1)), (ch) => ch.charCodeAt(0));
      const decoder = new ImageDecoder({ data: bytes, type: type === 'image/apng' ? 'image/png' : type });
      await decoder.tracks.ready;
      const count = Math.min(decoder.tracks.selectedTrack.frameCount, 240);
      for (let i = 0; i < count; i++) {
        const { image } = await decoder.decode({ frameIndex: i });
        const c = canvasFor(image.displayWidth);
        c.getContext('2d').drawImage(image, 0, 0, c.width, c.width * (image.displayHeight / image.displayWidth));
        delays.push(Math.max(20, (image.duration || 100000) / 1000));
        image.close();
        frames.push(c);
      }
      decoder.close();
      if (frames.length) return { frames, delays };
    } catch {
      /* fall back to the first frame */
    }
  }
  const img = new Image();
  img.src = cape.data;
  await img.decode();
  // LabyMod layout (22:17): only the part of the 64x32 canvas that holds the cape
  if (img.width * 17 === img.height * 22) {
    const c = canvasFor((img.width * 64) / 22);
    const scale = c.width / 64;
    c.getContext('2d').drawImage(img, 0, 0, 22 * scale, 17 * scale);
    return { frames: [c], delays: [100] };
  }
  const frameH = img.width / 2;
  const count = Math.max(1, Math.round(img.height / frameH));
  for (let i = 0; i < count; i++) {
    const c = canvasFor(img.width);
    const g = c.getContext('2d');
    g.imageSmoothingEnabled = false;
    g.drawImage(img, 0, i * frameH, img.width, frameH, 0, 0, c.width, c.height);
    frames.push(c);
    delays.push(100);
  }
  return { frames, delays };
}

/** Puts the chosen cape on a 3D player (the preview or the start page's character) and plays its frames. */
async function applyPreviewCape(cape, target = preview3d) {
  const viewer = target.viewer;
  if (!viewer || (cape ? cape.id : null) === target.capeId) return;
  target.capeId = cape ? cape.id : null;
  const token = ++target.token;
  clearTimeout(target.timer);
  if (!cape) {
    viewer.loadCape(null);
    return;
  }
  const anim = await capeFrames(cape).catch(() => null);
  if (token !== target.token || !anim) return;
  let i = 0;
  const step = () => {
    if (token !== target.token) return;
    // nobody watches a paused player: an animated cape just waits for the next frame
    if (anim.frames.length === 1 || !viewer.renderPaused) viewer.loadCape(anim.frames[i]);
    if (anim.frames.length > 1) {
      target.timer = setTimeout(step, anim.delays[i]);
      i = (i + 1) % anim.frames.length;
    }
  };
  step();
}

function renderCosPreview() {
  const c = state.cosmetics;
  const stage = $('#cos-stage');
  const cape = c.capes.find((x) => x.id === c.cape);
  const effect = c.effects.find((e) => e.id === c.effect);
  const viewer = preview3d.viewer;
  const player = !!(viewer && viewer.playerObject.visible);
  $('#cos-preview').classList.toggle('has-player', player);
  // the player wears everything you picked; without one the preview shows the picked item flat
  let flat = '';
  if (!player && state.cosTab === 'capes') flat = cape ? `<span class="cape-face" data-cape-face="${esc(cape.id)}"></span>` : '<div class="cos-none"><i data-icon="x"></i></div>';
  if (!player && state.cosTab === 'effects') flat = effect ? `<span class="fx-icon fx-big"><img src="${fxIcon(effect)}" alt="" /></span>` : '<div class="cos-none"><i data-icon="x"></i></div>';
  let layer = $('.cos-layer', stage);
  if (!layer) {
    layer = document.createElement('div');
    layer.className = 'cos-layer';
    stage.append(layer);
  }
  layer.innerHTML = flat;
  if (preview3d.particles) preview3d.particles.set(effect ? effect.id : null, c.tint, c.density);
  if (player) applyPreviewCape(cape);
  paintCapes(layer);
  hydrateIcons(layer);
  if (state.cosTab === 'capes') {
    $('#cos-name').textContent = cape ? cape.name : 'No cape';
  } else {
    $('#cos-name').textContent = effect ? effect.name : 'No effect';
  }
}

function renderCosmetics() {
  const c = state.cosmetics;
  if (!c) return;
  const grid = $('#cos-grid');
  const capes = state.cosTab === 'capes';
  grid.classList.toggle('effects', !capes);
  const check = '<span class="check"><i data-icon="check"></i></span>';
  const none = `<button class="cos-tile ${(capes ? c.cape : c.effect) === '' ? 'selected' : ''}" data-pick="">${check}<span class="cos-none"><i data-icon="x"></i></span><span class="n">None</span></button>`;
  const tiles = capes
    ? c.capes.map((cape, i) => `
      <button class="cos-tile ${cape.id === c.cape ? 'selected' : ''}" data-pick="${esc(cape.id)}" title="${esc(cape.name)}">
        ${check}${cape.bundled ? '' : `<span class="del" data-remove="${esc(cape.id)}" title="Delete cape"><i data-icon="trash-2"></i></span>`}
        <span class="cape-face" data-cape-face="${esc(cape.id)}"></span>
        <span class="n">${esc(cape.name)}</span>
      </button>`)
    : c.effects.map((e, i) => `
      <button class="cos-tile ${e.id === c.effect ? 'selected' : ''}" data-pick="${esc(e.id)}">
        ${check}<span class="fx-icon"><img src="${fxIcon(e)}" alt="" /></span>
        <span class="n">${esc(e.name)}</span>
      </button>`);
  grid.innerHTML = none + tiles.join('');
  hydrateIcons(grid);
  paintCapes(grid);
  $('.cos-footer').hidden = !capes;
  renderCosPreview();
  renderCosLock();
}

/** Moves the selection without rebuilding the grid (no replayed tile animation, no image reload). */
function markCosSelection() {
  const current = state.cosTab === 'capes' ? state.cosmetics.cape : state.cosmetics.effect;
  for (const tile of $$('#cos-grid [data-pick]')) tile.classList.toggle('selected', tile.dataset.pick === current);
  renderCosPreview();
}

/** While Minecraft runs, the game owns the config: the page only shows the current choice. */
function renderCosLock() {
  const locked = state.game === 'running' || state.game === 'preparing';
  $('.page-cosmetics').classList.toggle('locked', locked);
  $('#cos-notice').hidden = !locked;
}

/**
 * What was loaded ahead (at launcher start) shows at once; the files are read again behind it and
 * the page only redraws if something changed (e.g. a cape picked in the game).
 */
async function loadCosmetics() {
  const had = !!state.cosmetics;
  if (had) {
    renderCosmetics();
    loadPreviewSkin();
  }
  try {
    const fresh = await call(api.cosmetics.state());
    const changed = !had || cosSignature(fresh) !== cosSignature(state.cosmetics);
    state.cosmetics = fresh;
    if (changed) renderCosmetics();
    if (!had) loadPreviewSkin();
  } catch (e) {
    if (!had) toast(e.message, 'error');
  }
}

function cosSignature(c) {
  return [c.cape, c.effect, c.tint, c.density, ...c.capes.map((x) => `${x.id}:${x.data ? x.data.length : 0}`)].join('|');
}

async function cosmeticsAction(promise, message) {
  try {
    // a choice comes back alone ({ cape, effect }), adding or removing capes returns everything
    const result = await call(promise);
    state.cosmetics = { ...state.cosmetics, ...result };
    if (result.capes) renderCosmetics();
    else markCosSelection();
    loadLobbyCosmetics();
    if (message) toast(message, 'success', 2500);
  } catch (e) {
    toast(e.message, 'error', 6000);
  }
}

function setCosTab(tab) {
  state.cosTab = tab;
  const tabs = $$('#cos-tabs .tab');
  const active = tabs.find((t) => t.dataset.tab === tab);
  tabs.forEach((t) => t.classList.toggle('active', t === active));
  const ind = $('#cos-tabs .tab-indicator');
  ind.style.width = `${active.offsetWidth}px`;
  ind.style.transform = `translateX(${active.offsetLeft}px)`;
  $('#cos-scroll').scrollTop = 0;
  renderCosmetics();
}

function setupCosmetics() {
  $$('#cos-tabs .tab').forEach((t) => t.addEventListener('click', () => setCosTab(t.dataset.tab)));
  requestAnimationFrame(() => {
    const active = $('#cos-tabs .tab.active');
    const ind = $('#cos-tabs .tab-indicator');
    ind.style.width = `${active.offsetWidth}px`;
    ind.style.transform = `translateX(${active.offsetLeft}px)`;
  });
  $('#cos-folder').addEventListener('click', () => api.cosmetics.openFolder());
  $('#cos-grid').addEventListener('click', (e) => {
    const del = e.target.closest('[data-remove]');
    if (del) {
      e.stopPropagation();
      const cape = state.cosmetics.capes.find((c) => c.id === del.dataset.remove);
      if (cape && confirm(`Delete cape "${cape.name}"?`)) cosmeticsAction(api.cosmetics.remove(cape.id), 'Cape deleted');
      return;
    }
    const tile = e.target.closest('[data-pick]');
    if (!tile || tile.classList.contains('selected')) return;
    cosmeticsAction(api.cosmetics.select(state.cosTab === 'capes' ? { cape: tile.dataset.pick } : { effect: tile.dataset.pick }));
  });

  // own capes: drop PNG/APNG/GIF files anywhere on the page
  const page = $('.page-cosmetics');
  const drop = $('#cos-drop');
  let depth = 0;
  const hasFiles = (e) => e.dataTransfer && [...e.dataTransfer.types].includes('Files');
  page.addEventListener('dragenter', (e) => {
    if (!hasFiles(e) || page.classList.contains('locked')) return;
    e.preventDefault();
    depth++;
    drop.hidden = false;
  });
  page.addEventListener('dragover', (e) => {
    if (hasFiles(e)) e.preventDefault();
  });
  page.addEventListener('dragleave', () => {
    if (depth > 0 && --depth === 0) drop.hidden = true;
  });
  page.addEventListener('drop', (e) => {
    if (!hasFiles(e)) return;
    e.preventDefault();
    depth = 0;
    drop.hidden = true;
    if (page.classList.contains('locked')) return;
    if (state.cosTab !== 'capes') setCosTab('capes');
    const count = e.dataTransfer.files.length;
    cosmeticsAction(api.cosmetics.add(e.dataTransfer.files), count === 1 ? 'Cape added and equipped' : `${count} capes added`);
  });
}

// ---------------------------------------------------------------------------
// updates
// ---------------------------------------------------------------------------

function timeAgo(ms) {
  const min = Math.round((Date.now() - ms) / 60000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min} min ago`;
  const h = Math.round(min / 60);
  return h < 24 ? `${h} h ago` : `${Math.round(h / 24)} days ago`;
}

function updateStatusText(u) {
  if (!u.enabled) return IS_MAC ? 'Updates only work in the installed version' : 'Automatic updates only work in the installed version';
  switch (u.state) {
    case 'checking':
      return 'Checking for updates…';
    case 'downloading':
      // macOS checks a local build, Windows downloads from GitHub
      return `Version ${u.version} ${IS_MAC ? 'is being checked and prepared' : 'is downloading in the background'}… ${Math.round(u.progress * 100)}%`;
    case 'ready':
      return `Version ${u.version} is ready and installs on restart. Mods, worlds and settings are kept.`;
    case 'none':
      return `You have the latest version. Last checked ${timeAgo(u.checkedAt)}`;
    case 'error':
      return `Update check failed: ${u.error}`;
    default:
      return '';
  }
}

function renderUpdate(u) {
  const wasReady = state.update && state.update.state === 'ready';
  state.update = u;
  const ready = u.state === 'ready';
  const card = $('#update-card');
  card.hidden = !(u.state === 'downloading' || ready);
  if (!card.hidden) {
    $('#update-title').textContent = ready ? `Update ${u.version} ready` : `${IS_MAC ? 'Preparing' : 'Downloading'} update ${u.version || ''}`;
    $('#update-sub').textContent = ready ? (state.game === 'running' ? 'Installs after you stop playing' : 'Installs on restart') : `${Math.round(u.progress * 100)}%`;
    $('#update-bar').hidden = ready;
    $('#update-fill').style.width = `${Math.round(u.progress * 100)}%`;
    $('#update-restart').hidden = !ready;
    $('#update-restart').disabled = state.game === 'running';
    $('#update-icon').dataset.icon = ready ? 'circle-check' : 'download';
    hydrateIcons(card);
  }

  $('#upd-version').textContent = `LuminaClient ${u.current}`;
  $('#upd-status').textContent = updateStatusText(u);
  const action = $('#upd-action');
  action.className = ready ? 'btn btn-primary' : 'btn';
  action.disabled = !u.enabled || u.state === 'checking' || u.state === 'downloading' || (ready && state.game === 'running');
  action.innerHTML = ready ? '<i data-icon="rotate-cw"></i><span>Restart now</span>' : u.state === 'checking' ? '<i data-icon="loader-circle" class="spin"></i><span>Checking…</span>' : '<i data-icon="refresh-cw"></i><span>Check for updates</span>';
  hydrateIcons(action);
  $('#upd-notes-row').hidden = !u.notes;
  $('#upd-notes-title').textContent = u.version ? `New in ${u.version}` : 'New in this version';
  $('#upd-notes').textContent = u.notes || '';

  if (ready && !wasReady) toast(`Update ${u.version} is ready. Restart the launcher to install it`, 'success', 6000);
}

async function installUpdate() {
  try {
    await call(api.update.install());
  } catch (e) {
    toast(e.message, 'error', 6000);
  }
}

function formatBytes(n) {
  if (n >= 1024 ** 3) return `${(n / 1024 ** 3).toFixed(1)} GB`;
  if (n >= 1024 ** 2) return `${Math.round(n / 1024 ** 2)} MB`;
  return `${Math.max(1, Math.round(n / 1024))} KB`;
}

function backupDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

async function renderBackupSub() {
  try {
    const list = await call(api.backups.list());
    $('#backup-sub').textContent = list.length ? `Last: ${backupDate(list[0].createdAt)}` : '';
  } catch {
    /* the text stays generic */
  }
}

async function openBackups() {
  const modal = $('#backup-modal');
  const list = $('#backup-list');
  const backups = await call(api.backups.list());
  list.innerHTML = backups.length
    ? backups.map((b) => `
      <button class="target-row" data-backup="${esc(b.name)}">
        <span class="target-icon"><i data-icon="archive"></i></span>
        <span class="meta"><span class="n">${esc(backupDate(b.createdAt))}</span><span class="t">${esc(b.label)}${b.files ? ` · ${b.files} files, ${formatBytes(b.bytes)}` : ''}</span></span>
        <i data-icon="rotate-ccw" class="go"></i>
      </button>`).join('')
    : '<div class="empty"><p>No backups yet. Use Back up now to make the first one.</p></div>';
  hydrateIcons(list);
  for (const row of $$('[data-backup]', list)) {
    row.addEventListener('click', async () => {
      const b = backups.find((x) => x.name === row.dataset.backup);
      if (!confirm(`Restore the backup from ${backupDate(b.createdAt)}?\n\nSettings, Lumina config and mods go back to this state. The current state is backed up first.`)) return;
      try {
        await call(api.backups.restore(b.name));
        // settings, profiles and mod lists all changed: start the page fresh
        location.reload();
      } catch (e) {
        toast(e.message, 'error', 6000);
      }
    });
  }
  modal.hidden = false;
}

function setupUpdates() {
  api.on('update:state', renderUpdate);
  $('#upd-feed').addEventListener('click', () => api.update.openFeed());
  $('#set-after-minimize').textContent = WORDS.minimize;
  $$('#backup-modal [data-close]').forEach((el) => el.addEventListener('click', () => ($('#backup-modal').hidden = true)));
  $('#backup-restore').addEventListener('click', () => openBackups().catch((e) => toast(e.message, 'error')));
  $('#backup-now').addEventListener('click', async () => {
    const btn = $('#backup-now');
    btn.disabled = true;
    try {
      const folder = await call(api.backups.create());
      toast(folder ? 'Backup created' : 'Nothing to back up yet', folder ? 'success' : 'info', 3000);
      renderBackupSub();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      btn.disabled = false;
    }
  });
  renderBackupSub();
  $('#update-restart').addEventListener('click', installUpdate);
  $('#upd-action').addEventListener('click', async () => {
    if (state.update && state.update.state === 'ready') return installUpdate();
    try {
      const u = await call(api.update.check());
      if (u.state === 'none') toast('You have the latest version', 'success', 2500);
      else if (u.state === 'error') toast(u.error, 'error', 6000);
    } catch (e) {
      toast(e.message, 'error');
    }
  });
}

// ---------------------------------------------------------------------------
// boot
// ---------------------------------------------------------------------------

async function boot() {
  hydrateIcons();
  setupNav();
  let profiles;
  [state.info, state.settings, state.accounts, profiles] = await Promise.all([call(api.app.info()), call(api.settings.get()), call(api.accounts.list()), call(api.profiles.list())]);
  state.modProfile = profiles.selected;
  setProfiles(profiles);
  setupLobby();
  renderAccountChip();
  setupAccounts();
  setupWelcome();
  setupSkinRefresh();
  setupPlay();
  setupExtra();
  setupProfiles();
  setupSettings();
  setupMods();
  setupMigrate();
  setupCosmetics();
  setupUpdates();
  renderUpdate(await call(api.update.status()));
  await loadInstalled();
  state.core = await call(api.mods.core());
  const status = await call(api.game.status());
  if (status.extra) state.extra = status.extra;
  if (status.running) setGameState('running');
  renderExtra();
  // faces of all accounts and the Cosmetics page with its 3D preview are ready before anyone opens them
  preloadFaces();
  setTimeout(() => {
    if (!state.cosmetics) loadCosmetics();
  }, 600);
  if (!state.info.bundledJar) toast('Lumina mod not found. Please rebuild the launcher', 'error', 8000);
  // macOS menu bar: Einstellungen (⌘,) and Spielen (⌘↩)
  api.on('menu:page', (page) => showPage(page));
  api.on('menu:play', () => {
    const btn = $('#play-btn');
    if (state.game === 'idle' || state.game === 'error') btn.click();
  });
  showPage('home');
  if (!state.settings.welcomed) {
    if (state.accounts.accounts.length) call(api.settings.set({ welcomed: true })).then((s) => (state.settings = s));
    else showWelcome();
  }
}

const booted = boot().catch((e) => toast(e.message, 'error', 10000));

// Used by the dev screenshot mode (electron . --screenshot=file --page=mods-modrinth)
window.__devShow = async (page) => {
  document.documentElement.classList.add('no-anim');
  await booted;
  const [p, tab] = page.split('-');
  if (p === 'update') {
    // fake states for screenshots: update-downloading, update-ready
    const ready = tab === 'ready';
    renderUpdate({ enabled: true, state: ready ? 'ready' : 'downloading', current: state.info.version, version: '1.1.1', progress: ready ? 1 : 0.42, notes: '• New skin changer\n• Particles in the inventory preview\n• Bug fixes', checkedAt: Date.now() });
    showPage('settings');
    return;
  }
  if (p === 'accounts') {
    openAccounts();
    return;
  }
  if (p === 'play') {
    // play-busy, play-running, play-error, play-update, play-log: fake states of the start page
    if (tab === 'busy') {
      setGameState('preparing');
      $('#stage').textContent = 'Downloading Minecraft';
      $('#stage-sub').textContent = 'Libraries · 312 of 684';
      $('#play-fill').style.width = '46%';
    }
    if (tab === 'running') setGameState('running');
    // play-two: a second game runs; play-extra: its account menu is open
    if (tab === 'two') {
      const other = state.accounts.accounts.find((a) => a.id !== state.accounts.selected) || { name: 'Steve', type: 'offline' };
      state.extra = { state: 'running', accountId: other.id, name: other.name, type: other.type, uuid: other.uuid };
      setGameState('running');
    }
    if (tab === 'extra') {
      setGameState('running');
      openExtraMenu();
    }
    if (tab === 'log') $('#console').classList.add('visible');
    if (tab === 'error') setGameState('error', { error: "Can't reach Mojang's servers" });
    if (tab === 'update') renderUpdate({ enabled: true, state: 'ready', current: state.info.version, version: '1.7.0', progress: 1, notes: '', checkedAt: Date.now() });
    return;
  }
  if (p === 'welcome') {
    showWelcome();
    return;
  }
  if (p === 'crash') {
    setGameState('idle', { crashed: true, error: 'Exit code 1' });
    return;
  }
  if (p === 'versions') {
    $('#version-btn').click();
    return;
  }
  if (p === 'newprofile') {
    openProfileModal();
    return;
  }
  if (p === 'modpicker' || p === 'installed') {
    // the Mods page with the first profile that has mods (modpicker also opens the version menu)
    state.modProfile = 'default';
    renderModPicker();
    showPage('mods');
    if (p === 'modpicker') setTimeout(() => togglePicker('mods'), 300);
    return;
  }
  if (p === 'migrate') {
    // migrate: pick a target; migrate-check: result for the other Minecraft version
    state.modProfile = 'default';
    renderModPicker();
    showPage('mods');
    openMigrate('default');
    if (tab === 'check') {
      const from = profileById('default');
      const to = state.profiles.profiles.find((x) => x.builtin && x.mcVersion !== from.mcVersion);
      if (to) checkMigration(to.id);
    }
    if (tab === 'run') {
      // migrate-run: transfer everything available to the other Minecraft version
      const from = profileById('default');
      const to = state.profiles.profiles.find((x) => x.builtin && x.mcVersion !== from.mcVersion);
      await checkMigration(to.id);
      if (migration.plan) runMigration(migration.plan.items.filter((i) => i.status === 'available'));
    }
    return;
  }
  if (p === 'cosmetics') {
    // cosmetics-capes, cosmetics-effects
    showPage('cosmetics');
    await loadCosmetics();
    if (tab) setCosTab(tab);
    return;
  }
  if (p === 'target') {
    showPage('mods');
    pickProfile('Sodium');
    return;
  }
  showPage(p);
  if (tab) setModTab(tab);
};
