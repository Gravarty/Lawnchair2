package app.lawnchair.jb

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView

/** Root view; owns the floating drag icon and routes touch during a drag. */
class DragLayer(ctx: Context) : FrameLayout(ctx) {

    interface Listener {
        fun onDragMove(x: Float, y: Float)
        fun onDrop(x: Float, y: Float)
        fun onDragCancel()
    }

    var listener: Listener? = null
    var lastRawX = 0f
        private set
    var lastRawY = 0f
        private set

    private var dragView: ImageView? = null
    private var offX = 0f
    private var offY = 0f
    private var dragW = 0
    private var dragH = 0

    val isDragging get() = dragView != null
    val dragCenterX get() = lastRawX - offX + dragW / 2f
    val dragCenterY get() = lastRawY - offY + dragH / 2f

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastRawX = ev.rawX
        lastRawY = ev.rawY
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (dragView == null) return false
        handleDragEvent(ev)
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (dragView == null) return super.onTouchEvent(ev)
        handleDragEvent(ev)
        return true
    }

    private fun handleDragEvent(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                updatePosition()
                listener?.onDragMove(ev.rawX, ev.rawY)
            }
            MotionEvent.ACTION_UP -> {
                listener?.onDrop(ev.rawX, ev.rawY)
                endDrag()
            }
            MotionEvent.ACTION_CANCEL -> {
                listener?.onDragCancel()
                endDrag()
            }
        }
    }

    /** screenX/Y: where the bitmap's top-left currently sits on screen. */
    fun startDrag(bmp: Bitmap, screenX: Int, screenY: Int) {
        endDrag()
        dragW = bmp.width
        dragH = bmp.height
        offX = lastRawX - screenX
        offY = lastRawY - screenY
        val iv = ImageView(context).apply {
            setImageBitmap(bmp)
            alpha = 0.9f
        }
        addView(iv, LayoutParams(bmp.width, bmp.height))
        dragView = iv
        updatePosition()
        iv.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
        listener?.onDragMove(lastRawX, lastRawY)
    }

    private fun updatePosition() {
        val iv = dragView ?: return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        iv.translationX = lastRawX - offX - loc[0]
        iv.translationY = lastRawY - offY - loc[1]
    }

    private fun endDrag() {
        dragView?.let { removeView(it) }
        dragView = null
    }
}
