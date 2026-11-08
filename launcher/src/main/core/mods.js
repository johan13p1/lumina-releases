'use strict';

const fsp = require('fs/promises');
const path = require('path');
const paths = require('./paths');
const { fetchJson, fetchWithRetry, downloadFile, sha1File } = require('./download');
const { readJson, writeJson } = require('./minecraft');
const { readFabricModJson } = require('./jar');
const profiles = require('./profiles');

const MODRINTH = 'https://api.modrinth.com/v2';

/**
 * Mods that ship with Lumina. They live in core-mods/ and are loaded via -Dfabric.addMods.
 * `required: false` entries can be toggled in the launcher settings.
 */
const CORE_MODS = [
  { id: 'fabric-api', project: 'P7dR8mSH', name: 'Fabric API', required: true },
  { id: 'sodium', project: 'AANobbMI', name: 'Sodium', required: true, group: 'performance' },
  { id: 'lithium', project: 'gvQqBUqZ', name: 'Lithium', required: true, group: 'performance' },
  { id: 'ferritecore', project: 'uXXizFIs', name: 'FerriteCore', required: true, group: 'performance' },
  { id: 'immediatelyfast', project: '5ZwdcRci', name: 'ImmediatelyFast', required: true, group: 'performance' },
  { id: 'entityculling', project: 'NNAgCjsB', name: 'Entity Culling', required: true, group: 'performance' },
  { id: 'moreculling', project: '51shyZVL', name: 'More Culling', required: true, group: 'performance' },
  { id: 'badoptimizations', project: 'g96Z4WVZ', name: 'BadOptimizations', required: true, group: 'performance' },
  { id: 'dynamic_fps', project: 'LQ3K71Q1', name: 'Dynamic FPS', required: true, group: 'performance' },
  { id: 'krypton', project: 'fQEb0iXm', name: 'Krypton', required: true, group: 'performance' },
  { id: 'scalablelux', project: 'Ps1zyz6x', name: 'ScalableLux', required: true, group: 'performance' },
  { id: 'cullleaves', project: 'GNxdLCoP', name: 'Cull Leaves', required: true, group: 'performance' },
  { id: 'sodium-extra', project: 'PtjYWJkn', name: 'Sodium Extra', required: true, group: 'performance' },
  { id: 'reeses-sodium-options', project: 'Bh37bMuy', name: "Reese's Sodium Options", required: true, group: 'performance' },
  { id: 'skinlayers3d', project: 'zV5r3pPn', name: '3D Skin Layers', required: true, group: 'features' },
  { id: 'modmenu', project: 'mOgUt4GM', name: 'Mod Menu', required: true, group: 'features' },
  { id: 'resourcify', project: 'RLzHAoZe', name: 'Resourcify', required: true, group: 'features' },
  { id: 'waveycapes', project: 'kYuIpRLv', name: 'Wavey Capes', required: true, group: 'features' },  { id: 'iris', project: 'YL57xq9U', name: 'Iris Shaders', required: false, group: 'optional', setting: 'shaders' },
];

// ---------------------------------------------------------------------------
// Manifest of user installed mods (one per profile)
// ---------------------------------------------------------------------------

async function loadManifest(profile) {
  return readJson(profiles.manifestFile(profile), { mods: [] });
}

async function saveManifest(profile, manifest) {
  await writeJson(profiles.manifestFile(profile), manifest);
}

// ---------------------------------------------------------------------------
// Modrinth
// ---------------------------------------------------------------------------

async function modrinthSearch({ query = '', mcVersion, offset = 0, limit = 20, sort = 'relevance' }) {
  const facets = JSON.stringify([['categories:fabric'], [`versions:${mcVersion}`], ['project_type:mod']]);
  const url = `${MODRINTH}/search?query=${encodeURIComponent(query)}&facets=${encodeURIComponent(facets)}&index=${sort}&offset=${offset}&limit=${limit}`;
  const res = await fetchJson(url);
  return {
    total: res.total_hits,
    hits: res.hits.map((h) => ({
      source: 'modrinth',
      id: h.project_id,
      slug: h.slug,
      name: h.title,
      author: h.author,
      description: h.description,
      downloads: h.downloads,
      icon: h.icon_url || null,
      url: `https://modrinth.com/mod/${h.slug}`,
      categories: h.display_categories || h.categories || [],
    })),
  };
}

