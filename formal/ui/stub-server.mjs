#!/usr/bin/env node
// Local website stub for Bombadil. Serves client/ and answers the account and
// server routes the page calls on load. Microsoft login is a cookie redirect
// on this process; there are no credentials and no call to Microsoft.
// Pairing is refused so the page does not navigate to mineportal://, which
// would leave the HTTP origin Bombadil is checking. Pairing itself is modeled
// in formal/DesktopPairing.tla.

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../client");
const port = Number(process.argv[2] || process.env.PORT || 4173);

const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".cjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".png": "image/png",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
};

const sessions = new Map();

function sessionId(req) {
  const header = req.headers.cookie || "";
  const match = header.match(/(?:^|;\s*)mp=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function session(req) {
  const id = sessionId(req);
  return id && sessions.has(id) ? sessions.get(id) : null;
}

function send(res, status, body, extra = {}) {
  const payload = typeof body === "string" ? body : JSON.stringify(body);
  res.writeHead(status, {
    "content-type":
      typeof body === "string" ? "text/html; charset=utf-8" : "application/json; charset=utf-8",
    "cache-control": "no-store",
    ...extra,
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => {
      const raw = Buffer.concat(chunks).toString("utf8");
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (error) {
        reject(error);
      }
    });
    req.on("error", reject);
  });
}

function parseAddress(address) {
  const idx = address.lastIndexOf(":");
  if (idx === -1) return { host: address, port: 25565 };
  const host = address.slice(0, idx);
  const portText = address.slice(idx + 1);
  const parsed = Number(portText);
  if (!host || !Number.isInteger(parsed) || parsed <= 0 || parsed > 65535) {
    return { host: address, port: 25565 };
  }
  return { host, port: parsed };
}

function summary(server) {
  return {
    id: server.id,
    host: server.host,
    port: server.port,
    version: server.version,
    status: server.status,
    phase: server.phase,
    connected: server.connected,
  };
}

function staticFile(urlPath) {
  const requested = urlPath === "/" ? "/index.html" : urlPath;
  const file = path.normalize(path.join(root, requested));
  if (!file.startsWith(root)) return null;
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) return null;
  return file;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${port}`);
  const pathname = decodeURIComponent(url.pathname);

  if (req.method === "GET" && pathname === "/api/account") {
    const current = session(req);
    if (!current) return send(res, 200, { status: "logged-out" });
    return send(res, 200, {
      status: "logged-in",
      profile: { id: "test-player", name: "TestPlayer" },
      desktopConnected: false,
    });
  }

  if (req.method === "GET" && pathname === "/api/account/login/microsoft") {
    const id = `s${Date.now().toString(36)}`;
    sessions.set(id, { servers: [] });
    return send(res, 302, "", {
      "set-cookie": `mp=${encodeURIComponent(id)}; Path=/; HttpOnly; SameSite=Lax`,
      location: "/",
    });
  }

  if (req.method === "POST" && pathname === "/api/account/logout") {
    const id = sessionId(req);
    if (id) sessions.delete(id);
    return send(
      res,
      200,
      { ok: true },
      {
        "set-cookie": "mp=; Path=/; Max-Age=0",
      },
    );
  }

  if (req.method === "POST" && pathname === "/api/desktop/pair") {
    return send(res, 409, { error: "desktop pairing is not part of this UI run" });
  }

  if (req.method === "GET" && pathname === "/api/ping") {
    return send(res, 200, { online: false });
  }

  if (req.method === "GET" && pathname === "/api/servers/recent") {
    return send(res, 200, []);
  }

  if (req.method === "GET" && pathname === "/api/servers") {
    const current = session(req);
    return send(res, 200, current ? current.servers.map(summary) : []);
  }

  if (req.method === "POST" && pathname === "/api/servers") {
    const current = session(req);
    if (!current) {
      return send(res, 409, { error: "Log in before adding a server", code: "not-logged-in" });
    }
    let body;
    try {
      body = await readBody(req);
    } catch {
      return send(res, 400, { error: "host and version are required" });
    }
    const hostText = typeof body.host === "string" ? body.host.trim() : "";
    const version = typeof body.version === "string" ? body.version.trim() : "";
    if (!hostText || hostText.length > 253 || !version || version.length > 64) {
      return send(res, 400, { error: "host and version are required" });
    }
    const parsed = parseAddress(hostText);
    if (
      current.servers.some(
        (item) =>
          item.host.toLowerCase() === parsed.host.toLowerCase() && item.port === parsed.port,
      )
    ) {
      return send(res, 400, { error: "이미 등록된 서버 주소예요" });
    }
    const created = {
      id: `srv-${current.servers.length + 1}`,
      host: parsed.host,
      port: parsed.port,
      version,
      status: "idle",
      phase: "idle",
      connected: false,
      messages: [],
    };
    current.servers.push(created);
    return send(res, 201, summary(created));
  }

  const serverMatch = pathname.match(/^\/api\/servers\/([^/]+)(?:\/(connect|disconnect|chat))?$/);
  if (serverMatch) {
    const current = session(req);
    const id = decodeURIComponent(serverMatch[1]);
    const action = serverMatch[2];
    const found = current && current.servers.find((item) => item.id === id);
    if (!found) return send(res, 404, { error: "server not found" });
    if (req.method === "GET" && !action) {
      return send(res, 200, {
        ...summary(found),
        statusHistory: [],
        messages: found.messages,
      });
    }
    if (req.method === "POST" && action === "connect") {
      found.phase = "active";
      found.connected = false;
      found.status = "connecting";
      return send(res, 200, { ok: true });
    }
    if (req.method === "POST" && action === "disconnect") {
      found.phase = "closed";
      found.connected = false;
      found.status = "disconnected";
      return send(res, 200, { ok: true });
    }
    if (req.method === "POST" && action === "chat") {
      if (!found.connected)
        return send(res, 409, { error: "not connected", code: "not-connected" });
      let body;
      try {
        body = await readBody(req);
      } catch {
        return send(res, 400, { error: "message must contain 1 to 256 characters" });
      }
      const message = typeof body.message === "string" ? body.message : "";
      if (!message || message.length > 256) {
        return send(res, 400, { error: "message must contain 1 to 256 characters" });
      }
      found.messages.push({ username: "TestPlayer", message, timestamp: Date.now() });
      return send(res, 200, { ok: true });
    }
  }

  if (req.method !== "GET" && req.method !== "HEAD") {
    return send(res, 404, { error: "not found" });
  }

  const file = staticFile(pathname);
  if (!file) return send(res, 404, "not found");
  const type = types[path.extname(file)] || "application/octet-stream";
  if (file.endsWith(`${path.sep}index.html`) || file.endsWith("/index.html")) {
    const html = fs
      .readFileSync(file, "utf8")
      .replace(
        "<head>",
        "<head><script>window.alert=function(){};window.confirm=function(){return true;}</script>",
      );
    res.writeHead(200, { "content-type": type, "cache-control": "no-store" });
    res.end(html);
    return;
  }
  res.writeHead(200, { "content-type": type, "cache-control": "no-store" });
  if (req.method === "HEAD") return res.end();
  fs.createReadStream(file).pipe(res);
});

server.on("upgrade", (_req, socket) => {
  socket.destroy();
});

server.listen(port, "127.0.0.1", () => {
  console.log(`mineportal ui stub http://127.0.0.1:${port}`);
});
