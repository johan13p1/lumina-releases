'use strict';

const { contextBridge, ipcRenderer, webUtils } = require('electron');

const invoke = (channel, ...args) => ipcRenderer.invoke(channel, ...args);

// lets the stylesheet adapt to the platform (macOS: native traffic lights instead of own buttons)
window.addEventListener('DOMContentLoaded', () => document.documentElement.classList.add(`platform-${process.platform}`));

contextBridge.exposeInMainWorld('lumina', {
  window: {
    minimize: () => invoke('window:minimize'),
    maximize: () => invoke('window:maximize'),
    close: () => invoke('window:close'),
  },
  platform: process.platform,
  app: {
    info: () => invoke('app:info'),
    openExternal: (url) => invoke('app:openExternal', url),
    openFolder: (which, profileId) => invoke('app:openFolder', which, profileId),
  },
  update: {
    status: () => invoke('update:status'),
    check: () => invoke('update:check'),
    install: () => invoke('update:install'),
    openFeed: () => invoke('update:openFeed'),
  },
  cosmetics: {
    state: () => invoke('cosmetics:state'),
    worn: () => invoke('cosmetics:worn'),
    skin: () => invoke('cosmetics:skin'),
    select: (choice) => invoke('cosmetics:select', choice),
    // dropped files: their paths only exist here, in the preload
    add: (files) => invoke('cosmetics:add', Array.from(files, (f) => webUtils.getPathForFile(f)).filter(Boolean)),
    remove: (id) => invoke('cosmetics:remove', id),
    openFolder: () => invoke('cosmetics:openFolder'),
  },
  backups: {
    list: () => invoke('backups:list'),
    create: () => invoke('backups:create'),
    restore: (name) => invoke('backups:restore', name),
  },
  profiles: {
    list: () => invoke('profiles:list'),
    create: (opts) => invoke('profiles:create', opts),
    rename: (id, name) => invoke('profiles:rename', id, name),
    remove: (id) => invoke('profiles:remove', id),
    select: (id) => invoke('profiles:select', id),
  },
  accounts: {
    list: () => invoke('accounts:list'),
    addMicrosoft: () => invoke('accounts:addMicrosoft'),
    addOffline: (name) => invoke('accounts:addOffline', name),
    remove: (id) => invoke('accounts:remove', id),
    select: (id) => invoke('accounts:select', id),
    skin: (uuid) => invoke('accounts:skin', uuid),
  },
  settings: {
    get: () => invoke('settings:get'),
    set: (partial) => invoke('settings:set', partial),
  },
  game: {
    launch: () => invoke('game:launch'),
    launchExtra: (accountId) => invoke('game:launchExtra', accountId),
    kill: (slot) => invoke('game:kill', slot),
    status: () => invoke('game:status'),
  },
  mods: {
    search: (opts) => invoke('mods:search', opts),
    install: (opts) => invoke('mods:install', opts),
    installed: (profileId) => invoke('mods:installed', profileId),
    core: () => invoke('mods:core'),
    setEnabled: (profileId, file, enabled) => invoke('mods:setEnabled', profileId, file, enabled),
    remove: (profileId, file) => invoke('mods:remove', profileId, file),
    updates: (profileId) => invoke('mods:updates', profileId),
    migrationPlan: (fromId, toId) => invoke('mods:migrationPlan', fromId, toId),
    migrate: (toId, items) => invoke('mods:migrate', toId, items),
  },
  shaders: {
    search: (opts) => invoke('shaders:search', opts),
    install: (opts) => invoke('shaders:install', opts),
    installed: () => invoke('shaders:installed'),
    remove: (file) => invoke('shaders:remove', file),
  },
  on: (channel, callback) => {
    const allowed = ['accounts:changed', 'game:progress', 'game:log', 'game:state', 'game:extra', 'mods:progress', 'update:state', 'menu:page', 'menu:play'];
    if (!allowed.includes(channel)) return () => {};
    const handler = (_event, payload) => callback(payload);
    ipcRenderer.on(channel, handler);
    return () => ipcRenderer.removeListener(channel, handler);
  },
});
