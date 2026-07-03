/**
 * Loads and validates the ERP Copilot tool manifest — the same single source of truth used by the
 * Spring Boot assistant backend (src/main/resources/assistant/tools.json, parsed there by
 * com.erp.assistant.application.ToolManifest). See that class's javadoc for the input -> HTTP
 * mapping rules that this MCP server implements identically:
 *   - any `{var}` in pathTemplate is substituted from the input and consumed;
 *   - GET: remaining top-level input fields become query parameters;
 *   - POST: the remaining input object is sent as the JSON request body root.
 */

import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

export type ToolKind = "read" | "write";

export interface ToolSpec {
  name: string;
  kind: ToolKind;
  method: string;
  pathTemplate: string;
  description: string;
  inputSchema: Record<string, unknown>;
  requiredRoles: string[];
}

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * Default location of tools.json relative to this file (mcp-server/src or mcp-server/dist once
 * built): ../../src/main/resources/assistant/tools.json from mcp-server/dist, i.e. two levels up
 * from mcp-server/ to the repo root, then into src/main/resources/assistant/tools.json.
 * Overridable via the ERP_TOOLS_MANIFEST env var for deployments that package the manifest
 * alongside the built server instead of relying on the monorepo layout.
 */
function defaultManifestPath(): string {
  return path.resolve(__dirname, "..", "..", "src", "main", "resources", "assistant", "tools.json");
}

export function resolveManifestPath(env: NodeJS.ProcessEnv = process.env): string {
  return env.ERP_TOOLS_MANIFEST && env.ERP_TOOLS_MANIFEST.length > 0
    ? env.ERP_TOOLS_MANIFEST
    : defaultManifestPath();
}

export function loadManifest(manifestPath: string = resolveManifestPath()): ToolSpec[] {
  let raw: string;
  try {
    raw = readFileSync(manifestPath, "utf-8");
  } catch (err) {
    const cause = err instanceof Error ? err.message : String(err);
    throw new Error(
      `could not read tool manifest at '${manifestPath}' (${cause}). ` +
        "If this repo layout has moved or you're running the built server outside the monorepo, " +
        "set the ERP_TOOLS_MANIFEST environment variable to the absolute path of tools.json.",
    );
  }
  const json = JSON.parse(raw) as { tools?: unknown };
  if (!Array.isArray(json.tools)) {
    throw new Error(`tool manifest at ${manifestPath} must have a 'tools' array`);
  }
  const specs = json.tools.map((tool) => parseTool(tool));
  const names = new Set<string>();
  for (const spec of specs) {
    if (names.has(spec.name)) {
      throw new Error(`tool manifest at ${manifestPath} has duplicate tool name '${spec.name}'`);
    }
    names.add(spec.name);
  }
  return specs;
}

function parseTool(node: unknown): ToolSpec {
  if (typeof node !== "object" || node === null) {
    throw new Error("tool manifest entry must be an object");
  }
  const obj = node as Record<string, unknown>;
  const name = requireText(obj, "name");
  const kindText = requireText(obj, "kind");
  if (kindText !== "read" && kindText !== "write") {
    throw new Error(`tool '${name}' has invalid 'kind' (expected 'read' or 'write'): ${kindText}`);
  }
  const method = requireText(obj, "method").toUpperCase();
  const pathTemplate = requireText(obj, "pathTemplate");
  const description = requireText(obj, "description");
  const inputSchema = obj.input_schema;
  if (typeof inputSchema !== "object" || inputSchema === null || Array.isArray(inputSchema)) {
    throw new Error(`tool '${name}' must have an object 'input_schema'`);
  }
  const requiredRoles = parseRequiredRoles(obj.requiredRoles, name);
  return {
    name,
    kind: kindText,
    method,
    pathTemplate,
    description,
    inputSchema: inputSchema as Record<string, unknown>,
    requiredRoles,
  };
}

function parseRequiredRoles(value: unknown, toolName: string): string[] {
  if (value === undefined) {
    return [];
  }
  if (!Array.isArray(value)) {
    throw new Error(`tool '${toolName}' has invalid 'requiredRoles' (expected an array)`);
  }
  return value.map((role) => String(role));
}

function requireText(obj: Record<string, unknown>, field: string): string {
  const value = obj[field];
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`tool manifest entry missing text field '${field}'`);
  }
  return value;
}
