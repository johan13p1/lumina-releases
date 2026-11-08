'use strict';

const crypto = require('crypto');
const fsp = require('fs/promises');
const path = require('path');
const paths = require('./paths');
const { sha1File } = require('./download');
const { readJson, writeJson } = require('./minecraft');

/**
 * Profiles are "modpacks": a name, a Minecraft version and an own mods folder. Saves, options and
 * the Lumina config stay shared in game/. The default profile keeps using game/mods so existing
 * installs don't lose their mods.
 */

/** Minecraft versions Lumina can start, oldest first. More are added once the client supports them. */
const VERSIONS = ['1.21.11', '26.2', '26.3'];
const NEWEST = VERSIONS[VERSIONS.length - 1];
const DEFAULT_ID = 'default';

const FILE = () => path.join(paths.data, 'profiles.json');

function defaultProfile() {
  return { id: DEFAULT_ID, name: 'Lumina', mcVersion: VERSIONS[0], loader: 'fabric', createdAt: null };
}

/** Id of the profile Lumina brings for a Minecraft version (the first version uses "default"). */
function builtinId(mcVersion) {
  return mcVersion === VERSIONS[0] ? DEFAULT_ID : `lumina-${mcVersion.replace(/\W/g, '-')}`;
}

/** Lumina's own profiles are always called "Lumina <version>" and cannot be deleted or renamed. */
function isBuiltin(profile) {
  return VERSIONS.some((v) => profile.id === builtinId(v));
}

/** The selection is set once per launcher start (see load), afterwards it follows the picker. */
let startup = true;

async function load() {
  const data = await readJson(FILE(), {});
  const profiles = Array.isArray(data.profiles) ? data.profiles.filter((p) => p && p.id) : [];
  if (!profiles.some((p) => p.id === DEFAULT_ID)) profiles.unshift(defaultProfile());
  for (const p of profiles) if (!VERSIONS.includes(p.mcVersion)) p.mcVersion = VERSIONS[0];
  let selected = profiles.some((p) => p.id === data.selected) ? data.selected : DEFAULT_ID;
  let changed = false;
  // a newly supported Minecraft version comes with a ready profile once
  const known = Array.isArray(data.knownVersions) ? data.knownVersions : [VERSIONS[0]];
  const added = VERSIONS.filter((v) => !known.includes(v));
  if (added.length) {
    for (const v of added) {
      if (profiles.some((p) => p.mcVersion === v)) continue;
      profiles.push({ id: builtinId(v), name: `Lumina ${v}`, mcVersion: v, loader: 'fabric', createdAt: new Date().toISOString() });
    }
    changed = true;
  }
  // the launcher opens on the profile you played last; before the first game on the newest version
  // (a launcher older than 1.3.0 only kept the selection, which was the last played one then)
  if (startup) {
    startup = false;
    const last = profiles.find((p) => p.id === (data.lastPlayed || data.selected));
    const newest = profiles.find((p) => p.id === builtinId(NEWEST)) || profiles.find((p) => p.id === DEFAULT_ID);
    const want = (last || newest).id;
    if (want !== selected) {
      selected = want;
      changed = true;
    }
  }
  if (changed) await save({ ...data, profiles, selected, knownVersions: VERSIONS });
  return { selected, profiles };
}

async function save(data) {
  const stored = await readJson(FILE(), {});
  await writeJson(FILE(), { knownVersions: stored.knownVersions, lastPlayed: stored.lastPlayed, ...data });
  return data;
}

/** Remembers the profile that was started; the launcher opens on it next time. */
async function markPlayed(id) {
  const stored = await readJson(FILE(), {});
  await writeJson(FILE(), { ...stored, selected: id, lastPlayed: id });
}

async function list() {
  const data = await load();
  const profiles = data.profiles.map((p) => (isBuiltin(p) ? { ...p, name: `Lumina ${p.mcVersion}`, builtin: true } : { ...p, builtin: false }));
  return { ...data, profiles, versions: VERSIONS };
}

