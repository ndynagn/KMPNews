import SwiftUI

struct ProfileNameFields: View {
    @Binding var details: PersonalDetails
    var surnameFocus: FocusState<Bool>.Binding?

    var body: some View {
        if let surnameFocus { surname.focused(surnameFocus) } else { surname }
        TextField("profile.firstName", text: $details.firstName)
            .textContentType(.givenName).accessibilityIdentifier("profile.firstName")
        TextField("profile.middleName", text: $details.middleName)
            .textContentType(.middleName).accessibilityIdentifier("profile.middleName")
    }

    private var surname: some View {
        TextField("profile.lastName", text: $details.lastName)
            .textContentType(.familyName).accessibilityIdentifier("profile.lastName")
    }
}
