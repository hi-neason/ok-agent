const TOKEN_KEY = "ok-agent.access-token";
export const AUTH_UNAUTHORIZED_EVENT = "ok-agent:unauthorized";

let installed = false;

type ApiEnvelope = {
  success: boolean;
  code: string;
  message: string;
  data: unknown;
};

function isApiEnvelope(value: unknown): value is ApiEnvelope {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<ApiEnvelope>;
  return typeof candidate.success === "boolean"
    && typeof candidate.code === "string"
    && typeof candidate.message === "string"
    && "data" in candidate;
}

async function unwrapSuccessfulApiResponse(response: Response): Promise<Response> {
  if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) {
    return response;
  }
  let envelope: unknown;
  try {
    envelope = await response.clone().json();
  } catch {
    return response;
  }
  if (!isApiEnvelope(envelope) || !envelope.success) return response;
  return new Response(JSON.stringify(envelope.data), {
    status: response.status,
    statusText: response.statusText,
    headers: response.headers,
  });
}

export function getAccessToken(): string | null {
  return window.sessionStorage.getItem(TOKEN_KEY);
}

export function storeAccessToken(token: string): void {
  window.sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearAccessToken(): void {
  window.sessionStorage.removeItem(TOKEN_KEY);
}

export function installAuthenticatedFetch(): void {
  if (installed) return;
  installed = true;
  const nativeFetch = window.fetch.bind(window);

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const requestUrl = input instanceof Request ? input.url : String(input);
    const url = new URL(requestUrl, window.location.origin);
    const isApiRequest =
      url.origin === window.location.origin && url.pathname.startsWith("/api/v1/");
    const headers = new Headers(input instanceof Request ? input.headers : undefined);
    if (init?.headers) {
      new Headers(init.headers).forEach((value, key) => headers.set(key, value));
    }

    const token = getAccessToken();
    if (isApiRequest && token) headers.set("Authorization", `Bearer ${token}`);
    const response = await nativeFetch(input, { ...init, headers });
    if (
      isApiRequest &&
      response.status === 401 &&
      url.pathname !== "/api/v1/auth/login"
    ) {
      clearAccessToken();
      window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT));
    }
    return isApiRequest ? unwrapSuccessfulApiResponse(response) : response;
  };
}
