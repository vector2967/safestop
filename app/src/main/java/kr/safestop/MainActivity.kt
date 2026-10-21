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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var progress: LinearLayout
    private lateinit var badge: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var qrBox: LinearLayout
    private lateinit var qr: ImageView
    private lateinit var nextBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var status: TextView
    private var qrFor: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(24), dp(32), dp(24)) }
        progress = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        STEPS.forEach { (_, label) -> progress.addView(text(26f, Color.WHITE).apply { text = label }, weight(1f).apply { setMargins(dp(4), 0, dp(4), 0) }) }

        badge = text(120f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD }
        title = text(72f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD }
        body = text(40f, Color.WHITE)
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            listOf(badge, title, body).forEach(::addView)
        }
        qr = ImageView(this)
        qrBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(qr, LinearLayout.LayoutParams(dp(340), dp(340)))
            addView(text(26f, Color.WHITE).apply { text = "휴대폰 카메라로 찍으면\n112 신고 문자가 준비됩니다" })
        }
        val content = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(main, if (wide) weight(1f) else LinearLayout.LayoutParams(-1, -2))
            addView(qrBox, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(16), dp(16), dp(16), dp(16)) })
        }

        nextBtn = button(Color.WHITE, Color.BLACK) { Guard.next() }
        cancelBtn = button(Color.TRANSPARENT, Color.WHITE) { Guard.cancel() }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(nextBtn, weight(1f, dp(140)).apply { setMargins(0, 0, dp(12), 0) })
            addView(cancelBtn, weight(1f, dp(140)))
        }
        status = text(24f, Color.argb(170, 255, 255, 255))

        root.addView(progress, LinearLayout.LayoutParams(-1, dp(64)))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2))
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        setContentView(root)
    }

    private fun render() {
        val s = Guard.state
        val step = Guard.guide.step
        val now = System.currentTimeMillis()
        val bg = when (step) {
            Step.IDLE -> Color.rgb(18, 22, 28)
            Step.CONFIRM -> Color.rgb(196, 110, 0)
            Step.REPORT -> Color.rgb(22, 82, 190)
            else -> Color.rgb(190, 28, 36)
        }
        root.setBackgroundColor(bg)

        progress.visibility = if (step == Step.IDLE || step == Step.CONFIRM) View.INVISIBLE else View.VISIBLE
        STEPS.forEachIndexed { i, (st, _) ->
            val cell = progress.getChildAt(i) as TextView
            val reached = st.ordinal <= step.ordinal
            cell.background = round(if (st == step) Color.WHITE else if (reached) Color.argb(120, 255, 255, 255) else Color.argb(40, 255, 255, 255))
            cell.setTextColor(if (st == step) bg else Color.WHITE)
        }

        badge.text = when (step) {
            Step.IDLE -> "●"
            Step.CONFIRM -> Guard.guide.confirmLeftSec(now).toString()
            else -> (STEPS.indexOfFirst { it.first == step } + 1).toString()
        }
        badge.setTextColor(if (step == Step.IDLE) Color.rgb(60, 200, 120) else Color.WHITE)
        title.text = when (step) {
            Step.IDLE -> "고속도로 정차 감시 중"
            Step.CONFIRM -> "고속도로 정차 감지"
            else -> TITLES.getValue(step)
        }
        body.text = when (step) {
            Step.IDLE -> Guard.advice ?: "차가 고속도로에 멈추면 대피 순서를 음성으로 안내합니다."
            Step.CONFIRM -> "잠시 뒤 대피 안내를 시작합니다.\n정상 정차라면 '괜찮아요'를 누르세요."
            Step.REPORT -> "112 · 1588-2504 (무료 긴급견인)\n${Guard.report?.where ?: Guard.address ?: "위치 확인 중"}"
            else -> StopGuide.SPEECH.getValue(step)
        }

        val report = Guard.report.takeIf { step == Step.EVACUATE || step == Step.REPORT }
        qrBox.visibility = if (report == null) View.GONE else View.VISIBLE
        if (report != null && report.sms != qrFor) { qr.setImageBitmap(report.qr(dp(340))); qrFor = report.sms }

        nextBtn.visibility = if (step in setOf(Step.IDLE, Step.REPORT)) View.GONE else View.VISIBLE
        nextBtn.text = if (step == Step.CONFIRM) "지금 안내 시작" else "다음 단계"
        nextBtn.setTextColor(bg)
        cancelBtn.visibility = if (step == Step.IDLE) View.GONE else View.VISIBLE
        cancelBtn.text = if (step == Step.CONFIRM) "괜찮아요 (정상 정차)" else "안내 종료"

        status.text = listOfNotNull(
            "%.0f km/h".format(s.speedKmh), if (s.parkedOrNeutral) "P/N" else "주행", "탑승 ${s.occupiedSeats}명",
            s.socPct?.let { "배터리 $it%" }, "휴게소·쉼터 부근".takeIf { s.nearStopZone }, Guard.report?.where ?: Guard.address,
        ).joinToString("  ·  ")
    }

    private fun text(size: Float, color: Int) = TextView(this).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(fill: Int, fg: Int, onClick: () -> Unit) = Button(this).apply {
        textSize = 40f; setTextColor(fg); isAllCaps = false; stateListAnimator = null
        background = round(fill).apply { setStroke(dp(2), Color.WHITE) }
        setOnClickListener { onClick() }
    }

    private fun round(color: Int) = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(color) }
    private fun weight(w: Float, h: Int = -1) = LinearLayout.LayoutParams(0, h, w)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        val STEPS = listOf(Step.HAZARD to "비상등", Step.TRUNK to "트렁크", Step.EVACUATE to "대피", Step.REPORT to "신고")
        val TITLES = mapOf(Step.HAZARD to "비상등 켜기", Step.TRUNK to "트렁크 열기", Step.EVACUATE to "가드레일 밖으로", Step.REPORT to "신고")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { GuardService.start(context) }
}
