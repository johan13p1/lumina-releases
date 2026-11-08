'use strict';

const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const { Readable } = require('stream');
const { pipeline } = require('stream/promises');

// Modrinth and the other APIs ask every client to say who it is and where to find it
const { version } = require('../../../package.json');
const USER_AGENT = `LuminaClient/${version} (+https://github.com/johan13p1/lumina-releases)`;

async function fetchWithRetry(url, options = {}, retries = 3) {
  let lastError;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      const res = await fetch(url, {
        ...options,
        headers: { 'User-Agent': USER_AGENT, ...(options.headers || {}) },
        signal: options.signal || AbortSignal.timeout(options.timeout || 60000),
      });
      if (!res.ok) {
        const err = new Error(`HTTP ${res.status} for ${url}`);
        err.status = res.status;
        // Client errors will not get better by retrying.
        if (res.status >= 400 && res.status < 500 && res.status !== 429) throw err;
        throw Object.assign(err, { retryable: true });
      }
      return res;
    } catch (e) {
      lastError = e;
      if (e.status && !e.retryable) throw e;
      if (attempt < retries) await new Promise((r) => setTimeout(r, 500 * (attempt + 1) ** 2));
    }
  }
  throw lastError;
}

async function fetchJson(url, options) {
  const res = await fetchWithRetry(url, options);
  return res.json();
}

async function sha1File(file) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha1');
    fs.createReadStream(file)
      .on('data', (d) => hash.update(d))
      .on('end', () => resolve(hash.digest('hex')))
      .on('error', reject);
  });
}

async function fileOk(file, sha1, size) {
  try {
    const st = await fsp.stat(file);
    if (!st.isFile()) return false;
    if (size != null && st.size !== size) return false;
    if (sha1) return (await sha1File(file)) === sha1.toLowerCase();
    return st.size > 0;
  } catch {
    return false;
  }
}

/**
 * Downloads a single file atomically (tmp file + rename) and verifies its sha1 when given.
 */
async function downloadFile(url, dest, { sha1, size, onBytes, headers } = {}) {
  if (await fileOk(dest, sha1, size)) return false;
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  const tmp = `${dest}.${process.pid}.${Math.random().toString(36).slice(2)}.part`;
  let lastError;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetchWithRetry(url, { headers, timeout: 120000 }, 1);
      const body = Readable.fromWeb(res.body);
      if (onBytes) body.on('data', (chunk) => onBytes(chunk.length));
      await pipeline(body, fs.createWriteStream(tmp));
      if (sha1) {
        const actual = await sha1File(tmp);
        if (actual !== sha1.toLowerCase()) throw new Error(`Checksum mismatch for ${path.basename(dest)}`);
      }
      await fsp.rename(tmp, dest);
      return true;
    } catch (e) {
      lastError = e;
      await fsp.rm(tmp, { force: true }).catch(() => {});
    }
  }
  throw lastError;
}

/**
 * Downloads many files with limited concurrency. Reports progress as (doneCount, total, bytes).
 */
async function downloadAll(items, { concurrency = 16, onProgress } = {}) {
  let index = 0;
  let done = 0;
  let bytes = 0;
  const total = items.length;
  const errors = [];
  const report = () => onProgress && onProgress({ done, total, bytes });

  async function worker() {
    while (index < items.length) {
      const item = items[index++];
      try {
        await downloadFile(item.url, item.path, {
          sha1: item.sha1,
          size: item.size,
          onBytes: (n) => {
            bytes += n;
          },
        });
        if (item.executable) await fsp.chmod(item.path, 0o755).catch(() => {});
      } catch (e) {
        errors.push({ item, error: e });
      }
      done++;
      report();
    }
  }

  const timer = setInterval(report, 250);
  try {
    await Promise.all(Array.from({ length: Math.min(concurrency, items.length || 1) }, worker));
  } finally {
    clearInterval(timer);
  }
  report();
  if (errors.length) {
    const first = errors[0];
    throw new Error(`${errors.length} download(s) failed, e.g. ${path.basename(first.item.path)}: ${first.error.message}`);
  }
}

module.exports = { USER_AGENT, fetchWithRetry, fetchJson, downloadFile, downloadAll, sha1File, fileOk };
