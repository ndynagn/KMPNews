import Foundation

@main
struct SearchPresentationTests {
    @MainActor
    static func main() async throws {
        let clock = ManualSearchClock()
        let client = FakeSearchClient()
        let model = SearchViewModel(client: client, sleep: clock.sleep)
        model.onEvent(.activate)
        model.onEvent(.queryChanged("s"))
        await settle()
        clock.advance(.milliseconds(200))
        model.onEvent(.queryChanged("space"))
        await settle()
        clock.advance(.milliseconds(100))
        model.onEvent(.queryChanged("space "))
        await settle()
        precondition(client.requests.isEmpty)
        precondition(clock.durations == [.milliseconds(500), .milliseconds(500), .milliseconds(500)])
        clock.advance(.milliseconds(499))
        await settle()
        precondition(client.requests.isEmpty)
        clock.advance(.milliseconds(1))
        await settle()
        precondition(client.requests.map(\.query) == ["space"])
        model.onEvent(.submit)
        precondition(client.requests.count == 1)
        client.complete(0, .page([article("a")], nextCursor: "next"))
        await settle()

        model.onEvent(.loadMore)
        model.onEvent(.loadMore)
        await settle()
        precondition(client.requests.count == 2)
        precondition(model.state.isAppending)
        model.onEvent(.deactivate)
        precondition(!model.state.isAppending)
        model.onEvent(.activate)
        await settle()
        precondition(client.requests.map(\.cursor) == [nil, "next", "next"])
        client.complete(1, .page([article("stale")], nextCursor: nil))
        client.complete(2, .failed(.rateLimited))
        await settle()
        precondition(model.state.articles.map(\.id) == ["a"])
        precondition(model.state.appendFailure == .rateLimited)
        model.onEvent(.loadMore)
        model.onEvent(.deactivate)
        model.onEvent(.activate)
        await settle()
        precondition(client.requests.count == 3)
        model.onEvent(.retry)
        await settle()
        precondition(model.state.isAppending && model.state.appendFailure == .rateLimited)
        client.complete(3, .page([article("a"), article("b")], nextCursor: nil))
        await settle()
        precondition(model.state.articles.map(\.id) == ["a", "b"])
        precondition(!model.state.canAppend)

        model.onEvent(.queryChanged("science"))
        await settle()
        model.onEvent(.submit)
        await settle()
        precondition(client.requests.count == 5)
        model.onEvent(.queryChanged(""))
        client.complete(4, .page([article("late")], nextCursor: nil))
        clock.release()
        await settle()
        precondition(model.state.phase == .idle && model.state.articles.isEmpty)

        let other = SearchViewModel(client: client, sleep: clock.sleep)
        other.onEvent(.activate)
        other.onEvent(.queryChanged("window two"))
        await settle()
        clock.release()
        await settle()
        other.onEvent(.deactivate)
        other.onEvent(.activate)
        await settle()
        precondition(client.requests.suffix(2).map(\.query) == ["window two", "window two"])
        client.complete(5, .failed(.service))
        client.complete(6, .page([article("window-two")], nextCursor: nil))
        await settle()
        precondition(other.state.articles.count == 1 && model.state.articles.isEmpty)
        other.onEvent(.deactivate)
        model.onEvent(.deactivate)
        clock.release()
        await verifyErrorsAndLinks()
        await verifyDebounceSuspension()
        await verifyRetainedContentDuringDebounce()
        print("Search presentation: debounce, stale results, append recovery, retries and window isolation passed")
    }

    @MainActor private static func verifyErrorsAndLinks() async {
        let client = FakeSearchClient()
        let model = SearchViewModel(client: client, sleep: { _ in })
        model.onEvent(.activate)
        model.onEvent(.queryChanged("empty"))
        await settle()
        client.complete(0, .page([], nextCursor: nil))
        await settle()
        precondition(model.state.phase == .loaded && model.state.articles.isEmpty)
        for problem in [SearchProblem.network, .timeout, .rateLimited, .service, .invalidResponse] {
            model.onEvent(.queryChanged(problem.rawValue))
            await settle()
            client.complete(client.requests.count - 1, .failed(problem))
            await settle()
            precondition(model.state.phase == .failed(problem))
            let count = client.requests.count
            model.onEvent(.retry)
            model.onEvent(.retry)
            await settle()
            precondition(client.requests.count == count + 1)
            client.complete(count, .page([], nextCursor: nil))
            await settle()
        }
        for problem in [SearchProblem.notConfigured, .accessDenied, .quotaExceeded, .invalidQuery] {
            model.onEvent(.queryChanged(problem.rawValue))
            await settle()
            client.complete(client.requests.count - 1, .failed(problem))
            await settle()
            let count = client.requests.count
            model.onEvent(.retry)
            await settle()
            precondition(client.requests.count == count)
        }
        let count = client.requests.count
        model.onEvent(.queryChanged("invalid input"))
        await settle()
        precondition(client.requests.count == count && model.state.phase == .failed(.invalidQuery))
        model.onEvent(.deactivate)
        var link = article("url")
        for value in ["file:///tmp/news", "javascript:alert(1)", "https:///", "not a URL"] {
            link.articleURL = value
            precondition(link.openingURL == nil)
        }
        link.articleURL = "https://example.com/news"
        precondition(link.openingURL != nil)
    }

