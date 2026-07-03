import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";

import { buildServer } from "../src/index.js";
import { ErpClient } from "../src/erpClient.js";
import type { ToolSpec } from "../src/manifest.js";

const readTool: ToolSpec = {
  name: "search_items",
  kind: "read",
  method: "GET",
  pathTemplate: "/api/masterdata/items",
  description: "List items",
  inputSchema: { type: "object", properties: {}, required: [] },
  requiredRoles: [],
};

const writeTool: ToolSpec = {
  name: "create_sales_order",
  kind: "write",
  method: "POST",
  pathTemplate: "/api/sales/sales-orders",
  description: "Create a sales order",
  inputSchema: {
    type: "object",
    properties: { partnerId: { type: "integer" } },
    required: ["partnerId"],
  },
  requiredRoles: ["SALES"],
};

async function connectedClient(server: ReturnType<typeof buildServer>) {
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: "test-client", version: "0.0.1" }, { capabilities: {} });
  await Promise.all([client.connect(clientTransport), server.connect(serverTransport)]);
  return client;
}

describe("MCP server", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("lists tools from the manifest with correct annotations per kind", async () => {
    const erpClient = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const server = buildServer(erpClient, [readTool, writeTool]);
    const client = await connectedClient(server);

    const { tools } = await client.listTools();

    expect(tools).toHaveLength(2);
    const read = tools.find((t) => t.name === "search_items")!;
    expect(read.annotations).toEqual({ readOnlyHint: true });
    expect(read.inputSchema).toEqual(readTool.inputSchema);

    const write = tools.find((t) => t.name === "create_sales_order")!;
    expect(write.annotations).toEqual({ readOnlyHint: false, destructiveHint: false, idempotentHint: false });
    expect(write.inputSchema).toEqual(writeTool.inputSchema);
  });

  it("calls a GET tool and returns JSON text content", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ accessToken: "tok" }), { status: 200, headers: { "Content-Type": "application/json" } }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify([{ id: 1, sku: "SKU-1" }]), { status: 200, headers: { "Content-Type": "application/json" } }),
      );

    const erpClient = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const server = buildServer(erpClient, [readTool]);
    const client = await connectedClient(server);

    const result = await client.callTool({ name: "search_items", arguments: {} });

    expect(result.isError).toBeFalsy();
    const content = result.content as Array<{ type: string; text: string }>;
    expect(JSON.parse(content[0].text)).toEqual([{ id: 1, sku: "SKU-1" }]);
  });

  it("returns isError with problem detail text on an HTTP 4xx", async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>;
    fetchMock
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ accessToken: "tok" }), { status: 200, headers: { "Content-Type": "application/json" } }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ status: 403, title: "Forbidden", detail: "missing role SALES" }), {
          status: 403,
          headers: { "Content-Type": "application/problem+json" },
        }),
      );

    const erpClient = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const server = buildServer(erpClient, [writeTool]);
    const client = await connectedClient(server);

    const result = await client.callTool({ name: "create_sales_order", arguments: { partnerId: 1 } });

    expect(result.isError).toBe(true);
    const content = result.content as Array<{ type: string; text: string }>;
    expect(content[0].text).toContain("403");
    expect(content[0].text).toContain("missing role SALES");
  });

  it("returns isError for an unknown tool name", async () => {
    const erpClient = new ErpClient({ baseUrl: "http://localhost:8081", username: "a", password: "b" });
    const server = buildServer(erpClient, [readTool]);
    const client = await connectedClient(server);

    const result = await client.callTool({ name: "does_not_exist", arguments: {} });

    expect(result.isError).toBe(true);
  });
});
