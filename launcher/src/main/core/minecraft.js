'use strict';

const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const os = require('os');
const paths = require('./paths');
const { fetchJson, downloadFile, downloadAll, fileOk } = require('./download');

const VERSION_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2';
const JAVA_MANIFEST =
  'https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';
const RESOURCES_URL = 'https://resources.download.minecraft.net';

const LAUNCHER_VERSION = require('../../../package.json').version;

const IS_MAC = process.platform === 'darwin';
const OS_NAME = { win32: 'windows', darwin: 'osx', linux: 'linux' }[process.platform];
const ARCH = { x64: 'x86_64', ia32: 'x86', arm64: 'arm64' }[process.arch] || process.arch;

async function readJson(file, fallback) {
  try {
    return JSON.parse(await fsp.readFile(file, 'utf8'));
  } catch {
    return fallback;
  }
}

async function writeJson(file, data) {
  await fsp.mkdir(path.dirname(file), { recursive: true });
  await fsp.writeFile(file, JSON.stringify(data, null, 2));
}

// ---------------------------------------------------------------------------
// Rules & maven helpers
// ---------------------------------------------------------------------------

function rulesAllow(rules, features = {}) {
  if (!rules || rules.length === 0) return true;
  let allowed = false;
  for (const rule of rules) {
    let matches = true;
    if (rule.os) {
      if (rule.os.name && rule.os.name !== OS_NAME) matches = false;
      if (rule.os.arch && rule.os.arch !== ARCH) matches = false;
      if (rule.os.version && !new RegExp(rule.os.version).test(os.release())) matches = false;
    }
    if (rule.features) {
      for (const [k, v] of Object.entries(rule.features)) {
        if (!!features[k] !== v) matches = false;
      }
    }
    if (matches) allowed = rule.action === 'allow';
  }
  return allowed;
}

function mavenPath(name) {
  // group:artifact:version[:classifier][@ext]
  let ext = 'jar';
  let coords = name;
  const at = coords.indexOf('@');
  if (at >= 0) {
    ext = coords.slice(at + 1);
    coords = coords.slice(0, at);
  }
  const [group, artifact, version, classifier] = coords.split(':');
  const file = `${artifact}-${version}${classifier ? `-${classifier}` : ''}.${ext}`;
  return path.join(...group.split('.'), artifact, version, file);
}

// ---------------------------------------------------------------------------
// Vanilla
// ---------------------------------------------------------------------------

async function getVersionJson(versionId) {
  const file = path.join(paths.versions, versionId, `${versionId}.json`);
  const cached = await readJson(file, null);
  if (cached) return cached;
  const manifest = await fetchJson(VERSION_MANIFEST);
  const entry = manifest.versions.find((v) => v.id === versionId);
  if (!entry) throw new Error(`Minecraft ${versionId} was not found in the version manifest.`);
  await downloadFile(entry.url, file, { sha1: entry.sha1 });
  return readJson(file, null);
}

function collectLibraries(version) {
  const result = [];
  for (const lib of version.libraries || []) {
    if (!rulesAllow(lib.rules)) continue;
    const art = lib.downloads && lib.downloads.artifact;
    if (art) {
      result.push({ name: lib.name, path: path.join(paths.libraries, art.path), url: art.url, sha1: art.sha1, size: art.size });
    } else if (!lib.natives) {
      // Maven-style library (Fabric profiles use this form)
      const rel = mavenPath(lib.name);
      const base = (lib.url || 'https://libraries.minecraft.net/').replace(/\/?$/, '/');
      result.push({ name: lib.name, path: path.join(paths.libraries, rel), url: base + rel.split(path.sep).join('/'), sha1: lib.sha1, size: lib.size });
    }
    // Old-style natives (pre 1.19)
    if (lib.natives && lib.natives[OS_NAME]) {
      const classifier = lib.natives[OS_NAME].replace('${arch}', process.arch === 'x64' ? '64' : '32');
      const nat = lib.downloads && lib.downloads.classifiers && lib.downloads.classifiers[classifier];
      if (nat) {
        result.push({ name: `${lib.name}:${classifier}`, path: path.join(paths.libraries, nat.path), url: nat.url, sha1: nat.sha1, size: nat.size, native: true, extract: lib.extract });
      }
    }
  }
  return result;
}

