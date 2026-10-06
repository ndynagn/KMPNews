import Foundation

@main
struct ErrorFeedbackPresentationTests {
    @MainActor
    static func main() async {
        await verifyAuthFailureFeedback()
        await verifyProfileFailureFeedback()
        await verifyFavoriteFailureFeedback()
        await verifyManualLoadingFailureFeedback()
        print(
            "PASS: OTP retry ownership, repeated failures, manual vs automatic loading, cancellation and feedback counts"
        )
    }

    @MainActor
    private static func verifyAuthFailureFeedback() async {
        for step in [AuthStep.confirm, .recoveryCode] {
            let client = ErrorFeedbackAuthStub()
            let model = AuthViewModel(client: client, step: step)

            for (index, problem) in [AuthProblem.invalidCode, .expired, .network, .service, .rateLimited].enumerated() {
                client.problem = problem
                model.onEvent(.code("11111"))
                model.onEvent(.code("111111"))
                await settle()

                precondition(model.errorFeedback == index + 1)
                precondition(model.state.canRetryCodeVerification == (index >= 2))
                precondition(model.state.code == "111111")
            }

            model.onEvent(.submit)
            await settle()

            precondition(model.errorFeedback == 6 && model.state.canRetryCodeVerification)

            model.onEvent(.resend)
            await settle()

            precondition(model.errorFeedback == 7 && !model.state.canRetryCodeVerification)

            model.onEvent(.code("11111"))

            precondition(!model.state.canRetryCodeVerification && model.errorFeedback == 7)

            model.beginDismissal()
            model.onEvent(.submit)

            precondition(model.errorFeedback == 7)
        }

        let client = ErrorFeedbackAuthStub()
        client.validation = "auth.invalid_email"
        let model = AuthViewModel(client: client, step: .signIn)

        model.onEvent(.submit)
        model.onEvent(.submit)

        precondition(model.errorFeedback == 2)

        client.validation = nil
        client.problem = .invalidCredentials
        model.onEvent(.submit)
        await settle()
        model.onEvent(.submit)
        await settle()

        precondition(model.errorFeedback == 4)

        client.delayed = true
        model.onEvent(.submit)
        model.onEvent(.submit)
        model.beginDismissal()
        await settle()

        precondition(model.errorFeedback == 4)

        let registration = AuthViewModel(client: client, step: .register)
        registration.onEvent(.photoFailed)

        precondition(registration.errorFeedback == 1)

        registration.beginDismissal()
        registration.onEvent(.photoFailed)

        precondition(registration.errorFeedback == 1)
    }

    @MainActor
    private static func verifyProfileFailureFeedback() async {
        let client = ErrorFeedbackProfileStub()
        let editor = ProfileEditViewModel(profile: client.profile, generation: UUID(), client: client)

        editor.save()
        editor.save()

        precondition(editor.errorFeedback == 2)

        client.validation = nil
        editor.photoFailed()

        precondition(editor.errorFeedback == 3)

        editor.save()
        editor.save()
        await settle()

        precondition(editor.errorFeedback == 4)

        editor.save()
        await settle()

        precondition(editor.errorFeedback == 5)

        client.delayed = true
        editor.save()
        editor.cancel()
        await settle()

        precondition(editor.errorFeedback == 5)

        client.delayed = false

        let auth = ErrorFeedbackAuthStub()
        let profile = ProfileViewModel(client: auth, profiles: client)
        await profile.observe()
        await profile.loadProfile()

        precondition(profile.errorFeedback == 0)

        await profile.loadProfile(userInitiated: true)

        precondition(profile.errorFeedback == 1)

        profile.restore()
        await settle()

        precondition(profile.errorFeedback == 1)

        profile.restore(userInitiated: true)
        await settle()

        precondition(profile.errorFeedback == 2)

        profile.signOut()
        profile.signOut()
        await settle()

        precondition(profile.errorFeedback == 3)
    }

    @MainActor
    private static func verifyFavoriteFailureFeedback() async {
        let client = ErrorFeedbackFavoritesStub()
        let model = FavoriteSaveViewModel(client: client)
        let article = FeedArticle(id: "one", title: "Story", summary: nil, imageURL: nil, source: nil, publishedAt: nil)
        let account = ProfileState.authenticated(email: "reader@example.test")

        model.save(article, profile: .guest)

        precondition(model.errorFeedback == 0)

        model.dismiss()
        model.accountChanged(account)
        model.save(article, profile: account, isSaved: true)
        await settle()

        precondition(model.errorFeedback == 1 && model.isSaved(article.id, fallback: false))

        model.retry(profile: account)
        await settle()

        precondition(model.errorFeedback == 2)

        client.result = .saved
        model.retry(profile: account)
        await settle()

        precondition(model.errorFeedback == 2 && model.addedFeedback == 0)

        client.result = .failed
        model.save(article, profile: account)
        model.discardPendingFeedback()
        await settle()

        precondition(model.hasError && model.errorFeedback == 2)

        model.save(article, profile: account)
        model.accountChanged(.authenticated(email: "other@example.test"))
        await settle()

        precondition(model.errorFeedback == 2 && !model.hasError)

        let list = FavoritesViewModel(client: client)
        list.accountChanged(account)
        await list.refresh(userInitiated: false)

        precondition(list.errorFeedback == 0)

        await list.refresh()

        precondition(list.errorFeedback == 1)

        list.retry()
        await settle()

        precondition(list.errorFeedback == 2)

        list.retry()
        list.stop()
        await settle()

        precondition(list.errorFeedback == 2)
    }

