import { createFeedHandler } from "./handler.ts";

// Supabase injects this named-key map. Malformed configuration fails closed.
function publishableKeys(): string[] {
    try {
        const value: unknown = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
        if (!value || typeof value !== "object" || Array.isArray(value)) return [];
        return Object.values(value).filter((key): key is string => typeof key === "string" && key.length > 0);
    } catch {
        return [];
    }
}

Deno.serve(createFeedHandler({
    newsDataKey: Deno.env.get("NEWSDATA_API_KEY") ?? "",
    publishableKeys: publishableKeys(),
    supabaseUrl: Deno.env.get("SUPABASE_URL") ?? "",
    serviceRoleKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
}));
