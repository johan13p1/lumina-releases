'use strict';

const fsp = require('fs/promises');
const path = require('path');
const { fetchJson, fetchWithRetry } = require('./download');
const { readEntry } = require('./jar');
const paths = require('./paths');

/**
 * The current skin of a Minecraft account, straight from Mojang's session server. Third-party
 * avatar services cache skins for hours; the texture URL here changes with every new skin, so a
 * changed skin shows up on the next refresh.
 */
const cache = new Map(); // texture url -> data url

async function current(uuid) {
  if (!uuid) return null;
  const id = String(uuid).replace(/-/g, '');
  const profile = await fetchJson(`https://sessionserver.mojang.com/session/minecraft/profile/${id}?unsigned=true&t=${Date.now()}`, { timeout: 15000 });
  const prop = (profile.properties || []).find((p) => p.name === 'textures');
  if (!prop) return null;
  const textures = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8')).textures || {};
  const skin = textures.SKIN;
  if (!skin || !skin.url) return null;
  const url = skin.url.replace(/^http:/, 'https:');
  if (!cache.has(url)) {
    const res = await fetchWithRetry(url, { timeout: 15000 }, 1);
    cache.set(url, `data:image/png;base64,${Buffer.from(await res.arrayBuffer()).toString('base64')}`);
  }
  return { url, data: cache.get(url), slim: !!(skin.metadata && skin.metadata.model === 'slim') };
}

let steve = null;

/** Steve's texture on Mojang's server: a new install has no game jar to take it from yet. */
const STEVE_URL = 'https://textures.minecraft.net/texture/31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb';

/** Minecraft's default skin (Steve) from an installed game jar, for offline accounts or when Mojang can't be reached. */
async function fallback() {
  if (steve) return steve;
  const asSkin = (buf) => (steve = { url: 'steve', data: `data:image/png;base64,${buf.toString('base64')}`, slim: false });
  const entry = 'assets/minecraft/textures/entity/player/wide/steve.png';
  for (const v of await fsp.readdir(paths.versions).catch(() => [])) {
    const buf = await readEntry(path.join(paths.versions, v, `${v}.jar`), entry).catch(() => null);
    if (buf) return asSkin(buf);
  }
  const res = await fetchWithRetry(STEVE_URL, { timeout: 15000 }, 1).catch(() => null);
  return res ? asSkin(Buffer.from(await res.arrayBuffer())) : null;
}

module.exports = { current, fallback };
