package app.lawnchair.jb

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class LauncherWidgetHost(ctx: Context, id: Int) : AppWidgetHost(ctx, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherWidgetHostView(context)
}

/** Detects long-press over widget content so widgets can be dragged. */
class LauncherWidgetHostView(ctx: Context) : AppWidgetHostView(ctx) {
    private var performedLongPress = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val checkLongPress = Runnable {
        if (parent != null && hasWindowFocus() && performLongClick()) performedLongPress = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (performedLongPress) {
            performedLongPress = false
            return true
        }
        track(ev)
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return true
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                removeCallbacks(checkLongPress)
                postDelayed(checkLongPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(checkLongPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(checkLongPress)
        }
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(checkLongPress)
    }
}