async function get(id) {
  const data = await load();
  const profile = data.profiles.find((p) => p.id === (id || data.selected));
  if (!profile) throw new Error('Profile not found.');
  return profile;
}

/**
 * Game folder (worlds, options, Lumina config) of a profile. Every Minecraft version gets its own:
 * a newer version upgrades worlds for good, so they must not be shared. The first version keeps
 * game/ so existing installs stay as they are; the others start with a copy of its settings.
 */
function gameDir(profileOrVersion) {
  const v = typeof profileOrVersion === 'string' ? profileOrVersion : profileOrVersion.mcVersion;
  return v === VERSIONS[0] ? paths.game : path.join(paths.root, `game-${v}`);
}

/** Lumina + performance pack for one Minecraft version (loaded via fabric.addMods). */
function coreModsDir(mcVersion) {
  return mcVersion === VERSIONS[0] ? paths.coreMods : path.join(paths.root, `core-mods-${mcVersion}`);
}

/** Settings copied into the game folder of a new Minecraft version (never worlds). Lumina's own are shared, see luminaDir. */
const SEED = ['options.txt', 'servers.dat'];

async function ensureGameDir(profile) {
  const target = gameDir(profile);
  const fresh = !(await exists(target));
  await fsp.mkdir(target, { recursive: true });
  if (fresh && target !== paths.game) {
    for (const name of SEED) {
      const src = path.join(paths.game, name);
      if (await exists(src)) {
        await fsp.cp(src, path.join(target, name), { recursive: true, errorOnExist: false, force: false }).catch(() => {});
      }
    }
  }
  await shareFolders(target, profile.mcVersion);
  return target;
}

/** Game folder of a second game started next to the first (another account), e.g. game-2. */
function extraGameDir(profile) {
  return `${gameDir(profile)}-2`;
}

/**
 * A second game gets its own folder, so the two games don't overwrite each other's options and
 * Lumina settings. It starts once with a copy of the normal folder's settings (never worlds).
 */
async function ensureExtraGameDir(profile) {
  const main = await ensureGameDir(profile);
  const target = extraGameDir(profile);
  const fresh = !(await exists(target));
  await fsp.mkdir(target, { recursive: true });
  if (fresh) {
    for (const name of SEED) {
      const src = path.join(main, name);
      if (await exists(src)) {
        await fsp.cp(src, path.join(target, name), { recursive: true, errorOnExist: false, force: false }).catch(() => {});
      }
    }
  }
  await shareFolders(target, profile.mcVersion);
  return target;
}

/**
 * Lumina's own folder (menu settings, waypoints, capes, skins) is one for every Minecraft version;
 * the game gets it as -Dlumina.data. A second game next to the first has its own, also shared by
 * all versions. The first time, it starts as a copy of the version folder whose Lumina settings were
 * saved last, plus the capes, skins and waypoints only the other versions had. The old
 * <game folder>/lumina folders are left as they were.
 */
function luminaDir(extra = false) {
  return extra ? paths.luminaExtra : paths.lumina;
}

const seeding = new Map();

/** The Cosmetics page and a launch may ask at the same moment; the folder is made once. */
function ensureLuminaDir(extra = false) {
  const target = luminaDir(extra);
  if (!seeding.has(target)) seeding.set(target, seedLuminaDir(target, extra).finally(() => seeding.delete(target)));
  return seeding.get(target);
}

