package com.doktorigi.troop53

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.format.DateUtils
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private const val INK = 0xFF23251F.toInt()
private const val MUTED = 0xFF6B6A5E.toInt()
private const val CARD = 0xFFFFFDF7.toInt()
private const val LINE = 0xFFE3DAC4.toInt()
private const val GREEN_OK = 0xFF2E7D32.toInt()
private const val SCOUTBOOK_URL = "https://advancements.scouting.org/"
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
    object Chat : Screen
    data class ChatGroup(val id: String) : Screen
    data class GroupEdit(val id: String?) : Screen
    object ChatMembers : Screen
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
        intent.getStringExtra("group")?.let { go(Screen.Chat); go(Screen.ChatGroup(it)) }
        if (chat.uid != null) ChatJob.schedule(this)
    }

    override fun onPause() { super.onPause(); paused = true }
    override fun onResume() { super.onResume(); paused = false }

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
        renders++
        host.removeAllViews()
        host.addView(when (val s = stack.last()) {
            Screen.Home -> homeScreen()
            is Screen.Cat -> categoryScreen(guide.category(s.id))
            is Screen.Art -> articleScreen(guide.article(s.id)!!)
            Screen.Saved -> savedScreen()
            Screen.Quiz -> quizScreen()
            Screen.About -> aboutScreen()
            Screen.Chat -> chatScreen()
            is Screen.ChatGroup -> chatGroupScreen(s.id)
            is Screen.GroupEdit -> groupEditScreen(s.id)
            Screen.ChatMembers -> membersScreen()
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
        tools.addView(toolButton("💬", "Chat") { go(Screen.Chat) }, weightLp(gap = 10))
        main.addView(tools, lp(margin = 16, top = 14))
        main.addView(bigButton("Open Scoutbook Plus  ↗", FOREST) { openScoutbook() }, lp(margin = 16, top = 10))

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
        col.addView(imageCard(Block(Kind.IMAGE, "${c.title} field illustration", imagePath = "headers/${c.id}.xml")), lp(margin = 16, top = 14))
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
        val diagrams = a.blocks.filter { it.kind == Kind.IMAGE && it.imagePath?.startsWith("knots/") == true }
        if (diagrams.isNotEmpty()) body.addView(navButton("Knot Practice  ›", Gravity.CENTER) { practice(a, diagrams) })
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
        col.addView(text("Troop 53 Scout Guide", 12f, MUTED).apply {
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
        Kind.IMAGE -> imageCard(b)
    }

    private fun imageCard(b: Block): View = column().apply {
        background = rounded(CARD, 16f, LINE)
        setPadding(dp(8), dp(8), dp(8), dp(10))
        val path = requireNotNull(b.imagePath)
        addView(IllustrationView(this@MainActivity, path, b.text).apply {
            setOnClickListener { showImage(b) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(text(b.text, 13f, MUTED).apply { para(); setPadding(dp(6), dp(8), dp(6), 0) })
        addView(text("Tap to enlarge", 11f, RED).apply { setPadding(dp(6), dp(4), 0, 0) })
        layoutParams = lp(top = 10)
    }

    private fun fullScreenDialog(): Dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    private fun showImage(b: Block) {
        val dialog = fullScreenDialog()
        val col = column()
        val image = IllustrationView(this, requireNotNull(b.imagePath), b.text, zoomable = true)
        col.addView(text(b.text, 16f, FOREST, bold = true).apply { setPadding(dp(16), dp(16), dp(16), dp(8)) })
        col.addView(text("Pinch to zoom · Drag to move · Double-tap to zoom/reset", 13f, MUTED).apply { setPadding(dp(16), 0, dp(16), dp(8)) })
        col.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val controls = LinearLayout(this)
        listOf("−" to { image.zoomBy(0.7f) }, "+" to { image.zoomBy(1.4f) }, "Reset" to { image.setZoom(1f) }, "Close" to { dialog.dismiss() }).forEach { (label, action) ->
            controls.addView(navButton(label, Gravity.CENTER, action).apply {
                contentDescription = when (label) { "+" -> "Zoom in"; "−" -> "Zoom out"; else -> label }
                minimumHeight = dp(48)
            }, weightLp(gap = 4))
        }
        col.addView(controls, lp(margin = 8, top = 8))
        col.setPadding(0, 0, 0, dp(12))
        dialog.setContentView(col); dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun practice(a: Article, diagrams: List<Block>) {
        val dialog = fullScreenDialog()
        var index = 0
        fun draw() {
            val col = column().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
            col.addView(text(a.title, 26f, FOREST, bold = true))
            col.addView(text("Step ${index + 1} of ${diagrams.size}", 14f, MUTED).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE })
            col.addView(scroll(column().apply { addView(imageCard(diagrams[index])) }), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            val nav = LinearLayout(this)
            nav.addView(navButton("Back", Gravity.CENTER) { if (index > 0) { index--; draw() } }.apply { isEnabled = index > 0; alpha = if (isEnabled) 1f else 0.4f; minimumHeight = dp(56) }, weightLp())
            nav.addView(navButton(if (index == diagrams.lastIndex) "Done" else "Next", Gravity.CENTER) {
                if (index == diagrams.lastIndex) dialog.dismiss() else { index++; draw() }
            }.apply { minimumHeight = dp(56) }, weightLp(gap = 10))
            col.addView(nav); col.addView(navButton("Close practice", Gravity.CENTER) { dialog.dismiss() })
            dialog.setContentView(col)
        }
        draw(); dialog.show(); dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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

    // ---------- Chat ----------

    private val chat by lazy { Chat(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var renders = 0
    private var paused = false
    private var me: Member? = null

    /** Run network [work] off the main thread, then hand the result to [done] (or the error to [fail]). */
    private fun <T> bg(work: () -> T, fail: (Throwable) -> Unit = { toast(it.message ?: "Something went wrong.") }, done: (T) -> Unit) {
        Thread {
            val r = runCatching(work)
            r.exceptionOrNull()?.let { android.util.Log.w("Troop53", "chat call failed", it) }
            runOnUiThread { if (!isDestroyed) r.fold(done, fail) }
        }.start()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private fun note(t: String) = text(t, 15f, MUTED).apply { para(); layoutParams = lp(top = 12) }

    private fun ago(ts: String): CharSequence {
        val t = millis(ts)
        val now = System.currentTimeMillis()
        return if (now - t < 60_000) "just now" else DateUtils.getRelativeTimeSpanString(t, now, DateUtils.MINUTE_IN_MILLIS)
    }

    private fun confirm(message: String, action: String, onYes: () -> Unit) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton(action) { _, _ -> onYes() }.setNegativeButton("Cancel", null).show()
    }

    private fun field(hint: String, type: Int, max: Int = 100) = EditText(this).apply {
        this.hint = hint
        inputType = type
        typeface = Typeface.DEFAULT // password input types otherwise switch to monospace
        filters = arrayOf(InputFilter.LengthFilter(max))
        textSize = 16f
        setTextColor(INK)
        setHintTextColor(MUTED)
        background = rounded(CARD, 14f, LINE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = lp(top = 12)
    }

    private fun chatScreen(): View {
        val col = column().apply { setPadding(dp(16), dp(4), dp(16), dp(24)) }
        col.addView(text("💬  Troop Chat", 26f, FOREST, bold = true))
        val body = column()
        col.addView(body)
        val view = page(topBar(PAPER, "", dark = true), scroll(col))
        if (chat.uid == null) { signInForm(body); return view }
        body.addView(note("Loading…"))
        bg({
            val m = chat.me()
            val ok = m?.approved == true
            Triple(m, if (ok) chat.groups(m!!) else emptyList(), if (ok && m!!.leader) chat.members() else emptyList())
        }, fail = { e ->
            body.removeAllViews()
            if (chat.uid == null) signInForm(body) else {
                body.addView(note(e.message ?: "Something went wrong."))
                body.addView(bigButton("Try again", FOREST) { render() }.apply { layoutParams = lp(top = 12) })
                accountLinks(body)
            }
        }) { (m, groups, all) ->
            body.removeAllViews()
            me = m
            when {
                m == null -> profileForm(body)
                !m.approved -> {
                    body.addView(note("Thanks, ${m.name}! A troop leader needs to approve your account before you can chat."))
                    body.addView(bigButton("Check again", FOREST) { render() }.apply { layoutParams = lp(top = 16) })
                    accountLinks(body)
                }
                else -> groupList(body, m, groups, all)
            }
        }
        return view
    }

    private fun signInForm(body: LinearLayout) {
        body.addView(note("Chat with your troop. A leader approves every new account, and leaders can read every group."))
        val email = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field("Password (6 or more characters)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val extra = column().apply { visibility = View.GONE }
        val profile = profileFields(extra)
        extra.addView(note("Scouts under 13: ask a parent to set up your account with you."))
        var creating = false
        val submit = bigButton("Sign in", FOREST) {}
        val toggle = navButton("New here? Create an account", Gravity.CENTER) {}
        val failed: (Throwable) -> Unit = { submit.isEnabled = true; toast(it.message ?: "Something went wrong."); if (chat.uid != null) render() }
        submit.setOnClickListener {
            val e = email.text.toString().trim()
            val pw = password.text.toString()
            if (creating) {
                val (name, role) = profile()
                if (name.isEmpty() || role == null) { toast("Enter your name and choose Scout or Adult."); return@setOnClickListener }
                submit.isEnabled = false
                bg({ chat.signUp(e, pw, name, role) }, failed) { signedIn() }
            } else {
                submit.isEnabled = false
                bg({ chat.signIn(e, pw) }, failed) { signedIn() }
            }
        }
        toggle.setOnClickListener {
            creating = !creating
            extra.visibility = if (creating) View.VISIBLE else View.GONE
            submit.text = if (creating) "Create account" else "Sign in"
            toggle.text = if (creating) "Have an account? Sign in" else "New here? Create an account"
        }
        val forgot = navButton("Forgot password?", Gravity.CENTER) {
            val e = email.text.toString().trim()
            if (e.isEmpty()) toast("Enter your email first.") else bg({ chat.resetPassword(e) }) { toast("Check your email for a reset link.") }
        }
        listOf(email, password, extra, submit, toggle, forgot).forEach { body.addView(it.apply { layoutParams = lp(top = 12) }) }
    }

    /** Adds name + Scout/Adult inputs to [container]; returns a reader for them. */
    private fun profileFields(container: LinearLayout): () -> Pair<String, String?> {
        val name = field("Your name, as the troop knows you", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, max = 40)
        val roles = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val scout = RadioButton(this).apply { text = "Scout"; id = View.generateViewId(); textSize = 16f }
        val adult = RadioButton(this).apply { text = "Adult (parent or leader)"; id = View.generateViewId(); textSize = 16f }
        roles.addView(scout); roles.addView(adult)
        container.addView(name)
        container.addView(roles, lp(top = 8))
        return { name.text.toString().trim() to when (roles.checkedRadioButtonId) { scout.id -> "scout"; adult.id -> "adult"; else -> null } }
    }

    private fun profileForm(body: LinearLayout) {
        body.addView(note("Finish setting up your account."))
        val profile = profileFields(body)
        body.addView(bigButton("Save", FOREST) {
            val (name, role) = profile()
            if (name.isEmpty() || role == null) toast("Enter your name and choose Scout or Adult.")
            else bg({ chat.createProfile(name, role) }) { render() }
        }.apply { layoutParams = lp(top = 16) })
        accountLinks(body)
    }

    private fun signedIn() {
        ChatJob.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        render()
    }

    private fun accountLinks(body: LinearLayout) {
        body.addView(text("Signed in as ${chat.email}", 13f, MUTED).apply { gravity = Gravity.CENTER; layoutParams = lp(top = 28) })
        body.addView(navButton("Sign out", Gravity.CENTER) {
            chat.signOut(); ChatJob.cancel(this); me = null; render()
        }.apply { layoutParams = lp(top = 8) })
        body.addView(navButton("Delete my account", Gravity.CENTER) {
            val password = field("Your password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            AlertDialog.Builder(this).setMessage("Delete your chat account? Your messages stay in their groups.")
                .setView(FrameLayout(this).apply { setPadding(dp(20), 0, dp(20), 0); addView(password) })
                .setPositiveButton("Delete") { _, _ ->
                    bg({ chat.deleteAccount(password.text.toString()) }) { ChatJob.cancel(this); me = null; render() }
                }
                .setNegativeButton("Cancel", null).show()
        }.apply { setTextColor(RED); layoutParams = lp(top = 8) })
    }

    private fun groupList(body: LinearLayout, m: Member, groups: List<Group>, all: List<Member>) {
        if (m.leader) {
            val waiting = all.count { !it.approved }
            body.addView(navButton("👥  Members & approvals" + if (waiting > 0) "  ($waiting waiting)" else "", Gravity.START) {
                go(Screen.ChatMembers)
            }.apply { layoutParams = lp(top = 12) })
        }
        body.addView(bigButton("+  New group", FOREST) { go(Screen.GroupEdit(null)) }.apply { layoutParams = lp(top = 12) })
        if (groups.isEmpty()) body.addView(note("No groups yet. Start one for your patrol!"))
        val seen = getSharedPreferences("chat_seen", MODE_PRIVATE)
        groups.forEach { g ->
            val member = m.uid in g.members
            val row = column().apply {
                background = ripple(rounded(CARD, 14f, LINE))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = lp(top = 10)
                setOnClickListener { go(Screen.ChatGroup(g.id)) }
            }
            val unread = member && seen.getString(g.id, null) != g.lastAt
            row.addView(text((if (unread) "● " else "") + g.name, 17f, INK, bold = true))
            row.addView(text("${g.members.size} members · ${ago(g.lastAt)}" + if (member) "" else " · leader view", 13f, MUTED))
            body.addView(row)
        }
        accountLinks(body)
    }

    private fun chatGroupScreen(gid: String): View {
        val more = text("⋯", 24f, Color.WHITE).apply { contentDescription = "Group options"; setPadding(dp(14), 0, dp(14), 0) }
        val bar = topBar(FOREST, "Loading…", more)
        val list = column().apply { setPadding(dp(12), dp(8), dp(12), dp(12)) }
        val sc = scroll(list)
        val input = field("Message", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE, max = 2000).apply {
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val send = bigButton("Send", FOREST) {}
        val compose = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(10))
            visibility = View.GONE
            addView(input)
            addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        }
        val view = column().apply {
            addView(bar)
            addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(compose)
        }

        val empty = note("No messages yet. Say hi!")
        val shown = HashSet<String>()
        var last: String? = null
        val gen = renders
        fun add(msgs: List<Msg>) {
            if (msgs.isNotEmpty()) list.removeView(empty)
            msgs.filter { shown.add(it.id) }.forEach { list.addView(bubble(gid, it)) }
            msgs.lastOrNull()?.let { last = it.at }
            if (msgs.isNotEmpty()) sc.post { sc.fullScroll(View.FOCUS_DOWN) }
        }
        // ponytail: 4 s polling while this screen is open; Firestore's listen stream would need the SDK
        fun poll() {
            handler.postDelayed({
                when {
                    gen != renders -> Unit
                    paused -> poll()
                    else -> bg({ chat.messages(gid, last) }, { poll() }) { add(it); poll() }
                }
            }, 4000)
        }

        bg({ Triple(me ?: chat.me(), chat.group(gid), chat.messages(gid, null)) }, { list.addView(note(it.message ?: "")) }) { (m, g, msgs) ->
            me = m
            if (m == null || g == null) { list.addView(note("This group no longer exists.")); return@bg }
            (bar.getChildAt(1) as TextView).text = g.name
            ChatJob.markSeen(this, g)
            if (m.uid in g.members) compose.visibility = View.VISIBLE
            else list.addView(note("Leader view: you can read this group but you are not in it."))
            list.addView(empty)
            add(msgs)
            poll()
            more.setOnClickListener { groupMenu(g, m) }
        }
        send.setOnClickListener {
            val t = input.text.toString().trim()
            val m = me ?: return@setOnClickListener
            if (t.isEmpty()) return@setOnClickListener
            send.isEnabled = false
            bg({ chat.send(gid, m.name, t); chat.messages(gid, last) }, { send.isEnabled = true; toast(it.message ?: "Not sent.") }) {
                input.setText(""); send.isEnabled = true; add(it)
            }
        }
        return view
    }

    private fun bubble(gid: String, msg: Msg): View {
        val mine = msg.uid == chat.uid
        val box = column().apply {
            background = rounded(if (mine) FOREST else CARD, 16f, if (mine) null else LINE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        if (!mine) box.addView(text(msg.name, 12f, RED, bold = true))
        box.addView(text(msg.text, 16f, if (mine) Color.WHITE else INK).apply { para() })
        box.addView(text(ago(msg.at), 11f, if (mine) 0xCCFFFFFF.toInt() else MUTED))
        val row = LinearLayout(this).apply { gravity = if (mine) Gravity.END else Gravity.START; layoutParams = lp(top = 6) }
        row.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (mine) marginStart = dp(48) else marginEnd = dp(48)
        })
        if (mine || me?.leader == true) box.setOnLongClickListener {
            confirm("Delete this message?", "Delete") { bg({ chat.deleteMessage(gid, msg.id) }) { (row.parent as? ViewGroup)?.removeView(row) } }
            true
        }
        return row
    }

    private fun groupMenu(g: Group, m: Member) {
        val member = m.uid in g.members
        val options = buildList {
            if (member || m.leader) add("Edit name and members" to { go(Screen.GroupEdit(g.id)) })
            if (member) add("Leave group" to {
                confirm("Leave ${g.name}?", "Leave") { bg({ chat.editGroup(g.id, g.name, g.members - m.uid) }) { onBackPressed() } }
            })
            if (m.leader || g.createdBy == m.uid) add("Delete group" to {
                confirm("Delete ${g.name} for everyone?", "Delete") { bg({ chat.deleteGroup(g.id) }) { onBackPressed() } }
            })
        }
        AlertDialog.Builder(this).setTitle(g.name)
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> options[i].second() }
            .setNegativeButton("Close", null).show()
    }

    private fun groupEditScreen(gid: String?): View {
        val col = column().apply { setPadding(dp(16), dp(4), dp(16), dp(24)) }
        col.addView(text(if (gid == null) "New group" else "Edit group", 26f, FOREST, bold = true))
        val name = field("Group name, e.g. Eagle Patrol", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS, max = 50)
        col.addView(name)
        col.addView(text("WHO'S IN IT", 13f, MUTED, bold = true).apply { letterSpacing = 0.1f; layoutParams = lp(top = 20) })
        val boxes = column()
        col.addView(boxes)
        col.addView(note("Leaders can read every group. A group can't be just one adult and one scout."))
        val save = bigButton(if (gid == null) "Create group" else "Save", FOREST) {}.apply { layoutParams = lp(top = 16) }
        col.addView(save)
        boxes.addView(note("Loading…"))
        bg({ Triple(me ?: chat.me() ?: throw ChatException("Please sign in again."), chat.members().filter { it.approved }, gid?.let { chat.group(it) }) }) { (m, all, g) ->
            me = m
            boxes.removeAllViews()
            g?.let { name.setText(it.name) }
            val chosen = (g?.members ?: listOf(m.uid)).toMutableSet()
            all.sortedWith(compareBy({ it.uid != m.uid }, { it.name.lowercase() })).forEach { p ->
                boxes.addView(CheckBox(this).apply {
                    text = (if (p.uid == m.uid) "You (${p.name})" else p.name) + "  ·  " + if (p.leader) "leader" else p.role
                    textSize = 16f
                    isChecked = p.uid in chosen
                    setOnCheckedChangeListener { _, on -> if (on) chosen += p.uid else chosen -= p.uid }
                })
            }
            save.setOnClickListener {
                val n = name.text.toString().trim()
                val roles = chosen.mapNotNull { id -> all.find { it.uid == id }?.role }.toSet()
                when {
                    n.isEmpty() -> toast("Give the group a name.")
                    chosen.isEmpty() -> toast("Pick at least one person.")
                    gid == null && m.uid !in chosen -> toast("Include yourself in a new group.")
                    chosen.size == 2 && roles.size == 2 -> toast("Add a third person. One adult and one scout alone isn't allowed.")
                    else -> {
                        save.isEnabled = false
                        bg({ if (gid == null) chat.createGroup(n, chosen.toList()) else chat.editGroup(gid, n, chosen.toList()) },
                            { save.isEnabled = true; toast(it.message ?: "Not saved.") }) { onBackPressed() }
                    }
                }
            }
        }
        return page(topBar(PAPER, "", dark = true), scroll(col))
    }

    private fun membersScreen(): View {
        val col = column().apply { setPadding(dp(16), dp(4), dp(16), dp(24)) }
        col.addView(text("Members", 26f, FOREST, bold = true))
        col.addView(note("Approve only people you know from the troop. For scouts under 13, confirm a parent agreed."))
        val list = column()
        col.addView(list)
        list.addView(note("Loading…"))
        bg({ chat.members() }) { all ->
            list.removeAllViews()
            all.sortedWith(compareBy({ it.approved }, { it.name.lowercase() })).forEach { p ->
                val row = column().apply {
                    background = rounded(CARD, 14f, LINE)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    layoutParams = lp(top = 10)
                }
                row.addView(text(p.name, 17f, INK, bold = true))
                row.addView(text(listOfNotNull(p.role, "leader".takeIf { p.leader }, "waiting for approval".takeIf { !p.approved }).joinToString(" · "),
                    13f, if (p.approved) MUTED else RED))
                if (p.uid != chat.uid) {
                    val actions = LinearLayout(this)
                    fun act(label: String, onClick: () -> Unit) =
                        actions.addView(navButton(label, Gravity.CENTER, onClick), weightLp(gap = if (actions.childCount == 0) 0 else 8))
                    if (!p.approved) act("Approve") { bg({ chat.setUser(p.uid, true, false) }) { render() } }
                    if (p.approved && p.role == "adult") act(if (p.leader) "Remove leader" else "Make leader") {
                        bg({ chat.setUser(p.uid, true, !p.leader) }) { render() }
                    }
                    act("Remove") { confirm("Remove ${p.name} from troop chat?", "Remove") { bg({ chat.removeUser(p.uid) }) { render() } } }
                    row.addView(actions, lp(top = 8))
                }
                list.addView(row)
            }
        }
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

    private fun openScoutbook() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SCOUTBOOK_URL)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser found. Visit $SCOUTBOOK_URL", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(host.windowToken, 0)
    }

    companion object {
        private const val KHAKI_DARK = 0xFFB89B52.toInt()
    }
}
