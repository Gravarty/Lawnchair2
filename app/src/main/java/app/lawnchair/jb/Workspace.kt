package app.lawnchair.jb

import android.app.WallpaperManager
import android.content.Context

class Workspace(ctx: Context, pages: Int, cols: Int, rows: Int) : PagedView(ctx) {

    val cellLayouts: List<CellLayout> = List(pages) { CellLayout(ctx, cols, rows).also { addView(it) } }
    private val wallpaperManager = WallpaperManager.getInstance(ctx)

    init {
        if (pages > 1) wallpaperManager.setWallpaperOffsetSteps(1f / (pages - 1), 1f)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        updateWallpaperOffset()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { updateWallpaperOffset() }
    }

    /** Parallax wallpaper scrolling, the classic JB effect. */
    private fun updateWallpaperOffset() {
        val token = windowToken ?: return
        val range = (pageCount - 1) * width
        if (range <= 0) return
        wallpaperManager.setWallpaperOffsets(token, (scrollX.toFloat() / range).coerceIn(0f, 1f), 0.5f)
    }
}