async function modrinthBestVersion(projectId, mcVersion, loader = 'fabric') {
  const url = `${MODRINTH}/project/${projectId}/version?loaders=${encodeURIComponent(`["${loader}"]`)}&game_versions=${encodeURIComponent(`["${mcVersion}"]`)}`;
  const versions = await fetchJson(url);
  if (!versions.length) return null;
  // Prefer releases, then betas, then alphas; the API already sorts by date (newest first).
  const rank = { release: 0, beta: 1, alpha: 2 };
  return [...versions].sort((a, b) => (rank[a.version_type] ?? 3) - (rank[b.version_type] ?? 3))[0];
}

async function modrinthResolve(projectId, mcVersion, seen = new Set()) {
  if (seen.has(projectId)) return [];
  seen.add(projectId);
  const version = await modrinthBestVersion(projectId, mcVersion);
  if (!version) {
    const err = new Error(`No Fabric version found for ${mcVersion}.`);
    err.projectId = projectId;
    throw err;
  }
  const file = version.files.find((f) => f.primary) || version.files[0];
  const list = [{ projectId, versionId: version.id, versionNumber: version.version_number, file }];
  for (const dep of version.dependencies || []) {
    if (dep.dependency_type !== 'required') continue;
    let depProject = dep.project_id;
    if (!depProject && dep.version_id) {
      const v = await fetchJson(`${MODRINTH}/version/${dep.version_id}`);
      depProject = v.project_id;
    }
    if (!depProject) continue;
    try {
      list.push(...(await modrinthResolve(depProject, mcVersion, seen)));
    } catch (e) {
      // versions built for several loaders also list e.g. NeoForge-only libraries; those are not needed on Fabric
      const project = await fetchJson(`${MODRINTH}/project/${depProject}`).catch(() => null);
      if (project && Array.isArray(project.loaders) && !project.loaders.includes('fabric') && !project.loaders.includes('quilt')) continue;
      throw e;
    }
  }
  return list;
}

async function modrinthProjects(ids) {
  if (!ids.length) return [];
  return fetchJson(`${MODRINTH}/projects?ids=${encodeURIComponent(JSON.stringify(ids))}`);
}

// ---------------------------------------------------------------------------
// Install / remove
// ---------------------------------------------------------------------------

function coreConflict(projectId) {
  // optional core mods (Iris) may be switched off, so user mods are allowed to bring them
  return CORE_MODS.find((m) => m.project === projectId && m.required) || null;
}

async function installMod({ id, profile, onProgress = () => {} }) {
  const mcVersion = profile.mcVersion;
  const modsDir = profiles.modsDir(profile);
  const manifest = await loadManifest(profile);
  await fsp.mkdir(modsDir, { recursive: true });

  const core = coreConflict(id);
  if (core) {
    const err = new Error(`${core.name} is already included in Lumina.`);
    err.code = 'ALREADY_CORE';
    throw err;
  }

  onProgress({ stage: 'Resolving dependencies' });
  const plan = await modrinthResolve(id, mcVersion);

  const installed = [];
  for (const [i, entry] of plan.entries()) {
    // Skip dependencies that Lumina already ships (e.g. Fabric API).
    if (coreConflict(entry.projectId)) continue;
    const existing = manifest.mods.find((m) => m.source === 'modrinth' && m.projectId === entry.projectId);
    if (existing && existing.versionId === entry.versionId) continue;

    const [project] = await modrinthProjects([entry.projectId]);
    const meta = { name: project.title, icon: project.icon_url, slug: project.slug, author: project.team || '' };
    const fileName = entry.file.filename;
    const url = entry.file.url;
    const sha1 = entry.file.hashes && entry.file.hashes.sha1;

    onProgress({ stage: `Downloading ${meta.name}`, index: i, total: plan.length });
    const dest = path.join(modsDir, fileName);
    await downloadFile(url, dest, { sha1 });
    if (existing && existing.file !== fileName) {
      await fsp.rm(path.join(modsDir, existing.file), { force: true });
      await fsp.rm(path.join(modsDir, `${existing.file}.disabled`), { force: true });
    }

    let modId = null;
    try {
      const fmj = await readFabricModJson(dest);
      modId = fmj && fmj.id;
    } catch {
      /* not fatal */
    }

    const record = {
      source: 'modrinth',
      projectId: entry.projectId,
      versionId: entry.versionId,
      version: entry.versionNumber,
      file: fileName,
      modId,
      name: meta.name,
      icon: meta.icon || null,
      slug: meta.slug,
      enabled: true,
      dependency: entry.projectId !== String(id),
      installedAt: new Date().toISOString(),
    };
    manifest.mods = manifest.mods.filter((m) => !(m.source === 'modrinth' && m.projectId === entry.projectId));
    manifest.mods.push(record);
    installed.push(record);
  }
  await saveManifest(profile, manifest);
  return installed;
}

