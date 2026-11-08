'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { safeStorage } = require('electron');
const paths = require('./paths');
const { readJson, writeJson } = require('./minecraft');

const FILE = () => path.join(paths.data, 'accounts.json');

function protect(text) {
  if (!text) return null;
  if (safeStorage.isEncryptionAvailable()) return 'enc:' + safeStorage.encryptString(text).toString('base64');
  return 'b64:' + Buffer.from(text, 'utf8').toString('base64');
}

function unprotect(value) {
  if (!value) return null;
  if (value.startsWith('enc:')) {
    try {
      return safeStorage.decryptString(Buffer.from(value.slice(4), 'base64'));
    } catch {
      // the key lives in the macOS keychain; a denied access prompt (e.g. after an update) lands here
      if (process.platform === 'darwin') throw new Error('The keychain denied access to your sign-in. Restart the launcher and choose "Always Allow", or sign in again.');
      // Windows keeps the key in the launcher's own profile (DPAPI); without it the login can't be read
      throw new Error('Your saved sign-in can no longer be read. Please sign in again.');
    }
  }
  if (value.startsWith('b64:')) return Buffer.from(value.slice(4), 'base64').toString('utf8');
  return value;
}

async function load() {
  const data = await readJson(FILE(), { selected: null, accounts: [] });
  if (!Array.isArray(data.accounts)) data.accounts = [];
  return data;
}

async function save(data) {
  await writeJson(FILE(), data);
}

/** Public view of the accounts without any secrets. */
async function list() {
  const data = await load();
  return {
    selected: data.selected,
    accounts: data.accounts.map((a) => ({ id: a.id, type: a.type, name: a.name, uuid: a.uuid })),
  };
}

function offlineUuid(name) {
  // Same algorithm as vanilla: UUID v3 of "OfflinePlayer:<name>"
  const hash = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = hash.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Steps of the Microsoft login, as msmc names them in its errors. */
const LOGIN_STEPS = {
  'error.auth.microsoft': 'Microsoft sign-in',
  'error.auth.xboxLive': 'Xbox Live sign-in',
  'error.auth.xsts': 'Xbox security token',
  'error.auth.minecraft.login': 'Minecraft sign-in',
  'error.auth.minecraft.profile': 'Minecraft profile',
  'error.auth.minecraft.entitlements': 'Minecraft entitlements',
};

/** Xbox's XErr codes that have a clear fix. */
const XBOX_ERRORS = {
  2148916227: 'This account is banned from Xbox.',
  2148916229: 'An adult has to allow online games for this account (Xbox family settings).',
  2148916233: 'This Microsoft account has no Xbox profile yet. Sign in once at xbox.com to create one, then try again.',
  2148916234: 'This account still has to accept the terms of use on xbox.com.',
  2148916235: 'Xbox Live is not available in your country.',
  2148916236: 'This account needs age verification on xbox.com.',
  2148916237: 'This account needs age verification on xbox.com.',
  2148916238: 'This account belongs to a child (under 18) and must be added to a Microsoft family by an adult.',
};

function logAuth(text) {
  try {
    fs.mkdirSync(paths.logs, { recursive: true });
    fs.appendFileSync(path.join(paths.logs, 'auth.log'), `[${new Date().toISOString()}] ${text}\n`);
  } catch {
    /* logging must never break the login */
  }
}

/**
 * msmc rejects with plain strings ("error.gui.closed") or with { response, ts } for failed HTTP
 * calls, which would only show up as "[object Object]". This turns both into a readable Error.
 */
async function loginError(e) {
  if (e instanceof Error) {
    logAuth(`error: ${e.stack || e.message}`);
    return e;
  }
  if (typeof e === 'string') {
    logAuth(`error: ${e}`);
    return new Error(e === 'error.gui.closed' ? 'Sign-in cancelled.' : e);
  }
  const ts = (e && e.ts) || 'error.auth';
  const step = LOGIN_STEPS[ts] || 'Sign-in';
  const res = e && e.response;
  let body = '';
  try {
    body = res && typeof res.text === 'function' ? await res.text() : '';
  } catch {
    /* body already read */
  }
  logAuth(`${ts}: HTTP ${res && res.status} ${res && res.url} ${body.slice(0, 2000)}`);
  let detail = '';
  try {
    const json = JSON.parse(body);
    if (json.XErr && XBOX_ERRORS[json.XErr]) return new Error(XBOX_ERRORS[json.XErr]);
    detail = json.XErr ? `Xbox error ${json.XErr}` : json.errorMessage || json.error_description || json.error || '';
  } catch {
    /* not JSON */
  }
  if (ts === 'error.auth.minecraft.profile' && res && res.status === 404) {
    return new Error('This Microsoft account does not own Minecraft: Java Edition.');
  }
  const status = res && res.status ? `HTTP ${res.status}` : '';
  return new Error(`${step} failed${status || detail ? ` (${[status, detail].filter(Boolean).join(', ')})` : ''}. Details: logs/auth.log`);
}

async function addMicrosoft(parentWindow) {
  try {
    return await addMicrosoftAccount(parentWindow);
  } catch (e) {
    throw await loginError(e);
  }
}

async function addMicrosoftAccount(parentWindow) {
  const { Auth } = require('msmc');
  const auth = new Auth('select_account');
  auth.on('load', (code) => logAuth(`step: ${code}`));
  const xbox = await auth.launch('electron', {
    width: 520,
    height: 680,
    resizable: false,
    title: 'Microsoft Login – LuminaClient',
    backgroundColor: '#0b0b0d',
    autoHideMenuBar: true,
    // macOS shows a modal child as a sheet glued to the (smaller) launcher window: cut off and
    // without a close button. There the login gets its own normal window instead.
    ...(process.platform === 'darwin'
      ? { center: true, minimizable: false, fullscreenable: false }
      : { parent: parentWindow || undefined, modal: !!parentWindow, alwaysOnTop: !parentWindow }),
  });
  const mc = await xbox.getMinecraft();
  if (!mc.profile || mc.isDemo()) {
    throw new Error('This Microsoft account does not own Minecraft: Java Edition.');
  }
  const data = await load();
  const id = mc.profile.id;
  const record = {
    id,
    type: 'msa',
    name: mc.profile.name,
    uuid: mc.profile.id,
    xuid: mc.xuid,
    refresh: protect(xbox.save()),
    token: protect(mc.mcToken),
    exp: mc.exp,
    addedAt: new Date().toISOString(),
  };
  data.accounts = data.accounts.filter((a) => a.id !== id);
  data.accounts.push(record);
  data.selected = id;
  data.hadMicrosoft = true;
  await save(data);
  return list();
}

async function addOffline(name) {
  const data = await load();
  if (!data.hadMicrosoft && !process.env.LUMINA_DEV) {
    throw new Error('Add a Microsoft account with Minecraft first. Offline profiles are for singleplayer only.');
  }
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) throw new Error('Name: 3 to 16 characters, only letters, numbers and _.');
  const uuid = offlineUuid(name);
  const id = `offline:${uuid}`;
  data.accounts = data.accounts.filter((a) => a.id !== id);
  data.accounts.push({ id, type: 'offline', name, uuid, addedAt: new Date().toISOString() });
  data.selected = id;
  await save(data);
  return list();
}

