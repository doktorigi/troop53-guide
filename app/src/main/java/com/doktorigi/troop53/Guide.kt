package com.doktorigi.troop53

enum class Kind { PARA, HEADING, BULLET, STEP, WARN, TIP }

data class Block(val kind: Kind, val text: String, val number: Int = 0)

data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val categoryId: String,
    val blocks: List<Block>,
) {
    val searchText: String by lazy {
        (listOf(title, summary) + blocks.map { it.text }).joinToString(" ").lowercase()
    }
}

data class Category(
    val id: String,
    val title: String,
    val icon: String,
    val color: Long,
    val subtitle: String,
    val articles: List<Article>,
)

data class Question(val prompt: String, val answer: String, val wrong: List<String>, val explanation: String)

/**
 * Guide files are line based:
 *   === id | Title | icon | #RRGGBB | Subtitle   starts a category
 *   # id | Title | Summary                       starts an article
 *   ## Heading, - bullet, 1. step, ! warning, > tip, anything else is a paragraph.
 */
object GuideParser {
    private val stepRegex = Regex("""^(\d+)\.\s+(.*)$""")

    fun parse(text: String): List<Category> {
        val categories = mutableListOf<Category>()
        var cat: Array<String>? = null
        val articles = mutableListOf<Article>()
        var art: Array<String>? = null
        val blocks = mutableListOf<Block>()

        fun closeArticle() {
            val a = art ?: return
            val c = cat ?: error("Article ${a[0]} outside a category")
            articles += Article(a[0], a[1], a[2], c[0], blocks.toList())
            blocks.clear()
            art = null
        }
        fun closeCategory() {
            closeArticle()
            val c = cat ?: return
            categories += Category(c[0], c[1], c[2], c[3].removePrefix("#").toLong(16) or 0xFF000000, c[4], articles.toList())
            articles.clear()
            cat = null
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("===") -> {
                    closeCategory()
                    cat = fields(line.removePrefix("==="), 5)
                }
                line.startsWith("## ") -> blocks += Block(Kind.HEADING, line.drop(3))
                line.startsWith("# ") -> {
                    closeArticle()
                    art = fields(line.drop(2), 3)
                }
                line.startsWith("- ") -> blocks += Block(Kind.BULLET, line.drop(2))
                line.startsWith("! ") -> blocks += Block(Kind.WARN, line.drop(2))
                line.startsWith("> ") -> blocks += Block(Kind.TIP, line.drop(2))
                else -> {
                    val m = stepRegex.matchEntire(line)
                    blocks += if (m != null) Block(Kind.STEP, m.groupValues[2], m.groupValues[1].toInt())
                    else Block(Kind.PARA, line)
                }
            }
        }
        closeCategory()
        return categories
    }

    private fun fields(s: String, n: Int): Array<String> {
        val parts = s.split("|").map { it.trim() }
        require(parts.size == n) { "Expected $n fields in: $s" }
        return parts.toTypedArray()
    }

    fun parseQuiz(text: String): List<Question> =
        text.split(Regex("""\n\s*\n""")).mapNotNull { chunk ->
            val lines = chunk.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return@mapNotNull null
            fun get(p: String) = lines.filter { it.startsWith(p) }.map { it.drop(p.length).trim() }
            Question(get("Q:").single(), get("A:").single(), get("W:"), get("E:").firstOrNull() ?: "")
        }
}

class Guide(val categories: List<Category>) {
    val articles: List<Article> = categories.flatMap { it.articles }
    private val byId = articles.associateBy { it.id }

    fun article(id: String) = byId[id]
    fun category(id: String) = categories.first { it.id == id }

    /** All words must appear; title hits rank above body hits. */
    fun search(query: String): List<Article> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return articles.filter { a -> words.all { a.searchText.contains(it) } }
            .sortedByDescending { a -> words.count { a.title.lowercase().contains(it) } }
    }
}
