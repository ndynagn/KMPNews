import SwiftUI

/// Native field presentation is confined to Search. Window selection is the explicit focus trigger.
struct SearchNavigationDestination<Content: View>: View {
    @Binding var query: String
    let isSelected: Bool
    let usesSearchRole: Bool
    @Binding var articlePath: [ArticleRoute]
    var isShowingArticle = false
    let onClose: () -> Void
    let onSubmit: () -> Void
    @ViewBuilder var content: () -> Content
    @State private var fieldText = ""
    @State private var isPresented = false
    @FocusState private var isFocused: Bool

    var body: some View {
        NavigationStack(path: $articlePath) {
            content()
                .navigationTitle("search.title")
                .searchable(
                    text: $fieldText, isPresented: searchPresentation, placement: placement, prompt: "search.prompt"
                )
                .searchPresentationToolbarBehavior(.avoidHidingContent)
                .searchFocused($isFocused)
        }
        .onSubmit(of: .search) {
            query = fieldText
            onSubmit()
        }
        .onChange(of: isSelected, initial: true) { _, selected in
            if isShowingArticle {
                isPresented = false
                isFocused = false
                return
            }
            if selected { fieldText = query }
            if isPhone && !usesSearchRole {
                isPresented = selected
                isFocused = selected
            }
        }
        .onChange(of: fieldText) { _, text in
            if isSelected, !isShowingArticle, isPresented || !isPhone { query = text }
        }
        .onChange(of: isPresented) { previous, presented in
            if isPhone, isSelected, !isShowingArticle, previous, !presented { onClose() }
        }
        .onChange(of: isShowingArticle) { _, showing in
            if showing {
                isFocused = false
                isPresented = false
            } else {
                fieldText = query
                isFocused = false
            }
        }
    }

    private var isPhone: Bool { UIDevice.current.userInterfaceIdiom == .phone }
    private var searchPresentation: Binding<Bool> {
        Binding(
            get: { isPresented && !isShowingArticle },
            set: { isPresented = $0 && !isShowingArticle })
    }
    private var placement: SearchFieldPlacement {
        isPhone && usesSearchRole ? .automatic : .navigationBarDrawer(displayMode: .always)
    }
}

struct SearchTabActivation: ViewModifier {
    let usesSearchRole: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            content.tabViewSearchActivation(
                usesSearchRole && UIDevice.current.userInterfaceIdiom == .phone ? .searchTabSelection : .automatic)
        } else {
            content
        }
    }
}
