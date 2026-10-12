package com.doktorigi.troop53

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val INK = 0xFF23251F.toInt()
private const val MUTED = 0xFF6B6A5E.toInt()
private const val CARD = 0xFFFFFDF7.toInt()
private const val LINE = 0xFFE3DAC4.toInt()
private const val GREEN_OK = 0xFF2E7D32.toInt()
private val FOREST = EmblemView.FOREST.toInt()
private val KHAKI = EmblemView.KHAKI.toInt()
private val RED = EmblemView.RED.toInt()
private val PAPER = EmblemView.PAPER.toInt()

sealed interface Screen {
    object Home : Screen
    data class Cat(val id: String) : Screen
    data class Art(val id: String) : Screen
    object Saved : Screen
    object Quiz : Screen
    object About : Screen
}

class MainActivity : Activity() {
    private lateinit var guide: Guide
    private lateinit var questions: List<Question>
    private lateinit var prefs: SharedPreferences
    private val stack = ArrayList<Screen>()
    private lateinit var host: FrameLayout

    // Quiz state survives leaving and re-entering the quiz screen.
    private var quizRound: List<Question> = emptyList()
    private var quizIndex = 0
    private var quizScore = 0
    private var quizAnswered: String? = null
    private var quizOptions: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val files = assets.list("guide")!!.sorted()
        guide = Guide(files.flatMap { f -> GuideParser.parse(assets.open("guide/$f").bufferedReader().readText()) })
        questions = GuideParser.parseQuiz(assets.open("quiz.txt").bufferedReader().readText())
        prefs = getSharedPreferences("troop53", MODE_PRIVATE)
        host = FrameLayout(this).apply { setBackgroundColor(PAPER) }
        setContentView(host)
        go(Screen.Home)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            render()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun go(s: Screen) { stack += s; render() }

    private fun render() {
        hideKeyboard()
        host.removeAllViews()
        host.addView(when (val s = stack.last()) {
            Screen.Home -> homeScreen()
            is Screen.Cat -> categoryScreen(guide.category(s.id))
            is Screen.Art -> articleScreen(guide.article(s.id)!!)
            Screen.Saved -> savedScreen()
            Screen.Quiz -> quizScreen()
            Screen.About -> aboutScreen()
        })
    }

    // ---------- Home ----------

