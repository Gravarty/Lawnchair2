package app.lawnchair.jb

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** JB apps drawer: "APPS | WIDGETS" tabs over horizontally paged grids. */
class AllAppsView(private val launcher: LauncherActivity) : LinearLayout(launcher) {

    private val tabApps = TabView(launcher, "Apps")
    private val tabWidgets = TabView(launcher, "Widgets")
    private val pager = PagedView(launcher)
    private val indicator = PageIndicator(launcher)
    private var apps: List<AppInfo> = emptyList()
    private var widgets: List<AppWidgetProviderInfo>? = null
    private var showingWidgets = false
    private var builtKey: String? = null
    private val executor = Executors.newSingleThreadExecutor()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xE6000000.toInt())
        isClickable = true

        val tabs = LinearLayout(launcher).apply {
            orientation = HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(tabApps, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(tabWidgets, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(View(launcher).apply { setBackgroundColor(0x6633B5E5) }, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)))
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(indicator, LayoutParams(LayoutParams.MATCH_PARENT, dp(12)))

        tabApps.setOnClickListener { showTab(false) }
        tabWidgets.setOnClickListener { showTab(true) }
        pager.onScroll = { indicator.setScroll(it) }
        pager.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { rebuild() } }
        showTab(false)
    }

    fun setApps(list: List<AppInfo>) {
        apps = list
        if (!showingWidgets) invalidatePages()
    }

    fun reloadWidgets() {
        widgets = null
        if (showingWidgets) showTab(true)
    }

    fun showTab(widgets: Boolean) {
        showingWidgets = widgets
        tabApps.setActive(!widgets)
        tabWidgets.setActive(widgets)
        if (widgets && this.widgets == null) this.widgets = launcher.loadWidgetProviders()
        invalidatePages()
        pager.setCurrentPage(0)
    }

    private fun invalidatePages() {
        builtKey = null
        rebuild()
    }

    private fun rebuild() {
        val w = pager.width
        val h = pager.height
        if (w == 0 || h == 0) return
        val key = "$showingWidgets:$w:$h"
        if (key == builtKey) return
        builtKey = key
        val page = pager.currentPage
        pager.removeAllViews()
        if (showingWidgets) buildWidgetPages(w, h) else buildAppPages(w, h)
        indicator.pageCount = pager.pageCount
        pager.setCurrentPage(min(page, pager.pageCount - 1))
    }

    private fun buildAppPages(w: Int, h: Int) {
        val cols = max(4, w / dp(80))
        val rows = max(4, h / dp(100))
        val chunks = apps.chunked(cols * rows).ifEmpty { listOf(emptyList()) }
        for (chunk in chunks) {
            val cl = CellLayout(launcher, cols, rows).apply { setPadding(dp(8), dp(8), dp(8), 0) }
            chunk.forEachIndexed { i, app ->
                val v = BubbleTextView(launcher).apply {
                    bind(app, launcher.iconSize, true)
                    setOnClickListener { launcher.startApp(app, it) }
                    setOnLongClickListener { launcher.startDrawerDrag(app); true }
                }
                cl.addView(v, CellLayout.LayoutParams(i % cols, i / cols))
            }
            pager.addView(cl)
        }
    }

    private fun buildWidgetPages(w: Int, h: Int) {
        val cols = max(2, w / dp(170))
        val rows = max(2, h / dp(180))
        val chunks = (widgets ?: emptyList()).chunked(cols * rows).ifEmpty { listOf(emptyList()) }
        for (chunk in chunks) {
            val cl = CellLayout(launcher, cols, rows).apply { setPadding(dp(8), dp(8), dp(8), 0) }
            chunk.forEachIndexed { i, info ->
                cl.addView(widgetTile(info), CellLayout.LayoutParams(i % cols, i / cols))
            }
            pager.addView(cl)
        }
    }

    private fun widgetTile(info: AppWidgetProviderInfo): View {
        val preview = ImageView(launcher).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val label = TextView(launcher).apply {
            text = info.loadLabel(launcher.packageManager)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val span = launcher.widgetSpan(info)
        val dims = TextView(launcher).apply {
            text = "${span[0]} × ${span[1]}"
            setTextColor(0xFFAAAAAA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val density = resources.displayMetrics.densityDpi
        executor.execute {
            val d: Drawable? = try {
                info.loadPreviewImage(launcher, density) ?: info.loadIcon(launcher, density)
            } catch (e: Exception) {
                null
            }
            preview.post { preview.setImageDrawable(d) }
        }
        return LinearLayout(launcher).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = pressedBackground(launcher)
            addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(dims, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            setOnClickListener { launcher.addWidget(info) }
            setOnLongClickListener { launcher.addWidget(info); true }
        }
    }
}
