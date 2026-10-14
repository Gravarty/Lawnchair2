package app.lawnchair.jb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import kotlin.math.max

const val HOLO_BLUE = 0xFF33B5E5.toInt()

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()

fun View.dp(v: Number): Int = context.dp(v)

fun View.screenRect(): Rect {
    val loc = IntArray(2)
    getLocationOnScreen(loc)
    return Rect(loc[0], loc[1], loc[0] + width, loc[1] + height)
}

fun viewToBitmap(v: View): Bitmap {
    val b = Bitmap.createBitmap(max(1, v.width), max(1, v.height), Bitmap.Config.ARGB_8888)
    v.draw(Canvas(b))
    return b
}

fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val copy = d.constantState?.newDrawable()?.mutate() ?: d
    copy.setBounds(0, 0, size, size)
    copy.draw(Canvas(b))
    return b
}

/** Holo-blue pressed highlight. */
fun pressedBackground(ctx: Context): Drawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
        cornerRadius = ctx.dp(4).toFloat()
        setColor(0x5533B5E5)
    })
    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
}
