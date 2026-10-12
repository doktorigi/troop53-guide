package com.doktorigi.troop53

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.max

/** Native vectors stay sharp at every zoom level; bitmap decoding runs off the UI thread. */
class IllustrationView(context: Context, private val assetPath: String, caption: String,
                       private val zoomable: Boolean = false) : View(context) {
    private var art: Drawable? = null
    private var job: Future<*>? = null
    private var generation = 0
    private var failed = false
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 6f)
            val ratio = zoom / old
            offsetX = detector.focusX - width / 2f - (detector.focusX - width / 2f - offsetX) * ratio
            offsetY = detector.focusY - height / 2f - (detector.focusY - height / 2f - offsetY) * ratio
            clamp(); invalidate(); return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!pinch.isInProgress) { offsetX -= dx; offsetY -= dy; clamp(); invalidate() }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { setZoom(if (zoom > 1f) 1f else 2.5f); return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { performClick(); return true }
    })

    init { contentDescription = caption; isFocusable = true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (zoomable) { super.onMeasure(widthMeasureSpec, heightMeasureSpec); return }
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val ratio = art?.let { it.intrinsicHeight.toFloat() / it.intrinsicWidth }
            ?: if (assetPath.endsWith(".xml")) 450f / 720f else 9f / 16f
        setMeasuredDimension(w, resolveSize((w * ratio).toInt(), heightMeasureSpec))
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (width > 0) load() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && isAttachedToWindow) load()
        clamp()
    }

    private fun load() {
        if (art != null || job != null) return
        if (assetPath.endsWith(".xml")) {
            val name = "img_" + assetPath.removeSuffix(".xml").replace('/', '_').replace('-', '_')
            val id = resources.getIdentifier(name, "drawable", context.packageName)
            art = if (id == 0) null else context.getDrawable(id)
            failed = art == null; invalidate(); return
        }
        val token = ++generation
        val target = if (zoomable) 2048 else width.coerceAtMost(1080)
        job = decoder.submit {
            val drawable = try {
                require(GuideParser.validImagePath(assetPath))
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open("img/$assetPath").use { BitmapFactory.decodeStream(it, null, bounds) }
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (bounds.outWidth / sample > target || bounds.outHeight / sample > target ||
                    (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 4_194_304L) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
                context.assets.open("img/$assetPath").use { BitmapFactory.decodeStream(it, null, options) }
                    ?.let { BitmapDrawable(resources, it) }
            } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            post {
                if (token == generation && isAttachedToWindow) {
                    art = drawable; failed = drawable == null; job = null; requestLayout(); clamp(); invalidate()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        generation++; job?.cancel(true); job = null; art = null
        super.onDetachedFromWindow()
    }

    private fun fit(): Float = art?.let { minOf(width.toFloat() / it.intrinsicWidth, height.toFloat() / it.intrinsicHeight) } ?: 1f
    private fun clamp() {
        val d = art ?: return
        val s = fit() * zoom
        val x = max(0f, (d.intrinsicWidth * s - width) / 2)
        val y = max(0f, (d.intrinsicHeight * s - height) / 2)
        offsetX = offsetX.coerceIn(-x, x); offsetY = offsetY.coerceIn(-y, y)
    }
    fun setZoom(value: Float) { zoom = value.coerceIn(1f, 6f); if (zoom == 1f) { offsetX = 0f; offsetY = 0f }; clamp(); invalidate() }
    fun zoomBy(factor: Float) = setZoom(zoom * factor)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        if (!zoomable) canvas.clipPath(Path().apply {
            addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 12 * resources.displayMetrics.density,
                12 * resources.displayMetrics.density, Path.Direction.CW)
        })
        canvas.drawColor(EmblemView.PAPER.toInt())
        val d = art
        if (d != null) {
            val s = fit() * zoom
            canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
            canvas.scale(s, s)
            canvas.translate(-d.intrinsicWidth / 2f, -d.intrinsicHeight / 2f)
            d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight); d.draw(canvas)
        } else {
            paint.color = EmblemView.FOREST.toInt(); paint.textSize = 14 * resources.displayMetrics.scaledDensity
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(if (failed) "Illustration unavailable" else "Loading illustration…", width / 2f, height / 2f, paint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!zoomable) return super.onTouchEvent(event)
        pinch.onTouchEvent(event); gestures.onTouchEvent(event)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }

    companion object { private val decoder = Executors.newFixedThreadPool(2) }
}
