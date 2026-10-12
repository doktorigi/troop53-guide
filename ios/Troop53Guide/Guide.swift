import Foundation

enum BlockKind: String, Hashable { case paragraph, heading, bullet, step, warning, tip, image }

struct GuideBlock: Hashable {
    let kind: BlockKind
    let text: String
    var number: Int = 0
    var imagePath: String? = nil
}

struct GuideArticle: Identifiable, Hashable {
    let id: String
    let title: String
    let summary: String
    let categoryID: String
    let blocks: [GuideBlock]
    var searchText: String { ([title, summary] + blocks.map(\.text)).joined(separator: " ").lowercased() }
    var practiceDiagrams: [GuideBlock] { blocks.filter { $0.kind == .image && $0.imagePath?.hasPrefix("knots/") == true } }
}

struct GuideCategory: Identifiable, Hashable {
    let id: String
    let title: String
    let icon: String
    let colorHex: String
    let subtitle: String
    let articles: [GuideArticle]
}

struct QuizQuestion: Identifiable, Hashable {
    var id: String { prompt }
    let prompt: String
    let answer: String
    let wrong: [String]
    let explanation: String
}

enum GuideError: LocalizedError {
    case malformed(String)
    var errorDescription: String? {
        switch self { case .malformed(let message): return message }
    }
}

enum GuideParser {
    static func validImagePath(_ path: String) -> Bool {
        path.range(of: #"^[a-z0-9_-]+(?:/[a-z0-9_-]+)*\.(webp|png|jpg|jpeg|xml)$"#, options: .regularExpression) != nil
    }

    static func parse(_ text: String) throws -> [GuideCategory] {
        var categories: [GuideCategory] = []
        var category: [String]? = nil
        var article: [String]? = nil
        var articles: [GuideArticle] = []
        var blocks: [GuideBlock] = []
        func fields(_ line: String, count: Int) throws -> [String] {
            let values = line.split(separator: "|", omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces) }
            guard values.count == count, values.allSatisfy({ !$0.isEmpty }) else { throw GuideError.malformed("Expected \(count) fields: \(line)") }
            return values
        }
        func closeArticle() throws {
            guard let a = article else { return }
            guard let c = category else { throw GuideError.malformed("Article outside a section: \(a[0])") }
            articles.append(GuideArticle(id: a[0], title: a[1], summary: a[2], categoryID: c[0], blocks: blocks))
            article = nil; blocks = []
        }
        func closeCategory() throws {
            try closeArticle()
            guard let c = category else { return }
            guard c[3].range(of: #"^#[0-9A-Fa-f]{6}$"#, options: .regularExpression) != nil else { throw GuideError.malformed("Invalid section color: \(c[3])") }
            categories.append(GuideCategory(id: c[0], title: c[1], icon: c[2], colorHex: c[3], subtitle: c[4], articles: articles))
            category = nil; articles = []
        }
        for raw in text.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty { continue }
            if line.hasPrefix("===") {
                try closeCategory(); category = try fields(String(line.dropFirst(3)), count: 5)
            } else if line.hasPrefix("# ") {
                try closeArticle(); article = try fields(String(line.dropFirst(2)), count: 3)
            } else {
                guard article != nil else { throw GuideError.malformed("Content outside an article: \(line)") }
                if line.hasPrefix("## ") { blocks.append(GuideBlock(kind: .heading, text: String(line.dropFirst(3)))) }
                else if line.hasPrefix("- ") { blocks.append(GuideBlock(kind: .bullet, text: String(line.dropFirst(2)))) }
                else if line.hasPrefix("! ") { blocks.append(GuideBlock(kind: .warning, text: String(line.dropFirst(2)))) }
                else if line.hasPrefix("> ") { blocks.append(GuideBlock(kind: .tip, text: String(line.dropFirst(2)))) }
                else if line.hasPrefix("@ ") {
                    let parts = line.dropFirst(2).split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces) }
                    guard parts.count == 2, validImagePath(parts[0]), !parts[1].isEmpty else { throw GuideError.malformed("Invalid image block: \(line)") }
                    blocks.append(GuideBlock(kind: .image, text: parts[1], imagePath: parts[0]))
                } else if let dot = line.firstIndex(of: "."), let number = Int(line[..<dot]), number >= 0,
                          line.index(after: dot) < line.endIndex, line[line.index(after: dot)].isWhitespace {
                    blocks.append(GuideBlock(kind: .step, text: String(line[line.index(after: dot)...]).trimmingCharacters(in: .whitespaces), number: number))
                } else { blocks.append(GuideBlock(kind: .paragraph, text: line)) }
            }
        }
        try closeCategory()
        return categories
    }

    static func parseQuiz(_ text: String) throws -> [QuizQuestion] {
        let normalized = text.replacingOccurrences(of: "\r\n", with: "\n")
        let chunks = normalized.components(separatedBy: "\n\n").filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        return try chunks.map { chunk in
            let lines = chunk.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }
            func values(_ prefix: String) -> [String] { lines.filter { $0.hasPrefix(prefix) }.map { String($0.dropFirst(prefix.count)).trimmingCharacters(in: .whitespaces) } }
            let prompts = values("Q:"); let answers = values("A:"); let wrong = values("W:"); let explanations = values("E:")
            guard prompts.count == 1, answers.count == 1, wrong.count == 3, explanations.count == 1,
                  Set(wrong + answers).count == 4, !prompts[0].isEmpty, !explanations[0].isEmpty else { throw GuideError.malformed("Malformed quiz question") }
            return QuizQuestion(prompt: prompts[0], answer: answers[0], wrong: wrong, explanation: explanations[0])
        }
    }
}

