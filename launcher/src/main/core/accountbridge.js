'use strict';

const http = require('http');
const crypto = require('crypto');
const auth = require('./auth');
const skin = require('./skin');

/**
 * Lets the running game use the launcher's accounts, so switching doesn't need a restart. The
 * tokens stay in the launcher: the game asks for one when it switches and keeps it in memory.
 *
 * A small server on the loopback address, for this game session only. The address contains a random
 * secret and reaches the game as a system property (-Dlumina.accounts), next to where Minecraft's own
 * --accessToken argument already is.
 */
let server = null;
let adding = false;

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 4096) reject(new Error('Request too large'));
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch {
        reject(new Error('Invalid request'));
      }
    });
    req.on('error', reject);
  });
}

function routes(onChanged) {
  return {
    accounts: () => auth.list(),
    /** Fresh credentials for an account, which also becomes the launcher's selected one. */
    async use({ id }) {
      const credentials = await auth.credentials(id);
      onChanged(await auth.select(id));
      return credentials;
    },
    async add() {
      if (adding) throw new Error('The sign-in window is already open.');
      adding = true;
      try {
        const list = await auth.addMicrosoft(null);
        onChanged(list);
        return list;
      } finally {
        adding = false;
      }
    },
    async remove({ id }) {
      const list = await auth.remove(id);
      onChanged(list);
      return list;
    },
    /** The account's skin picture; Steve for offline accounts or when Mojang can't be reached. */
    async skin({ uuid }) {
      const current = await skin.current(uuid).catch(() => null);
      const picture = current || (await skin.fallback());
      return { data: picture ? picture.data : null };
    },
  };
}

/** Starts the server and returns the address for the game. */
function start(onChanged = () => {}) {
  stop();
  const secret = crypto.randomBytes(24).toString('hex');
  const handlers = routes(onChanged);
  const created = http.createServer(async (req, res) => {
    const reply = (status, value) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(value));
    };
    const [, key, action] = (req.url || '').split('/');
    if (req.method !== 'POST' || key !== secret || !Object.hasOwn(handlers, action)) return reply(404, { error: 'Not found' });
    try {
      reply(200, await handlers[action](await readBody(req)));
    } catch (e) {
      reply(400, { error: e.message || String(e) });
    }
  });
  return new Promise((resolve, reject) => {
    created.once('error', reject);
    created.listen(0, '127.0.0.1', () => {
      server = created;
      resolve(`http://127.0.0.1:${created.address().port}/${secret}`);
    });
  });
}

function stop() {
  if (server) server.close();
  server = null;
}

module.exports = { start, stop };
