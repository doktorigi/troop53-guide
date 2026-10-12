package com.doktorigi.troop53

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GuideTest {
    private val assets = File("src/main/assets")
    private val guide = Guide(assets.resolve("guide").listFiles()!!.sorted().flatMap { GuideParser.parse(it.readText()) })

    @Test fun everySectionAndGuideHasContent() {
        assertEquals(15, guide.categories.size)
        guide.categories.forEach { c -> assertTrue("${c.id} empty", c.articles.isNotEmpty()) }
        guide.articles.forEach { a ->
            assertTrue("${a.id} too short", a.blocks.size >= 3)
            assertTrue("${a.id} missing summary", a.summary.isNotBlank())
        }
        assertTrue(guide.articles.size >= 90)
    }

    @Test fun idsAreUnique() {
        assertEquals(guide.articles.size, guide.articles.map { it.id }.toSet().size)
        assertEquals(guide.categories.size, guide.categories.map { it.id }.toSet().size)
    }

    @Test fun homeShortcutsPointAtRealGuides() {
        listOf("scout-law", "hurry-cases", "cpr", "bleeding", "emergency-steps").forEach {
            assertTrue("missing $it", guide.article(it) != null)
        }
    }

    @Test fun scoutLawHasTwelvePointsInOrder() {
        val steps = guide.article("scout-law")!!.blocks.filter { it.kind == Kind.STEP }
        assertEquals((1..12).toList(), steps.map { it.number })
        assertTrue(steps.last().text.startsWith("Reverent"))
    }

    @Test fun searchFindsByTitleFirst() {
        assertEquals("bowline", guide.search("bowline").first().id)
        assertTrue(guide.search("snake bite").isEmpty() || guide.search("snakebite").first().id == "snakebite")
        assertTrue(guide.search("zzzz").isEmpty())
    }

    @Test fun parserHandlesBlockKinds() {
        val c = GuideParser.parse("=== x | X | i | #112233 | sub\n# a | A | s\n## H\n- b\n3. step\n! w\n> t\nplain").single()
        assertEquals(0xFF112233, c.color)
        assertEquals(listOf(Kind.HEADING, Kind.BULLET, Kind.STEP, Kind.WARN, Kind.TIP, Kind.PARA), c.articles.single().blocks.map { it.kind })
        assertEquals(3, c.articles.single().blocks[2].number)
    }

    @Test fun quizQuestionsAreWellFormed() {
        val qs = GuideParser.parseQuiz(assets.resolve("quiz.txt").readText())
        assertTrue(qs.size >= 40)
        qs.forEach { q ->
            assertEquals(q.prompt, 3, q.wrong.size)
            assertTrue(q.prompt, q.answer !in q.wrong)
            assertTrue(q.prompt, q.explanation.isNotBlank())
        }
    }

    @Test fun imageBlocksKeepCaptionAndPathSeparate() {
        val a = GuideParser.parse("=== x | X | i | #112233 | s\n# a | A | s\n@ knots/bowline-3.webp | Around the tree | behind the standing part").single().articles.single()
        val block = a.blocks.single()
        assertEquals(Kind.IMAGE, block.kind)
        assertEquals("knots/bowline-3.webp", block.imagePath)
        assertEquals("Around the tree | behind the standing part", block.text)
        assertTrue(a.searchText.contains("around the tree"))
        assertTrue(!a.searchText.contains(".webp"))
    }

    @Test fun imagesRejectUnsafePathsAndMissingCaptions() {
        listOf("../bowline.webp | c", "/bowline.webp | c", "knots\\bowline.webp | c",
            "https://example.com/a.webp | c", "bowline.gif | c", "bowline.webp", "bowline.webp | ").forEach { image ->
            try {
                GuideParser.parse("=== x | X | i | #112233 | s\n# a | A | s\n@ $image")
                throw AssertionError("Accepted invalid image: $image")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun everyImageHasAnAssetAndEveryVectorHasACompiledTwin() {
        val images = guide.articles.flatMap { it.blocks }.filter { it.kind == Kind.IMAGE }
        assertTrue(images.size >= 70)
        images.forEach { b ->
            val path = requireNotNull(b.imagePath)
            assertTrue(path, assets.resolve("img/$path").isFile)
            assertTrue(path, b.text.isNotBlank())
            if (path.endsWith(".xml")) {
                val name = "img_" + path.removeSuffix(".xml").replace('/', '_').replace('-', '_') + ".xml"
                val resource = File("src/main/res/drawable/$name")
                assertTrue(path, resource.isFile)
                assertEquals(path, assets.resolve("img/$path").readText(), resource.readText())
                Regex("android:pathData=\"([^\"]+)\"").findAll(resource.readText()).forEach { match ->
                    assertTrue("$path exceeds Android string pool limit", match.groupValues[1].length < 32000)
                }
            }
        }
        guide.categories.forEach { c ->
            assertTrue(c.id, assets.resolve("img/headers/${c.id}.xml").isFile)
            assertTrue(c.id, File("src/main/res/drawable/img_headers_${c.id}.xml").isFile)
        }
    }

    @Test fun eachNumberedKnotAndLashingStepHasItsOwnDiagramImmediatelyBelow() {
        guide.category("knots").articles.forEach { a ->
            a.blocks.forEachIndexed { i, b ->
                if (b.kind == Kind.STEP) {
                    val next = a.blocks.getOrNull(i + 1)
                    assertEquals("${a.id} step ${b.number}", Kind.IMAGE, next?.kind)
                    assertEquals("knots/${a.id}-${b.number}.xml", next?.imagePath)
                    assertTrue(next!!.text.contains(b.text))
                }
            }
        }
    }
}
