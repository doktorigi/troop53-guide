# Troop 53 Scout Guide

An offline Android field guide for the Scouts of Troop 53. **Built by Benjamin Doktor.**

- 15 sections, 100+ guides: Scout Basics (Oath, Law, motto, ranks, patrol method), First Aid (hurry cases, CPR, bleeding, burns, heat/cold, bites and stings…), Camping, Camp Cooking, Fire, Knots & Lashings, Navigation, Hiking, Survival, Weather & Nature, Water Safety, Woods Tools, Leave No Trace, Flag & Citizenship, Emergency Prep
- Emergency card on the home screen: Call 911, Poison Control, Hurry cases, CPR, Bleeding
- Search across every guide, save favorites with ☆, 40-question skills quiz (10 random per round)
- No network permission; everything works on the trail

Unofficial — not published or endorsed by Scouting America. First aid content is a quick reference, not a substitute for training.

## Download

Get the illustrated Android app from [GitHub Releases](https://github.com/doktorigi/troop53-guide/releases/latest). Download `Troop53-Scout-Guide.apk`; `SHA256SUMS.txt` records its checksum. This is a debug-signed build, matching the app tested on the Pixel. The private repository and downloads require access to this GitHub repository.

## Build

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat --offline testDebugUnitTest assembleDebug
```

Debug APK: `dist/Troop53-Scout-Guide.apk`. Min SDK 26, target 34, plain Android views, no dependencies.

For a fresh checkout, install JDK 17, Android SDK platform 34, and build-tools 34.0.0; set `ANDROID_HOME` to the SDK directory. Run `./gradlew testDebugUnitTest assembleDebug` on macOS/Linux or `gradlew.bat testDebugUnitTest assembleDebug` on Windows. Use `--offline` after Gradle dependencies are cached. GitHub Actions builds the APK and runs all unit tests on pushes to `main` and pull requests; download build artifacts from the workflow run.

## Editing content

All content lives in `app/src/main/assets/guide/*.txt` (one file per section) and `app/src/main/assets/quiz.txt`. Format:

```
=== id | Section title | emoji | #RRGGBB | subtitle
# article-id | Article title | one-line summary
## Subheading
- bullet          1. numbered step
! warning box     > tip box
@ knots/bowline-3.xml | Rabbit goes around the standing part
@ camping/tent-rainfly.webp | A rainfly with a ventilation gap
any other line is a paragraph
```

Unit tests (`GuideTest`) check every file parses, IDs are unique, and quiz questions are well formed.

## Illustrations

Image blocks use `@ relative/path | accessible caption`. The path is relative to `app/src/main/assets/img/`. Captions participate in search; paths do not. Supported bitmap formats: WebP, PNG, JPG/JPEG. Use lowercase names, hyphens/underscores, and subfolders. Missing captions, traversal, absolute paths, and remote URLs fail parsing.

Cards span the article body and include a caption. Tap for a full-screen viewer: pinch, pan, double-tap, or use the accessible zoom/reset buttons. Back or Close returns to the same article scroll position. Knot Practice walks through a knot's steps with large Back/Next buttons.

All 52 numbered steps in the knots section have original vector diagrams, including whipping, eight knots, and square/diagonal/shear lashings. There are 15 section illustrations and field figures for fire, navigation, first aid, poisonous plants, shelter/signaling, flag folding, water rescue, and camp layout. Two generated camping scenes are 1080-pixel-wide WebP.

Vectors have editable SVG sources in `art/svg/`. `art/vector_art.py` and `art/field_art.py` generate matching Android VectorDrawable XML under both `assets/img/` and `res/drawable/`. Android loads the compiled resource twin (e.g. `knots/bowline-3.xml` maps to `img_knots_bowline_3`) because VectorDrawable inflation requires compiled Android attributes. Vectors never allocate a full bitmap. Bitmap decoding runs on two background workers, reads bounds first, and limits dimensions/pixels using power-of-two sampling (cards ≤1080, viewer ≤2048, ≤4 megapixels). Detached views cancel queued loads and release their drawable references.

Regenerate vectors from the repo root with `python art/field_art.py`; authoring requires `fonttools` and Windows Arial for outlining labels, or set `TROOP53_ART_FONT` to a suitable local TrueType font. `python art/preview_vectors.py` creates review sheets using `resvg-py` and Pillow. No Python libraries or font files ship in the APK. Content remains editable by hand; `art/attach_art.py` can reattach the authored set idempotently using the packaged camping WebPs. Only if those WebPs are missing, set `TROOP53_IMAGEGEN_DIR` to the original generation folder to rebuild them.

Reference checks and limitations: [art/references.md](art/references.md). Exact built-in image-generation prompts: [art/image-prompts.md](art/image-prompts.md). Review downloads are excluded from git and the APK.

Real-device rendering/gesture checks use only Android framework instrumentation:

```
gradlew.bat --offline assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.doktorigi.troop53.test/com.doktorigi.troop53.IllustrationChecks
```

The runner opens the app on its home screen and reports `PASS` or `FAIL`. `art/device_review.py` provides additional screenshot checks; set `ANDROID_SERIAL` to the connected test phone when multiple devices are connected. It uses `adb` on PATH or from `ANDROID_HOME` / `ANDROID_SDK_ROOT`; `ADB` can specify an executable explicitly. `art/preview/` outputs stay local.
