'use strict';

const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const paths = require('./paths');
const profiles = require('./profiles');
const { readFolder } = require('./jar');

/**
 * The Cosmetics page of the launcher: the same cape and particle effect the Lumina menu picks in
 * game (module "cosmetics" in Lumina's config.json). Every Minecraft version uses the same Lumina
 * folder (profiles.ensureLuminaDir), so a choice and your own capes are there in all of them.
 *
 * Minecraft keeps the config in memory and saves it on changes, so nothing is written while it runs.
 */

/** Same list, ids (enum name in lower case), icon sprites and icon colors as ParticleEffects.Effect in the mod. */
const EFFECTS = [
  { id: 'hearts', name: 'Hearts', sprite: 'HEART', color: '#FF5C8A' },
  { id: 'sakura', name: 'Sakura', sprite: 'PETAL', color: '#FFB7CE' },
  { id: 'snow', name: 'Snow', sprite: 'SNOWFLAKE', color: '#EAF4FF' },
  { id: 'autumn', name: 'Autumn', sprite: 'LEAF', color: '#F08A3A' },
  { id: 'butterflies', name: 'Butterflies', sprite: 'BUTTERFLY', color: '#C9A6FF' },
  { id: 'fireflies', name: 'Fireflies', sprite: 'GLOW', color: '#E4FF7A' },
  { id: 'bubbles', name: 'Bubbles', sprite: 'BUBBLE', color: '#BDEBFF' },
  { id: 'starfall', name: 'Starfall', sprite: 'SPARKLE', color: '#FFE6A3' },
  { id: 'galaxy', name: 'Galaxy', sprite: 'SPARKLE', color: '#B9A6FF' },
  { id: 'orbit', name: 'Orbit', sprite: 'SPARKLE', color: '#F4F8FF', tinted: true },
  { id: 'sparks', name: 'Sparks', sprite: 'DOT', color: '#F4F8FF', tinted: true },
];
const DEFAULT_TINT = '#B9C6FF';
const CAPE_TYPES = { '.png': 'image/png', '.apng': 'image/apng', '.gif': 'image/gif' };
const BUNDLED_PREFIX = 'assets/lumina/capes/';

/** Built-in capes per jar file, read once (they only change with a launcher update). */
const bundledCache = new Map();

function capeType(file) {
  return CAPE_TYPES[path.extname(file).toLowerCase()] || null;
}

/** CapeLibrary.prettyName: "Star-Planet.apng" -> "Star Planet". */
function prettyName(file) {
  const base = file.replace(/\.[^.]+$/, '').replace(/[_-]/g, ' ').trim();
  const words = base.split(/\s+/).filter(Boolean).map((w) => w[0].toUpperCase() + w.slice(1));
  return words.length ? words.join(' ') : file;
}

function dataUrl(file, buf) {
  return `data:${capeType(file)};base64,${buf.toString('base64')}`;
}

async function exists(p) {
  return fsp.stat(p).then(() => true, () => false);
}

/** The Lumina folders the choice goes into: the one every version shares. */
async function targets() {
  return [await profiles.ensureLuminaDir()];
}

const configFile = (dir) => path.join(dir, 'config.json');
const capesDir = (dir) => path.join(dir, 'capes');

/** Your own capes, for "Open folder". */
async function capesFolder() {
  return capesDir(await profiles.ensureLuminaDir());
}

async function readConfig(dir) {
  let text;
  try {
    text = await fsp.readFile(configFile(dir), 'utf8');
  } catch {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('The Lumina settings are damaged. Start the game once and it will work again.');
  }
}

async function bundledCapes(jar) {
  if (!jar) return [];
  if (!bundledCache.has(jar)) {
    const files = await readFolder(jar, BUNDLED_PREFIX).catch(() => new Map());
    // order.txt (also read by the game's CapeLibrary): listed capes first, the rest alphabetical
    const listed = String(files.get('order.txt') || '').split(/\r?\n/).map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
    const rank = (name) => (listed.includes(name) ? listed.indexOf(name) : Infinity);
    const list = [...files.entries()]
      .filter(([name]) => capeType(name))
      .sort(([a], [b]) => rank(a) - rank(b) || (a < b ? -1 : a > b ? 1 : 0))
      .map(([name, buf]) => ({ id: `lumina:${name}`, name: prettyName(name), bundled: true, data: dataUrl(name, buf) }));
    bundledCache.set(jar, list);
  }
  return bundledCache.get(jar);
}

async function userCapes(dirs) {
  const seen = new Map();
  for (const dir of dirs) {
    let names = [];
    try {
      names = await fsp.readdir(capesDir(dir));
    } catch {
      continue;
    }
    for (const name of names.sort()) {
      if (seen.has(name) || name.startsWith('._') || !capeType(name)) continue;
      try {
        const buf = await fsp.readFile(path.join(capesDir(dir), name));
        seen.set(name, { id: `user:${name}`, name: prettyName(name), bundled: false, data: dataUrl(name, buf) });
      } catch {
        /* unreadable file: skip it like the game does */
      }
    }
  }
  return [...seen.values()];
}

