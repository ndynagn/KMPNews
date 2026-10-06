import SafariServices
import SwiftUI

/// Hosts system controllers modally and anchors iPad sharing to the toolbar menu.
struct ArticleActionPresenter: UIViewRepresentable {
    let action: ArticleDetailAction?
    let anchor: ArticleActionAnchor.Reference
    let onDismiss: () -> Void

    func makeUIView(context: Context) -> Presenter { Presenter() }

    func updateUIView(_ presenter: Presenter, context: Context) {
        presenter.requestedAction = action
        presenter.anchor = anchor.view
        presenter.onDismiss = onDismiss
        presenter.presentIfReady()
    }

    static func dismantleUIView(_ presenter: Presenter, coordinator: ()) {
        presenter.onDismiss = {}
        presenter.presentedController?.dismiss(animated: false)
    }

    final class Presenter: UIView, SFSafariViewControllerDelegate, UIPopoverPresentationControllerDelegate {
        var requestedAction: ArticleDetailAction?
        var onDismiss: () -> Void = {}
        private var isPresenting = false
        weak var presentedController: UIViewController?
        weak var anchor: UIView?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            presentIfReady()
        }

        func presentIfReady() {
            guard window != nil, !isPresenting, presentedController == nil,
                let requestedAction, let anchor, anchor.window != nil,
                let host = sequence(first: next, next: { $0?.next })
                    .compactMap({ $0 as? UIViewController }).first,
                host.presentedViewController == nil
            else { return }

            let controller: UIViewController
            switch requestedAction {
            case .source(let url):
                let browser = SFSafariViewController(url: url)
                browser.modalPresentationStyle = .pageSheet
                browser.delegate = self
                controller = browser
            case .share(let url):
                let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
                activity.completionWithItemsHandler = { [weak self] _, _, _, _ in self?.didClose() }
                activity.popoverPresentationController?.sourceView = anchor
                activity.popoverPresentationController?.sourceRect = anchor.bounds
                activity.popoverPresentationController?.delegate = self
                controller = activity
            }
            isPresenting = true
            presentedController = controller
            host.present(controller, animated: true)
            controller.presentationController?.delegate = self
        }

        func safariViewControllerDidFinish(_ controller: SFSafariViewController) {
            requestedAction = nil
            controller.dismiss(animated: true) { [weak self] in self?.didClose() }
        }

        func presentationControllerDidDismiss(_ presentationController: UIPresentationController) { didClose() }

        private func didClose() {
            guard isPresenting else { return }
            isPresenting = false
            presentedController = nil
            requestedAction = nil
            onDismiss()
        }
    }
}

/// Only the anchor belongs to the toolbar; the presenter lives with the reading page.
struct ArticleActionAnchor: UIViewRepresentable {
    final class Reference {
        weak var view: UIView?
    }

    let reference: Reference

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        reference.view = view
        return view
    }

    func updateUIView(_ view: UIView, context: Context) { reference.view = view }
}
