import Foundation
import Observation

/// One window owns this MVI state and its task. Deactivation preserves the exact interrupted operation.
@MainActor @Observable
final class SearchViewModel {
    private(set) var state = SearchState()
    private let client: any SearchClient
    private let sleep: (Duration) async throws -> Void
    private var task: Task<Void, Never>?
    private var generation = 0
    private var active = false
    private var normalizedQuery: String?
    private var pending: Operation?
    private var needsDebounce = false
    private var completedState = SearchState()
    private enum Operation { case first, append(String) }

    init(client: any SearchClient, sleep: @escaping (Duration) async throws -> Void = { try await Task.sleep(for: $0) })
    {
        self.client = client
        self.sleep = sleep
    }

    func onEvent(_ event: SearchEvent) {
        switch event {
        case .queryChanged(let text): changeQuery(text)
        case .activate:
            guard !active else { return }

            active = true
            if let pending { start(pending, debounce: needsDebounce) }
        case .deactivate:
            active = false
            cancelTask()
            if state.phase == .loading {
                restoreCompletedContent()
                state.isWaiting = true
            }
            state.isAppending = false
        case .submit:
            if state.isWaiting { start(.first, debounce: false) }
        case .loadMore:
            guard task == nil, state.canAppend, let cursor = state.nextCursor else { return }

            start(.append(cursor), debounce: false)
        case .retry:
            guard task == nil, !state.isWaiting else { return }

            if state.appendFailure?.canRetry == true, let cursor = state.nextCursor {
                start(.append(cursor), debounce: false)
            } else if case .failed(let problem) = state.phase, problem.canRetry, normalizedQuery != nil {
                start(.first, debounce: false)
            }
        }
    }

    private func changeQuery(_ text: String) {
        state.query = text

        let input = client.validate(text)

        if case .valid(let query) = input, query == normalizedQuery {
            if state.isWaiting, needsDebounce { start(.first, debounce: true) }
            return
        }

        cancelTask()
        pending = nil
        normalizedQuery = nil
        restoreCompletedContent()
        state.isAppending = false

        switch input {
        case .empty:
            state = SearchState(query: text)
            completedState = state
            needsDebounce = false
        case .invalid, .valid:
            if case .valid(let query) = input { normalizedQuery = query }
            pending = .first
            needsDebounce = true
            state.isWaiting = true
            start(.first, debounce: true)
        }
    }

    private func restoreCompletedContent() {
        let query = state.query

        state = completedState
        state.query = query
    }

    private func cancelTask() {
        generation += 1
        task?.cancel()
        task = nil
    }

    private func start(_ operation: Operation, debounce: Bool) {
        guard active else { return }

        cancelTask()
        pending = operation

        let query = normalizedQuery
        let revision = generation

        task = Task { [weak self, client, sleep] in
            do {
                if debounce { try await sleep(.milliseconds(500)) }
                try Task.checkCancellation()
                guard let self, revision == self.generation else { return }

                self.needsDebounce = false
                self.state.isWaiting = false
                guard let query else {
                    self.finish(.failed(.invalidQuery), operation: .first)
                    return
                }

                let cursor: String?

                switch operation {
                case .first:
                    cursor = nil
                    self.state.phase = .loading
                    self.state.articles = []
                    self.state.nextCursor = nil
                    self.state.appendFailure = nil
                case .append(let value):
                    cursor = value
                    self.state.isAppending = true
                }

                let result = try await client.fetch(query: query, cursor: cursor)

                guard !Task.isCancelled, revision == self.generation else { return }

                self.finish(result, operation: operation)
            } catch {
                guard !Task.isCancelled, let self, revision == self.generation else { return }
                self.finish(.failed(.network), operation: operation)
            }
        }
    }

    private func finish(_ result: SearchPageResult, operation: Operation) {
        task = nil
        pending = nil
        state.isWaiting = false
        state.isAppending = false
        switch result {
        case .page(let articles, let cursor):
            state.appendFailure = nil
            if case .first = operation { state.articles = [] }
            var ids = Set(state.articles.map(\.id))
            state.articles += articles.filter { ids.insert($0.id).inserted }
            state.nextCursor = cursor
            // A repeated cursor cannot advance; stop instead of issuing an infinite sequence of requests.
            if case .append(let previous) = operation, cursor == previous { state.nextCursor = nil }
            state.phase = .loaded
        case .failed(let problem):
            switch operation {
            case .first:
                state.phase = .failed(problem)
                state.articles = []
                state.nextCursor = nil
                state.appendFailure = nil
            case .append: state.appendFailure = problem
            }
        }
        completedState = state
    }
}
