/// Shared policy and cancellable network boundary; contains no navigation or screen state.
@MainActor
protocol SearchClient {
    func validate(_ query: String) -> SearchInput
    func fetch(query: String, cursor: String?) async throws -> SearchPageResult
}
