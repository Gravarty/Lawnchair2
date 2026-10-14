package app.lawnchair.jb

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.speech.RecognizerIntent
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/** Icon + label with drop shadow, like Launcher2's BubbleTextView. */
class BubbleTextView(ctx: Context) : TextView(ctx) {
    init {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xDD000000.toInt())
        compoundDrawablePadding = dp(4)
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        setPadding(dp(2), dp(8), dp(2), 0)
        background = pressedBackground(ctx)
    }

    fun bind(app: AppInfo, iconSize: Int, showLabel: Boolean) {
        contentDescription = app.label
        if (showLabel) {
            text = app.label
        } else {
            text = null
            gravity = Gravity.CENTER
            compoundDrawablePadding = 0
            setPadding(0, 0, 0, 0)
        }
        setIcon(app.icon, iconSize)
    }

    fun setIcon(d: Drawable, size: Int) {
        val icon = d.constantState?.newDrawable(resources)?.mutate() ?: d
        icon.setBounds(0, 0, size, size)
        setCompoundDrawables(null, icon, null, null)
    }
}

/** JB all-apps button: white ring with 2x3 dots. */
class AllAppsButtonDrawable(ctx: Context) : Drawable() {
    private val stroke = ctx.dp(2).toFloat()
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = 0xEEFFFFFF.toInt()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = min(b.width(), b.height()) / 2f - stroke
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        canvas.drawCircle(cx, cy, r, fillPaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
        val dot = r * 0.1f
        val gap = r * 0.36f
        for (row in 0..1) for (col in -1..1) {
            canvas.drawCircle(cx + col * gap, cy + (row - 0.5f) * gap, dot, dotPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        ringPaint.alpha = alpha; dotPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ringPaint.colorFilter = colorFilter; dotPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Quick search bar on top of the home screen. */
class SearchBar(ctx: Context) : LinearLayout(ctx) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(4), 0)
        val gray = ColorStateList.valueOf(0xCCFFFFFF.toInt())

        val search = TextView(ctx).apply {
            text = "Search"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            compoundDrawablePadding = dp(8)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            compoundDrawableTintList = gray
            background = pressedBackground(ctx)
            setOnClickListener { openSearch() }
        }
        addView(search, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(View(ctx).apply { setBackgroundColor(0x44FFFFFF) }, LayoutParams(dp(1), dp(24)))

        val mic = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_mic)
            imageTintList = gray
            scaleType = ImageView.ScaleType.CENTER
            background = pressedBackground(ctx)
            contentDescription = "Voice search"
            setOnClickListener { tryStart(Intent(RecognizerIntent.ACTION_WEB_SEARCH)) }
        }
        addView(mic, LayoutParams(dp(52), LayoutParams.MATCH_PARENT))
    }

    private fun openSearch() {
        tryStart(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, ""),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://duckduckgo.com")),
        )
    }

    private fun tryStart(vararg intents: Intent) {
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
    }
}

/** "Remove / Uninstall | App info" bar shown while dragging. */
class DropTargetBar(ctx: Context) : LinearLayout(ctx) {
    companion object {
        const val TARGET_NONE = 0
        const val TARGET_REMOVE = 1
        const val TARGET_INFO = 2
        private const val NORMAL = 0xFFCCCCCC.toInt()
        private const val RED = 0xFFFF4444.toInt()
    }

    private val remove = makeTarget(R.drawable.ic_close)
    private val info = makeTarget(R.drawable.ic_info).apply { text = "App info" }
    private var hovered = -1

    init {
        orientation = HORIZONTAL
        addView(remove, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(info, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    private fun makeTarget(icon: Int) = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = true
        compoundDrawablePadding = dp(6)
        setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
    }

    fun setup(fromDrawer: Boolean, isWidget: Boolean) {
        remove.text = if (fromDrawer) "Uninstall" else "Remove"
        info.visibility = if (isWidget) GONE else VISIBLE
        hovered = -1
        setHover(TARGET_NONE)
    }

    fun targetAt(x: Float, y: Float): Int {
        if (visibility != VISIBLE) return TARGET_NONE
        if (remove.screenRect().contains(x.toInt(), y.toInt())) return TARGET_REMOVE
        if (info.visibility == VISIBLE && info.screenRect().contains(x.toInt(), y.toInt())) return TARGET_INFO
        return TARGET_NONE
    }

    fun updateHover(x: Float, y: Float) = setHover(targetAt(x, y))

    private fun setHover(t: Int) {
        if (t == hovered) return
        hovered = t
        tint(remove, if (t == TARGET_REMOVE) RED else NORMAL)
        tint(info, if (t == TARGET_INFO) HOLO_BLUE else NORMAL)
    }

    private fun tint(v: TextView, c: Int) {
        v.setTextColor(c)
        v.compoundDrawableTintList = ColorStateList.valueOf(c)
    }
}

/** Holo tab: "APPS" / "WIDGETS" with blue underline. */
class TabView(ctx: Context, label: String) : TextView(ctx) {
    private val paint = Paint().apply { color = HOLO_BLUE }
    private var active = false

    init {
        text = label
        isAllCaps = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
        background = pressedBackground(ctx)
        setActive(false)
    }

    fun setActive(a: Boolean) {
        active = a
        setTextColor(if (a) Color.WHITE else 0xFF999999.toInt())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (active) canvas.drawRect(0f, (height - dp(3)).toFloat(), width.toFloat(), height.toFloat(), paint)
    }
}
