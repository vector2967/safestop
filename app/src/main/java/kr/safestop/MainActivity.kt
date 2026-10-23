package kr.safestop

import android.Manifest
import android.app.Activity
import android.car.Car
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
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
    private lateinit var qrNote: TextView
    private lateinit var familyCell: LinearLayout
    private lateinit var familyQr: ImageView
    private lateinit var familyNote: TextView
    private lateinit var hint: TextView
    private lateinit var langBtn: TextView
    private lateinit var place: TextView
    private lateinit var primary: Button
    private lateinit var secondary: Button
    private lateinit var end: Button
    private var qrFor: String? = null
    private var shownStep: Step? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        f = Fonts(this)
        buildUi()
        GuardService.start(this, intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        GuardService.start(this, intent)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        GuardService.start(this)
        render()
    }

    // 시스템 권한 창은 차속을 "위치 정보"로 보여 주고 주행 중에는 열리지 않아서, 먼저 이유를 설명하고 버튼으로 요청한다
    private fun missingPermissions() = arrayOf(Car.PERMISSION_SPEED, Manifest.permission.POST_NOTIFICATIONS)
        .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

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
        // 외국인 렌터카 운전자를 위해 언제든 언어를 바꿀 수 있게 둔다(바꾸면 지금 단계를 다시 읽는다)
        langBtn = text(24f, 600, Palette.ink).apply {
            gravity = Gravity.CENTER; setPadding(dp(24), 0, dp(24), 0)
            background = rounded(Color.TRANSPARENT, dp(28).toFloat(), dp(2), Palette.hairline)
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); Guard.setLang(this@MainActivity, Guard.guide.lang.next()) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(band, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56)))
            addView(where, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(16) })
            addView(langBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56)).apply { marginStart = dp(16) })
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
        fun qrCell(img: ImageView, note: TextView) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(img, LinearLayout.LayoutParams(dp(QR_DP), dp(QR_DP)))
            addView(note.apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) }, LinearLayout.LayoutParams(dp(QR_DP + 40), -2))
        }
        qr = ImageView(this); qrNote = text(22f, 600, Palette.ink)
        familyQr = ImageView(this); familyNote = text(22f, 600, Palette.ink)
        familyCell = qrCell(familyQr, familyNote)
        qrBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(20), 0, 0)
            addView(qrCell(qr, qrNote))
            addView(familyCell, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) })
        }
        if (wide) plate.addView(qrBox, LinearLayout.LayoutParams(-1, 0, 1f))

        pic = ImageView(this)
        count = text(88f, 700, Palette.onColor).apply { gravity = Gravity.CENTER }
        val picFrame = FrameLayout(this).apply {
            addView(pic, FrameLayout.LayoutParams(dp(180), dp(180), Gravity.CENTER))
            addView(count, FrameLayout.LayoutParams(-1, -1))
        }
        title = text(64f, 700, Palette.ink).apply { setLineSpacing(0f, 1.1f) }
        body = text(30f, 500, Palette.inkDim).apply { setLineSpacing(0f, 1.25f) }
        hint = text(26f, 600, Palette.glow)
        alert = text(40f, 700, Palette.onColor).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(28), dp(24), dp(28), dp(24)) }
        place = text(32f, 600, Palette.ink)
        strip = StripMap(this, f)
        main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (wide) 40 else 8), dp(8), dp(8), 0)
            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(alert, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(24) })
            addView(picFrame, LinearLayout.LayoutParams(dp(220), dp(220)))
            addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
            addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(hint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
            addView(place, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
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
        end = button(Color.TRANSPARENT, Palette.inkDim, 600) {}.apply {
            background = rounded(Color.TRANSPARENT, dp(16).toFloat(), dp(2), Palette.hairline)
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
        Step.IDLE -> missingPermissions().takeIf { it.isNotEmpty() }?.let { requestPermissions(it.toTypedArray(), 1) } ?: Guard.start()
        // 전화 앱이 없는 환경에서는 조용히 무시
        Step.REPORT -> runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }.let {}
        else -> Guard.next()
    }

    private fun onSecondary() = if (Guard.guide.step == Step.CONFIRM) Guard.cancel() else Guard.replay()

    private fun render() {
        val c = Guard.copy
        val s = Guard.state
        val step = Guard.guide.step
        val idle = step == Step.IDLE
        val asking = idle && missingPermissions().isNotEmpty()

        val (bandText, bandColor, bandInk) = when (step) {
            Step.IDLE -> if (Guard.problems.isEmpty() && !asking) Triple(c.bandWatching, Palette.raised, Palette.glow)
                else Triple(c.bandCheck, Palette.caution, Palette.onColor)
            Step.CONFIRM -> Triple(c.bandDetected, Palette.caution, Palette.onColor)
            Step.REPORT -> Triple(c.bandDone, Palette.safe, Palette.onColor)
            else -> Triple(c.bandGuiding, Palette.danger, Palette.onColor)
        }
        langBtn.text = Guard.guide.lang.label
        band.text = bandText; band.setTextColor(bandInk); band.background = rounded(bandColor, dp(28).toFloat())
        where.text = Guard.report?.around?.road ?: Guard.address ?: ""

        val order = listOf(Step.HAZARD, Step.TRUNK, Step.EVACUATE, Step.REPORT)
        rows.forEachIndexed { i, row ->
            row.label(c.stepNames[i])
            val at = order.indexOf(step)
            row.show(when {
                at < 0 -> StepRow.State.WAITING
                i < at -> StepRow.State.DONE
                i == at -> StepRow.State.NOW
                else -> StepRow.State.WAITING
            })
        }

        val icon = ICONS.getValue(step)
        val (t, b) = c.screen.getValue(step)
        pic.setImageResource(icon)
        val confirming = step == Step.CONFIRM
        pic.visibility = if (confirming) View.INVISIBLE else View.VISIBLE
        pic.setColorFilter(when (step) { Step.IDLE -> Palette.inkDim; Step.REPORT -> Palette.safe; else -> Palette.glow })
        count.visibility = if (confirming) View.VISIBLE else View.GONE
        if (confirming) {
            count.text = Guard.guide.confirmLeftSec(System.currentTimeMillis()).toString()
            count.background = rounded(Palette.caution, dp(90).toFloat())
        }
        title.text = if (asking) c.permTitle else t
        body.text = c.permWhy.takeIf { asking } ?: Guard.problems.takeIf { idle && it.isNotEmpty() }?.joinToString("\n")
            ?: Guard.advice.takeIf { idle } ?: if (step == Step.EVACUATE && s.dark) "$b\n${c.night}" else b

        val left = s.occupiedSeats
        alert.visibility = if (step == Step.EVACUATE && left > 0) View.VISIBLE else View.GONE
        alert.text = c.peopleLeft.format(left)
        hint.text = if (idle) Guard.stats?.summary(c) else (Guard.guide.voiceLang?.copy ?: c).sayHint[step]?.takeIf { Guard.voice }
        hint.setTextColor(if (idle) Palette.inkDim else Palette.glow)
        hint.visibility = if (hint.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        val stillInside = step == Step.EVACUATE && left > 0
        alert.background = rounded(Palette.danger, dp(16).toFloat())

        strip.copy = c
        strip.around = Guard.report?.around
        strip.visibility = if (strip.around != null && step in setOf(Step.IDLE, Step.REPORT)) View.VISIBLE else View.GONE
        place.text = Guard.report?.where
        place.visibility = if (step == Step.REPORT && Guard.report != null) View.VISIBLE else View.GONE

        val report = Guard.report.takeIf { step in setOf(Step.TRUNK, Step.REPORT) }
        qrBox.visibility = if (report == null) View.GONE else View.VISIBLE
        qrNote.text = if (step == Step.TRUNK) c.noteTrunk else c.label112
        familyNote.text = c.labelFamily
        // 트렁크 단계에서는 신고 QR 하나만, 대피 후에는 가족 공유 QR도 보여 준다
        familyCell.visibility = if (step == Step.REPORT) View.VISIBLE else View.GONE
        if (report != null && report.sms != qrFor) {
            qr.setImageBitmap(Report.qr(report.sms, dp(QR_DP))); familyQr.setImageBitmap(Report.qr(report.familySms, dp(QR_DP))); qrFor = report.sms
        }

        primary.visibility = if (idle && s.speedKmh >= 1f) View.GONE else View.VISIBLE
        primary.text = when (step) { Step.IDLE -> if (asking) c.allow else c.start; Step.CONFIRM -> c.startNow; Step.REPORT -> c.call; else -> if (stillInside) c.allOut else c.next }
        // 사람이 남아 있으면 넘기기 버튼을 눈에 띄지 않게 해 성급한 진행을 막는다
        primary.background = rounded(if (stillInside) Palette.pressed else Palette.glow, dp(16).toFloat())
        primary.setTextColor(if (stillInside) Palette.ink else Palette.onColor)
        (secondary.layoutParams as LinearLayout.LayoutParams).marginStart = if (primary.visibility == View.VISIBLE) dp(24) else 0
        secondary.visibility = if (idle) View.GONE else View.VISIBLE
        secondary.text = if (confirming) c.normalStop else c.replay
        end.text = c.endHold
        secondary.setCompoundDrawablesRelativeWithIntrinsicBounds(if (confirming) 0 else R.drawable.ic_replay, 0, 0, 0)
        end.visibility = if (idle || confirming) View.GONE else View.VISIBLE

        if (shownStep != null && shownStep != step) {
            if (ValueAnimator.areAnimatorsEnabled()) {
                main.alpha = 0f; main.translationY = dp(8).toFloat()
                main.animate().alpha(1f).translationY(0f).setDuration(250).setInterpolator(PathInterpolator(0.16f, 1f, 0.3f, 1f)).start()
            }
        }
        shownStep = step
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

private const val QR_DP = 200
private val ICONS = mapOf(Step.IDLE to R.drawable.ic_road, Step.CONFIRM to R.drawable.ic_hazard, Step.HAZARD to R.drawable.ic_hazard,
    Step.TRUNK to R.drawable.ic_trunk, Step.EVACUATE to R.drawable.ic_evacuate, Step.REPORT to R.drawable.ic_check)

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { GuardService.start(context) }
}
