import Foundation

@MainActor
private final class FavoritesListFake: FavoriteSaveClient {
    var continuation: AsyncStream<FavoritesRead>.Continuation?
    var result = FavoriteSaveResult.saved
    var appends = 0

    func observe() -> AsyncStream<FavoritesRead> { AsyncStream { continuation = $0 } }
    func refresh() async throws -> FavoriteSaveResult { result }
    func loadMore() async throws -> FavoriteSaveResult {
        appends += 1
        return result
    }

    func save(_ article: FeedArticle) async throws -> FavoriteSaveResult { result }
    func remove(_ articleID: String) async throws -> FavoriteSaveResult { result }
    func membership(_ articleIDs: [String]) async throws -> FavoritesMembership { .snapshot([]) }
}

@main
struct FavoritesListPresentationTests {
    @MainActor
    static func main() async {
        let client = FavoritesListFake()
        let model = FavoritesViewModel(client: client)
        let article = FeedArticle(id: "one", title: "Saved", summary: nil, imageURL: nil, source: nil, publishedAt: nil)
        let snapshot = FavoritesSnapshot(articles: [article], hasMore: true, isInitialized: true)

        model.accountChanged(.authenticated(email: "first@example.test"))
        await settle()
        client.continuation?.yield(.snapshot(snapshot))
        await settle()
        precondition(model.snapshot == snapshot)

        client.result = .failed
        model.loadMore()
        await settle()
        precondition(model.hasError && model.snapshot == snapshot && client.appends == 1)

        client.result = .saved
        model.retry()
        await settle()
        precondition(!model.hasError && client.appends == 2)

        let oldStream = client.continuation
        model.accountChanged(.authenticated(email: "second@example.test"))
        precondition(model.snapshot == nil)
        oldStream?.yield(.snapshot(snapshot))
        await settle()
        precondition(model.snapshot == nil, "A previous account must never repopulate the list")

        model.accountChanged(.guest)
        precondition(model.snapshot == nil)
        model.stop()
        print("PASS: favorites cache, append retry, account isolation and logout")
    }

    private static func settle() async { for _ in 0..<50 { await Task.yield() } }
}
