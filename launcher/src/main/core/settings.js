'use strict';

const os = require('os');
const path = require('path');
const paths = require('./paths');
const { readJson, writeJson } = require('./minecraft');

const FILE = () => path.join(paths.data, 'settings.json');
// 2: Iris (shader support) is installed by default
// 3: the launcher hides itself while the game runs (was: minimized into the Dock)
const VERSION = 3;

function defaultMemory() {
  const totalGb = os.totalmem() / 1024 ** 3;
  if (totalGb >= 31) return 6144;
  if (totalGb >= 15) return 4096;
  if (totalGb >= 7) return 3072;
  return 2048;
}

const DEFAULTS = {
  memoryMb: defaultMemory(),
  javaPath: '',
  jvmArgs: '',
  width: 1280,
  height: 720,
  fullscreen: false,
  afterLaunch: 'close', // close (hide) | minimize | keep
  shaders: true,
  autoJoin: '',
  showConsole: false,
  welcomed: false, // the first start's welcome page was seen
};

async function get() {
  const stored = await readJson(FILE(), {});
  if ((stored.settingsVersion || 1) < 2) delete stored.shaders;
  delete stored.curseforgeKey;
  // the old default becomes the new one; a deliberate "keep" stays
  if ((stored.settingsVersion || 1) < 3 && stored.afterLaunch === 'minimize') delete stored.afterLaunch;
  return { ...DEFAULTS, ...stored, settingsVersion: VERSION };
}

async function set(partial) {
  const current = await get();
  const next = { ...current, ...partial };
  next.memoryMb = Math.max(1024, Math.min(Math.floor(os.totalmem() / 1024 ** 2) - 1024, Number(next.memoryMb) || DEFAULTS.memoryMb));
  next.width = Math.max(640, Number(next.width) || 1280);
  next.height = Math.max(480, Number(next.height) || 720);
  await writeJson(FILE(), next);
  return next;
}

module.exports = { get, set, DEFAULTS };
