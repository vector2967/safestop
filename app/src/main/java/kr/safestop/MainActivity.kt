package kr.safestop

import android.Manifest
import android.app.Activity
import android.car.Car
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var f: Fonts
    private lateinit var band: TextView
    private lateinit var where: TextView
    private val rows = mutableListOf<StepRow>()
    private lateinit var main: LinearLayout
    private lateinit var pic: ImageView
    private lateinit var count: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var alert: TextView
    private lateinit var strip: StripMap
    private lateinit var qrBox: LinearLayout
    private lateinit var qr: ImageView
    private lateinit var primary: Button
    private lateinit var secondary: Button
    private lateinit var end: Button
    private var qrFor: String? = null
    private var shownStep: Step? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        f = Fonts(this)
        buildUi()
        val missing = arrayOf(Car.PERMISSION_SPEED, Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        GuardService.start(this, intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        GuardService.start(this, intent)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        GuardService.start(this)
    }

    // 멀티 윈도우에서는 보이는 채로 pause되므로 start/stop 기준으로 구독한다
    override fun onStart() {
        super.onStart()
        Guard.onChange = { runOnUiThread(::render) }
        render()
    }

    override fun onStop() {
        Guard.onChange = null
        super.onStop()
    }

    private fun buildUi() {
        val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Palette.ground)
            setPadding(dp(28), dp(20), dp(28), dp(24))
        }

        band = text(26f, 700, Palette.ink).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        where = text(24f, 500, Palette.inkDim).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(band, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56)))
            addView(where, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(16) })
        }

        val plate = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            background = rounded(Palette.ground, dp(8).toFloat(), dp(1), Palette.hairline)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        listOf(
            Triple(R.drawable.ic_hazard, "비상등", Step.HAZARD), Triple(R.drawable.ic_trunk, "트렁크", Step.TRUNK),
            Triple(R.drawable.ic_evacuate, "대피", Step.EVACUATE), Triple(R.drawable.ic_report, "신고", Step.REPORT),
        ).forEachIndexed { i, (icon, label, _) ->
            val row = StepRow(this, f, i + 1, icon, label)
            rows += row
            plate.addView(row, if (wide) LinearLayout.LayoutParams(-1, dp(96)).apply { if (i > 0) topMargin = dp(12) }
            else LinearLayout.LayoutParams(0, dp(96), 1f).apply { if (i > 0) marginStart = dp(12) })
        }
        qr = ImageView(this)
        qrBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(20), 0, 0)
            addView(qr, LinearLayout.LayoutParams(dp(220), dp(220)))
            addView(text(24f, 600, Palette.ink).apply { text = "나가기 전에 휴대폰으로 찍기\n112 신고 문자가 준비됩니다"; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) })
        }
        if (wide) plate.addView(qrBox, LinearLayout.LayoutParams(-1, 0, 1f))

        pic = ImageView(this)
        count = text(88f, 700, Palette.onColor).apply { gravity = Gravity.CENTER }
        val picFrame = FrameLayout(this).apply {
            addView(pic, FrameLayout.LayoutParams(dp(140), dp(140), Gravity.CENTER))
            addView(count, FrameLayout.LayoutParams(-1, -1))
        }
        title = text(56f, 700, Palette.ink).apply { setLineSpacing(0f, 1.1f) }
        body = text(30f, 500, Palette.inkDim).apply { setLineSpacing(0f, 1.25f) }
        alert = text(30f, 700, Palette.onColor).apply { setPadding(dp(24), dp(16), dp(24), dp(16)) }
        strip = StripMap(this, f)
        main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (wide) 40 else 8), dp(8), dp(8), 0)
            addView(picFrame, LinearLayout.LayoutParams(dp(180), dp(180)))
            addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
            addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(alert, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(strip, LinearLayout.LayoutParams(-1, -2))
            if (!wide) addView(qrBox, LinearLayout.LayoutParams(-1, -2))
        }

        val content = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) {
                addView(plate, LinearLayout.LayoutParams(0, -1, 0.38f))
                addView(main, LinearLayout.LayoutParams(0, -1, 0.62f))
            } else {
                addView(plate, LinearLayout.LayoutParams(-1, -2))
                addView(main, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(24) })
            }
        }

        primary = button(Palette.glow, Palette.onColor, 700) { onPrimary() }
        secondary = button(Palette.pressed, Palette.ink, 600) { onSecondary() }.apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_replay, 0, 0, 0)
            compoundDrawablePadding = dp(12); compoundDrawableTintList = android.content.res.ColorStateList.valueOf(Palette.ink)
        }
        end = button(Color.TRANSPARENT, Palette.inkDim, 600) {
            end.text = "길게 눌러 종료"
        }.apply {
            setOnLongClickListener { Guard.cancel(); true }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(primary, LinearLayout.LayoutParams(0, dp(116), 1.4f))
            addView(secondary, LinearLayout.LayoutParams(0, dp(116), 1f).apply { marginStart = dp(24) })
            addView(end, LinearLayout.LayoutParams(0, dp(116), 0.8f).apply { marginStart = dp(24) })
        }

        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(20); bottomMargin = dp(20) })
        root.addView(actions, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun onPrimary() = when (Guard.guide.step) {
        Step.IDLE -> Guard.start()
        else -> Guard.next()
    }

    private fun onSecondary() = if (Guard.guide.step == Step.CONFIRM) Guard.cancel() else Guard.replay()

    private fun render() {
        val s = Guard.state
        val step = Guard.guide.step
        val idle = step == Step.IDLE

        val (bandText, bandColor, bandInk) = when (step) {
            Step.IDLE -> Triple("●  감시 중", Palette.raised, Palette.glow)
            Step.CONFIRM -> Triple("정차 감지", Palette.caution, Palette.onColor)
            Step.REPORT -> Triple("대피 완료", Palette.safe, Palette.onColor)
            else -> Triple("대피 안내 중", Palette.danger, Palette.onColor)
        }
        band.text = bandText; band.setTextColor(bandInk); band.background = rounded(bandColor, dp(28).toFloat())
        where.text = Guard.report?.around?.road ?: Guard.address ?: ""

        val order = listOf(Step.HAZARD, Step.TRUNK, Step.EVACUATE, Step.REPORT)
        rows.forEachIndexed { i, row ->
            val at = order.indexOf(step)
            row.show(when {
                at < 0 -> StepRow.State.WAITING
                i < at -> StepRow.State.DONE
                i == at -> if (step == Step.REPORT) StepRow.State.DONE else StepRow.State.NOW
                else -> StepRow.State.WAITING
            })
        }

        val (icon, t, b) = copy(step)
        pic.setImageResource(icon)
        val confirming = step == Step.CONFIRM
        pic.visibility = if (confirming) View.INVISIBLE else View.VISIBLE
        pic.setColorFilter(when (step) { Step.IDLE -> Palette.inkDim; Step.REPORT -> Palette.safe; else -> Palette.glow })
        count.visibility = if (confirming) View.VISIBLE else View.GONE
        if (confirming) {
            count.text = Guard.guide.confirmLeftSec(System.currentTimeMillis()).toString()
            count.background = rounded(Palette.caution, dp(90).toFloat())
        }
        title.text = t
        body.text = Guard.advice.takeIf { idle } ?: b

        val left = s.occupiedSeats
        alert.visibility = if (step == Step.EVACUATE && left > 0) View.VISIBLE else View.GONE
        alert.text = "차 안에 아직 ${left}명 있어요"
        alert.background = rounded(Palette.danger, dp(16).toFloat())

        strip.around = Guard.report?.around
        strip.visibility = if (strip.around != null && step in setOf(Step.IDLE, Step.REPORT)) View.VISIBLE else View.GONE

        val report = Guard.report.takeIf { step in setOf(Step.HAZARD, Step.TRUNK, Step.REPORT) }
        qrBox.visibility = if (report == null) View.GONE else View.VISIBLE
        if (report != null && report.sms != qrFor) { qr.setImageBitmap(report.qr(dp(220))); qrFor = report.sms }

        primary.visibility = if (idle && s.speedKmh >= 1f || step == Step.REPORT) View.GONE else View.VISIBLE
        primary.text = when (step) { Step.IDLE -> "대피 안내 시작"; Step.CONFIRM -> "지금 시작"; else -> "다음 단계" }
        secondary.visibility = if (idle) View.GONE else View.VISIBLE
        secondary.text = if (confirming) "정상 정차예요" else "다시 듣기"
        secondary.setCompoundDrawablesRelativeWithIntrinsicBounds(if (confirming) 0 else R.drawable.ic_replay, 0, 0, 0)
        end.visibility = if (idle || confirming) View.GONE else View.VISIBLE
        if (end.visibility == View.VISIBLE && shownStep != step) end.text = "안내 종료"

        if (shownStep != null && shownStep != step) {
            if (ValueAnimator.areAnimatorsEnabled()) {
                main.alpha = 0f; main.translationY = dp(8).toFloat()
                main.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f)).start()
            }
        }
        shownStep = step
    }

    private fun copy(step: Step) = when (step) {
        Step.IDLE -> Triple(R.drawable.ic_road, "고속도로 정차 감시 중", "차가 고속도로에 멈추면 대피 순서를 음성으로 안내합니다.")
        Step.CONFIRM -> Triple(R.drawable.ic_hazard, "고속도로 정차를 감지했어요", "잠시 뒤 대피 안내를 시작합니다.")
        Step.HAZARD -> Triple(R.drawable.ic_hazard, "비상등을 켜세요", "뒤차가 멀리서도 볼 수 있게 지금 바로 켜세요.")
        Step.TRUNK -> Triple(R.drawable.ic_trunk, "트렁크를 여세요", "열린 트렁크가 뒤차에 정차를 알려 줍니다.")
        Step.EVACUATE -> Triple(R.drawable.ic_evacuate, "가드레일 밖으로 나가세요", "차 뒤쪽이 아닌 가드레일 너머로, 모두 함께.")
        Step.REPORT -> Triple(R.drawable.ic_check, "모두 대피했어요", "112 또는 1588-2504(무료 긴급견인)에 신고하세요.")
    }

    private fun text(size: Float, weight: Int, color: Int) = TextView(this).style(f, size, weight, color)

    private fun button(fill: Int, ink: Int, weight: Int, onClick: () -> Unit) = Button(this).apply {
        style(f, 30f, weight, ink); isAllCaps = false; stateListAnimator = null
        background = rounded(fill, dp(16).toFloat())
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { GuardService.start(context) }
}