async function installAssets(version, onProgress) {
  const idx = version.assetIndex;
  const indexFile = path.join(paths.assets, 'indexes', `${idx.id}.json`);
  await downloadFile(idx.url, indexFile, { sha1: idx.sha1, size: idx.size });
  const index = await readJson(indexFile, { objects: {} });
  const items = [];
  const seen = new Set();
  for (const obj of Object.values(index.objects)) {
    if (seen.has(obj.hash)) continue;
    seen.add(obj.hash);
    const sub = obj.hash.slice(0, 2);
    items.push({ url: `${RESOURCES_URL}/${sub}/${obj.hash}`, path: path.join(paths.assets, 'objects', sub, obj.hash), size: obj.size });
  }
  // Only download objects that are missing or have a wrong size (hash checks for ~4000 files are slow).
  const missing = [];
  await Promise.all(
    items.map(async (it) => {
      try {
        const st = await fsp.stat(it.path);
        if (st.size !== it.size) missing.push(it);
      } catch {
        missing.push(it);
      }
    }),
  );
  await downloadAll(missing, { concurrency: 24, onProgress });
}

async function installVanilla(versionId, onProgress = () => {}) {
  onProgress({ stage: 'Downloading version data', progress: 0 });
  const version = await getVersionJson(versionId);

  const client = version.downloads.client;
  const jarPath = path.join(paths.versions, versionId, `${versionId}.jar`);
  const libs = collectLibraries(version);
  const files = [{ url: client.url, path: jarPath, sha1: client.sha1, size: client.size }, ...libs];

  onProgress({ stage: 'Downloading libraries', progress: 0.02 });
  const needed = [];
  for (const f of files) if (!(await fileOk(f.path, null, f.size))) needed.push(f);
  await downloadAll(needed, {
    onProgress: ({ done, total }) => onProgress({ stage: 'Downloading libraries', progress: 0.02 + 0.18 * (total ? done / total : 1) }),
  });

  onProgress({ stage: 'Downloading assets', progress: 0.2 });
  await installAssets(version, ({ done, total }) =>
    onProgress({ stage: `Downloading assets (${done}/${total})`, progress: 0.2 + 0.5 * (total ? done / total : 1) }),
  );
  return version;
}

// ---------------------------------------------------------------------------
// Fabric
// ---------------------------------------------------------------------------

async function getLatestFabricLoader(mcVersion) {
  const loaders = await fetchJson(`${FABRIC_META}/versions/loader/${mcVersion}`);
  const stable = loaders.find((l) => l.loader.stable) || loaders[0];
  if (!stable) throw new Error(`No Fabric Loader found for ${mcVersion}.`);
  return stable.loader.version;
}

async function installFabric(mcVersion, loaderVersion, onProgress = () => {}) {
  const id = `fabric-loader-${loaderVersion}-${mcVersion}`;
  const file = path.join(paths.versions, id, `${id}.json`);
  let profile = await readJson(file, null);
  if (!profile) {
    onProgress({ stage: 'Installing Fabric', progress: 0.7 });
    profile = await fetchJson(`${FABRIC_META}/versions/loader/${mcVersion}/${loaderVersion}/profile/json`);
    await writeJson(file, profile);
  }
  const libs = collectLibraries(profile);
  const needed = [];
  for (const f of libs) if (!(await fileOk(f.path, null, f.size))) needed.push(f);
  await downloadAll(needed, {
    onProgress: ({ done, total }) => onProgress({ stage: 'Installing Fabric', progress: 0.7 + 0.05 * (total ? done / total : 1) }),
  });
  return { id, profile };
}

// ---------------------------------------------------------------------------
// Java runtime (Mojang's official builds)
// ---------------------------------------------------------------------------

function javaPlatformKey() {
  if (process.platform === 'win32') return process.arch === 'arm64' ? 'windows-arm64' : 'windows-x64';
  if (process.platform === 'darwin') return process.arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  return 'linux';
}

