/** Public, read-only feed proxy. Only this boundary knows the provider credential. */
export interface FeedEnvironment {
    newsDataKey: string;
    publishableKeys: string[];
    supabaseUrl: string;
    serviceRoleKey: string;
}

type Fetch = typeof fetch;
type RecordValue = Record<string, unknown>;
const ARTICLE_FIELDS = [
    "article_id",
    "title",
    "link",
    "description",
    "image_url",
    "source_id",
    "source_name",
    "pubDate",
    "pubDateTZ",
] as const;

function record(value: unknown): value is RecordValue {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}

function failure(status: number, code: string, retryAfter?: number): Response {
    const headers: Record<string, string> = { "Cache-Control": "no-store" };
    if (retryAfter !== undefined) headers["Retry-After"] = String(retryAfter);
    if (status === 405) headers["Allow"] = "GET";
    return Response.json({ status: "error", results: { code } }, { status, headers });
}

function providerFailure(status: number, body: unknown): Response {
    const results = record(body) ? body.results : null;
    const details = Array.isArray(results) ? results[0] : results;
    const code = record(details) && typeof details.code === "string" ? details.code.trim() : "";
    switch (code) {
        case "ApiLimitExceeded":
            return failure(429, "ApiLimitExceeded");
        case "RateLimitExceeded":
        case "TooManyRequests":
            return failure(429, "RateLimitExceeded");
        case "Unauthorized":
        case "AccessDenied":
            return failure(403, "AccessDenied");
        case "ServerError":
        case "ServiceUnavailable":
            return failure(503, "ServiceUnavailable");
    }
    if (status === 401 || status === 403) return failure(403, "AccessDenied");
    if ([400, 404, 422].includes(status)) return failure(400, "InvalidRequest");
    if (status === 429) return failure(429, "RateLimitExceeded");
    if (status >= 500) return failure(503, "ServiceUnavailable");
    return failure(502, "InvalidResponse");
}

function projectSuccess(body: unknown): RecordValue | null {
    if (
        !record(body) || body.status !== "success" || !Number.isSafeInteger(body.totalResults) ||
        !Array.isArray(body.results) ||
        (body.nextPage !== undefined && body.nextPage !== null && typeof body.nextPage !== "string")
    ) return null;

    const articles: RecordValue[] = [];
    for (const article of body.results) {
        if (!record(article) || typeof article.article_id !== "string") return null;
        const selected: RecordValue = {};
        for (const field of ARTICLE_FIELDS) {
            const value = article[field];
            if (value !== undefined) {
                if (value !== null && typeof value !== "string") return null;
                selected[field] = value;
            }
        }
        articles.push(selected);
    }
    return { status: "success", totalResults: body.totalResults, results: articles, nextPage: body.nextPage ?? null };
}

/** Dependencies are injected for tests. Reservation failures always prevent an upstream request. */
export function createFeedHandler(env: FeedEnvironment, fetcher: Fetch = fetch, timeoutMs = 20_000) {
    return async (request: Request): Promise<Response> => {
        if (!env.publishableKeys.includes(request.headers.get("apikey") ?? "") || !request.headers.get("apikey")) {
            return failure(401, "AccessDenied");
        }
        if (request.method !== "GET") return failure(405, "InvalidRequest");
        const params = new URL(request.url).searchParams;
        if (
            [...params.keys()].some((key) => key !== "page") || params.getAll("page").length > 1 ||
            params.get("page") === ""
        ) return failure(400, "InvalidRequest");
        if (!env.newsDataKey || !env.supabaseUrl || !env.serviceRoleKey) return failure(503, "ServiceUnavailable");

        try {
            const reservation = await fetcher(`${env.supabaseUrl}/rest/v1/rpc/reserve_news_request`, {
                method: "POST",
                headers: {
                    apikey: env.serviceRoleKey,
                    Authorization: `Bearer ${env.serviceRoleKey}`,
                    "Content-Type": "application/json",
                },
                body: "{}",
                redirect: "error",
                signal: AbortSignal.timeout(5_000),
            });
            if (!reservation.ok) return failure(503, "ServiceUnavailable");
            const budget: unknown = await reservation.json();
            if (!record(budget) || typeof budget.allowed !== "boolean") return failure(503, "ServiceUnavailable");
            if (!budget.allowed) {
                if (
                    !["ApiLimitExceeded", "RateLimitExceeded"].includes(String(budget.code)) ||
                    typeof budget.retry_after !== "number" || !Number.isInteger(budget.retry_after) ||
                    budget.retry_after < 1
                ) return failure(503, "ServiceUnavailable");
                return failure(429, String(budget.code), budget.retry_after);
            }
        } catch {
            return failure(503, "ServiceUnavailable");
        }

        const upstream = new URL("https://newsdata.io/api/1/latest");
        upstream.searchParams.set("language", "en");
        upstream.searchParams.set("size", "10");
        upstream.searchParams.set("timezone", "UTC");
        const page = params.get("page");
        if (page !== null) upstream.searchParams.set("page", page);
        const deadline = AbortSignal.timeout(timeoutMs);
        try {
            const response = await fetcher(upstream, {
                headers: { "X-ACCESS-KEY": env.newsDataKey, Accept: "application/json" },
                redirect: "error",
                signal: AbortSignal.any([deadline, request.signal]),
            });
            let body: unknown;
            try {
                body = await response.json();
            } catch {
                if (deadline.aborted) return failure(504, "UpstreamTimeout");
                return response.ok ? failure(502, "InvalidResponse") : providerFailure(response.status, null);
            }
            if (!response.ok || (record(body) && body.status === "error")) {
                return providerFailure(response.status, body);
            }
            const result = projectSuccess(body);
            return result
                ? Response.json(result, { headers: { "Cache-Control": "no-store" } })
                : failure(502, "InvalidResponse");
        } catch {
            return deadline.aborted ? failure(504, "UpstreamTimeout") : failure(503, "ServiceUnavailable");
        }
    };
}
