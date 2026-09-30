import { deepStrictEqual, equal, ok } from "node:assert/strict";
import { createFeedHandler, type FeedEnvironment } from "./handler.ts";

const env: FeedEnvironment = {
    newsDataKey: "private-provider-key",
    publishableKeys: ["public-fixture-key"],
    supabaseUrl: "https://project.supabase.co",
    serviceRoleKey: "private-service-key",
};
const success = {
    status: "success",
    totalResults: 1,
    results: [{ article_id: "a", title: null }],
    nextPage: "next+/=",
};
function request(query = "", method = "GET", key = "public-fixture-key") {
    return new Request(`https://project.supabase.co/functions/v1/news-feed${query}`, {
        method,
        headers: { apikey: key },
    });
}
function upstreamStub(response: () => Response | Promise<Response>): typeof fetch {
    return (input) =>
        String(input).includes("/rpc/")
            ? Promise.resolve(Response.json({ allowed: true }))
            : Promise.resolve(response());
}
async function expectFailure(response: Response, status: number, code: string) {
    equal(response.status, status);
    deepStrictEqual(await response.json(), { status: "error", results: { code } });
}

Deno.test("only the server sends the provider key, fixed query and opaque cursor", async () => {
    const calls: string[] = [];
    const handler = createFeedHandler(env, (input, init) => {
        const url = new URL(String(input));
        calls.push(url.toString());
        equal(init?.redirect, "error");
        ok(!url.toString().includes("private-"));
        const headers = new Headers(init?.headers);
        if (url.pathname.includes("/rpc/")) {
            equal(headers.get("apikey"), env.serviceRoleKey);
            equal(headers.get("X-ACCESS-KEY"), null);
            return Promise.resolve(Response.json({ allowed: true }));
        }
        equal(url.origin + url.pathname, "https://newsdata.io/api/1/latest");
        deepStrictEqual(Object.fromEntries(url.searchParams), {
            language: "en",
            size: "10",
            timezone: "UTC",
            page: "next+/=",
        });
        equal(headers.get("X-ACCESS-KEY"), env.newsDataKey);
        equal(headers.get("Authorization"), null);
        return Promise.resolve(
            Response.json({ ...success, results: [{ ...success.results[0], content: "not-exported" }] }),
        );
    });
    const response = await handler(request("?page=next%2B%2F%3D"));
    equal(calls.length, 2);
    equal(response.headers.get("Cache-Control"), "no-store");
    deepStrictEqual(await response.json(), success);
});

Deno.test("invalid calls and missing configuration never reserve budget", async () => {
    const fetcher: typeof fetch = () => {
        throw new Error("Unexpected fetch");
    };
    const handler = createFeedHandler(env, fetcher);
    await expectFailure(await handler(request("", "GET", "wrong")), 401, "AccessDenied");
    await expectFailure(await handler(request("", "GET", "")), 401, "AccessDenied");
    await expectFailure(await handler(request("", "POST")), 405, "InvalidRequest");
    for (const query of ["?apikey=evil", "?url=https://evil.test", "?language=ru", "?page=a&page=b", "?page="]) {
        await expectFailure(await handler(request(query)), 400, "InvalidRequest");
    }
    await expectFailure(
        await createFeedHandler({ ...env, newsDataKey: "" }, fetcher)(request()),
        503,
        "ServiceUnavailable",
    );
});

Deno.test("budget denial and unavailable or malformed RPC fail closed", async () => {
    for (const code of ["ApiLimitExceeded", "RateLimitExceeded"]) {
        let count = 0;
        const handler = createFeedHandler(env, () => {
            count++;
            return Promise.resolve(Response.json({ allowed: false, code, retry_after: 17 }));
        });
        const response = await handler(request());
        equal(response.headers.get("Retry-After"), "17");
        await expectFailure(response, 429, code);
        equal(count, 1);
    }
    for (
        const response of [
            new Response("private", { status: 500 }),
            Response.json({}),
            Response.json({ allowed: false }),
        ]
    ) {
        let count = 0;
        await expectFailure(
            await createFeedHandler(env, () => {
                count++;
                return Promise.resolve(response);
            })(request()),
            503,
            "ServiceUnavailable",
        );
        equal(count, 1);
    }
});

Deno.test("first page omits cursor and successful empty page is preserved", async () => {
    const handler = createFeedHandler(env, (input) => {
        if (String(input).includes("/rpc/")) return Promise.resolve(Response.json({ allowed: true }));
        equal(new URL(String(input)).searchParams.has("page"), false);
        return Promise.resolve(Response.json({ status: "success", totalResults: 0, results: [] }));
    });
    deepStrictEqual(await (await handler(request())).json(), {
        status: "success",
        totalResults: 0,
        results: [],
        nextPage: null,
    });
});

Deno.test("provider failures are normalized and never expose messages", async () => {
    for (
        const [status, code, expectedStatus, expectedCode] of [
            [429, "ApiLimitExceeded", 429, "ApiLimitExceeded"],
            [429, "TooManyRequests", 429, "RateLimitExceeded"],
            [200, "Unauthorized", 403, "AccessDenied"],
            [503, "ServiceUnavailable ", 503, "ServiceUnavailable"],
            [422, "unknown", 400, "InvalidRequest"],
            [200, "unknown", 502, "InvalidResponse"],
        ] as const
    ) {
        for (const results of [{ code, message: "private-secret" }, [{ code, message: "private-secret" }]]) {
            const handler = createFeedHandler(
                env,
                upstreamStub(() => Response.json({ status: "error", results }, { status })),
            );
            await expectFailure(await handler(request()), expectedStatus, expectedCode);
        }
    }
});

Deno.test("malformed provider data cannot become an empty success", async () => {
    for (
        const body of [null, [], {}, { ...success, results: [{}] }, { ...success, nextPage: 1 }, {
            ...success,
            totalResults: "1",
        }, { ...success, results: [{ article_id: "a", title: 1 }] }]
    ) {
        await expectFailure(
            await createFeedHandler(env, upstreamStub(() => Response.json(body)))(request()),
            502,
            "InvalidResponse",
        );
    }
    await expectFailure(
        await createFeedHandler(env, upstreamStub(() => new Response("private-html")))(request()),
        502,
        "InvalidResponse",
    );
});

Deno.test("upstream timeout has a distinct sanitized failure and is never retried", async () => {
    let upstreamCalls = 0;
    const handler = createFeedHandler(env, (input, init) => {
        if (String(input).includes("/rpc/")) return Promise.resolve(Response.json({ allowed: true }));
        upstreamCalls++;
        return new Promise((_resolve, reject) =>
            init?.signal?.addEventListener("abort", () => reject(new Error("private")), { once: true })
        );
    }, 1);
    await expectFailure(await handler(request()), 504, "UpstreamTimeout");
    equal(upstreamCalls, 1);
});
