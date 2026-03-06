import { spawn } from "node:child_process";
import process from "node:process";

const host = process.env.PLAYWRIGHT_HOST || "127.0.0.1";
const port = process.env.PLAYWRIGHT_BASE_PORT || "4173";
const baseUrl = `http://${host}:${port}`;
const viteArgs = ["run", "dev", "--", "--host", host, "--port", String(port)];
const playwrightArgs = ["playwright", "test", ...process.argv.slice(2)];

let resolved = false;
let finished = false;
let serverExitedEarly = false;

const server = spawn("npm", viteArgs, {
  cwd: process.cwd(),
  env: {
    ...process.env,
    BROWSER: "none"
  },
  stdio: ["ignore", "pipe", "pipe"]
});

const cleanup = () => {
  if (!server.killed) {
    server.kill("SIGTERM");
  }
};

const waitForServer = new Promise((resolve, reject) => {
  const onData = (chunk) => {
    const text = chunk.toString();
    process.stdout.write(text);
    if (!resolved && text.includes(baseUrl)) {
      resolved = true;
      resolve();
    }
  };

  const onErrorData = (chunk) => {
    const text = chunk.toString();
    process.stderr.write(text);
  };

  server.stdout.on("data", onData);
  server.stderr.on("data", onErrorData);

  server.once("error", (error) => {
    reject(error);
  });

  server.once("exit", (code, signal) => {
    if (finished) {
      return;
    }
    serverExitedEarly = true;
    reject(new Error(`Vite server exited before tests started (code=${code}, signal=${signal})`));
  });

  setTimeout(() => {
    if (!resolved) {
      reject(new Error(`Timed out waiting for Vite to start at ${baseUrl}`));
    }
  }, 120_000);
});

async function run() {
  try {
    await waitForServer;
    if (serverExitedEarly) {
      process.exitCode = 1;
      return;
    }

    const testProcess = spawn("npx", playwrightArgs, {
      cwd: process.cwd(),
      env: process.env,
      stdio: "inherit"
    });

    const exitCode = await new Promise((resolve, reject) => {
      testProcess.once("error", reject);
      testProcess.once("exit", (code, signal) => {
        if (signal) {
          resolve(1);
          return;
        }
        resolve(code ?? 1);
      });
    });

    process.exitCode = exitCode;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    process.stderr.write(`${message}\n`);
    process.exitCode = 1;
  } finally {
    finished = true;
    cleanup();
  }
}

process.on("SIGINT", () => {
  cleanup();
  process.exit(130);
});

process.on("SIGTERM", () => {
  cleanup();
  process.exit(143);
});

await run();