    @MainActor
    private static func verifyManualLoadingFailureFeedback() async {
        let feed = FeedViewModel(client: ErrorFeedbackFeedStub(), isConfigured: true)
        feed.activate()
        await settle()

        precondition(feed.errorFeedback == 0)

        feed.onEvent(.retry)
        await settle()

        precondition(feed.errorFeedback == 1)

        await feed.refresh()

        precondition(feed.errorFeedback == 2)

        feed.onEvent(.refresh)
        feed.deactivate()
        await settle()

        precondition(feed.errorFeedback == 2)

        let search = SearchViewModel(client: ErrorFeedbackSearchStub(), sleep: { _ in })
        search.onEvent(.activate)
        search.onEvent(.queryChanged("news"))
        await settle()

        precondition(search.errorFeedback == 0)

        search.onEvent(.queryChanged("science"))
        search.onEvent(.submit)
        await settle()

        precondition(search.errorFeedback == 1)

        search.onEvent(.retry)
        await settle()

        precondition(search.errorFeedback == 2)

        search.onEvent(.retry)
        search.onEvent(.deactivate)
        await settle()

        precondition(search.errorFeedback == 2)
    }

    private static func settle() async { try? await Task.sleep(for: .milliseconds(30)) }
}

@MainActor
private final class ErrorFeedbackAuthStub: AuthClient, AuthRecoveryClient {
    var problem: AuthProblem? = .network
    var validation: String?
    var delayed = false

    func validate(_ input: AuthUiState, resend: Bool) -> String? { validation }

    func observe() -> AsyncStream<ProfileState> {
        AsyncStream {
            $0.yield(.authenticated(email: "reader@example.test"))
            $0.finish()
        }
    }

    func restore() async throws -> AuthProblem? { problem }

    func signIn(email: String, password: String) async throws -> AuthProblem? {
        if delayed { try await Task.sleep(for: .seconds(60)) }
        return problem
    }

    func register(email: String, password: String, details: PersonalDetails) async throws -> AuthProblem? { problem }

    func confirm(email: String, code: String) async throws -> AuthProblem? { problem }

    func resend(email: String) async throws -> AuthProblem? { problem }

    func signOut() async throws -> AuthProblem? { problem }

    func makeRecovery() -> any AuthRecoveryClient { self }

    func requestCode(email: String) async throws -> AuthProblem? { problem }

    func verifyCode(email: String, code: String) async throws -> AuthProblem? { problem }

    func resetPassword(_ password: String) async throws -> AuthResetResult { AuthResetResult(problem: problem) }

    func cancel() async {}
}

@MainActor
private final class ErrorFeedbackProfileStub: ProfileClient {
    var validation: String? = "profile.namesRequired"
    var delayed = false
    let profile = AccountProfile(
        userID: "one", email: "reader@example.test", details: PersonalDetails(), avatarURL: nil)

    func validate(_ details: PersonalDetails) -> String? { validation }

    func fetch() async throws -> ProfileLoadResult { ProfileLoadResult(errorKey: "auth.network") }

    func save(details: PersonalDetails, photo: Data?, removePhoto: Bool) async throws -> ProfileLoadResult {
        if delayed { try await Task.sleep(for: .seconds(60)) }
        return ProfileLoadResult(errorKey: "auth.network")
    }
}

@MainActor
private final class ErrorFeedbackFavoritesStub: FavoriteSaveClient {
    var result = FavoriteSaveResult.failed

    func save(_ article: FeedArticle) async throws -> FavoriteSaveResult { result }

    func remove(_ articleID: String) async throws -> FavoriteSaveResult { result }

    func membership(_ articleIDs: [String]) async throws -> FavoritesMembership { .failed }

    func observe() -> AsyncStream<FavoritesRead> {
        AsyncStream {
            $0.yield(.failed)
            $0.finish()
        }
    }

    func refresh() async throws -> FavoriteSaveResult { result }

    func loadMore() async throws -> FavoriteSaveResult { result }
}

@MainActor
private final class ErrorFeedbackFeedStub: FeedClient {
    func observe() -> AsyncStream<FeedRead> {
        AsyncStream {
            $0.yield(.storageFailure)
            $0.finish()
        }
    }

    func update(_ operation: FeedOperation) async throws -> FeedUpdate { .failure(isStorage: false) }
}

@MainActor
private final class ErrorFeedbackSearchStub: SearchClient {
    func validate(_ query: String) -> SearchInput { .valid(query) }

    func fetch(query: String, cursor: String?) async throws -> SearchPageResult { .failed(.network) }
}
