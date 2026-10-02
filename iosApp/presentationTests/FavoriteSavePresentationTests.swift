import Foundation

@MainActor
private final class SaveClient: FavoriteSaveClient {
    var articles: [FeedArticle] = []
    var result = FavoriteSaveResult.saved
    var isDelayed = false
    var storedIDs: Set<String> = []
    var removedIDs: [String] = []

    func membership(_ articleIDs: [String]) async throws -> FavoritesMembership {
        .snapshot(storedIDs.intersection(articleIDs))
    }

    func remove(_ articleID: String) async throws -> FavoriteSaveResult {
        removedIDs.append(articleID)
        if result == .saved { storedIDs.remove(articleID) }
        return result
    }

    func observe() -> AsyncStream<FavoritesRead> { AsyncStream { $0.finish() } }
    func refresh() async throws -> FavoriteSaveResult { .saved }
    func loadMore() async throws -> FavoriteSaveResult { .saved }

    func save(_ article: FeedArticle) async throws -> FavoriteSaveResult {
        articles.append(article)
        if isDelayed { try await Task.sleep(for: .seconds(30)) }
        if result == .saved { storedIDs.insert(article.id) }
        return result
    }
}

@main
struct FavoriteSavePresentationTests {
    @MainActor
    static func main() async throws {
        let article = FeedArticle(
            id: "original", title: "Story", summary: nil, imageURL: nil,
            source: nil, publishedAt: nil, articleURL: "https://example.test/story", sourceID: "source")
        let client = SaveClient()
        let model = FavoriteSaveViewModel(client: client)

        model.save(article, profile: .guest)
        precondition(client.articles.isEmpty)
        guard case .invitation = model.presentation else { fatalError("Expected invitation") }
        model.authenticate(.signIn)
        model.dismiss()
        model.presentationDidDismiss()
        precondition(model.presentation == nil, "Canceled handoff must not reopen authentication")
        model.authenticationSucceeded()
        model.presentationDidDismiss()
        await settle()
        precondition(client.articles.isEmpty, "Cancellation must discard pending save")

        model.save(article, profile: .guest)
        model.authenticate(.register)
        guard case .awaitingAuthentication(.register) = model.presentation else {
            fatalError("Authentication must wait until the invitation has closed")
        }
        model.presentationBindingDismissed()
        model.save(article, profile: .guest)
        model.presentationDidDismiss()
        guard case .authentication(.register) = model.presentation else { fatalError("Expected registration") }
        model.authenticationSucceeded()
        precondition(client.articles.isEmpty, "Saving waits for native sheet dismissal")
        model.presentationBindingDismissed()
        model.presentationDidDismiss()
        model.save(article, profile: .authenticated(email: "reader@example.test"))
        await settle()
        precondition(client.articles == [article], "Save the original complete snapshot exactly once")
        precondition(model.savedIDs == [article.id])

        let feedback = model.addedFeedback
        model.save(article, profile: .authenticated(email: "reader@example.test"))
        await settle()
        precondition(client.removedIDs == [article.id] && model.savedIDs.isEmpty)
        precondition(model.addedFeedback == feedback, "Removal must not trigger added feedback")

        model.accountChanged(.guest)
        precondition(model.savedIDs.isEmpty)
        client.result = .failed
        model.save(article, profile: .authenticated(email: "reader@example.test"))
        await settle()
        precondition(model.hasError && model.savedIDs.isEmpty)
        client.result = .saved
        model.retry(profile: .authenticated(email: "reader@example.test"))
        await settle()
        precondition(!model.hasError && model.savedIDs.contains(article.id))

        client.result = .authenticationRequired
        model.save(article, profile: .authenticated(email: "reader@example.test"))
        await settle()
        guard case .invitation = model.presentation else { fatalError("Expired session needs authentication") }
        model.dismiss()

        client.isDelayed = true
        model.save(article, profile: .authenticated(email: "reader@example.test"))
        await settle()
        model.accountChanged(.guest)
        await settle()
        precondition(model.savingID == nil && model.savedIDs.isEmpty)
        model.stop()
        print("PASS: invitation, auth cancellation, deferred snapshot, duplicate tap, retry, expired session, logout")
    }

    private static func settle() async { for _ in 0..<50 { await Task.yield() } }
}