struct Guide {
    let categories: [GuideCategory]
    var articles: [GuideArticle] { categories.flatMap(\.articles) }
    func article(_ id: String) -> GuideArticle? { articles.first { $0.id == id } }
    func category(_ id: String) -> GuideCategory? { categories.first { $0.id == id } }
    func search(_ query: String) -> [GuideArticle] {
        let words = query.lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
        guard !words.isEmpty else { return [] }
        return articles.enumerated().filter { _, article in words.allSatisfy { article.searchText.contains($0) } }
            .sorted { first, second in
                let firstScore = words.filter { first.element.title.lowercased().contains($0) }.count
                let secondScore = words.filter { second.element.title.lowercased().contains($0) }.count
                return firstScore == secondScore ? first.offset < second.offset : firstScore > secondScore
            }.map(\.element)
    }

    static func load(bundle: Bundle) throws -> (Guide, [QuizQuestion]) {
        guard let folder = bundle.resourceURL?.appendingPathComponent("guide"),
              let quizURL = bundle.url(forResource: "quiz", withExtension: "txt") else { throw GuideError.malformed("Bundled guide resources are missing") }
        let files = try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "txt" }.sorted { $0.lastPathComponent < $1.lastPathComponent }
        let categories = try files.flatMap { try GuideParser.parse(String(contentsOf: $0, encoding: .utf8)) }
        let guide = Guide(categories: categories)
        guard categories.count == 15, Set(categories.map(\.id)).count == categories.count,
              Set(guide.articles.map(\.id)).count == guide.articles.count else { throw GuideError.malformed("Missing or duplicate guide content") }
        return (guide, try GuideParser.parseQuiz(String(contentsOf: quizURL, encoding: .utf8)))
    }
}

struct QuizRound {
    private(set) var questions: [QuizQuestion] = []
    private(set) var index = 0
    private(set) var score = 0
    private(set) var selected: String? = nil
    private(set) var options: [String] = []
    var current: QuizQuestion? { questions.indices.contains(index) ? questions[index] : nil }
    var finished: Bool { !questions.isEmpty && index >= questions.count }
    mutating func restart(with bank: [QuizQuestion]) {
        questions = Array(bank.shuffled().prefix(10)); index = 0; score = 0; prepare()
    }
    mutating func answer(_ option: String) {
        guard selected == nil, let question = current, options.contains(option) else { return }
        selected = option
        if option == question.answer { score += 1 }
    }
    mutating func next() {
        guard selected != nil else { return }
        index += 1; prepare()
    }
    private mutating func prepare() { selected = nil; options = current.map { ($0.wrong + [$0.answer]).shuffled() } ?? [] }
}
