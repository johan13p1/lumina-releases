'use strict';

const path = require('path');
const os = require('os');

// macOS: ~/Library/Application Support/LuminaClient (like Minecraft's own folder), Windows: %APPDATA%\.luminaclient
function defaultRoot() {
  if (process.platform === 'darwin') return path.join(os.homedir(), 'Library', 'Application Support', 'LuminaClient');
  return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), '.luminaclient');
}

const ROOT = process.env.LUMINA_HOME || defaultRoot();

const paths = {
  root: ROOT,
  // Shared Minecraft files (versions, libraries, assets)
  runtime: path.join(ROOT, 'runtime'),
  versions: path.join(ROOT, 'runtime', 'versions'),
  libraries: path.join(ROOT, 'runtime', 'libraries'),
  assets: path.join(ROOT, 'runtime', 'assets'),
  natives: path.join(ROOT, 'runtime', 'natives'),
  // Java runtimes downloaded from Mojang
  java: path.join(ROOT, 'java'),
  // The game directory: saves, user mods, resource packs, options
  game: path.join(ROOT, 'game'),
  userMods: path.join(ROOT, 'game', 'mods'),
  // Resource and shader packs: the same for every Minecraft version, each game folder links here
  resourcepacks: path.join(ROOT, 'shared', 'resourcepacks'),
  shaderpacks: path.join(ROOT, 'shared', 'shaderpacks'),
  // Lumina's own folder (menu settings, waypoints, capes, skins): the same for every Minecraft
  // version, handed to the game as -Dlumina.data; a second game next to the first has its own
  lumina: path.join(ROOT, 'shared', 'lumina'),
  luminaExtra: path.join(ROOT, 'shared', 'lumina-2'),
  // Mods shipped with Lumina (client mod + performance pack), loaded via fabric.addMods
  coreMods: path.join(ROOT, 'core-mods'),
  // Extra profiles (own mods folder per profile); the default profile uses game/mods
  profiles: path.join(ROOT, 'profiles'),
  // Launcher state
  data: path.join(ROOT, 'launcher'),
  // Chromium's own profile (caches, local storage); kept out of the data root on macOS
  electron: path.join(ROOT, 'launcher', 'electron'),
  logs: path.join(ROOT, 'logs'),
  cache: path.join(ROOT, 'cache'),
};

module.exports = paths;
