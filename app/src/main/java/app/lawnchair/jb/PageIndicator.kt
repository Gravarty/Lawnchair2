package app.lawnchair.jb

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** Thin holo-blue bar that fades out after scrolling (JB 4.1/4.2 style). */
class PageIndicator(ctx: Context) : View(ctx) {

    var pageCount = 1
        set(value) {
            field = value.coerceAtLeast(1)
            invalidate()
        }
    private var progress = 0f
    private val trackPaint = Paint().apply { color = 0x33FFFFFF }
    private val barPaint = Paint().apply { color = HOLO_BLUE }
    private val fadeOut = Runnable { animate().alpha(0f).setDuration(450).start() }

    init {
        alpha = 0f
    }

    fun setScroll(p: Float) {
        progress = p
        animate().cancel()
        alpha = 1f
        removeCallbacks(fadeOut)
        postDelayed(fadeOut, 700)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val margin = dp(16).toFloat()
        val w = width - 2 * margin
        val cy = height / 2f
        val seg = w / pageCount
        canvas.drawRect(margin, cy - dp(0.5f), margin + w, cy + dp(0.5f), trackPaint)
        val left = margin + progress.coerceIn(0f, (pageCount - 1).toFloat()) * seg
        canvas.drawRect(left, cy - dp(1.5f), left + seg, cy + dp(1.5f), barPaint)
    }
}
