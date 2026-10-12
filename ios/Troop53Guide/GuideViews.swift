import SwiftUI

enum GuideRoute: Hashable { case category(String), article(String), saved, quiz, about }

struct RootView: View {
    @EnvironmentObject private var store: GuideStore
    @State private var path: [GuideRoute] = []
    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if let error = store.loadError {
                    VStack(spacing: 20) {
                        Text("The guide could not be loaded.").font(.headline)
                        Text(error).font(.callout)
                        Button("Try again") { store.load() }
                    }.padding()
                } else { HomeView() }
            }
            .navigationDestination(for: GuideRoute.self) { route in
                Group {
                    switch route {
                    case .category(let id):
                        if let category = store.guide.category(id) { CategoryView(category: category) }
                    case .article(let id):
                        if let article = store.guide.article(id) { ArticleView(article: article) }
                    case .saved: SavedView()
                    case .quiz: QuizView()
                    case .about: AboutView()
                    }
                }
                .toolbar { ToolbarItem(placement: .navigationBarTrailing) {
                    Button { path = [] } label: { Image(systemName: "house") }.accessibilityLabel("Home").accessibilityIdentifier("home-button")
                } }
            }
        }
    }
}

struct HomeView: View {
    @EnvironmentObject private var store: GuideStore
    @State private var query = ""
    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 12)]
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(spacing: 16) {
                    TroopEmblem().frame(width: 68, height: 80)
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Troop 53").font(.largeTitle.bold())
                        Text("Scout Guide").font(.title2)
                        Text("Built by Benjamin Doktor").font(.caption).italic()
                    }
                }
                .foregroundStyle(TroopTheme.paper).frame(maxWidth: .infinity, alignment: .leading)
                .padding(20).background(TroopTheme.forest, in: RoundedRectangle(cornerRadius: 20))
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(TroopTheme.muted)
                    TextField("Search the guide", text: $query).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .accessibilityIdentifier("guide-search")
                    if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill") }.accessibilityLabel("Clear search") }
                }.troopCard()
                if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    EmergencyCard()
                    HStack {
                        NavigationLink(value: GuideRoute.saved) { Label("Saved", systemImage: "bookmark.fill").frame(maxWidth: .infinity) }.accessibilityIdentifier("saved-guides")
                        NavigationLink(value: GuideRoute.quiz) { Label("Quiz", systemImage: "questionmark.circle.fill").frame(maxWidth: .infinity) }.accessibilityIdentifier("quiz-link")
                    }.buttonStyle(.bordered)
                    Text("Field Guide").font(.title2.bold()).foregroundStyle(TroopTheme.forest)
                    LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
                        ForEach(store.guide.categories) { category in
                            NavigationLink(value: GuideRoute.category(category.id)) {
                                VStack(alignment: .leading, spacing: 10) {
                                    Text(category.icon).font(.title).padding(8).background(Color(hex: category.colorHex), in: RoundedRectangle(cornerRadius: 12))
                                    Text(category.title).font(.headline).foregroundStyle(TroopTheme.ink)
                                    Text("\(category.articles.count) guides").font(.caption).foregroundStyle(TroopTheme.muted)
                                    Spacer(minLength: 0)
                                }.frame(maxWidth: .infinity, minHeight: 125, alignment: .leading).troopCard()
                            }.buttonStyle(.plain).accessibilityIdentifier("category-\(category.id)")
                        }
                    }
                    FooterView()
                } else {
                    let results = store.guide.search(query)
                    Text("\(results.count) results").font(.headline).accessibilityIdentifier("search-count")
                    ForEach(results) { ArticleRow(article: $0) }
                    if results.isEmpty { Text("Try another word or phrase.").foregroundStyle(TroopTheme.muted) }
                }
            }.padding(16).frame(maxWidth: 900).frame(maxWidth: .infinity)
        }.background(TroopTheme.paper).navigationTitle("Troop 53").navigationBarTitleDisplayMode(.inline)
    }
}

