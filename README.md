# Troop 53 Scout Guide

An offline Android field guide for the Scouts of Troop 53. **Built by Benjamin Doktor.**

- 15 sections, 100+ guides: Scout Basics (Oath, Law, motto, ranks, patrol method), First Aid (hurry cases, CPR, bleeding, burns, heat/cold, bites and stings…), Camping, Camp Cooking, Fire, Knots & Lashings, Navigation, Hiking, Survival, Weather & Nature, Water Safety, Woods Tools, Leave No Trace, Flag & Citizenship, Emergency Prep
- Emergency card on the home screen: Call 911, Poison Control, Hurry cases, CPR, Bleeding
- Search across every guide, save favorites with ☆, 40-question skills quiz (10 random per round)
- No network permission; everything works on the trail

Unofficial — not published or endorsed by Scouting America. First aid content is a quick reference, not a substitute for training.

## Build

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat --offline testDebugUnitTest assembleDebug
```

Debug APK: `dist/Troop53-Scout-Guide.apk`. Min SDK 26, target 34, plain Android views, no dependencies.

## Editing content

All content lives in `app/src/main/assets/guide/*.txt` (one file per section) and `app/src/main/assets/quiz.txt`. Format:

```
=== id | Section title | emoji | #RRGGBB | subtitle
# article-id | Article title | one-line summary
## Subheading
- bullet          1. numbered step
! warning box     > tip box
any other line is a paragraph
```

Unit tests (`GuideTest`) check every file parses, IDs are unique, and quiz questions are well formed.
