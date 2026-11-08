'use strict';

const fs = require('fs');
const path = require('path');
const { app } = require('electron');
const { autoUpdater } = require('electron-updater');
const paths = require('./paths');
const backup = require('./backup');

/**
 * Keeps the launcher (and the Lumina mod bundled with it) up to date from GitHub Releases.
 * Updates download in the background and install silently when you restart the launcher or
 * close it; your data in .luminaclient is never touched, and a backup of your settings is made
 * before every update.
 *
 * LUMINA_UPDATE_URL points the updater at another server (a folder with latest.yml), for tests.
 */
const CHECK_EVERY_MS = 3 * 3600 * 1000;
const TEST_URL = process.env.LUMINA_UPDATE_URL || '';

let send = () => {};
let gameRunning = () => false;
let timer = null;
let ready = null; // version of the downloaded update that waits for the restart
let status = {
  enabled: false,
  state: 'idle', // idle | checking | downloading | ready | none | error
  current: app.getVersion(),
  version: null,
  progress: 0,
  notes: '',
  error: null,
  checkedAt: 0,
  backup: null,
};

function set(patch) {
  status = { ...status, ...patch };
  send('update:state', status);
}

function log(level, args) {
  try {
    fs.mkdirSync(paths.logs, { recursive: true });
    const text = args.map((a) => (a instanceof Error ? a.stack || a.message : typeof a === 'string' ? a : JSON.stringify(a))).join(' ');
    fs.appendFileSync(path.join(paths.logs, 'updater.log'), `[${new Date().toISOString()}] [${level}] ${text}\n`);
  } catch {
    /* logging must never break updating */
  }
}

/** GitHub release notes arrive as HTML; the launcher shows them as plain text. */
function notesText(notes) {
  if (!notes) return '';
  const raw = Array.isArray(notes) ? notes.map((n) => n.note || '').join('\n\n') : String(notes);
  return raw
    .replace(/<\s*(br|\/p|\/li|\/h\d)\s*\/?>/gi, '\n')
    .replace(/<li[^>]*>/gi, '• ')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

/** a > b for versions like 1.2.10 */
function newer(a, b) {
  const pa = String(a).split('.').map(Number);
  const pb = String(b).split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d > 0;
  }
  return false;
}

function friendlyError(e) {
  const msg = (e && e.message) || String(e);
  if (/ENOTFOUND|ECONNREFUSED|ETIMEDOUT|ECONNRESET|net::ERR_/i.test(msg)) return 'No connection to the update server.';
  if (/404/.test(msg)) return 'No updates published yet.';
  return msg.split('\n')[0].slice(0, 200);
}

function init(options) {
  send = options.send;
  gameRunning = options.isGameRunning;
  status.enabled = app.isPackaged || !!TEST_URL;
  if (!status.enabled) return;

  autoUpdater.logger = { info: (...a) => log('info', a), warn: (...a) => log('warn', a), error: (...a) => log('error', a), debug: () => {} };
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.allowDowngrade = false;
  if (TEST_URL) {
    // outside an installed build electron-updater wants a config file next to the app
    const config = path.join(paths.cache, 'dev-app-update.yml');
    fs.mkdirSync(paths.cache, { recursive: true });
    fs.writeFileSync(config, `provider: generic\nurl: ${TEST_URL}\nupdaterCacheDirName: lumina-launcher-updater-test\n`);
    autoUpdater.updateConfigPath = config;
    autoUpdater.forceDevUpdateConfig = true;
    // a test run must never start the downloaded installer by itself
    autoUpdater.autoInstallOnAppQuit = false;
    autoUpdater.setFeedURL({ provider: 'generic', url: TEST_URL });
  }

  // while an update waits for the restart the launcher keeps checking: a newer one replaces it
  autoUpdater.on('checking-for-update', () => set(ready ? { error: null } : { state: 'checking', error: null }));
  autoUpdater.on('update-not-available', () => set(ready ? { checkedAt: Date.now() } : { state: 'none', checkedAt: Date.now() }));
  autoUpdater.on('update-available', (info) => {
    if (ready && !newer(info.version, ready)) return set({ checkedAt: Date.now() });
    if (ready) log('info', [`found ${info.version}, replaces ${ready}`]);
    ready = null;
    set({ state: 'downloading', version: info.version, notes: notesText(info.releaseNotes), progress: 0, checkedAt: Date.now() });
  });
  autoUpdater.on('download-progress', (p) => {
    if (!ready) set({ state: 'downloading', progress: Math.max(0, Math.min(1, (p.percent || 0) / 100)) });
  });
  autoUpdater.on('update-downloaded', async (info) => {
    // the waiting update was only found again in the cache: no second backup
    if (ready === info.version) return;
    ready = info.version;
    let folder = null;
    try {
      folder = await backup.create(`before update ${status.current} - ${info.version}`);
    } catch (e) {
      log('warn', ['backup failed', e]);
    }
    set({ state: 'ready', version: info.version, notes: notesText(info.releaseNotes) || status.notes, progress: 1, backup: folder });
  });
  autoUpdater.on('error', (e) => {
    log('error', [e]);
    // a failed re-check must not hide an update that is already downloaded
    if (status.state !== 'ready') set({ state: 'error', error: friendlyError(e), checkedAt: Date.now() });
  });

  setTimeout(() => check().catch(() => {}), TEST_URL ? 300 : 4000);
  timer = setInterval(() => check().catch(() => {}), CHECK_EVERY_MS);
}

async function check() {
  if (!status.enabled) return status;
  if (status.state === 'checking' || status.state === 'downloading') return status;
  try {
    await autoUpdater.checkForUpdates();
  } catch (e) {
    if (status.state !== 'ready') set({ state: 'error', error: friendlyError(e), checkedAt: Date.now() });
  }
  return status;
}

/** Restarts the launcher into the downloaded version. */
function install() {
  if (status.state !== 'ready') throw new Error('No update is ready yet.');
  if (gameRunning()) throw new Error('Stop Minecraft first, then the update can be installed.');
  if (timer) clearInterval(timer);
  // silent install (no installer window), start the launcher again afterwards
  setImmediate(() => autoUpdater.quitAndInstall(true, true));
  return true;
}

/** The Mac updater swaps the app when the launcher quits; electron-updater does that on its own (autoInstallOnAppQuit). */
function installOnQuit() {}

function get() {
  return status;
}

module.exports = { init, check, install, installOnQuit, get };
