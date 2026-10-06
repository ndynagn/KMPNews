import Foundation

enum AuthStep: Hashable, Identifiable {
    case signIn, register, confirm, recovery, recoveryCode, newPassword, registrationPhoto
    var id: Self { self }
}

struct AuthUiState {
    var step: AuthStep
    var history: [AuthStep] = []
    var email = ""
    var password = ""
    var repeatPassword = ""
    var code = ""
    var isBusy = false
    var errorKey: String?
    var canRetryCodeVerification = false
    var needsConfirmation = false
    var resendSeconds = 0
    var isComplete = false
    var passwordWasChanged = false
    var details = PersonalDetails()
    var photo: Data?
    var isPreparingPhoto = false
}
