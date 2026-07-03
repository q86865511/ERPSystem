import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { invokeTool } from "../src/index.js";
import { ErpClient } from "../src/erpClient.js";
import type { ToolSpec } from "../src/manifest.js";

describe("invokeTool", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
  }

  it("substitutes path template variables and sends remaining fields as query for GET", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok" }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));

    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const tool: ToolSpec = {
      name: "get_order",
      kind: "read",
      method: "GET",
      pathTemplate: "/api/sales/sales-orders/{orderId}",
      description: "test",
      inputSchema: { type: "object", properties: {}, required: [] },
      requiredRoles: [],
    };

    await invokeTool(client, tool, { orderId: "42", expand: "lines" });

    const [url] = fetchMock.mock.calls[1];
    expect(url).toBe("http://localhost:8081/api/sales/sales-orders/42?expand=lines");
  });

  it("substitutes path template variables and sends remaining fields as the JSON body for POST", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok" }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));

    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const tool: ToolSpec = {
      name: "add_line",
      kind: "write",
      method: "POST",
      pathTemplate: "/api/sales/sales-orders/{orderId}/lines",
      description: "test",
      inputSchema: { type: "object", properties: {}, required: [] },
      requiredRoles: [],
    };

    await invokeTool(client, tool, { orderId: "42", itemId: 7, qtyOrdered: 2 });

    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("http://localhost:8081/api/sales/sales-orders/42/lines");
    expect(JSON.parse(init.body)).toEqual({ itemId: 7, qtyOrdered: 2 });
  });

  it("throws when a required path variable is missing from the input", async () => {
    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const tool: ToolSpec = {
      name: "get_order",
      kind: "read",
      method: "GET",
      pathTemplate: "/api/sales/sales-orders/{orderId}",
      description: "test",
      inputSchema: { type: "object", properties: {}, required: [] },
      requiredRoles: [],
    };

    await expect(invokeTool(client, tool, {})).rejects.toThrow(/missing required path variable/);
  });

  const orderIdTool: ToolSpec = {
    name: "get_order",
    kind: "read",
    method: "GET",
    pathTemplate: "/api/sales/sales-orders/{orderId}",
    description: "test",
    inputSchema: { type: "object", properties: {}, required: [] },
    requiredRoles: [],
  };

  it("rejects '..' as a path variable value", async () => {
    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    await expect(invokeTool(client, orderIdTool, { orderId: ".." })).rejects.toThrow(/invalid value/);
  });

  it("rejects '.' as a path variable value", async () => {
    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    await expect(invokeTool(client, orderIdTool, { orderId: "." })).rejects.toThrow(/invalid value/);
  });

  it("rejects an empty string as a path variable value", async () => {
    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    await expect(invokeTool(client, orderIdTool, { orderId: "" })).rejects.toThrow(/invalid value/);
  });

  it("rejects a non-scalar (object) path variable value", async () => {
    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    await expect(invokeTool(client, orderIdTool, { orderId: { nested: true } })).rejects.toThrow(
      /must be a string, number or boolean/,
    );
  });

  it("URL-encodes a path variable value that contains a slash rather than letting it introduce a new path segment", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok" }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));

    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    await invokeTool(client, orderIdTool, { orderId: "a/b" });

    const [url] = fetchMock.mock.calls[1];
    expect(url).toBe("http://localhost:8081/api/sales/sales-orders/a%2Fb");
  });

  it("rejects a non-scalar (array) value bound for a GET query parameter", async () => {
    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    await expect(invokeTool(client, orderIdTool, { orderId: "42", tags: ["a", "b"] })).rejects.toThrow(
      /must be a string, number or boolean/,
    );
  });

  it("rejects a non-scalar (object) value bound for a GET query parameter", async () => {
    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    await expect(invokeTool(client, orderIdTool, { orderId: "42", filter: { active: true } })).rejects.toThrow(
      /must be a string, number or boolean/,
    );
  });

  it("passes non-scalar values (e.g. nested line items) through unchanged in a POST body", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "tok" }))
      .mockResolvedValueOnce(jsonResponse(200, { id: 1 }));

    const client = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const tool: ToolSpec = {
      name: "create_sales_order",
      kind: "write",
      method: "POST",
      pathTemplate: "/api/sales/sales-orders",
      description: "test",
      inputSchema: { type: "object", properties: {}, required: [] },
      requiredRoles: [],
    };
    const body = { partnerId: 1, lines: [{ itemId: 2, qtyOrdered: 3 }] };

    await invokeTool(client, tool, body);

    const [, init] = fetchMock.mock.calls[1];
    expect(JSON.parse(init.body)).toEqual(body);
  });
});
