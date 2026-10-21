package kr.safestop

import android.Manifest
import android.app.Activity
import android.car.Car
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var nextBtn: Button
    private lateinit var qr: ImageView
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48); setBackgroundColor(Color.BLACK)
        }
        status = text(22f, Color.GRAY)
        title = text(56f, Color.WHITE)
        body = text(30f, Color.WHITE)
        nextBtn = Button(this).apply {
            textSize = 32f; text = "다음 단계"
            setOnClickListener { Guard.next() }
        }
        val cancel = Button(this).apply {
            textSize = 24f; text = "괜찮아요 (안내 종료)"
            setOnClickListener { Guard.cancel() }
        }
        qr = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(360, 360) }
        listOf(status, title, body, qr, nextBtn, cancel).forEach(root::addView)
        setContentView(root)
    }

    private fun text(size: Float, color: Int) = TextView(this).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, 16, 0, 16)
    }

    private fun render() {
        val s = Guard.state
        val step = Guard.guide.step
        status.text = "%.0f km/h · %s · 탑승 %d명%s\n%s".format(s.speedKmh, if (s.parkedOrNeutral) "P/N" else "주행",
            s.occupiedSeats, if (s.nearStopZone) " · 휴게소·쉼터 부근" else "", Guard.address ?: "")
        val (t, color) = when (step) {
            Step.IDLE -> "안전 운행 중" to Color.BLACK
            Step.CONFIRM -> "고속도로 정차 감지" to Color.rgb(200, 120, 0)
            Step.HAZARD -> "① 비상등" to Color.rgb(200, 30, 30)
            Step.TRUNK -> "② 트렁크 열기" to Color.rgb(200, 30, 30)
            Step.EVACUATE -> "③ 가드레일 밖으로" to Color.rgb(200, 30, 30)
            Step.REPORT -> "④ 신고" to Color.rgb(20, 90, 200)
        }
        (title.parent as LinearLayout).setBackgroundColor(color)
        title.text = t
        body.text = when (step) {
            Step.IDLE -> Guard.advice ?: "고속도로에서 차가 멈추면 자동으로 안내합니다."
            Step.EVACUATE -> StopGuide.SPEECH.getValue(step) + "\n나가기 전에 QR을 휴대폰으로 찍으면 112 신고 문자가 준비됩니다."
            Step.REPORT -> "112 · 1588-2504 (무료 긴급견인)\n위치: ${Guard.report?.where ?: Guard.address ?: "확인 중"}"
            else -> StopGuide.SPEECH.getValue(step)
        }
        val report = Guard.report.takeIf { step == Step.EVACUATE || step == Step.REPORT }
        qr.visibility = if (report == null) View.GONE else View.VISIBLE
        if (report != null && report.sms != qrFor) { qr.setImageBitmap(report.qr(360)); qrFor = report.sms }
        nextBtn.isEnabled = step in setOf(Step.CONFIRM, Step.HAZARD, Step.TRUNK, Step.EVACUATE)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { GuardService.start(context) }
}
