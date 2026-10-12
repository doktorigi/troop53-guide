import SwiftUI

@MainActor
final class GuideStore: ObservableObject {
    @Published private(set) var guide = Guide(categories: [])
    @Published private(set) var questions: [QuizQuestion] = []
    @Published private(set) var saved: Set<String>
    @Published var quiz = QuizRound()
    @Published private(set) var loadError: String? = nil
    private let defaults: UserDefaults
    private let bundle: Bundle
    static let savedKey = "troop53.savedArticles"

    init(bundle: Bundle = .main, defaults: UserDefaults = .standard) {
        self.bundle = bundle; self.defaults = defaults
        saved = ProcessInfo.processInfo.arguments.contains("--ui-testing") ? [] : Set(defaults.stringArray(forKey: Self.savedKey) ?? [])
        load()
    }
    func load() {
        do {
            (guide, questions) = try Guide.load(bundle: bundle)
            loadError = nil
            saved.formIntersection(Set(guide.articles.map(\.id)))
            if quiz.questions.isEmpty { quiz.restart(with: questions) }
        } catch { loadError = error.localizedDescription }
    }
    func toggleSaved(_ id: String) {
        if saved.contains(id) { saved.remove(id) } else { saved.insert(id) }
        defaults.set(saved.sorted(), forKey: Self.savedKey)
    }
    func answer(_ option: String) { quiz.answer(option) }
    func nextQuestion() { quiz.next() }
    func restartQuiz() { quiz.restart(with: questions) }
}
