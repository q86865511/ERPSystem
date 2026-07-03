#!/usr/bin/env node
/**
 * ERP Copilot MCP server (stdio transport). Exposes the same ERP tools the in-app assistant uses
 * (src/main/resources/assistant/tools.json) to any MCP client — Claude Desktop, Claude Code, etc.
 *
 * Design note: write tools are NOT confirmed here. The manifest itself only records each tool's
 * `kind` ("read"/"write"); this server derives the MCP `annotations` (readOnlyHint/destructiveHint/
 * idempotentHint) from that `kind` when listing tools (see buildServer below). MCP clients use
 * those annotations to drive their own confirmation UX (e.g. Claude Desktop prompts "Allow this
 * action?" before calling a non-read-only tool). Duplicating that confirmation on the server side
 * would just be a second, redundant gate with no server-side identity to confirm against, so this
 * server always executes the call the client sends and lets the client's native affordance be the
 * confirmation step.
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { pathToFileURL } from "node:url";

import { erpClientFromEnv, ErpHttpError, type ErpClient } from "./erpClient.js";
import { loadManifest, resolveManifestPath, type ToolSpec } from "./manifest.js";

export function buildServer(client: ErpClient, tools: ToolSpec[]): Server {
  const byName = new Map(tools.map((tool) => [tool.name, tool]));

  const server = new Server(
    { name: "erp-mcp-server", version: "0.1.0" },
    { capabilities: { tools: {} } },
  );

  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: tools.map((tool) => ({
      name: tool.name,
      description: tool.description,
      inputSchema: tool.inputSchema,
      // Derived from the manifest's `kind`, not a field the manifest itself carries (see file
      // header). Write tools here are all "create DRAFT" style calls (e.g. create_sales_order),
      // so destructiveHint is false — nothing existing is destroyed. idempotentHint is false
      // because replaying the same call creates a duplicate record rather than converging on the
      // same state, so MCP clients should not silently retry/replay a write call.
      annotations:
        tool.kind === "read"
          ? { readOnlyHint: true }
          : { readOnlyHint: false, destructiveHint: false, idempotentHint: false },
    })),
  }));

  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const tool = byName.get(request.params.name);
    if (!tool) {
      return {
        isError: true,
        content: [{ type: "text", text: `Unknown tool: ${request.params.name}` }],
      };
    }
    const input = (request.params.arguments ?? {}) as Record<string, unknown>;
    try {
      const result = await invokeTool(client, tool, input);
      return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
    } catch (err) {
      if (err instanceof ErpHttpError) {
        const detail = typeof err.body === "string" ? err.body : JSON.stringify(err.body, null, 2);
        return {
          isError: true,
          content: [{ type: "text", text: `ERP request failed (HTTP ${err.status}): ${detail}` }],
        };
      }
      const message = err instanceof Error ? err.message : String(err);
      return { isError: true, content: [{ type: "text", text: `ERP request failed: ${message}` }] };
    }
  });

  return server;
}

/** A value that may safely become a path segment or query parameter (never an object/array). */
type ScalarValue = string | number | boolean;

function isScalar(value: unknown): value is ScalarValue {
  return typeof value === "string" || typeof value === "number" || typeof value === "boolean";
}

/**
 * Maps a tool call's input onto an HTTP request per the manifest's mapping rules: substitute
 * `{var}` path placeholders from the input (consuming those fields), then send whatever remains
 * as query params (GET) or as the JSON body root (POST/PUT/etc).
 */
export async function invokeTool(
  client: ErpClient,
  tool: ToolSpec,
  input: Record<string, unknown>,
): Promise<unknown> {
  const remaining = { ...input };
  const path = tool.pathTemplate.replace(/\{(\w+)\}/g, (_match, varName: string) => {
    if (!(varName in remaining)) {
      throw new Error(`missing required path variable '${varName}' for tool '${tool.name}'`);
    }
    const value = remaining[varName];
    delete remaining[varName];
    if (!isScalar(value)) {
      throw new Error(
        `path variable '${varName}' for tool '${tool.name}' must be a string, number or boolean`,
      );
    }
    const text = String(value);
    // Reject values that would let a path segment escape the intended URL structure (e.g.
    // `..` walking up to a different resource, or an empty segment collapsing `//`).
    if (text.length === 0 || text === "." || text === "..") {
      throw new Error(`path variable '${varName}' for tool '${tool.name}' has an invalid value: '${text}'`);
    }
    return encodeURIComponent(text);
  });

  const isGet = tool.method === "GET";
  if (isGet) {
    // Whatever fields the path template didn't consume become query params, which are
    // string-joined onto the URL (see ErpClient.send) — guard against object/array values
    // leaking through as "[object Object]". POST/PUT/etc bodies are passed through unchanged
    // below: a write tool's body legitimately contains nested objects/arrays (e.g.
    // create_sales_order's `lines`), so this guard only applies to the query-param path.
    for (const [key, value] of Object.entries(remaining)) {
      if (value !== undefined && value !== null && !isScalar(value)) {
        throw new Error(
          `field '${key}' for tool '${tool.name}' must be a string, number or boolean (got ${Array.isArray(value) ? "array" : typeof value})`,
        );
      }
    }
  }

  return client.request({
    method: tool.method,
    path,
    query: isGet ? remaining : undefined,
    body: isGet ? undefined : remaining,
  });
}

async function main(): Promise<void> {
  const client = erpClientFromEnv();
  const tools = loadManifest(resolveManifestPath());
  const server = buildServer(client, tools);
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

// Only auto-start when run directly (e.g. `node dist/index.js`), not when imported by tests.
// Uses pathToFileURL rather than hand-building a file:// URL: on Windows a manual
// `file://${path}` is missing the third slash (e.g. `file://C:/...` instead of
// `file:///C:/...`), so it never matches import.meta.url and the server silently never starts.
const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
