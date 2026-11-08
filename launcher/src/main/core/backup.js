'use strict';

const fsp = require('fs/promises');
const path = require('path');
const paths = require('./paths');

/** How many backups are kept; older ones are removed. */
const KEEP = 10;
const MANIFEST = 'backup.json';

const dir = () => path.join(paths.root, 'backups');

/**
 * What a backup holds, relative to the data folder: game options, mod configs, the Lumina config
 * (with capes and skins), the user's mods of every profile and the launcher's own settings/profiles.
 * Every game folder (game, game-26.3, ...) is included. Worlds, resource/shader packs and account
 * tokens are not copied (big, or secret) - an update never touches them anyway.
 */
const GAME_ENTRIES = ['options.txt', 'servers.dat', 'config', 'lumina', 'mods'];
const LAUNCHER_ENTRIES = ['settings.json', 'profiles.json', 'mods.json'];

/** Folders that a restore makes identical to the backup (an old and a new jar of one mod would crash Fabric). */
function mirrored(rel) {
  const parts = rel.split(path.sep);
  return (parts[0].startsWith('game') && parts[1] === 'mods' && parts.length === 2) || (parts[0] === 'profiles' && parts[2] === 'mods' && parts.length === 3);
}

async function exists(p) {
  return fsp.stat(p).then(() => true, () => false);
}

async function entries() {
  const list = [];
  for (const name of await fsp.readdir(paths.root).catch(() => [])) {
    if (!/^game(-[\w.]+)?$/.test(name)) continue;
    for (const e of GAME_ENTRIES) list.push(path.join(name, e));
  }
  // Lumina's settings, shared by every version (the old per-version game*/lumina stay included above)
  list.push(path.join('shared', 'lumina'), path.join('shared', 'lumina-2'));
  for (const e of LAUNCHER_ENTRIES) list.push(path.join('launcher', e));
  for (const id of await fsp.readdir(paths.profiles).catch(() => [])) {
    list.push(path.join('profiles', id, 'mods.json'), path.join('profiles', id, 'mods'));
  }
  return list;
}

/** Files the backup skips inside the copied folders. */
function skipped(name) {
  return name.startsWith('._') || name === '.DS_Store' || name.endsWith('.tmp');
}

async function copy(src, dest) {
  let st;
  try {
    st = await fsp.stat(src);
  } catch {
    return { files: 0, bytes: 0 };
  }
  if (st.isDirectory()) {
    const total = { files: 0, bytes: 0 };
    await fsp.mkdir(dest, { recursive: true });
    for (const name of await fsp.readdir(src)) {
      if (skipped(name)) continue;
      const n = await copy(path.join(src, name), path.join(dest, name));
      total.files += n.files;
      total.bytes += n.bytes;
    }
    return total;
  }
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  await fsp.copyFile(src, dest);
  return { files: 1, bytes: st.size };
}

/**
 * Copies the user's settings and mods into backups/<time> <label>. Returns the folder, or null if
 * there was nothing to back up yet (fresh install).
 */
async function create(label, extra = {}, protect = null) {
  const stamp = new Date().toISOString().replace(/[:T]/g, '-').slice(0, 19);
  const target = path.join(dir(), `${stamp} ${label}`.replace(/[^\w.\- ()]/g, '_'));
  const saved = [];
  let files = 0;
  let bytes = 0;
  for (const rel of await entries()) {
    const n = await copy(path.join(paths.root, rel), path.join(target, rel));
    if (n.files || (mirrored(rel) && (await exists(path.join(paths.root, rel))))) saved.push(rel);
    files += n.files;
    bytes += n.bytes;
  }
  if (!files) {
    await fsp.rm(target, { recursive: true, force: true });
    return null;
  }
  const manifest = { label, createdAt: new Date().toISOString(), files, bytes, entries: saved, ...extra };
  await fsp.writeFile(path.join(target, MANIFEST), JSON.stringify(manifest, null, 2));
  await prune(protect);
  return target;
}

async function folders() {
  try {
    return (await fsp.readdir(dir(), { withFileTypes: true })).filter((d) => d.isDirectory()).map((d) => d.name).sort();
  } catch {
    return [];
  }
}

/** Removes the oldest backups beyond KEEP; `protect` (a folder name) is never removed. */
async function prune(protect) {
  const names = (await folders()).filter((n) => n !== protect);
  for (const name of names.slice(0, Math.max(0, names.length - KEEP))) {
    await fsp.rm(path.join(dir(), name), { recursive: true, force: true });
  }
}

/** All backups, newest first. Old backups without a manifest are listed with what their folder name says. */
async function list() {
  const result = [];
  for (const name of (await folders()).reverse()) {
    let manifest = null;
    try {
      manifest = JSON.parse(await fsp.readFile(path.join(dir(), name, MANIFEST), 'utf8'));
    } catch {
      /* backup from an older launcher */
    }
    const m = name.match(/^(\d{4})-(\d\d)-(\d\d)-(\d\d)-(\d\d)-(\d\d) (.*)$/);
    result.push({
      name,
      label: (manifest && manifest.label) || (m ? m[7] : name),
      createdAt: (manifest && manifest.createdAt) || (m ? `${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}Z` : null),
      files: manifest ? manifest.files : null,
      bytes: manifest ? manifest.bytes : null,
      version: (manifest && manifest.version) || null,
    });
  }
  return result;
}

/**
 * Puts a backup back. A fresh backup of the current state is made first, so a restore can itself
 * be undone. Mod folders become exactly what the backup holds; everything else is only overwritten.
 */
async function restore(name) {
  const source = path.join(dir(), path.basename(name));
  if (!(await exists(source))) throw new Error('This backup no longer exists.');
  let saved = null;
  try {
    saved = JSON.parse(await fsp.readFile(path.join(source, MANIFEST), 'utf8')).entries;
  } catch {
    /* older backup: restore whatever it contains */
  }
  // the backup being restored must survive the pruning that the safety backup triggers
  const safety = await create('before restore', {}, path.basename(source));
  const rels = saved || (await fsp.readdir(source)).filter((n) => n !== MANIFEST && !skipped(n));
  for (const rel of rels) {
    const from = path.join(source, rel);
    const to = path.join(paths.root, rel);
    if (mirrored(rel)) await fsp.rm(to, { recursive: true, force: true });
    await copy(from, to);
  }
  return { restored: path.basename(source), safety: safety && path.basename(safety) };
}

/**
 * Backs up the user's data the first time a new launcher version starts, before the new code
 * touches anything - no matter how it got installed (updater, build.sh --install, DMG).
 */
async function atVersionChange(version) {
  const file = path.join(paths.data, 'last-version.json');
  let last = null;
  try {
    last = JSON.parse(await fsp.readFile(file, 'utf8')).version;
  } catch {
    /* first start of a launcher that records its version */
  }
  if (last === version) return null;
  const folder = await create(last ? `before first start of ${version} (was ${last})` : `before first start of ${version}`, { version: last });
  await fsp.mkdir(paths.data, { recursive: true });
  await fsp.writeFile(file, JSON.stringify({ version, startedAt: new Date().toISOString() }, null, 2));
  return folder;
}

module.exports = { create, list, restore, atVersionChange, dir, KEEP };
