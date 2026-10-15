import SwiftUI

struct ProfileEditScreen: View {
    let viewModel: ProfileEditViewModel
    let onSaved: (AccountProfile) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var showsDiscard = false

    var body: some View {
        NavigationStack {
            Form {
                Section("profile.personalDetails") {
                    ProfileNameFields(
                        details: Binding(
                            get: { viewModel.details }, set: viewModel.changeDetails))
                }
                Section("profile.photo") {
                    if viewModel.photo == nil && !viewModel.removesPhoto {
                        ProfileAvatar(profile: viewModel.original, generation: viewModel.generation, size: 88)
                            .frame(maxWidth: .infinity)
                    }
                    ProfilePhotoField(
                        photo: viewModel.photo, onPhoto: viewModel.changePhoto,
                        onLoading: viewModel.preparingPhoto, onError: viewModel.photoFailed,
                        hasExistingPhoto: viewModel.original.hasAvatar && !viewModel.removesPhoto)
                    if viewModel.original.hasAvatar && viewModel.photo == nil && !viewModel.removesPhoto {
                        Button("profile.removePhoto", role: .destructive) { viewModel.changePhoto(nil) }
                            .accessibilityIdentifier("profile.removePhoto")
                    }
                    if viewModel.isPreparingPhoto { ProgressView("profile.preparingPhoto") }
                }
                Section("profile.contacts") { Text(viewModel.original.email).textSelection(.enabled) }
                if let error = viewModel.error {
                    Section {
                        Text(LocalizedStringKey(error)).foregroundStyle(.red)
                            .accessibilityIdentifier("profile.edit.error")
                    }
                }
            }
            .disabled(viewModel.isSaving)
            .scrollDismissesKeyboard(.interactively)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("profile.cancel") {
                        if viewModel.hasChanges { showsDiscard = true } else { dismiss() }
                    }
                    .accessibilityIdentifier("profile.edit.cancel")
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: viewModel.save) {
                        if viewModel.isSaving { ProgressView() } else { Text("profile.save") }
                    }
                    .disabled(viewModel.isSaving || viewModel.isPreparingPhoto || !viewModel.hasChanges)
                    .accessibilityLabel(viewModel.isSaving ? "common.loading" : "profile.save")
                    .accessibilityIdentifier("profile.edit.save")
                }
            }
            .alert("profile.discardTitle", isPresented: $showsDiscard) {
                Button("profile.cancel", role: .cancel) {}
                Button("profile.discard", role: .destructive) { dismiss() }
                    .accessibilityIdentifier("profile.discard")
            }
        }
        .interactiveDismissDisabled(viewModel.hasChanges || viewModel.isSaving)
        .onChange(of: viewModel.saved) { _, profile in
            if let profile {
                onSaved(profile)
                dismiss()
            }
        }
        .onDisappear { viewModel.cancel() }
        .errorFeedback(viewModel.errorFeedback)
    }
}