async function seedLuminaDir(target, extra) {
  if (await exists(target)) return target;
  const sources = [];
  for (const v of VERSIONS) {
    const dir = path.join(extra ? extraGameDir(v) : gameDir(v), 'lumina');
    if (await exists(dir)) sources.push({ dir, saved: await savedAt(dir) });
  }
  // a second game that never ran starts from the first game's settings, like its game folder does
  if (extra && !sources.length && (await exists(paths.lumina))) sources.push({ dir: paths.lumina, saved: 0 });
  sources.sort((a, b) => b.saved - a.saved);
  const tmp = `${target}.tmp`;
  await fsp.rm(tmp, { recursive: true, force: true });
  await fsp.mkdir(tmp, { recursive: true });
  for (const [i, { dir }] of sources.entries()) {
    // the newest folder comes whole; the others only add what it doesn't have
    await fsp.cp(dir, tmp, { recursive: true, force: i === 0, errorOnExist: false });
    if (i > 0) {
      await mergeJson(path.join(dir, 'waypoints.json'), path.join(tmp, 'waypoints.json'), mergeWaypoints);
      await mergeJson(path.join(dir, 'skins', 'variants.json'), path.join(tmp, 'skins', 'variants.json'), (into, add) => ({ ...add, ...into }));
    }
  }
  await fsp.rename(tmp, target);
  return target;
}

async function savedAt(dir) {
  const st = await fsp.stat(path.join(dir, 'config.json')).catch(() => null);
  return st ? st.mtimeMs : 0;
}

/** Waypoints per world/server: a world only one version knows is added, and so are its missing points. */
function mergeWaypoints(into, add) {
  const same = (a, b) => a.name === b.name && a.x === b.x && a.y === b.y && a.z === b.z && a.dimension === b.dimension;
  const out = { ...into };
  for (const [world, list] of Object.entries(add)) {
    if (!Array.isArray(list)) continue;
    const have = Array.isArray(out[world]) ? [...out[world]] : [];
    for (const w of list) if (w && !have.some((h) => same(h, w))) have.push(w);
    out[world] = have;
  }
  return out;
}

async function mergeJson(from, into, merge) {
  const read = (f) => fsp.readFile(f, 'utf8').then(JSON.parse, () => null);
  const add = await read(from);
  if (!add || typeof add !== 'object') return;
  const current = (await read(into)) || {};
  await fsp.writeFile(into, JSON.stringify(merge(current, add), null, 2));
}

/** Folders every Minecraft version shares: <game folder>/<name> is a link to shared/<name>. */
const SHARED = ['resourcepacks', 'shaderpacks'];

function exists(p) {
  return fsp.stat(p).then(() => true, () => false);
}

async function sameFile(a, b) {
  const [sa, sb] = await Promise.all([fsp.stat(a), fsp.stat(b)]);
  return sa.isFile() && sb.isFile() && sa.size === sb.size && (await sha1File(a)) === (await sha1File(b));
}

/** Moves the packs of a game folder to the shared one. A different pack with the same name keeps both. */
async function movePacks(from, to, label) {
  for (const name of await fsp.readdir(from)) {
    const src = path.join(from, name);
    let dest = path.join(to, name);
    if (await exists(dest)) {
      if (await sameFile(src, dest)) {
        await fsp.rm(src);
        continue;
      }
      const { name: base, ext } = path.parse(name);
      dest = path.join(to, `${base} (${label})${ext}`);
      for (let i = 2; await exists(dest); i++) dest = path.join(to, `${base} (${label} ${i})${ext}`);
    }
    await fsp.rename(src, dest);
  }
}

async function shareFolder(game, name, label) {
  const link = path.join(game, name);
  const target = paths[name];
  await fsp.mkdir(target, { recursive: true });
  const st = await fsp.lstat(link).catch(() => null);
  if (st && st.isSymbolicLink()) {
    // a working link stays; one that leads nowhere (the data folder moved) is replaced
    if (await exists(link)) return;
    await fsp.unlink(link);
  } else if (st && st.isDirectory()) {
    await movePacks(link, target, label);
    await fsp.rmdir(link);
  } else if (st) {
    return;
  }
  await fsp.symlink(target, link, 'junction');
}

