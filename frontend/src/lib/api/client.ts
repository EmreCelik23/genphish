import type { AppSettings } from "@/lib/settings/types";

type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export class ApiError extends Error {
  status: number;
  detail?: unknown;

  constructor(status: number, message: string, detail?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.detail = detail;
  }
}

type ApiClientConfig = {
  apiBaseUrl: string;
  apiToken: string;
  companyId: string;
  onUnauthorized?: () => void;
  onForbidden?: (detail?: unknown) => void;
};

/**
 * Build config from settings + auth overrides.
 * Keeps backward compat: if you pass AppSettings it still works.
 */
export function buildApiConfig(
  settings: AppSettings,
  overrides?: { token?: string; companyId?: string; onUnauthorized?: () => void; onForbidden?: (detail?: unknown) => void }
): ApiClientConfig {
  return {
    apiBaseUrl: settings.apiBaseUrl,
    apiToken: overrides?.token ?? (settings as Record<string, string>).apiToken ?? "",
    companyId: overrides?.companyId ?? (settings as Record<string, string>).companyId ?? "",
    onUnauthorized: overrides?.onUnauthorized,
    onForbidden: overrides?.onForbidden
  };
}

export class ApiClient {
  private readonly config: ApiClientConfig;

  constructor(config: ApiClientConfig | AppSettings) {
    // Accept either shape
    if ("apiBaseUrl" in config && "apiToken" in config) {
      this.config = config as ApiClientConfig;
    } else {
      this.config = {
        apiBaseUrl: (config as AppSettings).apiBaseUrl,
        apiToken: (config as Record<string, string>).apiToken ?? "",
        companyId: (config as Record<string, string>).companyId ?? ""
      };
    }
  }

  get<T>(path: string): Promise<T> {
    return this.request<T>("GET", path);
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("POST", path, body);
  }

  postForm<T>(path: string, formData: FormData): Promise<T> {
    return this.request<T>("POST", path, formData);
  }

  put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("PUT", path, body);
  }

  delete(path: string): Promise<void> {
    return this.request<void>("DELETE", path);
  }

  private async request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
    const base = this.config.apiBaseUrl.replace(/\/$/, "");
    const url = `${base}${path.startsWith("/") ? path : `/${path}`}`;

    const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
    const headers: Record<string, string> = {};

    if (!isFormData) {
      headers["Content-Type"] = "application/json";
    }

    if (this.config.apiToken) {
      headers.Authorization = `Bearer ${this.config.apiToken}`;
      headers["X-Service-Token"] = this.config.apiToken;
    }

    if (this.config.companyId) {
      headers["X-Company-Id"] = this.config.companyId;
    }

    const fetchOpts: RequestInit = {
      method,
      headers,
      body: body ? (isFormData ? body : JSON.stringify(body)) : undefined,
      cache: "no-store"
    };

    // ── Retry logic (GET only, idempotent safe) ───────────────────────
    const maxAttempts = method === "GET" ? 3 : 1;
    const retryableStatuses = new Set([429, 502, 503, 504]);

    let lastError: unknown;
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        const response = await fetch(url, fetchOpts);

        if (!response.ok) {
          // Retry on transient server errors (GET only)
          if (method === "GET" && retryableStatuses.has(response.status) && attempt < maxAttempts) {
            await sleep(Math.pow(2, attempt - 1) * 1000); // 1s, 2s
            continue;
          }

          const text = await response.text();
          let parsed: unknown = text;
          try {
            parsed = text ? JSON.parse(text) : undefined;
          } catch {
            parsed = text;
          }

          // ── Error interceptors ─────────────────────────────────
          if (response.status === 401) {
            this.config.onUnauthorized?.();
          }
          if (response.status === 403) {
            this.config.onForbidden?.(parsed);
          }

          throw new ApiError(response.status, `Request failed (${response.status})`, parsed);
        }

        if (response.status === 204) {
          return undefined as T;
        }

        const contentType = response.headers.get("content-type") ?? "";
        if (!contentType.includes("application/json")) {
          return undefined as T;
        }

        return (await response.json()) as T;

      } catch (err) {
        // Retry on network errors (TypeError: failed to fetch) for GET
        if (method === "GET" && err instanceof TypeError && attempt < maxAttempts) {
          lastError = err;
          await sleep(Math.pow(2, attempt - 1) * 1000);
          continue;
        }
        throw err;
      }
    }

    throw lastError;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

