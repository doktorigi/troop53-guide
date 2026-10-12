package com.doktorigi.troop53

import android.app.Instrumentation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView

/** Real-device checks with only Android framework APIs, no test UI library. */
class IllustrationChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            val categories = targetContext.assets.list("guide")!!.sorted().flatMap { file ->
                targetContext.assets.open("guide/$file").bufferedReader().use { GuideParser.parse(it.readText()) }
            }
            val paths = categories.flatMap { c -> c.articles.flatMap { a -> a.blocks.mapNotNull { it.imagePath } } } +
                categories.map { "headers/${it.id}.xml" }
            val bitmap = Bitmap.createBitmap(720, 450, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            var vectors = 0
            paths.distinct().filter { it.endsWith(".xml") }.forEach { path ->
                val name = "img_" + path.removeSuffix(".xml").replace('/', '_').replace('-', '_')
                val id = targetContext.resources.getIdentifier(name, "drawable", targetContext.packageName)
                check(id != 0) { "Missing compiled vector $path" }
                val drawable = requireNotNull(targetContext.getDrawable(id))
                drawable.setBounds(0, 0, 720, 450); drawable.draw(canvas)
                val colors = mutableSetOf<Int>()
                for (x in 0 until 720 step 9) for (y in 0 until 450 step 9) colors += bitmap.getPixel(x, y)
                check(colors.size > 3) { "Blank vector $path" }
                vectors++
            }
            bitmap.recycle()

            result.putString("stage", "All $vectors vector renders passed; starting UI checks")
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
            waitForIdleSync()
            runOnMainSync {
                val root = activity.window.decorView
                descendants(root).filterIsInstance<EditText>().firstOrNull()?.setText("bowline") ?: error("Missing home search")
                val title = descendants(root).filterIsInstance<TextView>().firstOrNull { it.text.toString() == "Bowline" } ?: error("Missing Bowline search result")
                var row: View = title
                while (!row.isClickable) row = row.parent as View
                row.performClick()
                descendants(root).filterIsInstance<IllustrationView>().firstOrNull()?.performClick() ?: error("Missing article illustration")
            }
            waitForIdleSync(); SystemClock.sleep(250)
            val imageNode = awaitNode { it.contentDescription?.startsWith("Step 1:") == true }
            val bounds = Rect().also { imageNode.getBoundsInScreen(it) }
            val before = requireNotNull(uiAutomation.takeScreenshot())
            pinch(bounds)
            waitForIdleSync(); SystemClock.sleep(100)
            val enlarged = requireNotNull(uiAutomation.takeScreenshot())
            check(difference(before, enlarged, bounds) > 100) { "Pinch did not enlarge artwork" }
            click("Reset"); waitForIdleSync(); SystemClock.sleep(400)
            val reset = requireNotNull(uiAutomation.takeScreenshot())
            val resetDifference = difference(before, reset, bounds)
            // VectorDrawable may rebuild its raster cache at a sharper resolution after zoom.
            // Permit edge antialiasing changes, while requiring the original artwork bounds.
            val initialBox = contentBox(before, bounds); val restoredBox = contentBox(reset, bounds)
            check(listOf(initialBox.left - restoredBox.left, initialBox.top - restoredBox.top,
                initialBox.right - restoredBox.right, initialBox.bottom - restoredBox.bottom).all { kotlin.math.abs(it) <= 3 }) {
                "Reset did not restore original artwork bounds: $initialBox vs $restoredBox"
            }
            check(resetDifference < bounds.width() * bounds.height() / 36 * .02) { "Reset changed more than antialiased edges: $resetDifference" }
            before.recycle(); enlarged.recycle(); reset.recycle()
            click("Close"); waitForIdleSync()
            awaitNode { it.text?.toString() == "Knot Practice  ›" }
            result.putString("stream", "PASS: $vectors compiled vectors rendered; two-finger pinch enlarged artwork; Reset restored fit; Close returned to article.\n")
            finish(-1, result)
        } catch (e: Throwable) {
            result.putString("stream", "FAIL: ${e.stackTraceToString()}\n"); finish(0, result)
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun click(text: String) {
        val node = awaitNode { it.text?.toString() == text }
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Could not click $text" }
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val found = (nodes(uiAutomation.rootInActiveWindow) + uiAutomation.windows.flatMap { nodes(it.root) }).firstOrNull(predicate)
            if (found != null) return found
            SystemClock.sleep(100)
        }
        error("Missing accessible node after window settled")
    }
    private fun difference(a: Bitmap, b: Bitmap, bounds: Rect): Int {
        var changed = 0
        for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(a.width) step 6)
            for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(a.height) step 6)
                if (a.getPixel(x, y) != b.getPixel(x, y)) changed++
        return changed
    }
    private fun contentBox(bitmap: Bitmap, bounds: Rect): Rect {
        val box = Rect(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0)
        val paper = EmblemView.PAPER.toInt()
        for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width) step 3)
            for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height) step 3) {
                val pixel = bitmap.getPixel(x, y)
                if (kotlin.math.abs(Color.red(pixel) - Color.red(paper)) + kotlin.math.abs(Color.green(pixel) - Color.green(paper)) +
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(paper)) > 150) {
                    box.left = minOf(box.left, x); box.top = minOf(box.top, y)
                    box.right = maxOf(box.right, x); box.bottom = maxOf(box.bottom, y)
                }
            }
        return box
    }
    private fun pinch(bounds: Rect) {
        val down = SystemClock.uptimeMillis()
        val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        fun send(action: Int, spread: Float, count: Int = 2) {
            val properties = Array(count) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coordinates = Array(count) { i -> MotionEvent.PointerCoords().apply {
                x = cx + (if (i == 0) -spread else spread); y = cy; pressure = 1f; size = 1f
            } }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            check(uiAutomation.injectInputEvent(event, true)); event.recycle()
            SystemClock.sleep(20)
        }
        send(MotionEvent.ACTION_DOWN, 70f, 1)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 70f)
        for (i in 1..10) send(MotionEvent.ACTION_MOVE, 70f + i * 22f)
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 290f)
        send(MotionEvent.ACTION_UP, 290f, 1)
    }
}
