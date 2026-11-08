'use strict';

const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const { spawn } = require('child_process');
const paths = require('./paths');
const mc = require('./minecraft');
const mods = require('./mods');
const profiles = require('./profiles');

const MC_VERSION = profiles.VERSIONS[0];
const STATE = () => path.join(paths.data, 'state.json');

/**
 * Running games: "main" is the one the Play button starts; "extra" is a second game with another
 * account next to it, in its own game folder. Each holds { child, killRequested } while it runs;
 * killRequested is set when the launcher itself stopped the game, which is not a crash whatever
 * the exit code says.
 */
const slots = { main: null, extra: null };

/** Whether that game runs; without a slot, whether any game runs. */
function isRunning(slot) {
  return slot ? !!slots[slot] : !!(slots.main || slots.extra);
}

async function ensureDirs() {
  for (const dir of [paths.root, paths.runtime, paths.game, paths.userMods, paths.coreMods, paths.data, paths.logs, paths.java]) {
    await fsp.mkdir(dir, { recursive: true });
  }
}

/** Major version of a Java install from its release file, or Infinity when unknown (then it is trusted). */
function javaMajor(javaExe) {
  try {
    const release = fs.readFileSync(path.join(path.dirname(path.dirname(javaExe)), 'release'), 'utf8');
    const m = release.match(/JAVA_VERSION="(?:1\.)?(\d+)/);
    return m ? Number(m[1]) : Infinity;
  } catch {
    return Infinity;
  }
}

/** Picks the Fabric loader version, falling back to the last one used when offline. */
async function fabricLoader(mcVersion) {
  const state = await mc.readJson(STATE(), {});
  try {
    const v = await mc.getLatestFabricLoader(mcVersion);
    if (v !== state.fabricLoader) await mc.writeJson(STATE(), { ...state, fabricLoader: v });
    return v;
  } catch (e) {
    if (state.fabricLoader) return state.fabricLoader;
    throw e;
  }
}

/**
 * Makes sure everything needed to start the game is on disk. Safe to call on every launch:
 * already present files are only checked, not downloaded again.
 */
async function prepare({ profile, settings, bundledJar, onProgress, extra = false }) {
  await ensureDirs();
  const mcVersion = profile.mcVersion;
  const gameDir = extra ? await profiles.ensureExtraGameDir(profile) : await profiles.ensureGameDir(profile);
  const luminaDir = await profiles.ensureLuminaDir(extra);
  await fsp.mkdir(profiles.modsDir(profile), { recursive: true });
  const vanilla = await mc.installVanilla(mcVersion, onProgress);
  const loader = await fabricLoader(mcVersion);
  const fabric = await mc.installFabric(mcVersion, loader, onProgress);
  let java = settings.javaPath;
  const needed = (vanilla.javaVersion && vanilla.javaVersion.majorVersion) || 21;
  // a custom Java that is too old for this Minecraft version (26.x needs Java 25) is skipped
  if (java && fs.existsSync(java) && javaMajor(java) < needed) java = null;
  if (!java || !fs.existsSync(java)) {
    const component = (vanilla.javaVersion && vanilla.javaVersion.component) || 'java-runtime-delta';
    java = await mc.installJava(component, onProgress);
  }
  onProgress({ stage: 'Syncing Lumina mods', progress: 0.9 });
  let core = await mods.syncCoreMods({ mcVersion, settings, bundledLuminaJar: bundledJar, onProgress });
  core = await mods.resolveDuplicateCoreMods(core, profile);
  await fsp.mkdir(path.join(paths.natives, mcVersion), { recursive: true });
  onProgress({ stage: 'Ready', progress: 1 });
  return { version: mc.mergeVersions(fabric.profile, vanilla), versionId: fabric.id, java, core, gameDir, luminaDir };
}

async function launch({ slot = 'main', account, accountsUrl, profile, settings, bundledJar, onProgress, onLog, onExit }) {
  if (slots[slot]) throw new Error(slot === 'main' ? 'Minecraft is already running.' : 'A second game is already running.');
  const prep = await prepare({ profile, settings, bundledJar, onProgress, extra: slot === 'extra' });
  const extraJvm = (settings.jvmArgs || '').split(/\s+/).filter(Boolean);
  const args = mc.buildLaunchArgs({
    version: prep.version,
    vanillaId: profile.mcVersion,
    versionId: prep.versionId,
    gameDir: prep.gameDir,
    account,
    memoryMb: settings.memoryMb,
    javaMajor: javaMajor(prep.java),
    width: settings.fullscreen ? 0 : settings.width,
    height: settings.fullscreen ? 0 : settings.height,
    extraJvmArgs: extraJvm,
    extraSystemProps: {
      'fabric.addMods': prep.core.join(path.delimiter),
      // every profile has its own mods folder; worlds and options in game/ are shared
      'fabric.modsFolder': profiles.modsDir(profile),
      'lumina.launcher': '1',
      // Lumina's settings are the same in every Minecraft version
      'lumina.data': prep.luminaDir,
      'lumina.accounts': accountsUrl || '',
    },
    quickPlay: settings.autoJoin || '',
  });
  if (settings.fullscreen) args.push('--fullscreen');

  const logFile = path.join(paths.logs, slot === 'main' ? 'latest-launch.log' : 'latest-launch-2.log');
  const log = fs.createWriteStream(logFile, { flags: 'w' });
  log.write(`[Lumina] ${new Date().toISOString()} launching ${prep.versionId} (profile ${profile.name}) with ${prep.java}\n`);
  log.write(`[Lumina] core mods: ${prep.core.map((f) => path.basename(f)).join(', ')}\n`);

  const env = { ...process.env };
  // A global JAVA_TOOL_OPTIONS (set by some tools) would leak into the game.
  delete env.JAVA_TOOL_OPTIONS;
  delete env._JAVA_OPTIONS;
  const child = spawn(prep.java, args, { cwd: prep.gameDir, env, windowsHide: false });
  const run = { child, killRequested: false };
  slots[slot] = run;
  const started = Date.now();
  let buffer = '';
  const forward = (chunk) => {
    const text = chunk.toString('utf8');
    log.write(text);
    buffer += text;
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop();
    if (lines.length && onLog) onLog(lines);
  };
  child.stdout.on('data', forward);
  child.stderr.on('data', forward);
  child.on('error', (err) => {
    log.write(`[Lumina] failed to start: ${err.message}\n`);
    if (slots[slot] === run) slots[slot] = null;
    onExit && onExit({ code: -1, error: err.message, runtime: 0 });
  });
  child.on('exit', (code) => {
    log.end(`[Lumina] exited with code ${code}\n`);
    if (slots[slot] === run) slots[slot] = null;
    onExit && onExit({ code, killed: run.killRequested, runtime: Date.now() - started, logFile });
  });
  return { pid: child.pid };
}

function kill(slot = 'main') {
  const run = slots[slot];
  if (!run) return;
  run.killRequested = true;
  run.child.kill();
}

module.exports = { MC_VERSION, prepare, launch, isRunning, kill };
