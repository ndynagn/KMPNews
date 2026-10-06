import PhotosUI
import SwiftUI

/// Shared by registration and profile editing. The picker task belongs to this field's lifetime.
struct ProfilePhotoField: View {
    let photo: Data?
    let onPhoto: (Data?) -> Void
    let onLoading: (Bool) -> Void
    let onError: () -> Void
    var hasExistingPhoto = false
    @State private var selection: PhotosPickerItem?

    var body: some View {
        VStack(spacing: 12) {
            if let photo, let image = UIImage(data: photo) {
                Image(uiImage: image).resizable().scaledToFill()
                    .frame(width: 88, height: 88).clipShape(RoundedRectangle(cornerRadius: 12))
                    .accessibilityLabel("profile.photo")
            }
            PhotosPicker(selection: $selection, matching: .images, photoLibrary: .shared()) {
                Label(
                    photo == nil && !hasExistingPhoto ? "profile.choosePhoto" : "profile.replacePhoto",
                    systemImage: "photo"
                )
                .frame(minHeight: 44)
            }
            .accessibilityIdentifier("profile.choosePhoto")
            if photo != nil {
                Button("profile.removePhoto", role: .destructive) {
                    selection = nil
                    onLoading(false)
                    onPhoto(nil)
                }
                .frame(minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity)
        .task(id: selection) {
            guard let selection else {
                onLoading(false)
                return
            }

            onLoading(true)
            defer { if self.selection == selection { onLoading(false) } }

            do {
                guard let data = try await selection.loadTransferable(type: Data.self) else {
                    if !Task.isCancelled { onError() }
                    return
                }

                let prepared = await Task.detached(priority: .userInitiated) { ProfilePhotoProcessor.prepare(data) }
                    .value

                guard !Task.isCancelled else { return }

                if let prepared { onPhoto(prepared) } else { onError() }
            } catch {
                if !Task.isCancelled { onError() }
            }
        }
    }
}
