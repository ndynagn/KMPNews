import Foundation

@main
struct ArticleDetailPresentationTests {
    @MainActor static func main() {
        let article = FeedArticle(
            id: "one", title: "Headline", summary: "  Summary\n", imageURL: nil,
            source: nil, publishedAt: nil, articleURL: "https://example.com/news")
        let navigation = ArticleNavigationState()
        navigation.accountChanged(.authenticated(email: "one@example.test"))
        navigation.open(article, in: .news)
        guard let news = navigation.destinations[.news] else { fatalError("News must open") }
        navigation.open(article, in: .news)
        precondition(navigation.destinations[.news] === news, "Repeated taps must not replace the current reader")
        navigation.open(article, in: .search)
        navigation.open(article, in: .favorites)
        navigation.open(article, in: .profile)
        precondition(navigation.destinations.count == 3)
        precondition(news.article == article && news.summary == "Summary")

        guard let sourceURL = article.openingURL else { fatalError("Fixture URL must be valid") }
        news.openSource()
        precondition(news.presentedAction == .source(sourceURL))
        news.share()
        precondition(news.presentedAction == .source(sourceURL), "An active source excludes sharing")
        news.dismissAction()
        news.share()
        precondition(news.presentedAction == .share(sourceURL))
        news.dismissAction()

        navigation.close(.search)
        precondition(navigation.destinations[.news] === news && navigation.destinations[.favorites] != nil)
        navigation.accountChanged(.unavailable(.network))
        precondition(navigation.destinations[.favorites] != nil, "Transient account errors must not erase navigation")
        navigation.accountChanged(.authenticated(email: "two@example.test"))
        precondition(navigation.destinations[.favorites] == nil && navigation.destinations[.news] === news)
        navigation.open(article, in: .favorites)
        navigation.accountChanged(.guest)
        precondition(navigation.destinations[.favorites] == nil && navigation.destinations[.news] === news)

        for url in [nil, "file:///tmp/story", "javascript:alert(1)", "https://", "bad URL"] {
            let missing = FeedArticle(
                id: "missing", title: nil, summary: " \n", imageURL: nil, source: nil, publishedAt: nil,
                articleURL: url)
            let model = ArticleDetailViewModel(article: missing)
            model.openSource()
            model.share()
            precondition(model.presentedAction == nil && model.summary == nil)
        }
        print("PASS: section routes, duplicate taps, account isolation, snapshot and mutually exclusive URL actions")
    }
}