    private fun homeScreen(): View {
        val col = column()
        col.addView(header())

        val results = column()
        val main = column()

        val search = EditText(this).apply {
            hint = "Search: bowline, burns, lightning..."
            setSingleLine()
            textSize = 16f
            setTextColor(INK)
            setHintTextColor(MUTED)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = rounded(CARD, 28f, LINE)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            compoundDrawableTintList = ColorStateList.valueOf(MUTED)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val q = s.toString()
                    results.removeAllViews()
                    main.visibility = if (q.isBlank()) View.VISIBLE else View.GONE
                    if (q.isNotBlank()) {
                        val hits = guide.search(q)
                        results.addView(label(if (hits.isEmpty()) "No matches for \"$q\"" else "${hits.size} result${if (hits.size == 1) "" else "s"}"))
                        hits.forEach { results.addView(articleRow(it, showCategory = true)) }
                    }
                }
            })
        }
        col.addView(search, lp(margin = 16, top = -24))
        col.addView(results)
        col.addView(main)

        main.addView(emergencyCard())

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools.addView(toolButton("★", "Saved") { go(Screen.Saved) }, weightLp())
        tools.addView(toolButton("✔", "Quiz") { go(Screen.Quiz) }, weightLp(gap = 10))
        tools.addView(toolButton("⚜", "Scout Law") { go(Screen.Art("scout-law")) }, weightLp(gap = 10))
        main.addView(tools, lp(margin = 16, top = 14))

        main.addView(label("Guide sections"))
        guide.categories.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, c -> row.addView(categoryTile(c), weightLp(gap = if (i == 0) 0 else 10)) }
            if (pair.size == 1) row.addView(View(this), weightLp(gap = 10))
            main.addView(row, lp(margin = 16, top = 10))
        }

        main.addView(footer())
        return scroll(col)
    }

    private fun header(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(FOREST, 0xFF2E5232.toInt()))
            setPadding(dp(20), dp(22), dp(20), dp(44))
        }
        box.addView(EmblemView(this), LinearLayout.LayoutParams(dp(78), dp(90)))
        val texts = column().apply { setPadding(dp(16), 0, 0, 0) }
        texts.addView(text("TROOP 53", 14f, KHAKI, bold = true).apply { letterSpacing = 0.2f })
        texts.addView(text("Scout Guide", 30f, Color.WHITE, bold = true))
        texts.addView(text("Built by Benjamin Doktor", 13f, KHAKI).apply { setTypeface(typeface, Typeface.ITALIC) })
        texts.addView(text("Be Prepared  ·  Do a Good Turn Daily", 12f, 0xCCFFFFFF.toInt()).apply { setPadding(0, dp(6), 0, 0) })
        box.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.setOnLongClickListener { go(Screen.About); true }
        return box
    }

    private fun emergencyCard(): View {
        val card = column().apply {
            background = rounded(RED, 18f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        card.addView(text("EMERGENCY", 13f, 0xFFFFD9D4.toInt(), bold = true).apply { letterSpacing = 0.15f })
        card.addView(text("Check the scene · Call 911 · Care", 18f, Color.WHITE, bold = true))
        val r1 = LinearLayout(this)
        r1.addView(pill("📞 Call 911") { dial("911") }, weightLp())
        r1.addView(pill("☠ Poison Control") { dial("18002221222") }, weightLp(gap = 8))
        card.addView(r1, lp(top = 12))
        val r2 = LinearLayout(this)
        r2.addView(pill("Hurry cases") { go(Screen.Art("hurry-cases")) }, weightLp())
        r2.addView(pill("CPR") { go(Screen.Art("cpr")) }, weightLp(gap = 8))
        r2.addView(pill("Bleeding") { go(Screen.Art("bleeding")) }, weightLp(gap = 8))
        card.addView(r2, lp(top = 8))
        return card.also { it.layoutParams = lp(margin = 16, top = 16) }
    }

    private fun pill(label: String, onClick: () -> Unit) = text(label, 14f, RED, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(10), dp(8), dp(10))
        background = ripple(rounded(Color.WHITE, 22f))
        setOnClickListener { onClick() }
    }

    private fun toolButton(icon: String, label: String, onClick: () -> Unit) = column().apply {
        gravity = Gravity.CENTER
        background = ripple(rounded(CARD, 16f, LINE))
        setPadding(dp(6), dp(12), dp(6), dp(12))
        addView(text(icon, 22f, FOREST, bold = true).apply { gravity = Gravity.CENTER })
        addView(text(label, 13f, INK, bold = true).apply { gravity = Gravity.CENTER })
        setOnClickListener { onClick() }
    }

    private fun categoryTile(c: Category) = column().apply {
        background = ripple(rounded(CARD, 16f, LINE))
        setPadding(dp(14), dp(14), dp(14), dp(14))
        val badge = text(c.icon, 24f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = rounded(c.color.toInt(), 14f)
        }
        addView(badge, LinearLayout.LayoutParams(dp(46), dp(46)))
        addView(text(c.title, 16f, INK, bold = true).apply { setPadding(0, dp(10), 0, 0) })
        addView(text("${c.articles.size} guides", 12f, MUTED))
        setOnClickListener { go(Screen.Cat(c.id)) }
    }

    private fun footer() = column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(28), dp(16), dp(32))
        addView(EmblemView(this@MainActivity), LinearLayout.LayoutParams(dp(40), dp(46)))
        addView(text("Troop 53 Scout Guide", 14f, FOREST, bold = true).apply { setPadding(0, dp(8), 0, 0) })
        addView(text("Built by Benjamin Doktor", 13f, MUTED))
        addView(text("About this app", 13f, RED, bold = true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { go(Screen.About) }
        })
    }

    // ---------- Category / lists ----------

    private fun categoryScreen(c: Category): View {
        val col = column()
        val band = column().apply {
            setBackgroundColor(c.color.toInt())
            setPadding(dp(20), dp(8), dp(20), dp(22))
        }
        band.addView(text("${c.icon}  ${c.title}", 26f, Color.WHITE, bold = true))
        band.addView(text(c.subtitle, 14f, 0xDDFFFFFF.toInt()).apply { setPadding(0, dp(4), 0, 0) })
        col.addView(band)
        c.articles.forEach { col.addView(articleRow(it, showCategory = false)) }
        col.addView(View(this), lp(top = 24))
        return page(topBar(c.color.toInt(), ""), scroll(col))
    }

    private fun articleRow(a: Article, showCategory: Boolean): View {
        val c = guide.category(a.categoryId)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(rounded(CARD, 14f, LINE))
            setPadding(dp(14), dp(12), dp(10), dp(12))
            setOnClickListener { go(Screen.Art(a.id)) }
        }
        row.addView(View(this).apply { background = rounded(c.color.toInt(), 3f) }, LinearLayout.LayoutParams(dp(5), dp(38)))
        val texts = column().apply { setPadding(dp(12), 0, dp(6), 0) }
        if (showCategory) texts.addView(text(c.title.uppercase(), 11f, c.color.toInt(), bold = true).apply { letterSpacing = 0.08f })
        texts.addView(text(a.title, 16f, INK, bold = true))
        texts.addView(text(a.summary, 13f, MUTED))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (isSaved(a.id)) row.addView(text("★", 18f, KHAKI_DARK))
        row.addView(text("›", 24f, MUTED).apply { setPadding(dp(6), 0, 0, 0) })
        row.layoutParams = lp(margin = 16, top = 8)
        return row
    }

    // ---------- Article ----------

    private fun articleScreen(a: Article): View {
        val c = guide.category(a.categoryId)
        val color = c.color.toInt()
        val star = text(if (isSaved(a.id)) "★" else "☆", 26f, Color.WHITE).apply {
            contentDescription = "Save"
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        star.setOnClickListener {
            val now = toggleSaved(a.id)
            star.text = if (now) "★" else "☆"
            Toast.makeText(this, if (now) "Saved" else "Removed from saved", Toast.LENGTH_SHORT).show()
        }
        val bar = topBar(color, c.title, star)

        val col = column()
        val band = column().apply {
            setBackgroundColor(color)
            setPadding(dp(20), dp(4), dp(20), dp(22))
        }
        band.addView(text(a.title, 26f, Color.WHITE, bold = true))
        band.addView(text(a.summary, 15f, 0xE6FFFFFF.toInt()).apply { setPadding(0, dp(4), 0, 0) })
        col.addView(band)

        val body = column().apply { setPadding(dp(20), dp(12), dp(20), dp(8)) }
        a.blocks.forEach { body.addView(blockView(it, color)) }
        col.addView(body)

        // previous / next within the section
        val idx = c.articles.indexOf(a)
        val nav = LinearLayout(this)
        c.articles.getOrNull(idx - 1)?.let { p -> nav.addView(navButton("‹ ${p.title}", Gravity.START) { replaceTop(Screen.Art(p.id)) }, weightLp()) }
            ?: nav.addView(View(this), weightLp())
        c.articles.getOrNull(idx + 1)?.let { n -> nav.addView(navButton("${n.title} ›", Gravity.END) { replaceTop(Screen.Art(n.id)) }, weightLp(gap = 10)) }
            ?: nav.addView(View(this), weightLp(gap = 10))
        col.addView(nav, lp(margin = 16, top = 12))
        col.addView(text("Troop 53 Scout Guide · Built by Benjamin Doktor", 12f, MUTED).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(28))
        })
        return page(bar, scroll(col))
    }

    private fun replaceTop(s: Screen) { stack[stack.lastIndex] = s; render() }

    private fun navButton(label: String, g: Int, onClick: () -> Unit) = text(label, 14f, FOREST, bold = true).apply {
        gravity = g or Gravity.CENTER_VERTICAL
        maxLines = 2
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = ripple(rounded(CARD, 14f, LINE))
        setOnClickListener { onClick() }
    }

    private fun blockView(b: Block, color: Int): View = when (b.kind) {
        Kind.PARA -> text(b.text, 16f, INK).apply { para(); layoutParams = lp(top = 8) }
        Kind.HEADING -> text(b.text, 19f, color, bold = true).apply { layoutParams = lp(top = 20) }
        Kind.BULLET -> markerRow(text("•", 18f, color, bold = true).apply { gravity = Gravity.CENTER }, b.text, dp(18))
        Kind.STEP -> markerRow(text(b.number.toString(), 13f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        }, b.text, dp(26))
        Kind.WARN -> callout("⚠  ", b.text, 0xFFFBE3DF.toInt(), RED)
        Kind.TIP -> callout("Tip  ", b.text, 0xFFF1E8CF.toInt(), 0xFF7A5A2E.toInt())
    }

    private fun markerRow(marker: TextView, body: String, markerWidth: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        if (marker.layoutParams == null) marker.layoutParams = LinearLayout.LayoutParams(markerWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(marker)
        addView(TextView(context).apply {
            text = leadBold(body)
            textSize = 16f
            setTextColor(INK)
            para()
            setPadding(dp(10), dp(1), 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layoutParams = lp(top = 8)
    }

    private fun callout(prefix: String, body: String, bg: Int, accent: Int) = TextView(this).apply {
        val s = SpannableString(prefix + body)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = s
        textSize = 15f
        setTextColor(if (accent == RED) 0xFF7A1E17.toInt() else INK)
        para()
        background = rounded(bg, 12f, accent)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = lp(top = 12)
    }

    /** Bold a short "Label:" lead-in such as "Rest: stop using the injured part." */
    private fun leadBold(t: String): CharSequence {
        val i = t.indexOf(": ")
        if (i !in 1..44) return t
        return SpannableString(t).apply { setSpan(StyleSpan(Typeface.BOLD), 0, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    // ---------- Saved ----------

    private fun isSaved(id: String) = id in (prefs.getStringSet("saved", emptySet()) ?: emptySet())

    private fun toggleSaved(id: String): Boolean {
        val set = (prefs.getStringSet("saved", emptySet()) ?: emptySet()).toMutableSet()
        val now = if (id in set) { set.remove(id); false } else { set.add(id); true }
        prefs.edit().putStringSet("saved", set).apply()
        return now
    }

    private fun savedScreen(): View {
        val col = column()
        col.addView(text("★  Saved guides", 26f, FOREST, bold = true).apply { setPadding(dp(20), dp(8), dp(20), dp(4)) })
        val saved = guide.articles.filter { isSaved(it.id) }
        if (saved.isEmpty()) {
            col.addView(text("Nothing saved yet. Open any guide and tap ☆ at the top to keep it here for quick access.", 15f, MUTED).apply {
                para(); setPadding(dp(20), dp(12), dp(20), 0)
            })
        }
        saved.forEach { col.addView(articleRow(it, showCategory = true)) }
        return page(topBar(PAPER, "", dark = true), scroll(col))
    }

    // ---------- Quiz ----------

    private fun newRound() {
        quizRound = questions.shuffled().take(10)
        quizIndex = 0; quizScore = 0
        nextQuestion()
    }

    private fun nextQuestion() {
        quizAnswered = null
        quizOptions = quizRound.getOrNull(quizIndex)?.let { (it.wrong + it.answer).shuffled() } ?: emptyList()
    }

    private fun quizScreen(): View {
        if (quizRound.isEmpty()) newRound()
        val col = column().apply { setPadding(dp(16), dp(4), dp(16), dp(24)) }
        col.addView(text("Skills Quiz", 26f, FOREST, bold = true))

        if (quizIndex >= quizRound.size) {
            val pct = quizScore * 100 / quizRound.size
            col.addView(text("$quizScore / ${quizRound.size}", 56f, FOREST, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, dp(32), 0, 0) })
            col.addView(text(when {
                pct == 100 -> "Perfect! You are prepared."
                pct >= 80 -> "Great job, Scout!"
                pct >= 50 -> "Good effort. Review the guides and try again."
                else -> "Keep practicing. Every Eagle started as a Scout."
            }, 18f, INK).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(24)) })
            col.addView(bigButton("Play again", FOREST) { newRound(); render() })
            return page(topBar(PAPER, "", dark = true), scroll(col))
        }

        val q = quizRound[quizIndex]
        col.addView(text("Question ${quizIndex + 1} of ${quizRound.size}  ·  Score $quizScore", 13f, MUTED).apply { setPadding(0, dp(4), 0, dp(12)) })
        col.addView(text(q.prompt, 21f, INK, bold = true).apply { para() })
        quizOptions.forEach { opt ->
            val answered = quizAnswered
            val (bg, fg) = when {
                answered == null -> CARD to INK
                opt == q.answer -> GREEN_OK to Color.WHITE
                opt == answered -> RED to Color.WHITE
                else -> CARD to MUTED
            }
            col.addView(text(opt, 16f, fg, bold = true).apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = ripple(rounded(bg, 14f, if (bg == CARD) LINE else bg))
                layoutParams = lp(top = 10)
                if (answered == null) setOnClickListener {
                    quizAnswered = opt
                    if (opt == q.answer) quizScore++
                    render()
                }
            })
        }
        if (quizAnswered != null) {
            val right = quizAnswered == q.answer
            col.addView(callout(if (right) "Correct!  " else "Not quite.  ", q.explanation,
                if (right) 0xFFE3F1E1.toInt() else 0xFFFBE3DF.toInt(), if (right) GREEN_OK else RED))
            col.addView(bigButton(if (quizIndex + 1 < quizRound.size) "Next question" else "See score", FOREST) {
                quizIndex++; nextQuestion(); render()
            }.apply { layoutParams = lp(top = 16) })
        }
        return page(topBar(PAPER, "", dark = true), scroll(col))
    }

    private fun bigButton(label: String, color: Int, onClick: () -> Unit) = text(label, 17f, Color.WHITE, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = ripple(rounded(color, 26f))
        setOnClickListener { onClick() }
    }

    // ---------- About ----------

    private fun aboutScreen(): View {
        val col = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(8), dp(24), dp(32))
        }
        col.addView(EmblemView(this), LinearLayout.LayoutParams(dp(120), dp(138)))
        col.addView(text("Troop 53 Scout Guide", 24f, FOREST, bold = true).apply { setPadding(0, dp(16), 0, 0) })
        col.addView(text("Built by Benjamin Doktor", 16f, RED, bold = true).apply { setPadding(0, dp(4), 0, 0) })
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        col.addView(text("Version $version  ·  ${guide.categories.size} sections  ·  ${guide.articles.size} guides  ·  ${questions.size} quiz questions", 13f, MUTED).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(20))
        })
        listOf(
            "A pocket field guide for the Scouts of Troop 53: first aid, the Scout Oath and Law, camping, cooking, knots, navigation, survival, and more. Everything works offline, so it is ready on the trail.",
            "This is an unofficial guide made for our troop. It is not published or endorsed by Scouting America (the Boy Scouts of America).",
            "First aid information is a quick reference and does not replace hands-on training. In an emergency, call 911. Get trained in first aid and CPR.",
            "Rank and award requirements change. Always check the current Scouts BSA Handbook and ask your leaders.",
        ).forEach { col.addView(text(it, 15f, INK).apply { para(); layoutParams = lp(top = 12) }) }
        col.addView(text("Be Prepared.", 18f, FOREST, bold = true).apply { setPadding(0, dp(24), 0, 0) })
        return page(topBar(PAPER, "", dark = true), scroll(col))
    }

    // ---------- helpers ----------

    private fun page(bar: View, content: View) = column().apply {
        addView(bar)
        addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun topBar(bg: Int, title: String, vararg actions: View, dark: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(bg)
        setPadding(dp(4), dp(6), dp(4), dp(6))
        val fg = if (dark) FOREST else Color.WHITE
        addView(text("‹", 32f, fg).apply {
            contentDescription = "Back"
            setPadding(dp(14), 0, dp(14), dp(4))
            setOnClickListener { onBackPressed() }
        })
        addView(text(title, 15f, fg, bold = true).apply { alpha = 0.85f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.forEach { addView(it) }
        addView(text("⌂", 24f, fg).apply {
            contentDescription = "Home"
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { stack.clear(); go(Screen.Home) }
        })
    }

    private fun scroll(child: View) = ScrollView(this).apply {
        isFillViewport = true
        addView(child)
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun label(t: String) = text(t.uppercase(), 13f, MUTED, bold = true).apply {
        letterSpacing = 0.1f
        layoutParams = lp(margin = 20, top = 22)
    }

    private fun text(t: CharSequence, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun TextView.para() = setLineSpacing(0f, 1.25f)

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun ripple(d: GradientDrawable) = RippleDrawable(ColorStateList.valueOf(0x22000000), d, null)

    private fun lp(margin: Int = 0, top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        setMargins(dp(margin), dp(top), dp(margin), 0)
    }

    private fun weightLp(gap: Int = 0) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(gap) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun dial(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No phone app found. Dial $number.", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(host.windowToken, 0)
    }

    companion object {
        private const val KHAKI_DARK = 0xFFB89B52.toInt()
    }
}
