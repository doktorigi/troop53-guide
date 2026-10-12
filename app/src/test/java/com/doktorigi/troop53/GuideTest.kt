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
}
