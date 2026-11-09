'use strict';

const { app, BrowserWindow, ipcMain, shell, Menu, nativeTheme } = require('electron');
const fs = require('fs');
const path = require('path');
const os = require('os');
const paths = require('./core/paths');
const auth = require('./core/auth');
const accountBridge = require('./core/accountbridge');
const settingsStore = require('./core/settings');
const game = require('./core/launch');
const mods = require('./core/mods');
const profiles = require('./core/profiles');
const updater = require('./core/updater');
const backup = require('./core/backup');
const skin = require('./core/skin');
const cosmetics = require('./core/cosmetics');
const shaders = require('./core/shaders');
const online = require('./core/online');

const DEV_SCREENSHOT = process.argv.find((a) => a.startsWith('--screenshot='));
const DEV_PAGE = (process.argv.find((a) => a.startsWith('--page=')) || '').slice(7);
const DEV_WAIT = Number((process.argv.find((a) => a.startsWith('--wait=')) || '').slice(7)) || 2500;

const IS_MAC = process.platform === 'darwin';
let win = null;
// macOS: the red button only hides the launcher (it stays in the Dock); ⌘Q really quits
let quitting = false;
// ⌘Q while Minecraft runs: hide now, quit once the game has exited
let quitAfterGame = false;
/** The second game while it runs: { state, accountId, name, type, uuid, pid }. */
let extraGame = null;

// Chromium's profile lives next to the launcher data instead of a second folder in Application Support.
// Not on Windows: there the key that encrypts the saved logins (safeStorage) sits in this folder.
if (IS_MAC) app.setPath('userData', paths.electron);

if (DEV_SCREENSHOT) {
  app.commandLine.appendSwitch('disable-renderer-backgrounding');
  app.commandLine.appendSwitch('disable-backgrounding-occluded-windows');
  app.commandLine.appendSwitch('disable-background-timer-throttling');
}

/** The Lumina mod for a Minecraft version: lumina-client.jar for the first one, lumina-client-<version>.jar for the others. */
function bundledJar(mcVersion = profiles.VERSIONS[0]) {
  const name = mcVersion === profiles.VERSIONS[0] ? 'lumina-client.jar' : `lumina-client-${mcVersion}.jar`;
  const candidates = [
    path.join(process.resourcesPath || '', name),
    path.join(__dirname, '..', '..', 'resources', name),
  ];
  return candidates.find((p) => p && fs.existsSync(p)) || null;
}

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

function createWindow() {
  win = new BrowserWindow({
    // fixed size: the layout is made for exactly this window
    width: 980,
    height: 620,
    resizable: false,
    maximizable: false,
    fullscreenable: false,
    // macOS keeps its native traffic lights inside the launcher's own titlebar
    ...(IS_MAC ? { titleBarStyle: 'hiddenInset', trafficLightPosition: { x: 16, y: 25 } } : { frame: false }),
    show: false,
    backgroundColor: '#05060b',
    title: 'LuminaClient',
    icon: path.join(__dirname, '..', '..', 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // the screenshot mode renders a hidden window, which Chromium would otherwise throttle
      backgroundThrottling: !DEV_SCREENSHOT,
    },
  });
  win.setMenuBarVisibility(false);
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'), { query: { platform: process.platform } });
  if (IS_MAC) {
    win.on('close', (e) => {
      if (quitting || DEV_SCREENSHOT) return;
      e.preventDefault();
      win.hide();
    });
  }
  win.once('ready-to-show', () => {
    if (!DEV_SCREENSHOT) win.show();
  });
  // External links open in the real browser, never inside the launcher.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (!url.startsWith('file://')) {
      e.preventDefault();
      if (/^https?:\/\//.test(url)) shell.openExternal(url);
    }
  });
  if (DEV_SCREENSHOT) {
    const logFile = DEV_SCREENSHOT.slice('--screenshot='.length) + '.log';
    fs.writeFileSync(logFile, '');
    win.webContents.on('console-message', (e) => fs.appendFileSync(logFile, `[${e.level}] ${e.message}\n`));
    win.webContents.once('did-finish-load', async () => {
      const file = DEV_SCREENSHOT.slice('--screenshot='.length);
      if (DEV_PAGE) await win.webContents.executeJavaScript(`window.__devShow && window.__devShow(${JSON.stringify(DEV_PAGE)})`);
      await new Promise((r) => setTimeout(r, DEV_WAIT));
      const image = await win.webContents.capturePage();
      fs.writeFileSync(file, image.toPNG());
      app.quit();
    });
  }
}