struct EmergencyCard: View {
    @Environment(\.openURL) private var openURL
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("EMERGENCY").font(.caption.bold()).tracking(2)
            Text("Check the scene · Call 911 · Care").font(.headline)
            HStack {
                Button { openURL(URL(string: "tel:911")!) } label: { Label("Call 911", systemImage: "phone.fill").frame(maxWidth: .infinity) }
                Button { openURL(URL(string: "tel:18002221222")!) } label: { Text("Poison Control").frame(maxWidth: .infinity) }
            }
            HStack {
                NavigationLink("Hurry cases", value: GuideRoute.article("hurry-cases"))
                Spacer()
                NavigationLink("CPR", value: GuideRoute.article("cpr"))
                Spacer()
                NavigationLink("Bleeding", value: GuideRoute.article("bleeding"))
            }
        }
        .foregroundStyle(.white).buttonStyle(.bordered).tint(.white)
        .padding(16).background(TroopTheme.red, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct ArticleRow: View {
    let article: GuideArticle
    var body: some View {
        NavigationLink(value: GuideRoute.article(article.id)) {
            HStack {
                VStack(alignment: .leading, spacing: 5) {
                    Text(article.title).font(.headline).foregroundStyle(TroopTheme.ink)
                    Text(article.summary).font(.subheadline).foregroundStyle(TroopTheme.muted)
                }
                Spacer(minLength: 12)
                Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(TroopTheme.forest)
            }.frame(maxWidth: .infinity, alignment: .leading).troopCard()
        }.buttonStyle(.plain).accessibilityIdentifier("article-\(article.id)")
    }
}

struct CategoryView: View {
    let category: GuideCategory
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                Text(category.title).font(.largeTitle.bold()).foregroundStyle(Color(hex: category.colorHex))
                Text(category.subtitle).foregroundStyle(TroopTheme.muted)
                ArtworkCard(path: "headers/\(category.id).xml", caption: "\(category.title) field illustration")
                ForEach(category.articles) { ArticleRow(article: $0) }
            }.padding(16).frame(maxWidth: 800).frame(maxWidth: .infinity)
        }.background(TroopTheme.paper).navigationTitle(category.title).navigationBarTitleDisplayMode(.inline)
    }
}

struct ArticleView: View {
    let article: GuideArticle
    @EnvironmentObject private var store: GuideStore
    @State private var practicing = false
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                Text(article.title).font(.largeTitle.bold()).foregroundStyle(TroopTheme.forest)
                Text(article.summary).foregroundStyle(TroopTheme.muted)
                HStack {
                    Button { store.toggleSaved(article.id) } label: {
                        Label(store.saved.contains(article.id) ? "Saved" : "Save guide", systemImage: store.saved.contains(article.id) ? "bookmark.fill" : "bookmark")
                    }.accessibilityIdentifier("save-article").accessibilityValue(store.saved.contains(article.id) ? "Saved" : "Not saved")
                    if !article.practiceDiagrams.isEmpty {
                        Button("Knot Practice") { practicing = true }.accessibilityIdentifier("knot-practice")
                    }
                }.buttonStyle(.bordered)
                ForEach(Array(article.blocks.enumerated()), id: \.offset) { _, block in BlockView(block: block) }
                articleNavigation
                Text("Troop 53 Scout Guide · Built by Benjamin Doktor").font(.caption).foregroundStyle(TroopTheme.muted).padding(.top)
            }.padding(20).frame(maxWidth: 800).frame(maxWidth: .infinity)
        }
        .background(TroopTheme.paper).navigationTitle(article.title).navigationBarTitleDisplayMode(.inline).id(article.id)
        .sheet(isPresented: $practicing) { KnotPracticeView(article: article) }
    }
    @ViewBuilder private var articleNavigation: some View {
        if let category = store.guide.category(article.categoryID), let index = category.articles.firstIndex(where: { $0.id == article.id }) {
            Divider().padding(.vertical)
            HStack(alignment: .top) {
                if index > 0 { NavigationLink(value: GuideRoute.article(category.articles[index - 1].id)) { Label(category.articles[index - 1].title, systemImage: "chevron.left").font(.subheadline) } }
                Spacer()
                if index + 1 < category.articles.count { NavigationLink(value: GuideRoute.article(category.articles[index + 1].id)) { Label(category.articles[index + 1].title, systemImage: "chevron.right").font(.subheadline) } }
            }
        }
    }
}

