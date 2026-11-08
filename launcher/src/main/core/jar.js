'use strict';

const yauzl = require('yauzl');

/** Reads a single entry from a zip/jar file into a Buffer (or null if missing). */
function readEntry(file, entryName) {
  return new Promise((resolve, reject) => {
    yauzl.open(file, { lazyEntries: true, autoClose: true }, (err, zip) => {
      if (err) return reject(err);
      let found = false;
      zip.on('entry', (entry) => {
        if (entry.fileName !== entryName) return zip.readEntry();
        found = true;
        zip.openReadStream(entry, (e, stream) => {
          if (e) return reject(e);
          const chunks = [];
          stream.on('data', (c) => chunks.push(c));
          stream.on('end', () => {
            zip.close();
            resolve(Buffer.concat(chunks));
          });
          stream.on('error', reject);
        });
      });
      zip.on('end', () => {
        if (!found) resolve(null);
      });
      zip.on('error', reject);
      zip.readEntry();
    });
  });
}

/** Parses fabric.mod.json of a mod jar. Fabric allows a few JSON liberties, so be forgiving. */
async function readFabricModJson(file) {
  const buf = await readEntry(file, 'fabric.mod.json');
  if (!buf) return null;
  const text = buf.toString('utf8').replace(/^﻿/, '');
  try {
    return JSON.parse(text);
  } catch {
    // strip control characters inside strings (seen in the wild)
    return JSON.parse(text.replace(/[\u0000-\u001F]+/g, ' '));
  }
}

/** Reads every file below a folder of a zip/jar (e.g. "assets/lumina/capes/") into a Map name -> Buffer. */
function readFolder(file, prefix) {
  return new Promise((resolve, reject) => {
    yauzl.open(file, { lazyEntries: true, autoClose: true }, (err, zip) => {
      if (err) return reject(err);
      const files = new Map();
      zip.on('entry', (entry) => {
        if (!entry.fileName.startsWith(prefix) || entry.fileName.endsWith('/')) return zip.readEntry();
        zip.openReadStream(entry, (e, stream) => {
          if (e) return reject(e);
          const chunks = [];
          stream.on('data', (c) => chunks.push(c));
          stream.on('end', () => {
            files.set(entry.fileName.slice(prefix.length), Buffer.concat(chunks));
            zip.readEntry();
          });
          stream.on('error', reject);
        });
      });
      zip.on('end', () => resolve(files));
      zip.on('error', reject);
      zip.readEntry();
    });
  });
}

module.exports = { readEntry, readFolder, readFabricModJson };
