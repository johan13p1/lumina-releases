'use strict';

const fsp = require('fs/promises');
const path = require('path');
const paths = require('./paths');
const { fetchJson, downloadFile, sha1File } = require('./download');
const { readJson, writeJson } = require('./minecraft');
const { modrinthBestVersion } = require('./mods');

const MODRINTH = 'https://api.modrinth.com/v2';

/** What is known about the packs in the shared folder: { shaders: { <file>: { projectId, name, icon, version } } }. */
const FILE = () => path.join(paths.data, 'shaders.json');

async function search({ query = '', mcVersion, offset = 0, limit = 21, sort = 'relevance' }) {
  const facets = JSON.stringify([['project_type:shader'], ['categories:iris'], [`versions:${mcVersion}`]]);
  const url = `${MODRINTH}/search?query=${encodeURIComponent(query)}&facets=${encodeURIComponent(facets)}&index=${sort}&offset=${offset}&limit=${limit}`;
  const res = await fetchJson(url);
  return {
    total: res.total_hits,
    hits: res.hits.map((h) => ({
      id: h.project_id,
      name: h.title,
      author: h.author,
      downloads: h.downloads,
      icon: h.icon_url || null,
      image: h.featured_gallery || (h.gallery && h.gallery[0]) || null,
      url: `https://modrinth.com/shader/${h.slug}`,
    })),
  };
}

async function install({ id, mcVersion }) {
  const version = await modrinthBestVersion(id, mcVersion, 'iris');
  if (!version) throw new Error(`No Iris version found for ${mcVersion}.`);
  const file = version.files.find((f) => f.primary) || version.files[0];
  const project = await fetchJson(`${MODRINTH}/project/${id}`);
  await fsp.mkdir(paths.shaderpacks, { recursive: true });
  await downloadFile(file.url, path.join(paths.shaderpacks, file.filename), { sha1: file.hashes && file.hashes.sha1 });
  const manifest = await readJson(FILE(), { shaders: {} });
  manifest.shaders[file.filename] = { projectId: id, name: project.title, icon: project.icon_url || null, version: version.version_number };
  await writeJson(FILE(), manifest);
  return { file: file.filename, name: project.title };
}

/** Packs that came from elsewhere get their name and icon from Modrinth if it has them (found by file hash, once). */
async function identify(files, manifest) {
  const byHash = {};
  for (const file of files) {
    try {
      byHash[await sha1File(path.join(paths.shaderpacks, file))] = file;
    } catch {
      /* a folder, or unreadable */
    }
  }
  const hashes = Object.keys(byHash);
  if (!hashes.length) return false;
  try {
    const versions = await fetchJson(`${MODRINTH}/version_files`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hashes, algorithm: 'sha1' }),
    });
    for (const hash of hashes) {
      const version = versions[hash];
      const project = version && (await fetchJson(`${MODRINTH}/project/${version.project_id}`));
      manifest.shaders[byHash[hash]] = project ? { projectId: project.id, name: project.title, icon: project.icon_url || null, version: version.version_number } : { projectId: null, name: null, icon: null, version: '' };
    }
    return true;
  } catch {
    return false; // offline: try again next time
  }
}

async function list() {
  const names = await fsp.readdir(paths.shaderpacks).catch(() => []);
  // Iris keeps the settings of each pack next to it as <pack>.txt
  const files = names.filter((f) => !f.startsWith('.') && !f.endsWith('.txt'));
  const manifest = await readJson(FILE(), { shaders: {} });
  const unknown = files.filter((f) => !manifest.shaders[f]);
  if (unknown.length && (await identify(unknown, manifest))) await writeJson(FILE(), manifest);
  return files
    .map((file) => {
      const known = manifest.shaders[file] || {};
      return { file, name: known.name || file.replace(/\.zip$/i, ''), icon: known.icon || null, projectId: known.projectId || null, version: known.version || '' };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

async function remove(file) {
  if (path.basename(file) !== file) throw new Error('Invalid file name.');
  await fsp.rm(path.join(paths.shaderpacks, file), { recursive: true, force: true });
  const manifest = await readJson(FILE(), { shaders: {} });
  delete manifest.shaders[file];
  await writeJson(FILE(), manifest);
}

module.exports = { search, install, list, remove };