function javaExecutable(component) {
  const base = path.join(paths.java, component);
  if (process.platform === 'win32') return path.join(base, 'bin', 'javaw.exe');
  if (process.platform === 'darwin') return path.join(base, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return path.join(base, 'bin', 'java');
}

async function installJava(component, onProgress = () => {}) {
  const exe = javaExecutable(component);
  const marker = path.join(paths.java, component, '.lumina-complete');
  if (fs.existsSync(exe) && fs.existsSync(marker)) return exe;

  onProgress({ stage: 'Downloading Java', progress: 0.75 });
  const all = await fetchJson(JAVA_MANIFEST);
  const entries = (all[javaPlatformKey()] || {})[component];
  if (!entries || !entries.length) throw new Error(`Java runtime ${component} is not available for this system.`);
  const manifest = await fetchJson(entries[0].manifest.url);
  const base = path.join(paths.java, component);
  const items = [];
  const links = [];
  for (const [rel, info] of Object.entries(manifest.files)) {
    const target = path.join(base, rel);
    if (info.type === 'directory') {
      await fsp.mkdir(target, { recursive: true });
    } else if (info.type === 'link') {
      // the macOS runtimes (jre.bundle) contain symlinks, e.g. Contents/MacOS/libjli.dylib
      links.push({ target, to: info.target });
    } else if (info.type === 'file') {
      const raw = info.downloads.raw;
      items.push({ url: raw.url, path: target, sha1: raw.sha1, size: raw.size, executable: info.executable });
    }
  }
  await downloadAll(items, {
    onProgress: ({ done, total }) => onProgress({ stage: `Downloading Java (${done}/${total})`, progress: 0.75 + 0.15 * (total ? done / total : 1) }),
  });
  for (const link of links) {
    await fsp.mkdir(path.dirname(link.target), { recursive: true });
    await fsp.rm(link.target, { force: true });
    await fsp.symlink(link.to, link.target).catch(() => {});
  }
  await fsp.writeFile(marker, new Date().toISOString());
  return exe;
}

// ---------------------------------------------------------------------------
// Launch arguments
// ---------------------------------------------------------------------------

function mergeVersions(child, parent) {
  const merged = { ...parent, ...child };
  merged.libraries = [...(child.libraries || []), ...(parent.libraries || [])];
  merged.arguments = {
    game: [...((parent.arguments || {}).game || []), ...((child.arguments || {}).game || [])],
    jvm: [...((parent.arguments || {}).jvm || []), ...((child.arguments || {}).jvm || [])],
  };
  merged.mainClass = child.mainClass || parent.mainClass;
  merged.assetIndex = parent.assetIndex;
  merged.assets = parent.assets;
  merged.downloads = parent.downloads;
  merged.javaVersion = parent.javaVersion;
  return merged;
}

function expandArgs(list, vars, features) {
  const out = [];
  for (const entry of list) {
    if (typeof entry === 'string') {
      out.push(entry);
    } else if (rulesAllow(entry.rules, features)) {
      if (Array.isArray(entry.value)) out.push(...entry.value);
      else out.push(entry.value);
    }
  }
  return out.map((a) => a.replace(/\$\{([^}]+)\}/g, (m, k) => (k in vars ? vars[k] : m)));
}

/**
 * Builds the java command line for a merged (fabric + vanilla) version.
 */
/** macOS: name and icon of the game in the Dock and menu bar; AWT stays headless (it would fight GLFW for the main thread). */
function macJvmArgs() {
  if (!IS_MAC) return [];
  const args = ['-Xdock:name=LuminaClient', '-Djava.awt.headless=true'];
  const icon = [
    path.join(process.resourcesPath || '', 'game-icon.icns'),
    path.join(__dirname, '..', '..', '..', 'build', 'icon.icns'),
  ].find((p) => p && fs.existsSync(p));
  if (icon) args.push(`-Xdock:icon=${icon}`);
  return args;
}

