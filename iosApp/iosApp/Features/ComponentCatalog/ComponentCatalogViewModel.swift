#if DEBUG
    import Observation

    @MainActor @Observable
    final class ComponentCatalogViewModel {
        private(set) var isAuthenticated = false
        private(set) var isImageUnavailable = false
        private(set) var isLoading = false
        private(set) var isDisabled = false
        private(set) var feedback: String?

        func completeAuth() { isAuthenticated = true }

        func resetProfile() { isAuthenticated = false }

        func setImageUnavailable(_ value: Bool) { isImageUnavailable = value }

        func setLoading(_ value: Bool) { isLoading = value }

        func setDisabled(_ value: Bool) { isDisabled = value }

        func showFeedback() { feedback = "kit.actionFeedback" }

        func clearFeedback() { feedback = nil }
    }
#endif