async function remove(id) {
  const data = await load();
  data.accounts = data.accounts.filter((a) => a.id !== id);
  if (data.selected === id) data.selected = data.accounts[0] ? data.accounts[0].id : null;
  await save(data);
  return list();
}

async function select(id) {
  const data = await load();
  if (data.accounts.find((a) => a.id === id)) data.selected = id;
  await save(data);
  return list();
}

/** Returns credentials for the selected account, for launching. */
async function launchCredentials() {
  const data = await load();
  if (!data.selected) throw new Error('No account selected. Sign in first.');
  return credentials(data.selected);
}

/** Returns credentials for an account, refreshing Microsoft tokens when needed. */
async function credentials(id) {
  const data = await load();
  const acc = data.accounts.find((a) => a.id === id);
  if (!acc) throw new Error('No account selected. Sign in first.');
  if (acc.type === 'offline') {
    return { type: 'offline', name: acc.name, uuid: acc.uuid, accessToken: '0', xuid: '' };
  }
  const now = Date.now();
  let token = unprotect(acc.token);
  if (!token || !acc.exp || acc.exp - now < 10 * 60 * 1000) {
    const { Auth } = require('msmc');
    const auth = new Auth('select_account');
    const refresh = unprotect(acc.refresh);
    let xbox;
    try {
      xbox = await auth.refresh(refresh);
    } catch (e) {
      throw new Error('Your Microsoft sign-in has expired. Please sign in again.');
    }
    let mc;
    try {
      mc = await xbox.getMinecraft();
    } catch (e) {
      throw await loginError(e);
    }
    token = mc.mcToken;
    acc.token = protect(mc.mcToken);
    acc.exp = mc.exp;
    acc.refresh = protect(xbox.save());
    if (mc.profile) {
      acc.name = mc.profile.name;
      acc.uuid = mc.profile.id;
    }
    acc.xuid = mc.xuid;
    await save(data);
  }
  return { type: 'msa', name: acc.name, uuid: acc.uuid, accessToken: token, xuid: acc.xuid || '' };
}

module.exports = { list, addMicrosoft, addOffline, remove, select, launchCredentials, credentials, offlineUuid };
