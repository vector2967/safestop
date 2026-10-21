package kr.safestop

import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
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
import android.car.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.Locale

object Guard {
    val guide = StopGuide()
    var state = CarState()
    var address: String? = null
    var report: Report? = null
    var advice: String? = null
    val advisor = Advisor()
    var onChange: (() -> Unit)? = null
    var say: (String) -> Unit = {}

    fun next() { guide.next(System.currentTimeMillis())?.let(say); onChange?.invoke() }
    fun cancel() { guide.reset(); onChange?.invoke() }
}

class GuardService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val occupied = mutableSetOf<Int>()
    private lateinit var vehicle: Vehicle
    private lateinit var places: Places
    private var car: Car? = null
    private var navi: NaviHelper? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val fail: (Exception) -> Unit = { e -> Log.w(TAG, "vehicle: ${e.message}") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notification("고속도로 정차를 감시하고 있습니다"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        places = runCatching { Places.parse(assets.open("highway_places.csv").bufferedReader().readText()) }
            .getOrElse { Places(emptyList()) }
        Guard.say = ::say
        vehicle = Vehicle(applicationContext).also { it.initialize() }
        listenVehicle()
        listenNavi()
        tts = TextToSpeech(applicationContext, Mode.ON_DEVICE).also { t ->
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    t.setLocale(Locale.KOREA)
                    // 내비 안내 용도로 내면 AAOS가 재생 중인 음악을 줄인다
                    t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    ttsReady = true
                }
                override fun onFailure(errorType: ErrorType) { Log.w(TAG, "tts: $errorType") }
            })
        }
        listenSpeed()
        tick()
    }

    // 화면에서 권한을 받은 뒤 다시 불리면 차속 구독을 붙인다
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (car == null) listenSpeed()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        intent?.getStringExtra(FAKE_LOCATION)?.takeIf { debuggable }?.split(',')?.map(String::toDouble)?.let { v ->
            fakeLocation = true
            onLocation(v[0], v[1], "%.5f, %.5f (테스트 위치)".format(v[0], v[1]), v.getOrNull(2))
        }
        if (intent?.action == ACTION_ADD_WAYPOINT) navi?.addWaypoint(RequestWaypointInfo(
            intent.getDoubleExtra("lat", 0.0), intent.getDoubleExtra("lng", 0.0),
            intent.getStringExtra("name") ?: "", WaypointIndex.FIRST, "", "", "0", ""))
        return START_STICKY
    }

    private var fakeLocation = false
    private var location: Triple<Double, Double, Double?>? = null

    private fun onLocation(lat: Double, lng: Double, address: String?, heading: Double? = null) = set {
        location = Triple(lat, lng, heading)
        Guard.address = address ?: "%.5f, %.5f".format(lat, lng)
        Guard.report = Report(places.describe(lat, lng, heading), lat, lng)
        it.copy(nearStopZone = places.inStopZone(lat, lng))
    }

    private fun advise(advice: Advice) {
        val (lat, lng, heading) = location ?: return
        val types = if (advice == Advice.LOW_ENERGY) setOf("rest") else setOf("rest", "shelter")
        val (place, m) = places.nextAhead(lat, lng, heading, types) ?: return
        val name = Places.label(place)
        val text = when (advice) {
            Advice.LOW_ENERGY -> if (Guard.state.socPct != null) "배터리가 부족합니다. 갓길에 서지 말고 %.0f킬로미터 앞 %s에서 충전하세요."
                else "연료가 부족합니다. 갓길에 서지 말고 %.0f킬로미터 앞 %s에서 주유하세요."
            Advice.LONG_DRIVE -> "두 시간 넘게 운전하셨습니다. 갓길이 아닌 %.0f킬로미터 앞 %s에서 쉬어 가세요."
        }.format(m / 1000, name)
        say(text)
        Guard.advice = text
        val add = PendingIntent.getForegroundService(this, 3, Intent(this, GuardService::class.java)
            .setAction(ACTION_ADD_WAYPOINT).putExtra("lat", place.lat).putExtra("lng", place.lng).putExtra("name", name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, ALERT_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$name 경유 추천").setContentText(text).setCategory(Notification.CATEGORY_NAVIGATION)
            .addAction(Notification.Action.Builder(null, "경유지로 추가", add).build()).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(3, n)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        car?.disconnect(); navi?.release(); tts?.release(); vehicle.release()
        super.onDestroy()
    }

    private fun listenSpeed() {
        if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) return
        val c = Car.createCar(applicationContext).also { car = it }
        val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(v: CarPropertyValue<*>) = when (v.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> set { it.copy(speedKmh = (v.value as Float) * 3.6f) }
                else -> set { it.copy(parkedOrNeutral = (v.value as Int) in setOf(VehicleGear.GEAR_PARK, VehicleGear.GEAR_NEUTRAL)) }
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {}
        }
        cpm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        cpm.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
    }

    private fun listenVehicle() {
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
        vehicle.getEvBattery().registerEvBatteryStateOfCharge(object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(soc: Int?) = set { it.copy(socPct = soc) }
            override fun onFailed(e: Exception) = fail(e)
        })
        vehicle.getFuel().registerFuelPercent(object : FuelPercentListener {
            override fun onFuelPercentUpdated(pct: Int?) = set { it.copy(fuelPct = pct) }
            override fun onFailed(e: Exception) = fail(e)
        })
        vehicle.getSafety().registerSeatAirbagDeployed(SeatArea(SeatValue.ROW_1_LEFT), object : SeatAirbagDeployedListener {
            override fun onSeatAirbagDeployUpdated(deployed: Boolean?) = set { it.copy(airbagDeployed = deployed == true) }
            override fun onFailed(e: Exception) = fail(e)
        })
    }

    private fun listenNavi() {
        val n = NaviHelper(applicationContext).also { navi = it }
        val l = object : NaviHelperEventListener {
            override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
                if (!fakeLocation) onLocation(info.latitude, info.longitude, info.address, info.heading)
            }
            override fun onCurrentRoadSpeedLimit(speedLimit: Int) = set { it.copy(speedLimitKmh = speedLimit) }
        }
        n.initialize({ n.addListener(l); n.getCurrentLocationInfo() }, { t -> Log.w(TAG, "navi: $t") })
    }

    // 리스너는 바인더 스레드에서 호출된다
    private fun set(f: (CarState) -> CarState) { main.post { Guard.state = f(Guard.state); step() } }

    private var ticks = 0
    private fun tick() {
        if (ticks++ % 5 == 0) navi?.getCurrentLocationInfo()
        step()
        main.postDelayed(::tick, 1000)
    }

    private fun step() {
        val before = Guard.guide.step
        val now = System.currentTimeMillis()
        Guard.guide.update(Guard.state, now)?.let(::say)
        if (Guard.guide.step == Step.IDLE) Guard.advisor.update(Guard.state, now)?.let(::advise)
        if (before == Step.IDLE && Guard.guide.step != Step.IDLE) showGuide()
        Guard.onChange?.invoke()
    }

    private fun say(text: String) {
        if (ttsReady) tts?.speak(text) else Log.i(TAG, "say: $text")
    }

    // 백그라운드에서 화면을 바로 띄울 수 없는 경우를 대비해 전체 화면 알림으로 연다
    private fun showGuide() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, ALERT_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("고속도로 정차 감지").setContentText("대피 안내를 시작합니다")
            // car_warning은 시스템 앱 전용(SEND_CATEGORY_CAR_NOTIFICATIONS)이라 ALARM을 쓴다
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open).setFullScreenIntent(open, true).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
        runCatching { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w(TAG, "activity: ${it.message}") }
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, "감시 상태", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "정차 경보", NotificationManager.IMPORTANCE_HIGH))
        return Notification.Builder(this, STATUS_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("세이프스톱").setContentText(text).setOngoing(true).build()
    }

    companion object {
        const val TAG = "SafeStop"
        const val STATUS_CHANNEL = "status"
        const val ALERT_CHANNEL = "alert"
        // 에뮬레이터 v2.1.0 실측: 착좌 값이 Virtual Console과 반대로 온다. 실차 확인 필요
        const val SDK_OCCUPIED = false

        fun start(context: Context, from: Intent? = null) = context.startForegroundService(
            Intent(context, GuardService::class.java).apply { from?.getStringExtra(FAKE_LOCATION)?.let { putExtra(FAKE_LOCATION, it) } })

        // 에뮬레이터 내비가 GPS 주입을 따르지 않아 디버그 빌드에서만 위치 주입 허용
        const val FAKE_LOCATION = "fake_location"
        const val ACTION_ADD_WAYPOINT = "kr.safestop.ADD_WAYPOINT"
    }
}
