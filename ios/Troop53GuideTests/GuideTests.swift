import XCTest
import PDFKit
@testable import Troop53Guide

final class GuideTests: XCTestCase {
    func testSharedContentAndEveryIllustration() throws {
        let (guide, questions) = try Guide.load(bundle: .main)
        XCTAssertEqual(guide.categories.count, 15)
        XCTAssertEqual(guide.articles.count, 113)
        XCTAssertEqual(questions.count, 40)
        let images = guide.articles.flatMap(\.blocks).filter { $0.kind == .image }
        XCTAssertEqual(images.count, 76)
        let paths = Set(images.compactMap(\.imagePath) + guide.categories.map { "headers/\($0.id).xml" })
        XCTAssertEqual(paths.count, 91)
        for path in paths {
            let url = try XCTUnwrap(ArtworkResources.url(path), path)
            XCTAssertNotNil(ArtworkResources.thumbnail(url, maxPixels: 240), "Could not render \(path)")
            if url.pathExtension == "pdf" { XCTAssertEqual(PDFDocument(url: url)?.pageCount, 1, path) }
        }
    }

    func testEveryKnotStepHasItsOwnDiagram() throws {
        let (guide, _) = try Guide.load(bundle: .main)
        let knots = try XCTUnwrap(guide.category("knots"))
        var steps = 0
        for article in knots.articles {
            for (index, block) in article.blocks.enumerated() where block.kind == .step {
                steps += 1
                XCTAssertLessThan(index + 1, article.blocks.count)
                let image = article.blocks[index + 1]
                XCTAssertEqual(image.kind, .image)
                XCTAssertEqual(image.imagePath, "knots/\(article.id)-\(block.number).xml")
                XCTAssertEqual(image.text, "Step \(block.number): \(block.text)")
            }
        }
        XCTAssertEqual(steps, 52)
    }

    func testParserHandlesAllBlocksAndCaptionPipes() throws {
        let guide = try GuideParser.parse("""
        === sample | Sample | X | #1F3A24 | Subtitle
        # test | Test | Summary
        ## Heading
        Paragraph
        - Bullet
        12. Step
        ! Warning
        > Tip
        @ knots/bowline-3.xml | Caption | with pipe
        """)
        let blocks = try XCTUnwrap(guide.first?.articles.first?.blocks)
        XCTAssertEqual(blocks.map(\.kind), [.heading, .paragraph, .bullet, .step, .warning, .tip, .image])
        XCTAssertEqual(blocks[3].number, 12)
        XCTAssertEqual(blocks.last?.text, "Caption | with pipe")
        XCTAssertEqual(blocks.last?.imagePath, "knots/bowline-3.xml")
    }

    func testUnsafeOrMalformedImagesFail() {
        for path in ["../secret.xml", "/knots/a.xml", "knots//a.xml", "knots/a.svg", "https://example.com/a.jpg", "knots/A.xml", "knots/a.xml?x"] {
            XCTAssertFalse(GuideParser.validImagePath(path), path)
            XCTAssertThrowsError(try GuideParser.parse("=== a | A | X | #123456 | Sub\n# b | B | Sum\n@ \(path) | Caption"))
        }
        XCTAssertThrowsError(try GuideParser.parse("=== a | A | X | #123456 | Sub\n# b | B | Sum\n@ knots/a.xml | "))
    }

    func testSearchRequiresEveryWordAndPrefersTitle() throws {
        let (guide, _) = try Guide.load(bundle: .main)
        XCTAssertEqual(guide.search(" BOWLINE ").first?.id, "bowline")
        XCTAssertTrue(guide.search("  \n ").isEmpty)
        XCTAssertTrue(guide.search("bowline nonexistentword").isEmpty)
        let results = guide.search("rope loop")
        XCTAssertFalse(results.isEmpty)
        XCTAssertTrue(results.allSatisfy { $0.searchText.contains("rope") && $0.searchText.contains("loop") })
    }

    func testQuizOptionsRemainFixedAndAnswersScoreOnlyOnce() throws {
        let (_, bank) = try Guide.load(bundle: .main)
        var round = QuizRound(); round.restart(with: bank)
        XCTAssertEqual(round.questions.count, 10)
        XCTAssertEqual(Set(round.questions.map(\.id)).count, 10)
        let options = round.options
        let correct = try XCTUnwrap(round.current?.answer)
        round.answer("not an option"); round.next()
        XCTAssertEqual(round.index, 0); XCTAssertEqual(round.score, 0)
        round.answer(correct); round.answer(correct)
        XCTAssertEqual(round.score, 1); XCTAssertEqual(round.options, options)
        round.next()
        XCTAssertEqual(round.index, 1); XCTAssertNil(round.selected)
        while let question = round.current { round.answer(question.answer); round.next() }
        XCTAssertTrue(round.finished); XCTAssertEqual(round.score, 10)
        round.restart(with: bank)
        XCTAssertEqual(round.index, 0); XCTAssertEqual(round.score, 0); XCTAssertNil(round.selected)
    }

    func testMalformedQuizFails() {
        XCTAssertThrowsError(try GuideParser.parseQuiz("Q: Question\nA: One\nW: One\nW: Two\nW: Three\nE: Why"))
        XCTAssertThrowsError(try GuideParser.parseQuiz("Q: Question\nA: One\nE: Why"))
    }

    @MainActor func testSavedArticlesPersistAndRemoveStaleIDs() throws {
        let name = "Troop53Tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        defaults.set(["missing", "square-knot"], forKey: GuideStore.savedKey)
        let store = GuideStore(defaults: defaults)
        XCTAssertNil(store.loadError); XCTAssertEqual(store.saved, ["square-knot"])
        store.toggleSaved("bowline")
        XCTAssertEqual(GuideStore(defaults: defaults).saved, ["square-knot", "bowline"])
        store.toggleSaved("square-knot")
        XCTAssertEqual(GuideStore(defaults: defaults).saved, ["bowline"])
    }

    func testChatDecodesFirestoreDocumentsAndErrors() {
        let doc = FirestoreDoc(["name": "projects/p/databases/(default)/documents/groups/g1", "fields": [
            "name": ["stringValue": "Eagle Patrol"], "members": Chat.enc(["a", "b"]), "createdBy": Chat.enc("a"),
            "lastAt": ["timestampValue": "2026-10-12T03:04:05.123456Z"], "approved": Chat.enc(true)] as JSON])
        XCTAssertEqual(doc.group, ChatGroup(id: "g1", name: "Eagle Patrol", members: ["a", "b"], createdBy: "a", lastAt: "2026-10-12T03:04:05.123456Z"))
        XCTAssertTrue(doc.b("approved"))
        XCTAssertFalse(doc.b("missing"))
        XCTAssertEqual(chatDate("2026-10-12T03:04:05.123456Z"), chatDate("2026-10-12T03:04:05Z"))
        XCTAssertEqual(chatDate("2026-10-12T03:04:05Z").timeIntervalSince1970, 1_791_774_245)
        XCTAssertEqual(Chat.friendly(["error": ["message": "INVALID_LOGIN_CREDENTIALS"]]), "Wrong email or password.")
        XCTAssertEqual(Chat.friendly([["error": ["message": "Missing or insufficient permissions."]]]), "Not allowed. Ask a troop leader.")
        XCTAssertEqual(Chat.friendly(nil), "Something went wrong. Try again.")
    }
}
