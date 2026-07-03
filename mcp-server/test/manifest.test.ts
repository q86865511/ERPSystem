import { describe, expect, it } from "vitest";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadManifest, resolveManifestPath } from "../src/manifest.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

describe("manifest", () => {
  it("resolves the default path to the repo's tools.json when ERP_TOOLS_MANIFEST is unset", () => {
    const resolved = resolveManifestPath({});
    expect(resolved.replace(/\\/g, "/")).toMatch(/src\/main\/resources\/assistant\/tools\.json$/);
  });

  it("honors ERP_TOOLS_MANIFEST override", () => {
    const resolved = resolveManifestPath({ ERP_TOOLS_MANIFEST: "/custom/path/tools.json" });
    expect(resolved).toBe("/custom/path/tools.json");
  });

  it("loads the real repo tools.json: at least the 7 core tools, unique names, valid schemas, write tools flagged", () => {
    const tools = loadManifest(resolveManifestPath());

    // >= rather than an exact count: another PR is expected to grow tools.json to 12 tools, and
    // this parity check should keep passing as the manifest gains tools rather than go red the
    // moment the count changes.
    expect(tools.length).toBeGreaterThanOrEqual(7);

    const names = tools.map((t) => t.name);
    expect(new Set(names).size).toBe(names.length);
    expect(names).toEqual(
      expect.arrayContaining([
        "search_items",
        "search_partners",
        "get_inventory_status",
        "get_ar_aging",
        "get_kpi_summary",
        "get_reconciliation_health",
        "create_sales_order",
      ]),
    );

    for (const tool of tools) {
      expect(tool.method).toMatch(/^[A-Z]+$/);
      expect(tool.pathTemplate.startsWith("/")).toBe(true);
      expect(tool.description.length).toBeGreaterThan(0);
      expect(typeof tool.inputSchema).toBe("object");
      expect(tool.inputSchema).not.toBeNull();
      expect((tool.inputSchema as { type?: string }).type).toBe("object");
    }

    const writeTools = tools.filter((t) => t.kind === "write");
    const writeToolNames = writeTools.map((t) => t.name);
    expect(writeToolNames).toContain("create_sales_order");
    const createSalesOrder = writeTools.find((t) => t.name === "create_sales_order")!;
    expect(createSalesOrder.requiredRoles).toEqual(["SALES"]);

    // Read-tool count is intentionally not asserted here for the same reason as above — only
    // that the core read tools (checked via arrayContaining above) are present.
  });

  it("rejects a manifest with duplicate tool names", () => {
    const fixture = path.resolve(__dirname, "fixtures", "duplicate-names.json");
    expect(() => loadManifest(fixture)).toThrow(/duplicate tool name/);
  });

  it("rejects a manifest entry missing a required text field", () => {
    const fixture = path.resolve(__dirname, "fixtures", "missing-field.json");
    expect(() => loadManifest(fixture)).toThrow(/missing text field/);
  });

  it("rejects a manifest entry whose input_schema is not an object", () => {
    const fixture = path.resolve(__dirname, "fixtures", "bad-schema.json");
    expect(() => loadManifest(fixture)).toThrow(/must have an object 'input_schema'/);
  });

  it("wraps a missing manifest file in a message with the attempted path and the ERP_TOOLS_MANIFEST hint", () => {
    const missingPath = path.resolve(__dirname, "fixtures", "does-not-exist.json");
    expect(() => loadManifest(missingPath)).toThrow(
      new RegExp(
        `${missingPath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}.*ERP_TOOLS_MANIFEST`,
        "s",
      ),
    );
  });
});
