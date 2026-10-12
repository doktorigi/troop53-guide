package com.doktorigi.troop53

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View

/** The Troop 53 shield, drawn in a 100 x 115 unit box and scaled to fit. */
class EmblemView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outer = shield(0f)
    private val inner = shield(5f)

    init { contentDescription = "Troop 53 emblem" }

    private fun shield(i: Float) = Path().apply {
        moveTo(6f + i, 4f + i)
        lineTo(94f - i, 4f + i)
        lineTo(94f - i, 62f)
        cubicTo(94f - i, 88f - i / 2, 72f - i / 2, 101f - i, 50f, 111f - i * 1.4f)
        cubicTo(28f + i / 2, 101f - i, 6f + i, 88f - i / 2, 6f + i, 62f)
        close()
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width / 100f, height / 115f)
        canvas.save()
        canvas.translate((width - 100f * s) / 2, (height - 115f * s) / 2)
        canvas.scale(s, s)

        paint.style = Paint.Style.FILL
        paint.color = KHAKI.toInt(); canvas.drawPath(outer, paint)
        paint.color = FOREST.toInt(); canvas.drawPath(inner, paint)

        canvas.save()
        canvas.clipPath(inner)
        paint.color = RED.toInt(); canvas.drawRect(0f, 0f, 100f, 31f, paint)
        // mountains along the bottom
        paint.color = FOREST_LIGHT.toInt()
        canvas.drawPath(Path().apply {
            moveTo(0f, 100f); lineTo(30f, 80f); lineTo(44f, 90f); lineTo(64f, 74f); lineTo(100f, 98f); lineTo(100f, 120f); lineTo(0f, 120f); close()
        }, paint)
        canvas.restore()
        paint.color = KHAKI.toInt()
        paint.strokeWidth = 1.6f
        canvas.drawLine(11f, 31f, 89f, 31f, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = PAPER.toInt()
        paint.textSize = 15f
        paint.letterSpacing = 0.18f
        canvas.drawText("TROOP", 51f, 23f, paint)
        paint.letterSpacing = 0f
        paint.color = KHAKI.toInt()
        paint.textSize = 44f
        canvas.drawText("53", 50f, 73f, paint)
        canvas.restore()
    }

    companion object {
        const val FOREST = 0xFF1F3A24
        const val FOREST_LIGHT = 0xFF2E5232
        const val KHAKI = 0xFFD9C79A
        const val RED = 0xFFB5332A
        const val PAPER = 0xFFF4EEDF
    }
}