function buildLaunchArgs({ version, vanillaId, versionId, gameDir, account, memoryMb, width, height, javaMajor = 0, extraJvmArgs = [], extraSystemProps = {}, quickPlay }) {
  // Deduplicate libraries by group:artifact(:classifier) — the first (child/fabric) entry wins.
  const seen = new Set();
  const classpath = [];
  for (const lib of collectLibraries(version)) {
    if (lib.native) continue;
    const parts = lib.name.split(':');
    const key = `${parts[0]}:${parts[1]}${parts[3] ? `:${parts[3]}` : ''}`;
    if (seen.has(key)) continue;
    seen.add(key);
    classpath.push(lib.path);
  }
  classpath.push(path.join(paths.versions, vanillaId, `${vanillaId}.jar`));

  const vars = {
    auth_player_name: account.name,
    // shown in crash reports; the game calls itself "Lumina <version>"
    version_name: `Lumina ${vanillaId}`,
    game_directory: gameDir || paths.game,
    assets_root: paths.assets,
    game_assets: paths.assets,
    assets_index_name: version.assetIndex.id,
    auth_uuid: account.uuid.replace(/-/g, ''),
    auth_access_token: account.accessToken,
    auth_session: account.accessToken,
    clientid: account.clientId || '',
    auth_xuid: account.xuid || '',
    user_type: account.type === 'msa' ? 'msa' : 'legacy',
    version_type: 'LuminaClient',
    user_properties: '{}',
    resolution_width: String(width || 1280),
    resolution_height: String(height || 720),
    natives_directory: path.join(paths.natives, vanillaId),
    launcher_name: 'LuminaClient',
    launcher_version: LAUNCHER_VERSION,
    classpath: classpath.join(path.delimiter),
    classpath_separator: path.delimiter,
    library_directory: paths.libraries,
    quickPlayMultiplayer: quickPlay || '',
  };
  const features = {
    has_custom_resolution: !!(width && height),
    is_quick_play_multiplayer: !!quickPlay,
  };

  const jvm = [
    `-Xms${Math.min(1024, memoryMb)}M`,
    `-Xmx${memoryMb}M`,
    // Low-latency G1 settings that work well for Minecraft
    '-XX:+UseG1GC',
    '-XX:+ParallelRefProcEnabled',
    '-XX:MaxGCPauseMillis=200',
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:+DisableExplicitGC',
    // pre-touching the whole heap slows the start down a lot on Apple Silicon (unified memory)
    ...(IS_MAC ? [] : ['-XX:+AlwaysPreTouch']),
    '-XX:G1NewSizePercent=30',
    '-XX:G1MaxNewSizePercent=40',
    '-XX:G1HeapRegionSize=8M',
    '-XX:G1ReservePercent=20',
    '-XX:G1HeapWastePercent=5',
    '-XX:G1MixedGCCountTarget=4',
    '-XX:InitiatingHeapOccupancyPercent=15',
    '-XX:G1MixedGCLiveThresholdPercent=90',
    '-XX:G1RSetUpdatingPauseTimePercent=5',
    '-XX:SurvivorRatio=32',
    '-XX:+PerfDisableSharedMem',
    '-XX:MaxTenuringThreshold=1',
    // Java 25+: 8-byte object headers instead of 12, so less memory and better cache use (JEP 519).
    // Older Javas refuse to start with this flag, so only for a known version.
    ...(Number.isFinite(javaMajor) && javaMajor >= 25 ? ['-XX:+UseCompactObjectHeaders'] : []),
    ...macJvmArgs(),
    ...extraJvmArgs,
  ];
  for (const [k, v] of Object.entries(extraSystemProps)) jvm.push(`-D${k}=${v}`);

  const versionJvm = version.arguments && version.arguments.jvm && version.arguments.jvm.length
    ? expandArgs(version.arguments.jvm, vars, features)
    : [`-Djava.library.path=${vars.natives_directory}`, '-cp', vars.classpath];
  jvm.push(...versionJvm);


  const game = expandArgs((version.arguments && version.arguments.game) || [], vars, features);
  if (quickPlay && !game.includes('--quickPlayMultiplayer')) game.push('--quickPlayMultiplayer', quickPlay);
  return [...jvm, version.mainClass, ...game];
}

module.exports = {
  readJson,
  writeJson,
  getVersionJson,
  installVanilla,
  getLatestFabricLoader,
  installFabric,
  installJava,
  javaExecutable,
  mergeVersions,
  buildLaunchArgs,
};