async function listInstalled(profile) {
  const modsDir = profiles.modsDir(profile);
  const manifest = await loadManifest(profile);
  // Pick up jars that were dropped into the mods folder manually.
  let files = [];
  try {
    files = await fsp.readdir(modsDir);
  } catch {
    /* folder missing */
  }
  const known = new Set(manifest.mods.flatMap((m) => [m.file, `${m.file}.disabled`]));
  const manual = [];
  for (const f of files) {
    if (known.has(f) || !(f.endsWith('.jar') || f.endsWith('.jar.disabled'))) continue;
    const full = path.join(modsDir, f);
    let meta = null;
    try {
      meta = await readFabricModJson(full);
    } catch {
      /* ignore */
    }
    manual.push({
      source: 'local',
      projectId: f,
      file: f.replace(/\.disabled$/, ''),
      name: (meta && meta.name) || f.replace(/\.jar(\.disabled)?$/, ''),
      version: (meta && meta.version) || '',
      modId: meta && meta.id,
      enabled: !f.endsWith('.disabled'),
      icon: null,
    });
  }
  // Drop manifest entries whose files were deleted by hand.
  const present = new Set(files);
  const mods = manifest.mods.filter((m) => present.has(m.file) || present.has(`${m.file}.disabled`));
  for (const m of mods) m.enabled = present.has(m.file);
  return [...mods, ...manual];
}

async function setEnabled(profile, file, enabled) {
  const on = path.join(profiles.modsDir(profile), file);
  const off = `${on}.disabled`;
  if (enabled) await fsp.rename(off, on).catch(() => {});
  else await fsp.rename(on, off).catch(() => {});
}

async function removeMod(profile, file) {
  const modsDir = profiles.modsDir(profile);
  await fsp.rm(path.join(modsDir, file), { force: true });
  await fsp.rm(path.join(modsDir, `${file}.disabled`), { force: true });
  const manifest = await loadManifest(profile);
  manifest.mods = manifest.mods.filter((m) => m.file !== file);
  await saveManifest(profile, manifest);
}

// ---------------------------------------------------------------------------
// Moving mods to another profile / Minecraft version
// ---------------------------------------------------------------------------

/** Modrinth project ids of jars that did not come from Modrinth, e.g. dropped into the mods folder by hand (found by file hash). */
async function identifyLocalJars(profile, local) {
  const byHash = {};
  for (const m of local) {
    const base = path.join(profiles.modsDir(profile), m.file);
    const file = m.enabled ? base : `${base}.disabled`;
    try {
      byHash[await sha1File(file)] = m;
    } catch {
      /* unreadable jar */
    }
  }
  const found = new Map();
  if (!Object.keys(byHash).length) return found;
  try {
    const res = await fetchJson(`${MODRINTH}/version_files`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hashes: Object.keys(byHash), algorithm: 'sha1' }),
    });
    for (const [hash, version] of Object.entries(res)) found.set(byHash[hash].file, version.project_id);
  } catch {
    /* offline: these stay unknown */
  }
  return found;
}

