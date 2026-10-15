import XCTest

final class GuideUITests: XCTestCase {
    private var app: XCUIApplication!
    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication(); app.launchArguments = ["--ui-testing"]; app.launch()
    }
    override func tearDownWithError() throws { XCUIDevice.shared.orientation = .portrait }
    private func waitUntilHittable(_ element: XCUIElement) {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in element.isHittable }, object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: 10), .completed)
    }
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    private func search(_ text: String) {
        let field = app.textFields["guide-search"]
        XCTAssertTrue(field.waitForExistence(timeout: 10)); field.tap(); field.typeText(text)
    }
    private func openArticle(_ id: String, query: String) {
        search(query)
        let link = app.buttons["article-\(id)"]
        XCTAssertTrue(link.waitForExistence(timeout: 5)); link.tap()
        XCTAssertTrue(app.buttons["save-article"].waitForExistence(timeout: 5))
    }

    func testSearchSavePracticeAndVectorZoom() {
        capture("01-home")
        openArticle("bowline", query: "bowline")
        capture("02-bowline")
        app.buttons["save-article"].tap()
        XCTAssertEqual(app.buttons["save-article"].value as? String, "Saved")
        app.buttons["knot-practice"].tap()
        XCTAssertTrue(app.staticTexts["practice-step"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["practice-step"].label, "Step 1 of 5")
        app.buttons["practice-next"].tap()
        XCTAssertEqual(app.staticTexts["practice-step"].label, "Step 2 of 5")
        capture("03-knot-practice")
        let image = app.buttons["practice-image-knots/bowline-2.xml"]
        XCTAssertTrue(image.waitForExistence(timeout: 5)); image.tap()
        XCTAssertTrue(app.buttons["close-illustration"].waitForExistence(timeout: 5))
        let viewer = app.images["zoomable-illustration"]
        XCTAssertTrue(viewer.waitForExistence(timeout: 5))
        viewer.pinch(withScale: 2, velocity: 1)
        app.buttons["Zoom in"].tap(); app.buttons["Reset"].tap()
        capture("04-vector-viewer")
        app.buttons["close-illustration"].tap()
        XCTAssertTrue(app.buttons["practice-next"].waitForExistence(timeout: 5))
        app.buttons["practice-back"].tap()
        XCTAssertEqual(app.staticTexts["practice-step"].label, "Step 1 of 5")
        app.buttons["close-practice"].tap()
        app.buttons["home-button"].tap()
        if app.buttons["Clear search"].exists { app.buttons["Clear search"].tap() }
        app.buttons["saved-guides"].tap()
        XCTAssertTrue(app.buttons["article-bowline"].waitForExistence(timeout: 5))
        capture("05-saved")
    }

    func testQuizAndReturnToEmergencyGuide() {
        app.buttons["quiz-link"].tap()
        XCTAssertTrue(app.buttons["quiz-option-0"].waitForExistence(timeout: 5))
        app.buttons["quiz-option-0"].tap()
        XCTAssertTrue(app.staticTexts["quiz-explanation"].waitForExistence(timeout: 5))
        capture("06-quiz")
        app.swipeUp()
        app.buttons["quiz-next"].tap()
        XCTAssertTrue(app.staticTexts["quiz-progress"].label.hasPrefix("Question 2 of 10"))
        app.buttons["home-button"].tap()
        app.buttons["CPR"].tap()
        XCTAssertTrue(app.buttons["save-article"].waitForExistence(timeout: 5))
        capture("07-cpr")
    }

    func testSectionArtworkAndBitmapViewer() {
        app.buttons["category-camping"].tap()
        XCTAssertTrue(app.buttons["image-headers/camping.xml"].waitForExistence(timeout: 5))
        capture("08-camping-section")
        app.buttons["home-button"].tap()
        openArticle("tents", query: "tents")
        let image = app.buttons["image-camping/tent-rainfly.webp"]
        for _ in 0..<8 where !image.isHittable { app.swipeUp() }
        XCTAssertTrue(image.exists); image.tap()
        XCTAssertTrue(app.buttons["close-illustration"].waitForExistence(timeout: 5))
        capture("09-bitmap-viewer")
        app.buttons["close-illustration"].tap()
        XCTAssertTrue(app.buttons["save-article"].exists)
    }

    func testRotationKeepsGuideAndIllustrationUsable() {
        openArticle("bowline", query: "bowline")
        app.buttons["knot-practice"].tap()
        XCTAssertTrue(app.buttons["practice-image-knots/bowline-1.xml"].waitForExistence(timeout: 5))
        app.buttons["practice-image-knots/bowline-1.xml"].tap()
        XCTAssertTrue(app.buttons["close-illustration"].waitForExistence(timeout: 5))
        XCUIDevice.shared.orientation = .landscapeLeft
        waitUntilHittable(app.buttons["Reset"])
        capture("10-landscape-viewer")
        XCTAssertTrue(app.buttons["Reset"].isHittable)
        app.buttons["Reset"].tap()
        app.buttons["close-illustration"].tap()
        XCTAssertTrue(app.buttons["practice-next"].waitForExistence(timeout: 5))
        app.buttons["practice-next"].tap()
        XCTAssertEqual(app.staticTexts["practice-step"].label, "Step 2 of 5")
        XCUIDevice.shared.orientation = .portrait
        waitUntilHittable(app.buttons["practice-next"])
        capture("11-rotated-practice")
    }

    func testChatOpensToSignInWithoutNetwork() {
        let chat = app.buttons["chat-link"]
        XCTAssertTrue(chat.waitForExistence(timeout: 10)); chat.tap()
        let submit = app.buttons["chat-submit"]
        XCTAssertTrue(submit.waitForExistence(timeout: 10))
        XCTAssertEqual(submit.label, "Sign in")
        app.buttons["chat-toggle"].tap()
        XCTAssertEqual(submit.label, "Create account")
        XCTAssertTrue(app.textFields["chat-name"].exists)
        capture("12-chat-sign-up")
    }
}
