/**
 * Thin HTTP client for the ERP backend, used by the MCP server to invoke ERP REST endpoints on
 * behalf of an MCP tool call.
 *
 * Auth: logs in once (POST {base}/api/auth/login with {username,password}, per
 * frontend/openapi/openapi.json's LoginRequest/TokenResponse schemas) and caches the resulting
 * accessToken in memory. The backend issues short-lived (15 min) access tokens backed by a
 * refresh cookie; rather than simulate the cookie-based refresh flow, this client simply re-logs
 * in and retries once whenever a call comes back 401.
 *
 * Login is single-flight: concurrent callers (the initial lazy login, or several requests that
 * all hit a 401 at once) share one in-flight login promise instead of each firing their own
 * POST /api/auth/login, so a burst of concurrent tool calls doesn't hammer the login endpoint or
 * race to overwrite accessToken with a stale response.
 */

export interface ErpClientConfig {
  baseUrl: string;
  username: string;
  password: string;
}

export interface ErpRequest {
  method: string;
  path: string;
  query?: Record<string, unknown>;
  body?: unknown;
}

export interface ErpErrorBody {
  status?: number;
  title?: string;
  detail?: string;
  [key: string]: unknown;
}

/** Thrown when the ERP backend returns a non-2xx response (after the 401-retry has been tried). */
export class ErpHttpError extends Error {
  readonly status: number;
  readonly body: ErpErrorBody | string | undefined;

  constructor(status: number, body: ErpErrorBody | string | undefined) {
    super(`ERP request failed with status ${status}`);
    this.name = "ErpHttpError";
    this.status = status;
    this.body = body;
  }
}

export class ErpClient {
  private readonly baseUrl: string;
  private readonly username: string;
  private readonly password: string;
  private accessToken: string | undefined;
  // Shared in-flight login promise. While set, every caller that needs a (re-)login awaits this
  // same promise instead of starting a new POST /api/auth/login; cleared once the login settles
  // so the next expiry triggers a fresh login rather than reusing a resolved promise forever.
  private loginPromise: Promise<void> | undefined;

  constructor(config: ErpClientConfig) {
    // Strip a trailing slash so path joining below never produces a double slash.
    this.baseUrl = config.baseUrl.replace(/\/+$/, "");
    this.username = config.username;
    this.password = config.password;
  }

  /**
   * Logs in and caches the access token, replacing any previously cached token. Single-flight:
   * if a login is already in progress, callers await that same promise instead of firing a
   * second POST /api/auth/login.
   */
  private login(): Promise<void> {
    if (!this.loginPromise) {
      this.loginPromise = this.doLogin().finally(() => {
        this.loginPromise = undefined;
      });
    }
    return this.loginPromise;
  }

  private async doLogin(): Promise<void> {
    const response = await fetch(`${this.baseUrl}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: this.username, password: this.password }),
    });
    if (!response.ok) {
      const body = await readErrorBody(response);
      throw new ErpHttpError(response.status, body);
    }
    const token = (await response.json()) as { accessToken?: string };
    if (!token.accessToken) {
      throw new Error("login response did not include an accessToken");
    }
    this.accessToken = token.accessToken;
  }

  /**
   * Sends one ERP request, mapping query values with encodeURIComponent and serializing body as
   * JSON. Logs in lazily on first use, and once more (with a single retry) if a call comes back
   * 401 — the cached token may have expired.
   */
  async request(req: ErpRequest): Promise<unknown> {
    if (!this.accessToken) {
      await this.login();
    }
    let response = await this.send(req, this.accessToken!);
    if (response.status === 401) {
      await this.login();
      response = await this.send(req, this.accessToken!);
    }
    if (!response.ok) {
      const body = await readErrorBody(response);
      throw new ErpHttpError(response.status, body);
    }
    if (response.status === 204) {
      return undefined;
    }
    const text = await response.text();
    if (text.length === 0) {
      return undefined;
    }
    return JSON.parse(text);
  }

  private send(req: ErpRequest, token: string): Promise<Response> {
    let path = req.path;
    if (req.query && Object.keys(req.query).length > 0) {
      const parts: string[] = [];
      for (const [key, value] of Object.entries(req.query)) {
        if (value === undefined || value === null) {
          continue;
        }
        parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
      }
      if (parts.length > 0) {
        path = `${path}?${parts.join("&")}`;
      }
    }
    const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
    let body: string | undefined;
    if (req.body !== undefined) {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify(req.body);
    }
    return fetch(`${this.baseUrl}${path}`, { method: req.method, headers, body });
  }
}

async function readErrorBody(response: Response): Promise<ErpErrorBody | string | undefined> {
  const text = await response.text();
  if (text.length === 0) {
    return undefined;
  }
  try {
    return JSON.parse(text) as ErpErrorBody;
  } catch {
    return text;
  }
}

/** Builds an {@link ErpClient} from the ERP_BASE_URL / ERP_USERNAME / ERP_PASSWORD env vars. */
export function erpClientFromEnv(env: NodeJS.ProcessEnv = process.env): ErpClient {
  const baseUrl = env.ERP_BASE_URL;
  const username = env.ERP_USERNAME;
  const password = env.ERP_PASSWORD;
  if (!baseUrl || !username || !password) {
    throw new Error(
      "ERP_BASE_URL, ERP_USERNAME and ERP_PASSWORD environment variables are required to start the MCP server",
    );
  }
  return new ErpClient({ baseUrl, username, password });
}