/** Never stops the game: a pack that cannot move now (still in use) stays until the next try. */
async function shareFolders(game, label) {
  for (const name of SHARED) {
    await shareFolder(game, name, label).catch((e) => console.warn(`Could not share ${name} of ${game}:`, e.message));
  }
}

/** For every game folder that already exists; a new one is linked when it is created. */
async function shareAll() {
  for (const v of VERSIONS) {
    const game = gameDir(v);
    if (await exists(game)) await shareFolders(game, v);
  }
}

function dir(profile) {
  return path.join(paths.profiles, profile.id);
}

function modsDir(profile) {
  return profile.id === DEFAULT_ID ? paths.userMods : path.join(dir(profile), 'mods');
}

function manifestFile(profile) {
  return profile.id === DEFAULT_ID ? path.join(paths.data, 'mods.json') : path.join(dir(profile), 'mods.json');
}

async function create({ name, mcVersion }) {
  name = String(name || '').trim().slice(0, 32);
  if (!name) throw new Error('Give the profile a name.');
  if (!VERSIONS.includes(mcVersion)) throw new Error(`Minecraft ${mcVersion} is not supported yet.`);
  const data = await load();
  const profile = { id: crypto.randomUUID().slice(0, 8), name, mcVersion, loader: 'fabric', createdAt: new Date().toISOString() };
  data.profiles.push(profile);
  data.selected = profile.id;
  await fsp.mkdir(modsDir(profile), { recursive: true });
  await save(data);
  return list();
}

async function rename(id, name) {
  name = String(name || '').trim().slice(0, 32);
  if (!name) throw new Error('Give the profile a name.');
  const data = await load();
  const profile = data.profiles.find((p) => p.id === id);
  if (!profile) throw new Error('Profile not found.');
  if (isBuiltin(profile)) throw new Error('Lumina profiles cannot be renamed.');
  profile.name = name;
  await save(data);
  return list();
}

async function remove(id) {
  const data = await load();
  const profile = data.profiles.find((p) => p.id === id);
  if (!profile) return list();
  if (isBuiltin(profile)) throw new Error('Lumina profiles cannot be deleted.');
  data.profiles = data.profiles.filter((p) => p.id !== id);
  if (data.selected === id) data.selected = DEFAULT_ID;
  await save(data);
  // only the profile's own folder (its mods) goes; worlds live in the shared game folder
  await fsp.rm(dir(profile), { recursive: true, force: true });
  return list();
}

async function select(id) {
  const data = await load();
  if (!data.profiles.some((p) => p.id === id)) throw new Error('Profile not found.');
  data.selected = id;
  await save(data);
  return list();
}

/**
 * The installer's privacy page leaves launcher/install-options.json when "Lumina Online" was
 * unticked. Its choice goes into Lumina's settings (Lumina Settings > Lumina Online) once; the
 * game then sends nothing to Lumina's server. Later changes happen in the game's menu.
 */
async function applyInstallOptions() {
  const file = path.join(paths.data, 'install-options.json');
  const options = await readJson(file, null);
  if (!options) return;
  if (options.online === false) {
    const dirs = [await ensureLuminaDir(false)];
    if (await exists(luminaDir(true))) dirs.push(luminaDir(true));
    for (const dir of dirs) {
      const configFile = path.join(dir, 'config.json');
      const config = await readJson(configFile, { version: 1, modules: {}, ui: {} });
      config.modules = config.modules || {};
      const client = (config.modules.client = config.modules.client || { enabled: true, settings: {} });
      client.settings = client.settings || {};
      client.settings.online = false;
      await writeJson(configFile, config);
    }
  }
  await fsp.rm(file, { force: true });
}

module.exports = { VERSIONS, DEFAULT_ID, list, get, create, rename, remove, select, markPlayed, modsDir, manifestFile, gameDir, coreModsDir, ensureGameDir, ensureExtraGameDir, luminaDir, ensureLuminaDir, shareAll, applyInstallOptions };
