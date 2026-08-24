const TOKEN_KEY = "ok-agent.access-token";
export const AUTH_UNAUTHORIZED_EVENT = "ok-agent:unauthorized";

let installed = false;

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
    return response;
  };
}
