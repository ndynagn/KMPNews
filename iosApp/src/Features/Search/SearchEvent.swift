enum SearchEvent {
    case queryChanged(String)
    case submit, loadMore, retry, activate, deactivate
}