/** The macOS menu bar: without the Edit menu ⌘C/⌘V/⌘A would not work in the launcher's text fields. */
function buildMacMenu() {
  app.setAboutPanelOptions({
    applicationName: 'LuminaClient',
    applicationVersion: app.getVersion(),
    copyright: 'Lumina · not an official Minecraft product',
  });
  const template = [
    {
      label: 'LuminaClient',
      submenu: [
        { role: 'about', label: 'About LuminaClient' },
        { type: 'separator' },
        { label: 'Settings…', accelerator: 'Cmd+,', click: () => showWindow('settings') },
        { label: 'Check for Updates…', click: () => { showWindow('settings'); updater.check().catch(() => {}); } },
        { type: 'separator' },
        { role: 'services', label: 'Services' },
        { type: 'separator' },
        { role: 'hide', label: 'Hide LuminaClient' },
        { role: 'hideOthers', label: 'Hide Others' },
        { role: 'unhide', label: 'Show All' },
        { type: 'separator' },
        { role: 'quit', label: 'Quit LuminaClient' },
      ],
    },
    {
      label: 'Edit',
      submenu: [
        { role: 'undo', label: 'Undo' },
        { role: 'redo', label: 'Redo' },
        { type: 'separator' },
        { role: 'cut', label: 'Cut' },
        { role: 'copy', label: 'Copy' },
        { role: 'paste', label: 'Paste' },
        { role: 'selectAll', label: 'Select All' },
      ],
    },
    {
      label: 'Game',
      submenu: [
        { label: 'Play', accelerator: 'Cmd+Return', click: () => { showWindow('home'); send('menu:play'); } },
        { type: 'separator' },
        { label: 'Show Game Folder in Finder', click: () => openFolder('game') },
        { label: 'Show Mods Folder in Finder', click: () => openFolder('mods') },
        { label: 'Show Screenshots in Finder', click: () => openFolder('screenshots') },
        { label: 'Show Logs in Finder', click: () => openFolder('logs') },
      ],
    },
    {
      label: 'Window',
      role: 'windowMenu',
      submenu: [
        { role: 'minimize', label: 'Minimize' },
        { role: 'close', label: 'Close Window' },
        { type: 'separator' },
        { label: 'LuminaClient', accelerator: 'Cmd+0', click: () => showWindow() },
      ],
    },
  ];
  if (!app.isPackaged) template.push({ label: 'Developer', submenu: [{ role: 'reload' }, { role: 'toggleDevTools' }] });
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function showWindow(page) {
  if (!win || win.isDestroyed()) createWindow();
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
  if (page) send('menu:page', page);
}

async function openFolder(which, profileId) {
  const profile = await profiles.get(profileId);
  const modsDir = profiles.modsDir(profile);
  const gameDir = profiles.gameDir(profile);
  const dir = { game: gameDir, mods: modsDir, logs: paths.logs, screenshots: path.join(gameDir, 'screenshots'), backups: backup.dir(), resourcepacks: paths.resourcepacks, shaderpacks: paths.shaderpacks }[which] || paths.root;
  fs.mkdirSync(dir, { recursive: true });
  return shell.openPath(dir);
}

function handle(channel, fn) {
  ipcMain.handle(channel, async (_event, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (e) {
      const message = (e && e.message) || (typeof e === 'string' ? e : JSON.stringify(e)) || 'Unknown error';
      return { ok: false, error: message, code: e && e.code, url: e && e.url };
    }
  });
}

function registerIpc() {
  handle('window:minimize', () => win.minimize());
  handle('window:maximize', () => (win.isMaximized() ? win.unmaximize() : win.maximize()));
  handle('window:close', () => win.close());

  handle('app:info', () => ({
    version: app.getVersion(),
    mcVersion: game.MC_VERSION,
    totalMemoryMb: Math.floor(os.totalmem() / 1024 ** 2),
    dataDir: paths.root,
    bundledJar: !!bundledJar(),
    platform: process.platform,
  }));
  handle('app:openExternal', (url) => {
    if (/^https:\/\//.test(url)) return shell.openExternal(url);
    return null;
  });
  handle('app:openFolder', (which, profileId) => openFolder(which, profileId));
  handle('app:online', () => online.count());

  handle('update:status', () => updater.get());
  handle('update:check', () => updater.check());
  handle('update:install', () => updater.install());
  // macOS only: the local folder the Mac updater reads new versions from
  if (updater.FEED_DIR) {
    handle('update:openFeed', () => {
      fs.mkdirSync(updater.FEED_DIR, { recursive: true });
      return shell.openPath(updater.FEED_DIR);
    });
  }

  // cosmetics live in the game's own config, which Minecraft rewrites while it runs
  const cosmeticsArgs = async (extra) => {
    const profile = await profiles.get();
    return { profile, jar: bundledJar(profile.mcVersion), ...extra };
  };
  const cosmeticsWrite = async (fn, extra) => {
    if (game.isRunning()) throw new Error('Stop Minecraft first. In the game, pick cosmetics through the Lumina menu.');
    return fn(await cosmeticsArgs(extra));
  };
  handle('cosmetics:state', async () => cosmetics.state(await cosmeticsArgs()));
  handle('cosmetics:worn', async () => cosmetics.worn(await cosmeticsArgs()));
  // the skin the preview wears: the selected Microsoft account's, otherwise Steve
  handle('cosmetics:skin', async () => {
    const { selected, accounts } = await auth.list();
    const acc = accounts.find((a) => a.id === selected);
    if (acc && acc.type === 'msa') {
      const own = await skin.current(acc.uuid).catch(() => null);
      if (own) return own;
    }
    return skin.fallback();
  });
  handle('cosmetics:select', (choice) => cosmeticsWrite(cosmetics.select, { cape: choice.cape, effect: choice.effect }));
  handle('cosmetics:add', (files) => cosmeticsWrite(cosmetics.addCapes, { files: Array.isArray(files) ? files.map(String) : [] }));
  handle('cosmetics:remove', (id) => cosmeticsWrite(cosmetics.removeCape, { id: String(id) }));
  handle('cosmetics:openFolder', async () => {
    const dir = await cosmetics.capesFolder();
    fs.mkdirSync(dir, { recursive: true });
    return shell.openPath(dir);
  });

  handle('backups:list', () => backup.list());
  handle('backups:create', () => backup.create('manuell', { version: app.getVersion() }));
  handle('backups:restore', (name) => {
    if (game.isRunning()) throw new Error('Stop Minecraft first, then the backup can be restored.');
    return backup.restore(name);
  });

  handle('accounts:list', () => auth.list());
  handle('accounts:addMicrosoft', () => auth.addMicrosoft(win));
  handle('accounts:addOffline', (name) => auth.addOffline(name));
  handle('accounts:remove', (id) => auth.remove(id));
  handle('accounts:select', (id) => auth.select(id));
  handle('accounts:skin', (uuid) => skin.current(uuid));

  handle('settings:get', () => settingsStore.get());
  handle('settings:set', (partial) => settingsStore.set(partial));

  handle('game:status', () => ({ running: game.isRunning('main'), extra: game.isRunning('extra') ? extraGame : null }));
  handle('game:kill', (slot) => game.kill(slot === 'extra' ? 'extra' : 'main'));
  handle('profiles:list', () => profiles.list());
  handle('profiles:create', (opts) => profiles.create(opts));
  handle('profiles:rename', (id, name) => profiles.rename(id, name));
  handle('profiles:remove', (id) => profiles.remove(id));
  handle('profiles:select', (id) => profiles.select(id));

  handle('game:launch', async () => {
    const settings = await settingsStore.get();
    send('game:state', { state: 'preparing' });
    try {
      const account = await auth.launchCredentials();
      const profile = await profiles.get();
      const accountsUrl = await accountBridge.start((list) => send('accounts:changed', list));
      let lastSent = 0;
      const result = await game.launch({
        account,
        accountsUrl,
        profile,
        settings,
        bundledJar: bundledJar(profile.mcVersion),
        onProgress: (p) => {
          const now = Date.now();
          if (now - lastSent > 80 || p.progress === 1) {
            lastSent = now;
            send('game:progress', p);
          }
        },
        onLog: (lines) => send('game:log', lines),
        onExit: (info) => {
          accountBridge.stop();
          // closing the game window ends with 0; anything else that the launcher didn't ask for is a crash
          const crashed = !info.killed && info.code !== 0;
          send('game:state', { state: 'stopped', ...info, crashed });
          if ((quitAfterGame || BrowserWindow.getAllWindows().length === 0) && !game.isRunning()) return quit();
          if (win && !win.isDestroyed()) {
            // a crash always brings the launcher back, so its message is seen
            // hidden or minimized while playing: the launcher comes back once the game is closed
            if (settings.afterLaunch !== 'keep' || crashed) showWindow();
          }
        },
      });
      send('game:state', { state: 'running', pid: result.pid });
      await profiles.markPlayed(profile.id).catch(() => {});
      // stopped within this delay: the game is gone, so the launcher has to stay
      const whilePlaying = (action) => setTimeout(() => game.isRunning('main') && win && !win.isDestroyed() && action(), 1500);
      if (settings.afterLaunch === 'minimize') whilePlaying(() => win.minimize());
      if (settings.afterLaunch === 'close') whilePlaying(() => win.hide());
      return result;
    } catch (e) {
      accountBridge.stop();
      send('game:state', { state: 'error', error: e.message });
      throw e;
    }
  });

  // A second game with another account next to the first, e.g. to see each other's cosmetics. It has
  // its own game folder and no account switching in the game; the launcher window stays as it is.
  handle('game:launchExtra', async (accountId) => {
    const { accounts } = await auth.list();
    const acc = accounts.find((a) => a.id === accountId);
    if (!acc) throw new Error('That account is gone.');
    const who = { accountId: acc.id, name: acc.name, type: acc.type, uuid: acc.uuid };
    send('game:extra', { state: 'preparing', ...who });
    try {
      const settings = await settingsStore.get();
      const profile = await profiles.get();
      const result = await game.launch({
        slot: 'extra',
        account: await auth.credentials(acc.id),
        accountsUrl: '',
        profile,
        settings,
        bundledJar: bundledJar(profile.mcVersion),
        onProgress: () => {},
        onLog: null,
        onExit: (info) => {
          extraGame = null;
          send('game:extra', { state: 'stopped', ...who, ...info, crashed: !info.killed && info.code !== 0 });
          if ((quitAfterGame || BrowserWindow.getAllWindows().length === 0) && !game.isRunning()) quit();
        },
      });
      // (unless it already ended again, which sent its own 'stopped')
      if (game.isRunning('extra')) {
        extraGame = { state: 'running', ...who, pid: result.pid };
        send('game:extra', extraGame);
      }
      return result;
    } catch (e) {
      send('game:extra', { state: 'error', ...who, error: e.message });
      throw e;
    }
  });

  handle('mods:search', async ({ query, offset, sort, profileId }) => {
    const { mcVersion } = await profiles.get(profileId);
    return mods.modrinthSearch({ query, offset, sort, mcVersion });
  });
  handle('mods:install', async ({ id, profileId }) => {
    return mods.installMod({
      id,
      profile: await profiles.get(profileId),
      onProgress: (p) => send('mods:progress', { id, ...p }),
    });
  });
  handle('mods:installed', async (profileId) => mods.listInstalled(await profiles.get(profileId)));
  handle('mods:core', () => mods.CORE_MODS.map((m) => ({ id: m.id, name: m.name, project: m.project, required: m.required, group: m.group, setting: m.setting })));
  handle('mods:setEnabled', async (profileId, file, enabled) => mods.setEnabled(await profiles.get(profileId), file, enabled));
  handle('mods:remove', async (profileId, file) => mods.removeMod(await profiles.get(profileId), file));
  handle('mods:migrationPlan', async (fromId, toId) => mods.migrationPlan({ from: await profiles.get(fromId), to: await profiles.get(toId) }));
  handle('mods:migrate', async (toId, items) => mods.migrate({ to: await profiles.get(toId), items, onProgress: (p) => send('mods:progress', { id: 'migrate', ...p }) }));
  handle('mods:updates', async (profileId) => mods.checkUpdates({ profile: await profiles.get(profileId) }));

  handle('shaders:search', async ({ query, offset, sort, profileId }) => {
    const { mcVersion } = await profiles.get(profileId);
    return shaders.search({ query, offset, sort, mcVersion });
  });
  handle('shaders:install', async ({ id, profileId }) => {
    const { mcVersion } = await profiles.get(profileId);
    return shaders.install({ id, mcVersion });
  });
  handle('shaders:installed', () => shaders.list());
  handle('shaders:remove', (file) => shaders.remove(file));
}

function quit() {
  quitting = true;
  app.quit();
}

if (!app.requestSingleInstanceLock() && !DEV_SCREENSHOT) {
  app.quit();
} else {
  app.on('second-instance', () => showWindow());
  if (process.platform === 'win32') app.setAppUserModelId('dev.lumina.launcher');
  // the launcher is always dark; native parts (menus, dialogs, scrollbars) follow
  nativeTheme.themeSource = 'dark';
  app.whenReady().then(async () => {
    // before anything reads or migrates the data: keep a copy of what the previous version left
    if (app.isPackaged && !DEV_SCREENSHOT) await backup.atVersionChange(app.getVersion()).catch(() => {});
    // dev screenshots only touch a test folder
    if (!DEV_SCREENSHOT || process.env.LUMINA_HOME) await profiles.shareAll().catch(() => {});
    // the installer's privacy page may have switched Lumina Online off
    if (!DEV_SCREENSHOT || process.env.LUMINA_HOME) await profiles.applyInstallOptions().catch(() => {});
    if (IS_MAC) {
      buildMacMenu();
      if (!app.isPackaged) app.dock.setIcon(path.join(__dirname, '..', '..', 'assets', 'icon.png'));
    }
    registerIpc();
    createWindow();
    if (!DEV_SCREENSHOT || process.env.LUMINA_UPDATE_DIR || process.env.LUMINA_UPDATE_URL) updater.init({ send, isGameRunning: () => game.isRunning() });
  });
  // macOS: clicking the Dock icon brings the hidden launcher back
  app.on('activate', () => app.isReady() && showWindow());
  app.on('before-quit', (e) => {
    quitting = true;
    if (IS_MAC && game.isRunning()) {
      // Minecraft is a child of the launcher: keep it alive and leave once it is closed
      e.preventDefault();
      quitAfterGame = true;
      quitting = false;
      if (win && !win.isDestroyed()) win.hide();
    }
  });
  // a prepared update is installed when the launcher really quits (not while Minecraft runs)
  app.on('will-quit', () => updater.installOnQuit());
  app.on('window-all-closed', () => {
    if (!IS_MAC && !game.isRunning()) app.quit();
  });
}

