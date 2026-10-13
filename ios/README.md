# Troop 53 Scout Guide for iOS

Native SwiftUI app for iPhone and iPad, iOS 16+. All 15 sections, 113 articles, 40 quiz questions, 52 knot/lashing step diagrams, and 91 illustrations work offline. Search, saved guides, emergency shortcuts, the 10-question quiz, and Knot Practice match the Android app. There are no third-party runtime dependencies, accounts, analytics, or remote content.

## Open and install

Clone the entire repository, then open `ios/Troop53Guide.xcodeproj` with Xcode 16.4 or newer on a Mac. Select the `Troop53Guide` scheme and an iPhone or iPad simulator, then Run. No package installation or project generator is needed.

For a physical device, open the app target's **Signing & Capabilities**, select your Apple development team, connect the device, and Run. The bundle ID is `com.doktorigi.troop53`, version `1.0.0`, build `1`. If that ID is already registered by a different Apple team, use an available ID. Enable Developer Mode on the phone when Xcode asks. TestFlight and App Store distribution need an Apple Developer Program membership, signing credentials, and an App Store Connect app record.

The Windows workspace cannot run Xcode or sign an iPhone app. GitHub Actions produces an **unsigned simulator app**, unit/UI test results, and screenshots. Its simulator ZIP cannot be installed on a physical iPhone. Download it from the [iOS workflow](https://github.com/doktorigi/troop53-guide/actions/workflows/ios.yml), unzip it on a Mac, and run:

```sh
xcrun simctl install booted Troop53Guide.app
xcrun simctl launch booted com.doktorigi.troop53
```

## Build and test

```sh
python3 ios/tools/check_resources.py
xcodebuild -project ios/Troop53Guide.xcodeproj -scheme Troop53Guide \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath ios/DerivedData CODE_SIGNING_ALLOWED=NO test
```

Choose a simulator installed on your Mac using `xcrun simctl list devices available`. CI uses Xcode 16.4 and iOS 18.5 on `macos-15`, and also builds the Release app for generic iOS without signing. Unit tests validate content parity, every image's native rendering, all knot-step associations, parser safety, search, persistent bookmarks, and quiz scoring. UI tests cover search/save, practice navigation, vector/bitmap viewers, quiz feedback, and emergency reference navigation. Tests never dial emergency numbers.

## Shared content and illustrations

The project includes `../app/src/main/assets/guide` and `quiz.txt` as bundle resources directly. Edit these canonical text files once for both apps. `Guide.swift` parses the same line format; an image's path and accessible caption remain separate. The app stores only saved article IDs in its own UserDefaults domain; `PrivacyInfo.xcprivacy` declares [Apple's app-scoped UserDefaults reason](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype) `CA92.1`.

Existing path-only SVGs under `art/svg/` are exported to single-page vector PDFs under `ios/Troop53Guide/Resources/Illustrations/`, preserving geometry, colors, masks, and outlined labels. The two camping WebPs become 1080-pixel JPEGs. The iOS app icon reuses the Android shield paths. Technical drawings are never regenerated with AI. Original research and generated scene prompts remain in `art/references.md` and `art/image-prompts.md`.

Cards rasterize thumbnails off the main thread, bounded to 1080 pixels with a 12 MiB cache. The full-screen viewer uses [Apple PDFKit](https://developer.apple.com/documentation/pdfkit/pdfview) to retain vector detail with pinch, pan, double-tap, Zoom in, and Reset. Scene decoding is bounded to 2048 pixels using [ImageIO thumbnail creation](https://developer.apple.com/documentation/imageio/cgimagesourcecreatethumbnailatindex(_:_:_:)). Captions are also accessibility labels, and text supports Dynamic Type.

After artwork changes, regenerate exports on an authoring machine:

```sh
python -m pip install fonttools reportlab pillow resvg-py
python ios/tools/prepare_art.py
python ios/tools/check_resources.py
```

`ios/art-manifest.json` records canonical SVG hashes with LF newlines and exact binary output hashes to catch stale exports across platforms. Export dependencies are build-time authoring tools only; a fresh Xcode checkout needs none of them. `ios/tools/generate_project.py` deterministically recreates the checked-in project after source files are added or removed. It does not replace Xcode or implement signing.
