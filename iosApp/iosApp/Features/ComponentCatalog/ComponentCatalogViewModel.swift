#if DEBUG
    import Observation

    @MainActor @Observable
    final class ComponentCatalogViewModel {
        private(set) var authenticated = false
        private(set) var imageUnavailable = false
        private(set) var isLoading = false
        private(set) var isDisabled = false
        private(set) var feedback: String?

        func completeAuth() { authenticated = true }
        func resetProfile() { authenticated = false }
        func setImageUnavailable(_ value: Bool) { imageUnavailable = value }
        func setLoading(_ value: Bool) { isLoading = value }
        func setDisabled(_ value: Bool) { isDisabled = value }
        func showFeedback() { feedback = "kit.actionFeedback" }
        func clearFeedback() { feedback = nil }
    }
#endif
