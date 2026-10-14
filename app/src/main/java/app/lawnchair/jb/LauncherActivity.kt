package app.lawnchair.jb

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import app.lawnchair.jb.ItemInfo.Companion.CONTAINER_DESKTOP
import app.lawnchair.jb.ItemInfo.Companion.CONTAINER_HOTSEAT
import app.lawnchair.jb.ItemInfo.Companion.TYPE_APP
import app.lawnchair.jb.ItemInfo.Companion.TYPE_WIDGET
import java.text.Collator
import kotlin.math.ceil
import kotlin.math.min

class LauncherActivity : Activity(), DragLayer.Listener {

    companion object {
        const val PAGES = 5
        const val DEFAULT_PAGE = 2
        const val COLS = 4
        const val ROWS = 5
        const val HOTSEAT_COUNT = 5
        const val ALL_APPS_CELL = 2
        private const val REQ_BIND = 11
        private const val REQ_CONFIGURE = 12
        private const val WIDGET_HOST_ID = 1024
    }

    private lateinit var launcherApps: LauncherApps
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var widgetHost: LauncherWidgetHost
    private lateinit var store: ItemStore

    private lateinit var dragLayer: DragLayer
    private lateinit var content: LinearLayout
    private lateinit var searchBar: SearchBar
    private lateinit var dropBar: DropTargetBar
    private lateinit var workspace: Workspace
    private lateinit var indicator: PageIndicator
    private lateinit var hotseat: CellLayout
    private lateinit var allApps: AllAppsView

    private var apps: List<AppInfo> = emptyList()
    private val appMap = HashMap<ComponentName, AppInfo>()
    private lateinit var items: MutableList<ItemInfo>
    private var allAppsOpen = false
    private var pendingWidgetId = -1

    val iconSize by lazy { dp(52) }

    // Drag state
    private data class DropCell(val layout: CellLayout, val container: Int, val page: Int, val x: Int, val y: Int, val vacant: Boolean)
    private var dragItem: ItemInfo? = null
    private var dragApp: AppInfo? = null
    private var dragSource: View? = null
    private var dragSpanX = 1
    private var dragSpanY = 1
    private var dragTarget: DropCell? = null
    private var edgeScroll: Runnable? = null

    private val appsCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            if (user == Process.myUserHandle()) {
                val gone = items.filter { it.type == TYPE_APP && it.component?.packageName == packageName }
                if (gone.isNotEmpty()) {
                    items.removeAll(gone)
                    save()
                }
            }
            reload()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt("pendingWidgetId", -1) ?: -1
        setupWindow()

        launcherApps = getSystemService(LauncherApps::class.java)!!
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = LauncherWidgetHost(applicationContext, WIDGET_HOST_ID)
        store = ItemStore(this)

        buildUi()
        setContentView(dragLayer)

        loadApps()
        items = store.load() ?: createDefaultItems().also { store.save(it) }
        bindWorkspace()
        workspace.setCurrentPage(DEFAULT_PAGE)

        launcherApps.registerCallback(appsCallback, Handler(Looper.getMainLooper()))
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    override fun onDestroy() {
        launcherApps.unregisterCallback(appsCallback)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pendingWidgetId", pendingWidgetId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val onHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
            when {
                allAppsOpen -> closeAllApps(onHome)
                onHome -> workspace.snapToPage(DEFAULT_PAGE)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (allAppsOpen) closeAllApps(true)
    }

    // ---- UI ----

    @Suppress("DEPRECATION")
    private fun setupWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
    }

    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        dragLayer = DragLayer(this).also { it.listener = this }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        searchBar = SearchBar(this)
        dropBar = DropTargetBar(this).apply { visibility = View.GONE }
        val topBar = FrameLayout(this).apply {
            addView(searchBar, FrameLayout.LayoutParams(match, match))
            addView(dropBar, FrameLayout.LayoutParams(match, match))
        }
        content.addView(topBar, LinearLayout.LayoutParams(match, dp(56)))

        workspace = Workspace(this, PAGES, COLS, ROWS)
        workspace.cellLayouts.forEach { cl ->
            cl.setPadding(dp(8), dp(4), dp(8), 0)
            cl.setOnLongClickListener { showHomeOptions(); true }
        }
        content.addView(workspace, LinearLayout.LayoutParams(match, 0, 1f))

