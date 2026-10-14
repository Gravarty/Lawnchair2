package app.lawnchair.jb

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import kotlin.math.floor

/** Grid container, like Launcher2's CellLayout. */
class CellLayout(ctx: Context, val countX: Int, val countY: Int) : ViewGroup(ctx) {

    class LayoutParams(
        var cellX: Int,
        var cellY: Int,
        var spanX: Int = 1,
        var spanY: Int = 1,
    ) : ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

    var cellWidth = 0
        private set
    var cellHeight = 0
        private set

    private val outline = RectF()
    private var outlineVisible = false
    private var outlineValid = true
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2).toFloat()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        cellWidth = (w - paddingLeft - paddingRight) / countX
        cellHeight = (h - paddingTop - paddingBottom) / countY
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(lp.spanX * cellWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(lp.spanY * cellHeight, MeasureSpec.EXACTLY),
            )
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            val x = paddingLeft + lp.cellX * cellWidth
            val y = paddingTop + lp.cellY * cellHeight
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = p is LayoutParams
    override fun generateDefaultLayoutParams() = LayoutParams(0, 0)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams?) = LayoutParams(0, 0)

    fun isRegionVacant(x: Int, y: Int, sx: Int, sy: Int, ignore: View? = null): Boolean {
        if (x < 0 || y < 0 || x + sx > countX || y + sy > countY) return false
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child === ignore) continue
            val lp = child.layoutParams as LayoutParams
            if (x < lp.cellX + lp.spanX && lp.cellX < x + sx &&
                y < lp.cellY + lp.spanY && lp.cellY < y + sy
            ) return false
        }
        return true
    }

    fun findVacantCell(sx: Int, sy: Int): IntArray? {
        for (y in 0..countY - sy) for (x in 0..countX - sx) {
            if (isRegionVacant(x, y, sx, sy)) return intArrayOf(x, y)
        }
        return null
    }

    /** Top-left cell of a span centered on the given local point. */
    fun cellAt(localX: Float, localY: Float, sx: Int, sy: Int): IntArray {
        if (cellWidth == 0 || cellHeight == 0) return intArrayOf(0, 0)
        val x = floor((localX - paddingLeft) / cellWidth - (sx - 1) / 2f).toInt()
        val y = floor((localY - paddingTop) / cellHeight - (sy - 1) / 2f).toInt()
        return intArrayOf(x.coerceIn(0, countX - sx), y.coerceIn(0, countY - sy))
    }

    fun showDragOutline(x: Int, y: Int, sx: Int, sy: Int, valid: Boolean) {
        val inset = dp(4).toFloat()
        outline.set(
            paddingLeft + x * cellWidth + inset,
            paddingTop + y * cellHeight + inset,
            paddingLeft + (x + sx) * cellWidth - inset,
            paddingTop + (y + sy) * cellHeight - inset,
        )
        outlineVisible = true
        outlineValid = valid
        invalidate()
    }

    fun clearDragOutline() {
        if (!outlineVisible) return
        outlineVisible = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!outlineVisible) return
        val color = if (outlineValid) HOLO_BLUE else 0xFFFF4444.toInt()
        val r = dp(4).toFloat()
        fillPaint.color = color and 0x00FFFFFF or 0x33000000
        strokePaint.color = color
        canvas.drawRoundRect(outline, r, r, fillPaint)
        canvas.drawRoundRect(outline, r, r, strokePaint)
    }
}
