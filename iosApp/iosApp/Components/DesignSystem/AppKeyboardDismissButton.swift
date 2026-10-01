import SwiftUI

/// The keyboard action is visually an icon while retaining a localized VoiceOver name.
struct AppKeyboardDismissButton: View {
    var body: some View {
        Button(action: AppKeyboardAccessory.dismiss) {
            Label("common.hideKeyboard", systemImage: AppKeyboardAccessory.dismissSymbol)
                .labelStyle(.iconOnly)
                .frame(minWidth: 44, minHeight: 44)
        }
        .padding(.bottom, AppKeyboardAccessory.bottomSpacing)
    }
}

/// Shared native accessory configuration for SwiftUI fields and the stable UIKit password input.
@MainActor
enum AppKeyboardAccessory {
    static let dismissSymbol = "keyboard.chevron.compact.down"
    static let bottomSpacing: CGFloat = 8

    static func dismiss() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    static func makeToolbar(target: AnyObject, action: Selector) -> UIView {
        let toolbar = UIToolbar()
        let dismissItem = UIBarButtonItem(
            image: UIImage(systemName: dismissSymbol), style: .plain, target: target, action: action)
        dismissItem.accessibilityLabel = String(localized: "common.hideKeyboard")
        toolbar.items = [UIBarButtonItem(systemItem: .flexibleSpace), dismissItem]
        toolbar.sizeToFit()
        let accessory = UIView(
            frame: CGRect(x: 0, y: 0, width: toolbar.bounds.width, height: toolbar.bounds.height + bottomSpacing))
        accessory.autoresizingMask = [.flexibleWidth]
        toolbar.autoresizingMask = [.flexibleWidth]
        accessory.addSubview(toolbar)
        return accessory
    }
}
