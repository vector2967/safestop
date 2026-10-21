package kr.safestop

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.app.Activity
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private val guide = StopGuide()
    private var state = CarState()
    private var address: String? = null
    private val occupied = mutableSetOf<Int>()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var vehicle: Vehicle
    private var car: Car? = null
    private var navi: NaviHelper? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var nextBtn: Button

    private val fail: (Exception) -> Unit = { e -> Log.w(TAG, "vehicle: ${e.message}") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        vehicle = Vehicle(applicationContext).also { it.initialize() }
        listenVehicle()
        listenNavi()
        tts = TextToSpeech(applicationContext, Mode.ON_DEVICE).also { t ->
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() { t.setLocale(Locale.KOREA); ttsReady = true }
                override fun onFailure(errorType: ErrorType) { Log.w(TAG, "tts: $errorType") }
            })
        }
        if (checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED) listenSpeed()
        else requestPermissions(arrayOf(Car.PERMISSION_SPEED), 1)
        tick()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) listenSpeed()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        car?.disconnect(); navi?.release(); tts?.release(); vehicle.release()
        super.onDestroy()
    }

    private fun listenSpeed() {
        val c = Car.createCar(applicationContext).also { car = it }
        val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        cpm.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(v: CarPropertyValue<*>) {
                val kmh = (v.value as Float) * 3.6f
                set { it.copy(speedKmh = kmh) }
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {}
        }, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
    }

    private fun listenVehicle() {
        vehicle.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(g: VehicleGear?) =
                set { it.copy(parkedOrNeutral = g == VehicleGear.GEAR_PARK || g == VehicleGear.GEAR_NEUTRAL) }
            override fun onFailed(e: Exception) = fail(e)
        })
        val door = vehicle.getDoor()
        door.registerDoorPosition(DoorArea(DoorValue.ROW_1_LEFT), object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) = set { it.copy(driverDoorOpen = (pos ?: 0) > 0) }
            override fun onFailed(e: Exception) = fail(e)
        })
        door.registerDoorPosition(DoorArea(DoorValue.REAR), object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) = set { it.copy(trunkOpen = (pos ?: 0) > 0) }
            override fun onFailed(e: Exception) = fail(e)
        })
        val seat = vehicle.getSeat()
        listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).forEach { sv ->
            seat.registerSeatOccupancy(SeatArea(sv), object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occ: Boolean?) = set {
                    if (occ == SDK_OCCUPIED) occupied += sv.ordinal else occupied -= sv.ordinal
                    it.copy(occupiedSeats = occupied.size)
                }
                override fun onFailed(e: Exception) = fail(e)
            })
        }
        vehicle.getSafety().registerSeatAirbagDeployed(SeatArea(SeatValue.ROW_1_LEFT), object : SeatAirbagDeployedListener {
            override fun onSeatAirbagDeployUpdated(deployed: Boolean?) = set { it.copy(airbagDeployed = deployed == true) }
            override fun onFailed(e: Exception) = fail(e)
        })
    }

    private fun listenNavi() {
        val n = NaviHelper(applicationContext).also { navi = it }
        val l = object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
                address = info.address ?: "%.5f, %.5f".format(info.latitude, info.longitude)
            }
            override fun onCurrentRoadSpeedLimit(speedLimit: Int) = set { it.copy(speedLimitKmh = speedLimit) }
        }
        n.initialize({ n.addListener(l); n.getCurrentLocationInfo() }, { t -> Log.w(TAG, "navi: $t") })
    }

    // 리스너는 바인더 스레드에서 호출된다
    private fun set(f: (CarState) -> CarState) { main.post { state = f(state); step() } }

    private fun tick() { step(); main.postDelayed(::tick, 1000) }

    private fun step() {
        guide.update(state, System.currentTimeMillis())?.let(::say)
        render()
    }

    private fun say(text: String) {
        if (ttsReady) tts?.speak(text) else Log.i(TAG, "say: $text")
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
            setOnClickListener { guide.next(System.currentTimeMillis())?.let(::say); render() }
        }
        val cancel = Button(this).apply {
            textSize = 24f; text = "괜찮아요 (안내 종료)"
            setOnClickListener { guide.reset(); render() }
        }
        listOf(status, title, body, nextBtn, cancel).forEach(root::addView)
        setContentView(root)
    }

    private fun text(size: Float, color: Int) = TextView(this).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, 16, 0, 16)
    }

    private fun render() {
        status.text = "%.0f km/h · %s · 탑승 %d명".format(state.speedKmh, if (state.parkedOrNeutral) "P/N" else "주행", state.occupiedSeats)
        val (t, color) = when (guide.step) {
            Step.IDLE -> "안전 운행 중" to Color.BLACK
            Step.HAZARD -> "① 비상등" to Color.rgb(200, 30, 30)
            Step.TRUNK -> "② 트렁크 열기" to Color.rgb(200, 30, 30)
            Step.EVACUATE -> "③ 가드레일 밖으로" to Color.rgb(200, 30, 30)
            Step.REPORT -> "④ 신고" to Color.rgb(20, 90, 200)
        }
        (title.parent as LinearLayout).setBackgroundColor(color)
        title.text = t
        body.text = when (guide.step) {
            Step.IDLE -> "고속도로에서 차가 멈추면 자동으로 안내합니다."
            Step.REPORT -> "112 · 1588-2504 (무료 긴급견인)\n현재 위치: ${address ?: "확인 중"}"
            else -> StopGuide.SPEECH.getValue(guide.step)
        }
        nextBtn.isEnabled = guide.step in setOf(Step.HAZARD, Step.TRUNK, Step.EVACUATE)
    }

    companion object {
        const val TAG = "SafeStop"
        // 에뮬레이터 v2.1.0 실측: 착좌 값이 Virtual Console과 반대로 온다. 실차 확인 필요
        const val SDK_OCCUPIED = false
    }
}
