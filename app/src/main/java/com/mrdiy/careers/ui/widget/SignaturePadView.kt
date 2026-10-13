package com.mrdiy.careers.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A simple finger/touch signature pad — draws a single stroke-path as the user signs,
 * exports to a white-background PNG bitmap. No external library: a hand-rolled
 * Canvas + Path view is enough for a plain signature, per ADMIN_MAJOR_PLAN.md §P/R research
 * (the szimek signature_pad JS library is the web equivalent of this same idea).
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var hasSignature: Boolean = false
        private set

    /** Fired once, the moment the first stroke starts (so a submit button can enable itself). */
    var onSignatureStarted: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
                val wasEmpty = !hasSignature
                hasSignature = true
                if (wasEmpty) onSignatureStarted?.invoke()
            }
            MotionEvent.ACTION_MOVE -> path.lineTo(event.x, event.y)
            MotionEvent.ACTION_UP -> { /* stroke segment complete */ }
            else -> return false
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }

    fun clear() {
        path.reset()
        hasSignature = false
        invalidate()
    }

    /** White-background PNG bytes of the current signature, or null if nothing was drawn. */
    fun exportPng(): ByteArray? {
        if (!hasSignature || width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawPath(path, paint)
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