struct BlockView: View {
    let block: GuideBlock
    var body: some View {
        Group {
            switch block.kind {
            case .image:
                if let path = block.imagePath { ArtworkCard(path: path, caption: block.text) }
            case .heading: Text(block.text).font(.title2.bold()).foregroundStyle(TroopTheme.forest).padding(.top, 8).accessibilityAddTraits(.isHeader)
            case .bullet: HStack(alignment: .firstTextBaseline, spacing: 10) { Text("•").foregroundStyle(TroopTheme.forest); Text(block.text) }
            case .step: HStack(alignment: .firstTextBaseline, spacing: 10) { Text("\(block.number).").fontWeight(.bold).foregroundStyle(TroopTheme.red); Text(block.text) }
            case .warning, .tip:
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: block.kind == .warning ? "exclamationmark.triangle.fill" : "lightbulb.fill").foregroundStyle(block.kind == .warning ? TroopTheme.red : TroopTheme.forest)
                    Text(block.text)
                }.padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(block.kind == .warning ? Color(hex: "#FFF0E7") : Color(hex: "#E9EFDF"), in: RoundedRectangle(cornerRadius: 12))
            case .paragraph: Text(block.text)
            }
        }.foregroundStyle(TroopTheme.ink).fixedSize(horizontal: false, vertical: true)
    }
}

struct KnotPracticeView: View {
    let article: GuideArticle
    @State private var index = 0
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("Step \(index + 1) of \(article.practiceDiagrams.count)").font(.headline).accessibilityIdentifier("practice-step")
                ScrollView {
                    let block = article.practiceDiagrams[index]
                    ArtworkCard(path: block.imagePath!, caption: block.text, identifierPrefix: "practice-image").id(index).padding()
                }
                HStack {
                    Button("Back") { index -= 1 }.disabled(index == 0).frame(maxWidth: .infinity).accessibilityIdentifier("practice-back")
                    Button(index + 1 == article.practiceDiagrams.count ? "Done" : "Next") {
                        if index + 1 == article.practiceDiagrams.count { dismiss() } else { index += 1 }
                    }.frame(maxWidth: .infinity).accessibilityIdentifier("practice-next")
                }.buttonStyle(.borderedProminent).controlSize(.large).padding()
            }.padding(.top).background(TroopTheme.paper).navigationTitle(article.title).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .navigationBarLeading) { Button("Close") { dismiss() }.accessibilityIdentifier("close-practice") } }
        }
    }
}

struct SavedView: View {
    @EnvironmentObject private var store: GuideStore
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                Text("Saved Guides").font(.largeTitle.bold()).foregroundStyle(TroopTheme.forest)
                let articles = store.guide.articles.filter { store.saved.contains($0.id) }
                if articles.isEmpty { Text("Tap Save guide in any article to keep it here.").foregroundStyle(TroopTheme.muted) }
                ForEach(articles) { ArticleRow(article: $0) }
            }.padding(20).frame(maxWidth: 800).frame(maxWidth: .infinity)
        }.background(TroopTheme.paper).navigationTitle("Saved Guides").navigationBarTitleDisplayMode(.inline)
    }
}