    @MainActor private static func verifyDebounceSuspension() async {
        let clock = ManualSearchClock()
        let client = FakeSearchClient()
        let model = SearchViewModel(client: client, sleep: clock.sleep)
        model.onEvent(.activate)
        model.onEvent(.queryChanged("pending"))
        await settle()
        model.onEvent(.deactivate)
        clock.release()
        await settle()
        precondition(client.requests.isEmpty)
        model.onEvent(.activate)
        model.onEvent(.activate)
        await settle()
        clock.advance(.milliseconds(500))
        await settle()
        precondition(client.requests.count == 1)
        client.complete(0, .page([], nextCursor: nil))
        await settle()
        model.onEvent(.deactivate)
    }

    @MainActor private static func verifyRetainedContentDuringDebounce() async {
        let clock = ManualSearchClock()
        let client = FakeSearchClient()
        let model = SearchViewModel(client: client, sleep: clock.sleep)
        model.onEvent(.activate)
        model.onEvent(.queryChanged("first"))
        await settle()
        precondition(model.state.phase == .idle && model.state.isWaiting)
        clock.advance(.milliseconds(499))
        await settle()
        precondition(model.state.phase == .idle && client.requests.isEmpty)
        clock.advance(.milliseconds(1))
        await settle()
        precondition(model.state.phase == .loading)
        client.complete(0, .page([article("retained")], nextCursor: "old-cursor"))
        await settle()

        model.onEvent(.queryChanged("second"))
        await settle()
        precondition(model.state.articles.map(\.id) == ["retained"] && model.state.phase == .loaded)
        precondition(!model.state.canAppend)
        model.onEvent(.loadMore)
        clock.advance(.milliseconds(499))
        await settle()
        precondition(client.requests.count == 1 && model.state.articles.count == 1)
        clock.advance(.milliseconds(1))
        await settle()
        precondition(model.state.phase == .loading && model.state.articles.isEmpty)
        precondition(client.requests.last?.cursor == nil)

        model.onEvent(.queryChanged("third"))
        await settle()
        precondition(model.state.phase == .loaded && model.state.articles.map(\.id) == ["retained"])
        client.complete(1, .page([article("late")], nextCursor: nil))
        await settle()
        precondition(model.state.articles.map(\.id) == ["retained"])
        model.onEvent(.submit)
        await settle()
        client.complete(2, .page([], nextCursor: nil))
        await settle()

        model.onEvent(.queryChanged("invalid input"))
        await settle()
        precondition(model.state.phase == .loaded && model.state.isWaiting)
        clock.advance(.milliseconds(500))
        await settle()
        precondition(model.state.phase == .failed(.invalidQuery) && client.requests.count == 3)
        model.onEvent(.queryChanged("fourth"))
        await settle()
        precondition(model.state.phase == .failed(.invalidQuery) && model.state.isWaiting)
        model.onEvent(.queryChanged(""))
        clock.release()
        await settle()
        precondition(model.state.phase == .idle && !model.state.isWaiting && client.requests.count == 3)
        model.onEvent(.deactivate)
    }

    @MainActor private static func settle() async {
        for _ in 0..<20 { await Task.yield() }
    }

    private static func article(_ id: String) -> FeedArticle {
        FeedArticle(id: id, title: id, summary: nil, imageURL: nil, source: nil, publishedAt: nil)
    }
}

@MainActor
private final class ManualSearchClock {
    var durations: [Duration] = []
    var now: Duration = .zero
    var waiters: [(Duration, CheckedContinuation<Void, Error>)] = []
    func sleep(_ duration: Duration) async throws {
        durations.append(duration)
        try await withCheckedThrowingContinuation { waiters.append((now + duration, $0)) }
    }
    func advance(_ duration: Duration) {
        now += duration
        let ready = waiters.filter { $0.0 <= now }
        waiters.removeAll { $0.0 <= now }
        ready.forEach { $0.1.resume() }
    }
    func release() {
        let pending = waiters
        waiters = []
        pending.forEach { $0.1.resume() }
    }
}

@MainActor
private final class FakeSearchClient: SearchClient {
    struct Request { let query: String; let cursor: String? }
    var requests: [Request] = []
    var completions: [Int: CheckedContinuation<SearchPageResult, Error>] = [:]
    func validate(_ query: String) -> SearchInput {
        if query == "invalid input" { return .invalid }
        let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? .empty : .valid(value)
    }
    func fetch(query: String, cursor: String?) async throws -> SearchPageResult {
        let id = requests.count
        requests.append(Request(query: query, cursor: cursor))
        // Deliberately ignores cancellation to verify the generation guard against late responses.
        return try await withCheckedThrowingContinuation { completions[id] = $0 }
    }
    func complete(_ id: Int, _ result: SearchPageResult) {
        completions.removeValue(forKey: id)?.resume(returning: result)
    }
}
