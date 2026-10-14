package app.lawnchair.jb

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Horizontal pager with snapping, like Launcher2's PagedView. */
open class PagedView(ctx: Context) : ViewGroup(ctx) {

    var currentPage = 0
        private set
    var onScroll: ((Float) -> Unit)? = null

    private val scroller = OverScroller(ctx, DecelerateInterpolator(1.5f))
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(ctx).scaledPagingTouchSlop
    private val minFlingVelocity = ctx.dp(400)
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var lastWidth = 0

    val pageCount get() = childCount
    val isScrolling get() = dragging || !scroller.isFinished

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(cw, ch)
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        for (i in 0 until childCount) getChildAt(i).layout(i * w, 0, (i + 1) * w, b - t)
        if (w != lastWidth) {
            lastWidth = w
            scroller.forceFinished(true)
            scrollTo(currentPage * w, 0)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastX = ev.x
                dragging = !scroller.isFinished
                if (dragging) scroller.forceFinished(true)
            }
            MotionEvent.ACTION_MOVE -> checkStartDrag(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                recycleVelocity()
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) scroller.forceFinished(true)
                downX = ev.x; downY = ev.y; lastX = ev.x
            }
            MotionEvent.ACTION_MOVE -> {
                checkStartDrag(ev)
                if (dragging) {
                    var dx = lastX - ev.x
                    lastX = ev.x
                    val max = (pageCount - 1) * width
                    if (scrollX + dx < 0 || scrollX + dx > max) dx *= 0.3f
                    scrollBy(dx.roundToInt(), 0)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    val vt = velocityTracker
                    vt?.computeCurrentVelocity(1000)
                    val v = vt?.xVelocity ?: 0f
                    val pos = scrollX.toFloat() / width.coerceAtLeast(1)
                    val page = when {
                        v > minFlingVelocity -> floor(pos).toInt()
                        v < -minFlingVelocity -> ceil(pos).toInt()
                        else -> pos.roundToInt()
                    }
                    snapToPage(page)
                }
                dragging = false
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) snapToPage((scrollX.toFloat() / width.coerceAtLeast(1)).roundToInt())
                dragging = false
                recycleVelocity()
            }
        }
        return true
    }

    private fun checkStartDrag(ev: MotionEvent) {
        if (dragging) return
        val dx = abs(ev.x - downX)
        val dy = abs(ev.y - downY)
        if (dx > touchSlop && dx > dy) {
            dragging = true
            lastX = ev.x
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun trackVelocity(ev: MotionEvent) {
        val vt = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        vt.addMovement(ev)
    }

    private fun recycleVelocity() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    fun snapToPage(page: Int) {
        if (pageCount == 0) return
        val p = page.coerceIn(0, pageCount - 1)
        currentPage = p
        if (width == 0) return
        val dx = p * width - scrollX
        val duration = min(550, 250 + abs(dx) * 300 / width)
        scroller.startScroll(scrollX, 0, dx, 0, duration)
        postInvalidateOnAnimation()
    }

    fun setCurrentPage(page: Int) {
        currentPage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scroller.forceFinished(true)
        scrollTo(currentPage * width, 0)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (width > 0) onScroll?.invoke(l.toFloat() / width)
    }
}