function colorOf(settings) {
  const argb = settings && settings.effect_color && settings.effect_color.argb;
  return typeof argb === 'string' && /^#[0-9a-f]{8}$/i.test(argb) ? `#${argb.slice(3)}` : DEFAULT_TINT;
}

async function state({ profile, jar }) {
  const dirs = await targets(profile);
  let settings = {};
  for (const dir of dirs) {
    const config = await readConfig(dir);
    const cosmetics = config && config.modules && config.modules.cosmetics;
    if (cosmetics) {
      settings = cosmetics.enabled === false ? {} : cosmetics.settings || {};
      break;
    }
  }
  const capes = [...(await bundledCapes(jar)), ...(await userCapes(dirs))];
  const cape = capes.some((c) => c.id === settings.cape) ? settings.cape : '';
  const effect = EFFECTS.some((e) => e.id === settings.effect) ? settings.effect : '';
  const density = Number(settings.density) > 0 ? Number(settings.density) : 1;
  return { capes, effects: EFFECTS, cape, effect, tint: colorOf(settings), density };
}

/** Only what the character on the start page wears, without sending all capes to the window. */
async function worn(args) {
  const s = await state(args);
  return { cape: s.capes.find((c) => c.id === s.cape) || null, effect: s.effect, tint: s.tint, density: s.density };
}

/** Writes cape and/or effect into the cosmetics module of one game folder (other settings stay). */
async function writeChoice(dir, choice, enable = true) {
  const config = (await readConfig(dir)) || { version: 1, modules: {}, ui: {} };
  config.modules = config.modules || {};
  const cosmetics = (config.modules.cosmetics = config.modules.cosmetics || {});
  cosmetics.settings = cosmetics.settings || {};
  if (choice.cape !== undefined) cosmetics.settings.cape = choice.cape;
  if (choice.effect !== undefined) cosmetics.settings.effect = choice.effect;
  // like picking in game: choosing something switches the cosmetics on
  if (enable) cosmetics.enabled = true;
  await fsp.mkdir(path.dirname(configFile(dir)), { recursive: true });
  const tmp = `${configFile(dir)}.tmp`;
  await fsp.writeFile(tmp, JSON.stringify(config, null, 2));
  await fsp.rename(tmp, configFile(dir));
}

/** Makes sure a user cape exists in every target folder. */
async function spreadCape(id, dirs) {
  if (!id.startsWith('user:')) return;
  const name = id.slice(5);
  const source = (await Promise.all(dirs.map(async (d) => ((await exists(path.join(capesDir(d), name))) ? d : null)))).find(Boolean);
  if (!source) throw new Error('This cape file no longer exists.');
  for (const dir of dirs) {
    const dest = path.join(capesDir(dir), name);
    if (dir === source || (await exists(dest))) continue;
    await fsp.mkdir(capesDir(dir), { recursive: true });
    await fsp.copyFile(path.join(capesDir(source), name), dest);
  }
}

/** Returns only the new choice; the cape pictures (several MB) stay where the page already has them. */
async function select({ profile, cape, effect }) {
  const dirs = await targets(profile);
  if (cape) await spreadCape(cape, dirs);
  for (const dir of dirs) await writeChoice(dir, { cape, effect });
  const config = await readConfig(dirs[0]);
  const settings = config.modules.cosmetics.settings;
  return { cape: settings.cape || '', effect: settings.effect || '' };
}

/** Copies cape files into every target folder (a taken name gets " 2", " 3", ... like in game) and wears the last one. */
async function addCapes({ profile, jar, files }) {
  const dirs = await targets(profile);
  let last = null;
  for (const src of files) {
    if (!capeType(src) || !fs.statSync(src, { throwIfNoEntry: false })?.isFile()) continue;
    const base = path.basename(src);
    let name = base;
    for (let i = 2; await Promise.all(dirs.map((d) => exists(path.join(capesDir(d), name)))).then((r) => r.some(Boolean)); i++) {
      name = base.replace(/(\.[^.]+)$/, ` ${i}$1`);
    }
    for (const dir of dirs) {
      await fsp.mkdir(capesDir(dir), { recursive: true });
      await fsp.copyFile(src, path.join(capesDir(dir), name));
    }
    last = `user:${name}`;
  }
  if (!last) throw new Error('Only PNG, APNG and GIF files can be capes.');
  await select({ profile, cape: last });
  return state({ profile, jar });
}

async function removeCape({ profile, jar, id }) {
  if (!id.startsWith('user:')) throw new Error('Built-in capes cannot be deleted.');
  const dirs = await targets(profile);
  const name = path.basename(id.slice(5));
  for (const dir of dirs) {
    await fsp.rm(path.join(capesDir(dir), name), { force: true });
    const config = await readConfig(dir);
    const settings = config && config.modules && config.modules.cosmetics && config.modules.cosmetics.settings;
    if (settings && settings.cape === id) await writeChoice(dir, { cape: '' }, false);
  }
  return state({ profile, jar });
}

module.exports = { state, worn, select, addCapes, removeCape, capesFolder };
