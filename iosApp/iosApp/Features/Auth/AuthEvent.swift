import Foundation

enum AuthEvent {
    case email(String), password(String), repeatPassword(String), code(String)
    case submit, resend, confirmEmail, recovery, returnToLogin, back
    case details(PersonalDetails), photo(Data?), preparingPhoto(Bool), photoFailed, skipPhoto
}