        indicator = PageIndicator(this).apply { pageCount = PAGES }
        workspace.onScroll = { indicator.setScroll(it) }
        content.addView(indicator, LinearLayout.LayoutParams(match, dp(12)))

        hotseat = CellLayout(this, HOTSEAT_COUNT, 1).apply { setPadding(dp(8), 0, dp(8), 0) }
        content.addView(hotseat, LinearLayout.LayoutParams(match, dp(80)))

        allApps = AllAppsView(this).apply { visibility = View.INVISIBLE }

        dragLayer.addView(content, FrameLayout.LayoutParams(match, match))
        dragLayer.addView(allApps, FrameLayout.LayoutParams(match, match))
        dragLayer.setOnApplyWindowInsetsListener { _, insets ->
            val i = systemInsets(insets)
            content.setPadding(i[0], i[1], i[2], i[3])
            allApps.setPadding(i[0], i[1], i[2], i[3])
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun systemInsets(i: WindowInsets): IntArray =
        if (Build.VERSION.SDK_INT >= 30) {
            val s = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            intArrayOf(s.left, s.top, s.right, s.bottom)
        } else {
            intArrayOf(i.systemWindowInsetLeft, i.systemWindowInsetTop, i.systemWindowInsetRight, i.systemWindowInsetBottom)
        }

    private fun showHomeOptions() {
        AlertDialog.Builder(this)
            .setTitle("Add to Home screen")
            .setItems(arrayOf("Widgets", "Wallpapers")) { _, which ->
                if (which == 0) openAllApps(widgets = true)
                else startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper from"))
            }
            .show()
    }

    fun openAllApps(widgets: Boolean = false) {
        allApps.showTab(widgets)
        if (allAppsOpen) return
        allAppsOpen = true
        allApps.animate().cancel()
        allApps.visibility = View.VISIBLE
        allApps.alpha = 0f
        allApps.scaleX = 0.92f
        allApps.scaleY = 0.92f
        allApps.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.5f)).withEndAction(null).start()
        content.animate().alpha(0f).setDuration(220).start()
    }

    fun closeAllApps(animated: Boolean) {
        if (!allAppsOpen) return
        allAppsOpen = false
        allApps.animate().cancel()
        if (animated) {
            allApps.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(180)
                .withEndAction { if (!allAppsOpen) allApps.visibility = View.INVISIBLE }.start()
            content.animate().alpha(1f).setDuration(180).start()
        } else {
            allApps.visibility = View.INVISIBLE
            content.animate().cancel()
            content.alpha = 1f
        }
    }

    // ---- Model ----

    private fun loadApps() {
        val user = Process.myUserHandle()
        val density = resources.displayMetrics.densityDpi
        val collator = Collator.getInstance()
        apps = launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != packageName }
            .map { AppInfo(it.componentName, user, it.label.toString(), it.getBadgedIcon(density)) }
            .sortedWith(compareBy(collator) { it.label })
        appMap.clear()
        apps.forEach { appMap[it.component] = it }
        allApps.setApps(apps)
    }

    private fun reload() {
        if (dragLayer.isDragging) return
        loadApps()
        rebindApps()
        allApps.reloadWidgets()
    }

    private fun save() = store.save(items)

    private fun createDefaultItems(): MutableList<ItemInfo> {
        val list = mutableListOf<ItemInfo>()
        val defaults = listOf(
            0 to Intent(Intent.ACTION_DIAL),
            1 to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            3 to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            4 to Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val used = HashSet<String>()
        for ((slot, intent) in defaults) {
            val pkg = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName ?: continue
            if (pkg == "android" || !used.add(pkg)) continue
            val app = apps.firstOrNull { it.component.packageName == pkg } ?: continue
            list += ItemInfo(TYPE_APP, CONTAINER_HOTSEAT, 0, slot, 0, component = app.component)
        }
        listOf("com.android.vending", "com.android.settings").forEachIndexed { i, pkg ->
            val app = apps.firstOrNull { it.component.packageName == pkg } ?: return@forEachIndexed
            list += ItemInfo(TYPE_APP, CONTAINER_DESKTOP, DEFAULT_PAGE, COLS - 2 + i, ROWS - 1, component = app.component)
        }
        return list
    }

    fun loadWidgetProviders(): List<AppWidgetProviderInfo> {
        val collator = Collator.getInstance()
        return widgetManager.getInstalledProvidersForProfile(Process.myUserHandle())
            .map { it to it.loadLabel(packageManager) }
            .sortedWith(compareBy(collator) { it.second })
            .map { it.first }
    }

    // ---- Binding ----

    private fun bindWorkspace() {
        workspace.cellLayouts.forEach { it.removeAllViews() }
        hotseat.removeAllViews()
        hotseat.addView(createAllAppsButton(), CellLayout.LayoutParams(ALL_APPS_CELL, 0))
        val invalid = mutableListOf<ItemInfo>()
        for (item in items) {
            val v = createItemView(item)
            if (v == null) invalid += item else addToLayout(item, v)
        }
        if (invalid.isNotEmpty()) {
            invalid.filter { it.type == TYPE_WIDGET }.forEach { widgetHost.deleteAppWidgetId(it.widgetId) }
            items.removeAll(invalid)
            save()
        }
    }

    private fun rebindApps() {
        for (cl in workspace.cellLayouts + hotseat) {
            (0 until cl.childCount).map { cl.getChildAt(it) }
                .filter { (it.tag as? ItemInfo)?.type == TYPE_APP }
                .forEach { cl.removeView(it) }
        }
        items.filter { it.type == TYPE_APP }.forEach { item -> createItemView(item)?.let { addToLayout(item, it) } }
    }

    private fun layoutFor(item: ItemInfo): CellLayout? =
        if (item.container == CONTAINER_HOTSEAT) hotseat else workspace.cellLayouts.getOrNull(item.page)

    private fun addToLayout(item: ItemInfo, v: View) {
        layoutFor(item)?.addView(v, CellLayout.LayoutParams(item.cellX, item.cellY, item.spanX, item.spanY))
    }

    private fun createAllAppsButton() = BubbleTextView(this).apply {
        text = null
        gravity = android.view.Gravity.CENTER
        compoundDrawablePadding = 0
        setPadding(0, 0, 0, 0)
        setIcon(AllAppsButtonDrawable(this@LauncherActivity), iconSize)
        contentDescription = "Apps"
        setOnClickListener { openAllApps() }
    }

    private fun createItemView(item: ItemInfo): View? {
        if (item.type == TYPE_WIDGET) return createWidgetView(item)
        val app = item.component?.let { appMap[it] } ?: return null
        return BubbleTextView(this).apply {
            bind(app, iconSize, showLabel = item.container == CONTAINER_DESKTOP)
            tag = item
            setOnClickListener { startApp(app, it) }
            setOnLongClickListener { startWorkspaceDrag(it); true }
        }
    }

    @Suppress("DEPRECATION")
    private fun createWidgetView(item: ItemInfo): View? {
        val info = widgetManager.getAppWidgetInfo(item.widgetId) ?: return null
        val hv = widgetHost.createView(this, item.widgetId, info)
        hv.tag = item
        hv.setOnLongClickListener { startWorkspaceDrag(it); true }
        hv.addOnLayoutChangeListener { v, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) {
                val d = resources.displayMetrics.density
                val w = ((r - l) / d).toInt()
                val h = ((b - t) / d).toInt()
                (v as AppWidgetHostView).updateAppWidgetSize(Bundle(), w, h, w, h)
            }
        }
        return hv
    }

    fun startApp(app: AppInfo, v: View) {
        try {
            val r = Rect()
            v.getGlobalVisibleRect(r)
            val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle()
            launcherApps.startMainActivity(app.component, app.user, r, opts)
        } catch (e: Exception) {
            Toast.makeText(this, "App isn't installed.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Widgets ----

    fun widgetSpan(info: AppWidgetProviderInfo): IntArray {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return intArrayOf(min(info.targetCellWidth, COLS), min(info.targetCellHeight, ROWS))
        }
        val cl = workspace.cellLayouts[0]
        val cw = if (cl.cellWidth > 0) cl.cellWidth else dp(80)
        val ch = if (cl.cellHeight > 0) cl.cellHeight else dp(100)
        return intArrayOf(
            ceil(info.minWidth.toFloat() / cw).toInt().coerceIn(1, COLS),
            ceil(info.minHeight.toFloat() / ch).toInt().coerceIn(1, ROWS),
        )
    }

    fun addWidget(info: AppWidgetProviderInfo) {
        closeAllApps(true)
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureOrPlace(id)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_BIND)
        }
    }

    private fun configureOrPlace(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // fall through and place it unconfigured
            }
        }
        placeWidget(id)
    }

    private fun placeWidget(id: Int) {
        pendingWidgetId = -1
        val info = widgetManager.getAppWidgetInfo(id)
        if (info == null) {
            widgetHost.deleteAppWidgetId(id)
            return
        }
        val (sx, sy) = widgetSpan(info)
        val current = workspace.currentPage
        for (p in listOf(current) + (0 until PAGES).filter { it != current }) {
            val cell = workspace.cellLayouts[p].findVacantCell(sx, sy) ?: continue
            val item = ItemInfo(TYPE_WIDGET, CONTAINER_DESKTOP, p, cell[0], cell[1], sx, sy, widgetId = id)
            val v = createWidgetView(item) ?: break
            items += item
            addToLayout(item, v)
            save()
            workspace.snapToPage(p)
            return
        }
        widgetHost.deleteAppWidgetId(id)
        Toast.makeText(this, "No more room on this Home screen.", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        if (id == -1) return
        when (requestCode) {
            REQ_BIND -> if (resultCode == RESULT_OK) configureOrPlace(id) else cancelWidget(id)
            REQ_CONFIGURE -> if (resultCode == RESULT_OK) placeWidget(id) else cancelWidget(id)
        }
    }

    private fun cancelWidget(id: Int) {
        pendingWidgetId = -1
        widgetHost.deleteAppWidgetId(id)
    }

    // ---- Drag & drop ----

    private fun startWorkspaceDrag(v: View) {
        if (dragLayer.isDragging || workspace.isScrolling) return
        val item = v.tag as? ItemInfo ?: return
        v.isPressed = false
        val bmp = viewToBitmap(v)
        val r = v.screenRect()
        beginDrag(bmp, r.left, r.top, item, item.component?.let { appMap[it] }, v)
        v.visibility = View.INVISIBLE
    }

    fun startDrawerDrag(app: AppInfo) {
        if (dragLayer.isDragging) return
        val bmp = drawableToBitmap(app.icon, iconSize)
        closeAllApps(true)
        val x = (dragLayer.lastRawX - bmp.width / 2f).toInt()
        val y = (dragLayer.lastRawY - bmp.height / 2f).toInt()
        beginDrag(bmp, x, y, null, app, null)
    }

    private fun beginDrag(bmp: android.graphics.Bitmap, x: Int, y: Int, item: ItemInfo?, app: AppInfo?, source: View?) {
        dragItem = item
        dragApp = app
        dragSource = source
        dragSpanX = item?.spanX ?: 1
        dragSpanY = item?.spanY ?: 1
        dropBar.setup(fromDrawer = item == null, isWidget = item?.type == TYPE_WIDGET)
        showDropBar(true)
        dragLayer.startDrag(bmp, x, y)
    }

    private fun showDropBar(show: Boolean) {
        val (inV, outV) = if (show) dropBar to searchBar else searchBar to dropBar
        outV.animate().alpha(0f).setDuration(150).withEndAction { outV.visibility = View.GONE }.start()
        inV.animate().cancel()
        inV.visibility = View.VISIBLE
        inV.alpha = 0f
        inV.animate().alpha(1f).setDuration(150).withEndAction(null).start()
    }

    private fun findDropCell(): DropCell? {
        val x = dragLayer.dragCenterX
        val y = dragLayer.dragCenterY
        val hr = hotseat.screenRect()
        if (hr.contains(x.toInt(), y.toInt())) {
            if (dragSpanX != 1 || dragSpanY != 1) return null
            val c = hotseat.cellAt(x - hr.left, y - hr.top, 1, 1)
            return DropCell(hotseat, CONTAINER_HOTSEAT, 0, c[0], c[1], hotseat.isRegionVacant(c[0], c[1], 1, 1, dragSource))
        }
        val page = workspace.currentPage
        val cl = workspace.cellLayouts[page]
        val wr = workspace.screenRect()
        if (y < wr.top || y > wr.bottom) return null
        val r = cl.screenRect()
        val c = cl.cellAt(x - r.left, y - r.top, dragSpanX, dragSpanY)
        return DropCell(cl, CONTAINER_DESKTOP, page, c[0], c[1], cl.isRegionVacant(c[0], c[1], dragSpanX, dragSpanY, dragSource))
    }

    override fun onDragMove(x: Float, y: Float) {
        dropBar.updateHover(x, y)
        val target = if (dropBar.targetAt(x, y) != DropTargetBar.TARGET_NONE) null else findDropCell()
        if (target?.layout !== dragTarget?.layout) dragTarget?.layout?.clearDragOutline()
        dragTarget = target
        target?.let { it.layout.showDragOutline(it.x, it.y, dragSpanX, dragSpanY, it.vacant) }
        handleEdgeScroll(x, target)
    }

    override fun onDrop(x: Float, y: Float) {
        cancelEdgeScroll()
        dragTarget?.layout?.clearDragOutline()
        val target = findDropCell()
        when (dropBar.targetAt(x, y)) {
            DropTargetBar.TARGET_REMOVE -> {
                val item = dragItem
                if (item != null) removeItem(item, dragSource) else dragApp?.let { uninstall(it) }
            }
            DropTargetBar.TARGET_INFO -> {
                dragApp?.let { launcherApps.startAppDetailsActivity(it.component, it.user, null, null) }
                revertDrag()
            }
            else -> if (target != null && target.vacant) commitDrop(target) else revertDrag()
        }
        finishDrag()
    }

    override fun onDragCancel() {
        cancelEdgeScroll()
        dragTarget?.layout?.clearDragOutline()
        revertDrag()
        finishDrag()
    }

    private fun commitDrop(t: DropCell) {
        val app = dragApp
        val item = dragItem ?: if (app != null) {
            ItemInfo(TYPE_APP, t.container, t.page, t.x, t.y, component = app.component).also { items += it }
        } else return
        item.container = t.container
        item.page = t.page
        item.cellX = t.x
        item.cellY = t.y
        val src = dragSource
        if (item.type == TYPE_WIDGET && src != null) {
            (src.parent as? ViewGroup)?.removeView(src)
            src.visibility = View.VISIBLE
            addToLayout(item, src)
        } else {
            src?.let { (it.parent as? ViewGroup)?.removeView(it) }
            createItemView(item)?.let { addToLayout(item, it) }
        }
        save()
    }

    private fun revertDrag() {
        dragSource?.visibility = View.VISIBLE
    }

    private fun finishDrag() {
        dragItem = null
        dragApp = null
        dragSource = null
        dragTarget = null
        showDropBar(false)
    }

    private fun removeItem(item: ItemInfo, view: View?) {
        items.remove(item)
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        if (item.type == TYPE_WIDGET) widgetHost.deleteAppWidgetId(item.widgetId)
        save()
    }

    private fun uninstall(app: AppInfo) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null)))
        } catch (e: Exception) {
            Toast.makeText(this, "Can't uninstall this app.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEdgeScroll(x: Float, target: DropCell?) {
        val edge = dp(28)
        val dir = when {
            target?.container == CONTAINER_HOTSEAT -> 0
            x < edge -> -1
            x > dragLayer.width - edge -> 1
            else -> 0
        }
        if (dir == 0) {
            cancelEdgeScroll()
            return
        }
        if (edgeScroll != null) return
        val r = Runnable {
            edgeScroll = null
            dragTarget?.layout?.clearDragOutline()
            workspace.snapToPage(workspace.currentPage + dir)
        }
        edgeScroll = r
        dragLayer.postDelayed(r, 500)
    }

    private fun cancelEdgeScroll() {
        edgeScroll?.let { dragLayer.removeCallbacks(it) }
        edgeScroll = null
    }
}