/**
 * Checks which mods of profile `from` exist for the Minecraft version of profile `to`. Mods pulled
 * in as dependencies are left out; installing the others brings them along again.
 * Status per mod: available | installed (already in `to`) | missing | unknown (not on Modrinth).
 */
async function migrationPlan({ from, to }) {
  const mods = (await listInstalled(from)).filter((m) => !m.dependency);
  const target = await listInstalled(to);
  const local = await identifyLocalJars(from, mods.filter((m) => m.source !== 'modrinth'));
  const items = [];
  await Promise.all(
    mods.map(async (m) => {
      const item = { name: m.name, icon: m.icon || null, file: m.file, enabled: m.enabled !== false, source: m.source, projectId: m.projectId, version: m.version || '' };
      if (m.source !== 'modrinth') {
        if (local.has(m.file)) Object.assign(item, { source: 'modrinth', projectId: local.get(m.file) });
        else return items.push({ ...item, status: 'unknown' });
      }
      if (target.some((t) => t.source === item.source && String(t.projectId) === String(item.projectId))) return items.push({ ...item, status: 'installed' });
      try {
        if (!item.icon) {
          const [project] = await modrinthProjects([item.projectId]).catch(() => []);
          if (project) Object.assign(item, { name: project.title, icon: project.icon_url || null });
        }
        // resolve the dependencies too: a mod is only usable if they exist for that version as well
        try {
          const resolved = await modrinthResolve(item.projectId, to.mcVersion);
          items.push({ ...item, status: 'available', targetVersion: resolved[0].versionNumber });
        } catch (e) {
          let missingDependency = null;
          if (e.projectId && e.projectId !== item.projectId) {
            const [dep] = await modrinthProjects([e.projectId]).catch(() => []);
            missingDependency = dep ? dep.title : 'a dependency';
          }
          items.push({ ...item, status: 'missing', missingDependency });
        }
      } catch {
        items.push({ ...item, status: 'missing' });
      }
    }),
  );
  items.sort((a, b) => a.name.localeCompare(b.name));
  return { from: from.id, to: to.id, items };
}

/** Installs the given mods (from a migration plan) into profile `to`; disabled mods stay disabled. */
async function migrate({ to, items, onProgress = () => {} }) {
  const done = [];
  const failed = [];
  for (const [i, item] of items.entries()) {
    onProgress({ stage: `Moving ${item.name}`, index: i, total: items.length });
    try {
      const records = await installMod({ id: item.projectId, profile: to });
      const main = records.find((r) => String(r.projectId) === String(item.projectId));
      if (main && !item.enabled) await setEnabled(to, main.file, false);
      done.push(item.name);
    } catch (e) {
      if (e.code === 'ALREADY_CORE') done.push(item.name);
      else failed.push({ name: item.name, error: e.message });
    }
  }
  return { done, failed };
}

async function checkUpdates({ profile }) {
  const mcVersion = profile.mcVersion;
  const mods = (await loadManifest(profile)).mods;
  const updates = [];
  for (const m of mods) {
    try {
      if (m.source === 'modrinth') {
        const v = await modrinthBestVersion(m.projectId, mcVersion);
        if (v && v.id !== m.versionId) updates.push({ ...m, latest: v.version_number });
      }
    } catch {
      /* skip mods that cannot be checked */
    }
  }
  return updates;
}

// ---------------------------------------------------------------------------
// Core mods (performance pack + integrations)
// ---------------------------------------------------------------------------

async function sameContent(a, b) {
  try {
    const [sa, sb] = await Promise.all([fsp.stat(a), fsp.stat(b)]);
    return sa.size === sb.size && (await sha1File(a)) === (await sha1File(b));
  } catch {
    return false;
  }
}

