import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ErpClient, ErpHttpError } from "../src/erpClient.js";

describe("ErpClient", () => {
  const baseUrl = "http://localhost:8081";

  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  it("logs in once and sends the access token as a Bearer header", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok-1", username: "admin", roles: ["ADMIN"] }))
      .mockResolvedValueOnce(jsonResponse(200, { items: [] }));

    const client = new ErpClient({ baseUrl, username: "admin", password: "admin" });
    const result = await client.request({ method: "GET", path: "/api/masterdata/items" });

    expect(result).toEqual({ items: [] });
    expect(fetchMock).toHaveBeenCalledTimes(2);

    const [loginUrl, loginInit] = fetchMock.mock.calls[0];
    expect(loginUrl).toBe(`${baseUrl}/api/auth/login`);
    expect(loginInit.method).toBe("POST");
    expect(JSON.parse(loginInit.body)).toEqual({ username: "admin", password: "admin" });

    const [dataUrl, dataInit] = fetchMock.mock.calls[1];
    expect(dataUrl).toBe(`${baseUrl}/api/masterdata/items`);
    expect(dataInit.headers.Authorization).toBe("Bearer tok-1");
  });

  it("re-logs in and retries once when a call returns 401", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok-1" })) // initial login
      .mockResolvedValueOnce(new Response("", { status: 401 })) // expired token
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok-2" })) // re-login
      .mockResolvedValueOnce(jsonResponse(200, { ok: true })); // retried call succeeds

    const client = new ErpClient({ baseUrl, username: "admin", password: "admin" });
    const result = await client.request({ method: "GET", path: "/api/masterdata/items" });

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(4);
    const lastCallInit = fetchMock.mock.calls[3][1];
    expect(lastCallInit.headers.Authorization).toBe("Bearer tok-2");
  });

  it("URL-encodes query parameter values for GET requests", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok-1" }))
      .mockResolvedValueOnce(jsonResponse(200, { rows: [] }));

    const client = new ErpClient({ baseUrl, username: "admin", password: "admin" });
    await client.request({
      method: "GET",
      path: "/api/sales/ar-aging",
      query: { asOf: "2026/07/03", note: "a b&c" },
    });

    const [url] = fetchMock.mock.calls[1];
    expect(url).toBe(`${baseUrl}/api/sales/ar-aging?asOf=2026%2F07%2F03&note=a%20b%26c`);
  });

  it("sends the remaining input as the JSON body root for POST requests", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok-1" }))
      .mockResolvedValueOnce(jsonResponse(200, { id: 42 }));

    const client = new ErpClient({ baseUrl, username: "admin", password: "admin" });
    const body = { partnerId: 1, orderDate: "2026-07-03", lines: [{ itemId: 2, qtyOrdered: 3, unitPrice: 9.5 }] };
    const result = await client.request({ method: "POST", path: "/api/sales/sales-orders", body });

    expect(result).toEqual({ id: 42 });
    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe(`${baseUrl}/api/sales/sales-orders`);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual(body);
  });

  it("throws ErpHttpError with the problem+json body on a non-2xx response", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok-1" }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ status: 400, title: "Bad Request", detail: "invalid partnerId" }), {
          status: 400,
          headers: { "Content-Type": "application/problem+json" },
        }),
      );

    const client = new ErpClient({ baseUrl, username: "admin", password: "admin" });
    await expect(client.request({ method: "POST", path: "/api/sales/sales-orders", body: {} })).rejects.toMatchObject(
      {
        status: 400,
        body: { title: "Bad Request", detail: "invalid partnerId" },
      },
    );
  });

  it("shares one in-flight login across concurrent requests (single-flight)", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    // Resolve the login fetch only after both concurrent requests have had a chance to call
    // fetch, so we can assert only one login POST was made even though two requests raced in.
    let resolveLogin!: (response: Response) => void;
    const loginResponse = new Promise<Response>((resolve) => {
      resolveLogin = resolve;
    });
    fetchMock.mockImplementationOnce(() => loginResponse);
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { a: 1 }))
      .mockResolvedValueOnce(jsonResponse(200, { b: 2 }));

    const client = new ErpClient({ baseUrl, username: "admin", password: "admin" });
    const p1 = client.request({ method: "GET", path: "/api/one" });
    const p2 = client.request({ method: "GET", path: "/api/two" });

    // Let both requests observe the missing accessToken and call login() before it resolves.
    await Promise.resolve();
    await Promise.resolve();
    resolveLogin(jsonResponse(200, { accessToken: "tok-1" }));

    const [r1, r2] = await Promise.all([p1, p2]);
    expect(r1).toEqual({ a: 1 });
    expect(r2).toEqual({ b: 2 });

    const loginCalls = fetchMock.mock.calls.filter(([url]) => url === `${baseUrl}/api/auth/login`);
    expect(loginCalls).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledTimes(3); // 1 login + 2 data calls
  });

  it("re-throws ErpHttpError as instanceof for callers to branch on", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok-1" }))
      .mockResolvedValueOnce(new Response("not found", { status: 404 }));

    const client = new ErpClient({ baseUrl, username: "admin", password: "admin" });
    try {
      await client.request({ method: "GET", path: "/api/nope" });
      expect.unreachable("expected request to throw");
    } catch (err) {
      expect(err).toBeInstanceOf(ErpHttpError);
      expect((err as ErpHttpError).status).toBe(404);
    }
  });
});