struct QuizView: View {
    @EnvironmentObject private var store: GuideStore
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Scout Quiz").font(.largeTitle.bold()).foregroundStyle(TroopTheme.forest)
                if store.quiz.finished {
                    Text("\(store.quiz.score) out of \(store.quiz.questions.count)").font(.title.bold()).accessibilityIdentifier("quiz-score")
                    Text("Keep practicing. Every outing is a chance to learn.")
                    Button("Try another round") { store.restartQuiz() }.buttonStyle(.borderedProminent)
                } else if let question = store.quiz.current {
                    Text("Question \(store.quiz.index + 1) of \(store.quiz.questions.count) · Score \(store.quiz.score)").font(.subheadline).foregroundStyle(TroopTheme.muted).accessibilityIdentifier("quiz-progress")
                    Text(question.prompt).font(.title2.bold())
                    ForEach(Array(store.quiz.options.enumerated()), id: \.offset) { index, option in
                        Button { store.answer(option) } label: {
                            HStack {
                                Text(option).multilineTextAlignment(.leading)
                                Spacer()
                                if store.quiz.selected != nil && option == question.answer { Image(systemName: "checkmark.circle.fill").foregroundStyle(TroopTheme.forest) }
                                else if option == store.quiz.selected { Image(systemName: "xmark.circle.fill").foregroundStyle(TroopTheme.red) }
                            }.frame(maxWidth: .infinity, alignment: .leading).troopCard()
                        }.buttonStyle(.plain).disabled(store.quiz.selected != nil).accessibilityIdentifier("quiz-option-\(index)")
                    }
                    if let selected = store.quiz.selected {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(selected == question.answer ? "Correct!" : "The answer is: \(question.answer)").font(.headline)
                            Text(question.explanation).accessibilityIdentifier("quiz-explanation")
                        }.troopCard()
                        Button(store.quiz.index + 1 == store.quiz.questions.count ? "See score" : "Next question") { store.nextQuestion() }
                            .buttonStyle(.borderedProminent).accessibilityIdentifier("quiz-next")
                    }
                }
            }.padding(20).frame(maxWidth: 800).frame(maxWidth: .infinity)
        }.background(TroopTheme.paper).navigationTitle("Scout Quiz").navigationBarTitleDisplayMode(.inline)
    }
}

struct FooterView: View {
    var body: some View {
        VStack(spacing: 8) {
            TroopEmblem().frame(width: 40, height: 46)
            Text("Troop 53 Scout Guide").font(.headline).foregroundStyle(TroopTheme.forest)
            Text("Built by Benjamin Doktor").font(.caption).foregroundStyle(TroopTheme.muted)
            NavigationLink("About this app", value: GuideRoute.about)
        }.padding(.vertical, 20).frame(maxWidth: .infinity)
    }
}

struct AboutView: View {
    @EnvironmentObject private var store: GuideStore
    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                TroopEmblem().frame(width: 120, height: 138)
                Text("Troop 53 Scout Guide").font(.title.bold()).foregroundStyle(TroopTheme.forest)
                Text("Built by Benjamin Doktor").font(.headline).foregroundStyle(TroopTheme.red)
                Text("Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0.0") · \(store.guide.categories.count) sections · \(store.guide.articles.count) guides · \(store.questions.count) quiz questions").font(.caption).foregroundStyle(TroopTheme.muted)
                VStack(alignment: .leading, spacing: 16) {
                    Text("A pocket field guide for the Scouts of Troop 53: first aid, the Scout Oath and Law, camping, cooking, knots, navigation, survival, and more. Everything works offline, so it is ready on the trail.")
                    Text("This is an unofficial guide made for our troop. It is not published or endorsed by Scouting America (the Boy Scouts of America).")
                    Text("First aid information is a quick reference and does not replace hands-on training. In an emergency, call 911. Get trained in first aid and CPR.")
                    Text("Rank and award requirements change. Always check the current Scouts BSA Handbook and ask your leaders.")
                }
                Text("Be Prepared.").font(.title3.bold()).foregroundStyle(TroopTheme.forest)
            }.padding(24).frame(maxWidth: 800).frame(maxWidth: .infinity)
        }.background(TroopTheme.paper).navigationTitle("About").navigationBarTitleDisplayMode(.inline)
    }
}