async function syncCoreMods({ mcVersion, settings, bundledLuminaJar, onProgress = () => {} }) {
  // every Minecraft version has its own folder, so switching profiles never re-downloads the pack
  const dir = profiles.coreModsDir(mcVersion);
  await fsp.mkdir(dir, { recursive: true });
  const stateFile = path.join(paths.data, mcVersion === profiles.VERSIONS[0] ? 'core-mods.json' : `core-mods-${mcVersion}.json`);
  let state = await readJson(stateFile, { mcVersion, mods: {}, checkedAt: 0, plan: null });
  const wanted = CORE_MODS.filter((m) => m.required || (m.setting && settings[m.setting]));
  const wantedKey = wanted.map((m) => m.project).sort().join(',');
  const recheck = state.mcVersion !== mcVersion || state.wantedKey !== wantedKey || !state.plan || Date.now() - state.checkedAt > 6 * 3600 * 1000;

  // Resolve the full set of projects including required dependencies (e.g. Cloth Config).
  let plan = state.plan;
  if (recheck) {
    try {
      onProgress({ stage: 'Checking Lumina mods', progress: 0.9 });
      const seen = new Set();
      const entries = [];
      for (const mod of wanted) {
        try {
          for (const e of await modrinthResolve(mod.project, mcVersion, seen)) {
            entries.push({ project: e.projectId, versionId: e.versionId, version: e.versionNumber, file: e.file.filename, url: e.file.url, sha1: e.file.hashes && e.file.hashes.sha1, name: mod.project === e.projectId ? mod.name : null });
          }
        } catch (err) {
          if (mod.required && !(state.plan || []).some((p) => p.project === mod.project)) throw new Error(`${mod.name} could not be downloaded: ${err.message}`);
        }
      }
      plan = entries;
      state = { ...state, mcVersion, wantedKey, plan, checkedAt: Date.now() };
    } catch (e) {
      // Offline: fall back to the last known plan if there is one.
      if (!state.plan) throw e;
      plan = state.plan;
    }
  }

  const keep = new Set();
  for (const [i, entry] of plan.entries()) {
    const dest = path.join(dir, entry.file);
    const have = await fsp.stat(dest).then(() => true, () => false);
    if (!have) {
      onProgress({ stage: `Downloading ${entry.name || entry.file}`, progress: 0.9 + 0.08 * (i / plan.length) });
      await downloadFile(entry.url, dest, { sha1: entry.sha1 });
    }
    keep.add(entry.file);
  }

  if (bundledLuminaJar) {
    const dest = path.join(dir, 'lumina-client.jar');
    // only when it changed: a game that is already running holds the file open
    if (!(await sameContent(bundledLuminaJar, dest))) await fsp.copyFile(bundledLuminaJar, dest);
    keep.add('lumina-client.jar');
  }

  // Remove core mods that are no longer wanted (e.g. shaders turned off). One a running game
  // still holds stays on disk until next time; it is no longer loaded either way.
  for (const f of await fsp.readdir(dir)) {
    if (!keep.has(f)) await fsp.rm(path.join(dir, f), { force: true }).catch(() => {});
  }
  delete state.mods;
  await writeJson(stateFile, state);
  return [...keep].map((f) => path.join(dir, f));
}

/**
 * Removes core mods that the user also installed manually so Fabric doesn't refuse to start
 * because of duplicate mod ids. The user's copy wins.
 */
async function resolveDuplicateCoreMods(coreFiles, profile) {
  const user = await listInstalled(profile);
  const userIds = new Set(user.filter((m) => m.enabled && m.modId).map((m) => m.modId));
  const result = [];
  for (const f of coreFiles) {
    let id = null;
    try {
      id = (await readFabricModJson(f)).id;
    } catch {
      /* ignore */
    }
    if (id && userIds.has(id) && id !== 'lumina') continue;
    result.push(f);
  }
  return result;
}

module.exports = {
  CORE_MODS,
  modrinthSearch,
  modrinthBestVersion,
  installMod,
  listInstalled,
  setEnabled,
  removeMod,
  checkUpdates,
  migrationPlan,
  migrate,
  syncCoreMods,
  resolveDuplicateCoreMods,
  fetchWithRetry,
};
