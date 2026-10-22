package kr.safestop

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

// Pleos 다크 토큰 위에, 지금 단계와 현재 위치에만 축광 라임을 쓴다
object Palette {
    val ground = Color.parseColor("#131417")
    val raised = Color.parseColor("#1D1E21")
    val pressed = Color.parseColor("#313236")
    val hairline = Color.parseColor("#313236")
    val ink = Color.parseColor("#F2F3F5")
    val inkDim = Color.parseColor("#B4B7BD")
    val glow = Color.parseColor("#D4F57A")
    val onColor = Color.parseColor("#131417")
    val danger = Color.parseColor("#FF4C28")
    val safe = Color.parseColor("#00E074")
    val caution = Color.parseColor("#F5A623")
}

class Fonts(ctx: Context) {
    val face: Typeface = ctx.resources.getFont(R.font.asta_sans)
}

fun TextView.style(fonts: Fonts, size: Float, weight: Int, color: Int) = apply {
    typeface = fonts.face; fontVariationSettings = "'wght' $weight"; textSize = size; setTextColor(color)
}

fun Context.dp(v: Number) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    cornerRadius = radius; setColor(color); if (stroke > 0) setStroke(stroke, strokeColor)
}

class StepRow(ctx: Context, fonts: Fonts, number: Int, icon: Int, label: String) : LinearLayout(ctx) {
    enum class State { WAITING, NOW, DONE }

    private val num = TextView(ctx).style(fonts, 26f, 700, Palette.ink).apply { text = number.toString(); gravity = Gravity.CENTER }
    private val pic = ImageView(ctx).apply { setImageResource(icon) }
    private val name = TextView(ctx).style(fonts, 30f, 600, Palette.ink).apply { text = label }
    private val done = ImageView(ctx).apply { setImageResource(R.drawable.ic_check) }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dp(20), 0, ctx.dp(20), 0)
        addView(num, LayoutParams(ctx.dp(52), ctx.dp(52)))
        addView(pic, LayoutParams(ctx.dp(52), ctx.dp(52)).apply { marginStart = ctx.dp(18) })
        addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(16) })
        addView(done, LayoutParams(ctx.dp(40), ctx.dp(40)))
    }

    fun show(state: State) {
        val r = context.dp(8).toFloat()
        val fg = when (state) {
            State.NOW -> Palette.glow
            State.DONE, State.WAITING -> Palette.inkDim
        }
        background = if (state == State.NOW) rounded(Palette.raised, r, context.dp(4), Palette.glow) else rounded(Palette.raised, r)
        num.background = rounded(Color.TRANSPARENT, context.dp(26).toFloat(), context.dp(3), fg)
        num.setTextColor(fg); name.setTextColor(fg); pic.setColorFilter(fg)
        done.setColorFilter(Palette.safe)
        done.visibility = if (state == State.DONE) VISIBLE else INVISIBLE
    }
}

class StripMap(ctx: Context, private val fonts: Fonts) : View(ctx) {
    var around: Places.Around? = null
        set(v) { field = v; invalidate() }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.inkDim; strokeWidth = ctx.dp(6).toFloat(); strokeCap = Paint.Cap.ROUND }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ink }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.glow; alpha = 110; maskFilter = BlurMaskFilter(ctx.dp(16).toFloat(), BlurMaskFilter.Blur.NORMAL) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.glow }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ink; typeface = fonts.face; fontVariationSettings = "'wght' 600"; textSize = ctx.dp(26).toFloat() }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.inkDim; typeface = fonts.face; fontVariationSettings = "'wght' 500"; textSize = ctx.dp(24).toFloat() }
    private val here = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.glow; typeface = fonts.face; fontVariationSettings = "'wght' 700"; textSize = ctx.dp(24).toFloat(); textAlign = Paint.Align.CENTER }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), context.dp(150))

    override fun onDraw(c: Canvas) {
        val a = around ?: return
        val pad = context.dp(12).toFloat()
        val y = context.dp(62).toFloat()
        val left = pad; val right = width - pad
        c.drawLine(left, y, right, y, line)
        val back = a.behind; val ahead = a.ahead
        // 현재 위치는 앞뒤 거리 비율대로, 한쪽만 알면 가운데
        val total = (back?.second ?: 0.0) + (ahead?.second ?: 0.0)
        val x = if (back != null && ahead != null && total > 0)
            left + (right - left) * (0.15f + 0.7f * (back.second / total).toFloat()) else (left + right) / 2
        back?.let { mark(c, left + context.dp(8), y, it, Paint.Align.LEFT, if (ahead != null) " 지남" else "") }
        ahead?.let { mark(c, right - context.dp(8), y, it, Paint.Align.RIGHT, " 앞") }
        c.drawCircle(x, y, context.dp(22).toFloat(), halo)
        c.drawCircle(x, y, context.dp(12).toFloat(), dot)
        c.drawText("현재 위치", x, y - context.dp(30), here)
    }

    private fun mark(c: Canvas, x: Float, y: Float, p: Pair<Place, Double>, align: Paint.Align, side: String) {
        c.drawRect(x - context.dp(5), y - context.dp(16), x + context.dp(5), y + context.dp(16), tick)
        label.textAlign = align; sub.textAlign = align
        c.drawText(Places.label(p.first), x, y + context.dp(48), label)
        c.drawText(Places.km(p.second) + side, x, y + context.dp(78), sub)
    }
}
