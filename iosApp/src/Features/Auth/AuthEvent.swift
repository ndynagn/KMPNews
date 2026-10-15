import Foundation

enum AuthEvent {
    case email(String)
    case password(String)
    case repeatPassword(String)
    case code(String)
    case submit, resend, confirmEmail, recovery, returnToLogin, back
    case details(PersonalDetails)
    case photo(Data?)
    case preparingPhoto(Bool)
    case photoFailed, skipPhoto
}
