'use strict';

const fsp = require('fs/promises');
const path = require('path');
const profiles = require('./profiles');

/**
 * How many games run Lumina right now, for the number in the top bar. Lumina's server counts the
 * games that reported in the last minutes (every running game does, every 10 minutes). Nothing about
 * this PC is sent. With Lumina Online turned off (Lumina Settings or the installer) the launcher
 * doesn't ask either.
 */
const API = process.env.LUMINA_API || 'https://lumina-api.lumina-cc1.workers.dev';
const { version } = require('../../../package.json');

async function enabled() {
  try {
    const config = JSON.parse(await fsp.readFile(path.join(profiles.luminaDir(false), 'config.json'), 'utf8'));
    const client = config && config.modules && config.modules.client;
    return !(client && client.settings && client.settings.online === false);
  } catch {
    return true; // no config yet: Lumina Online is on by default
  }
}

/** The number, or null when it is not known or not wanted. */
async function count() {
  if (!(await enabled())) return null;
  try {
    const res = await fetch(`${API}/v1/online`, {
      headers: { 'User-Agent': `LuminaClient/${version}` },
      signal: AbortSignal.timeout(8000),
    });
    if (!res.ok) return null;
    const { online } = await res.json();
    return Number.isInteger(online) && online >= 0 ? online : null;
  } catch {
    return null;
  }
}

module.exports = { count };
