import { AppSettings } from "@/lib/settings/types";

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

export class ApiClient {
  private readonly settings: AppSettings;

  constructor(settings: AppSettings) {
    this.settings = settings;
  }

  get<T>(path: string): Promise<T> {
    return this.request<T>("GET", path);
  }

  post<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("POST", path, body);
  }

  put<T>(path: string, body?: unknown): Promise<T> {
    return this.request<T>("PUT", path, body);
  }

  delete(path: string): Promise<void> {
    return this.request<void>("DELETE", path);
  }

  private async request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
    const base = this.settings.apiBaseUrl.replace(/\/$/, "");
    const url = `${base}${path.startsWith("/") ? path : `/${path}`}`;

    const headers: HeadersInit = {
      "Content-Type": "application/json"
    };

    if (this.settings.apiToken) {
      headers.Authorization = `Bearer ${this.settings.apiToken}`;
      headers["X-Service-Token"] = this.settings.apiToken;
    }

    if (this.settings.companyId) {
      headers["X-Company-Id"] = this.settings.companyId;
    }

    const response = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
      cache: "no-store"
    });

    if (!response.ok) {
      const text = await response.text();
      let parsed: unknown = text;
      try {
        parsed = text ? JSON.parse(text) : undefined;
      } catch {
        parsed = text;
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
  }
}
