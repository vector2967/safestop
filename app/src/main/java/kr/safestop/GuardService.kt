package kr.safestop

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import android.car.VehicleGear
import android.car.VehicleIgnitionState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
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
    var adviceKind: Advice? = null
    val problems = sortedSetOf<String>()
    var voice = false
    var stats: Stats? = null
    var onLang: (Lang) -> Unit = {}
    val copy get() = guide.lang.copy
    val advisor = Advisor()
    var onChange: (() -> Unit)? = null
    var say: (String) -> Unit = {}

    fun next() { guide.next(System.currentTimeMillis())?.let(say); onChange?.invoke() }
    fun cancel() { guide.dismiss(); onChange?.invoke() }
    fun start() { guide.startManually(System.currentTimeMillis())?.let(say); onChange?.invoke() }
    fun replay() { guide.speech(guide.step)?.let(say) }

    fun setLang(ctx: Context, l: Lang) {
        guide.lang = l
        guide.voiceLang = null
        ctx.getSharedPreferences(GuardService.PREFS, Context.MODE_PRIVATE).edit().putString("lang", l.name).apply()
        onLang(l)
        replay()
        onChange?.invoke()
    }
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
    private var stt: SpeechToText? = null
    private var sttReady = false
    private var lowBeam = false
    private val idle = IdleWatch()
    private var driveSec = 0L
    private val fail: (Exception) -> Unit = { e -> Log.w(TAG, "vehicle: ${e.message}"); problem("차량 신호 일부를 읽지 못했습니다.") }
    private fun problem(what: String) = main.post { Log.w(TAG, "problem: $what"); Guard.problems += what; Guard.onChange?.invoke() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notification(savedLang(this).copy.watching), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        places = runCatching { Places.parse(assets.open("highway_places.csv").bufferedReader().readText()) }
            .getOrElse { Places(emptyList()) }
        Guard.say = ::say
        Guard.stats = Stats(getSharedPreferences(STATS, MODE_PRIVATE))
        Guard.guide.lang = savedLang(this)
        Guard.onLang = { l -> tts?.setLocale(l.locale); stt?.setLocale(l.locale); Guard.voice = sttReady }
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
        val since = saved.getLong("since", 0)
        if (System.currentTimeMillis() - since < RESUME_MS)
            saved.getString("step", null)?.let { Guard.guide.restore(Step.valueOf(it), since) }
        vehicle = Vehicle(applicationContext).also { it.initialize() }
        listenVehicle()
        listenNavi()
        tts = TextToSpeech(applicationContext, Mode.ON_DEVICE).also { t ->
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    t.setLocale(Guard.guide.lang.locale)
                    // 내비 안내 용도로 내면 AAOS가 재생 중인 음악을 줄인다
                    t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    t.addEventListener(object : EventListener {
                        // 안내 음성이 끝난 뒤에만 마이크를 연다(자기 목소리를 듣지 않게)
                        override fun onDone() { main.post(::listen) }
                        override fun onStart() {}
                        override fun onReady() {}
                        override fun onStop() {}
                        override fun onError(errorType: ErrorType) { main.post { voiceFallback(errorType) } }
                        override fun onUpdatedRms(rms: Double) {}
                    })
                    ttsReady = true
                }
                override fun onFailure(errorType: ErrorType) { Log.w(TAG, "tts: $errorType"); problem("음성 안내를 쓸 수 없어 화면으로만 안내합니다.") }
            })
        }
        initStt()
        listenSpeed()
        tick()
    }

    private fun initStt() {
        val s = SpeechToText(applicationContext, SttMode.ON_DEVICE).also { stt = it }
        s.initialize(object : SttInit {
            override fun onSuccess() {
                s.setLocale(Guard.guide.lang.locale)
                s.addListener(object : ResultListener {
                    override fun onUpdated(text: String, isFinal: Boolean) { if (isFinal) main.post { heard(text) } }
                    override fun onError(e: SttError) {
                        Log.i(TAG, "stt: $e")
                        if (e == SttError.UNAVAILABLE_LANGUAGE) main.post { Guard.voice = false; Guard.onChange?.invoke() }
                    }
                    override fun onUpdatedEpdData(on: Long, off: Long) {}
                    override fun onStartedRecognition() {}
                    override fun onEndedRecognition() {}
                    override fun onReady() { testPcm?.let(::feed); testPcm = null }
                    override fun onUpdatedRms(rms: Double) {}
                })
                main.post { sttReady = true; Guard.voice = true; Guard.onChange?.invoke() }
            }
            override fun onFailure(errorType: SttError) { Log.w(TAG, "stt init: $errorType") }
        })
    }

    private var testPcm: ByteArray? = null

    // 엔진이 준비된 뒤 실시간 속도(100ms에 3200바이트)로 넣어야 인식된다
    private fun feed(pcm: ByteArray) = Thread {
        pcm.toList().chunked(3200).forEach { stt?.sendAudio(it.toByteArray()); Thread.sleep(100) }
        stt?.completeAudioSend()
    }.start()

    private fun voiceFallback(e: ErrorType) {
        Log.w(TAG, "tts: $e")
        val g = Guard.guide
        if (g.voiceLang != null || g.lang == Lang.KO) return
        g.voiceLang = if (g.lang == Lang.EN) Lang.KO else Lang.EN
        tts?.setLocale(g.voiceLang!!.locale); stt?.setLocale(g.voiceLang!!.locale)
        Guard.replay()
        Guard.onChange?.invoke()
    }

    private fun listen() {
        if (Guard.voice && Guard.guide.step in LISTEN_STEPS) runCatching { stt?.request() }.onFailure { Log.w(TAG, "stt: ${it.message}") }
    }

    private fun heard(text: String) {
        Log.i(TAG, "heard: $text")
        when (Voice.command(text, Guard.guide.step)) {
            Cmd.NEXT -> Guard.next()
            Cmd.REPLAY -> Guard.replay()
            Cmd.CANCEL -> Guard.cancel()
            null -> listen()
        }
    }

    // 화면에서 권한을 받은 뒤 다시 불리면 차속 구독을 붙인다
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (car == null) listenSpeed()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        intent?.getStringExtra(FAKE_LOCATION)?.takeIf { debuggable }?.split(',')?.map(String::toDouble)?.let { v ->
            fakeLocation = true
            onLocation(v[0], v[1], "%.5f, %.5f (테스트 위치)".format(v[0], v[1]), v.getOrNull(2))
        }
        intent?.getStringExtra(ROUTE)?.takeIf { debuggable }?.split(',')?.map(String::toDouble)?.let { v ->
            navi?.requestRoute(RouteInfo(latitude = v[0], longitude = v[1], poiName = "테스트 목적지", poiId = "", address = "", poiSubId = "0"))
        }
        // 음성 인식 검증용: 마이크 대신 16kHz·16bit·mono wav를 넣는다
        intent?.getStringExtra(STT_WAV)?.takeIf { debuggable }?.let { path ->
            val pcm = java.io.File(path).readBytes().drop(44)
            testPcm = (pcm + List(16000 + (3200 - pcm.size % 3200)) { 0.toByte() }).toByteArray()
            // 듣는 중이면 엔진 오류가 나서 먼저 멈춘다
            stt?.stop(); main.postDelayed({ stt?.request() }, 500)
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
        Guard.report = Report(places.describe(lat, lng, heading), lat, lng, places.around(lat, lng, heading))
        it.copy(nearStopZone = places.inStopZone(lat, lng))
    }

    private fun advise(advice: Advice) {
        val (lat, lng, heading) = location ?: return
        val types = if (advice == Advice.LOW_ENERGY) setOf("rest") else setOf("rest", "shelter")
        val (place, m) = places.nextAhead(lat, lng, heading, types) ?: return
        val name = Places.label(place)
        val c = Guard.copy
        val text = when (advice) {
            Advice.LOW_ENERGY -> if (Guard.state.socPct != null) c.lowBattery else c.lowFuel
            Advice.LONG_DRIVE -> c.longDrive
        }.format(m / 1000, name)
        Guard.stats?.add(Stats.BREAK)
        say(text)
        Guard.advice = text.replace("킬로미터", "km").replace("kilometers", "km").replace("公里", "km")
        Guard.adviceKind = advice
        val add = PendingIntent.getForegroundService(this, 3, Intent(this, GuardService::class.java)
            .setAction(ACTION_ADD_WAYPOINT).putExtra("lat", place.lat).putExtra("lng", place.lng).putExtra("name", name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, ALERT_CHANNEL).setSmallIcon(R.drawable.ic_road)
            .setContentTitle(c.waypointTitle.format(name)).setContentText(text).setCategory(Notification.CATEGORY_NAVIGATION)
            .addAction(Notification.Action.Builder(null, c.addWaypoint, add).build()).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(3, n)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        car?.disconnect(); navi?.release(); tts?.release(); stt?.release(); vehicle.release()
        super.onDestroy()
    }

    private fun listenSpeed() {
        if (checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) { problem(NO_SPEED); return }
        main.post { Guard.problems -= NO_SPEED }
        val c = Car.createCar(applicationContext).also { car = it }
        val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(v: CarPropertyValue<*>) = when (v.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> set { it.copy(speedKmh = (v.value as Float) * 3.6f) }
                VehiclePropertyIds.IGNITION_STATE -> set { it.copy(engineOn = v.value as Int in setOf(VehicleIgnitionState.ON, VehicleIgnitionState.START)) }
                else -> set { it.copy(parkedOrNeutral = (v.value as Int) in setOf(VehicleGear.GEAR_PARK, VehicleGear.GEAR_NEUTRAL)) }
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {}
        }
        cpm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        cpm.registerCallback(cb, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        runCatching { cpm.registerCallback(cb, VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
            .onFailure { Log.w(TAG, "ignition: ${it.message}") }
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
        vehicle.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
            // 에뮬레이터 실측: 비상등을 켜도 지시등 값은 바뀌지 않는다(비상등 감지 불가)
            override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = set { it.copy(rightSignal = value == TurnSignalValue.RIGHT) }
            override fun onFailed(e: Exception) = fail(e)
        })
        // AAOS PERF_STEERING_ANGLE은 signature 권한이라 Pleos SDK(CAR_DRIVE)로 읽는다
        vehicle.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(deg: Float?) = set { it.copy(steeringDeg = deg ?: 0f) }
            override fun onFailed(e: Exception) = fail(e)
        })
        // 자동 전조등은 밤과 터널에서 켜지므로 어두운 상황 신호로 쓴다
        vehicle.getLight().registerLowBeamLightState(object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) { main.post { lowBeam = state == LightState.ON } }
            override fun onFailed(e: Exception) { Log.w(TAG, "low beam: ${e.message}") }
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
            // 경로 안내 중이면 톨게이트·휴게소 진입을 TBT로 알 수 있다(톨게이트 좌표 데이터 대신)
            override fun onTBTInfo(list: List<TBTInfo>) = set { it.copy(naviStopZone = stopZoneAhead(list)) }
        }
        n.initialize({ n.addListener(l); n.getCurrentLocationInfo() }, { t -> Log.w(TAG, "navi: $t"); problem("내비 위치를 받지 못해 신고 위치를 알 수 없습니다.") })
    }

    // 리스너는 바인더 스레드에서 호출된다
    private fun set(f: (CarState) -> CarState) { main.post { Guard.state = f(Guard.state); step() } }

    private var ticks = 0
    private fun tick() {
        if (ticks++ % 5 == 0) navi?.getCurrentLocationInfo()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        Guard.state = Guard.state.copy(dark = lowBeam || hour !in 6..18)
        if (Guard.state.speedKmh >= 5f && ++driveSec % 60 == 0L) Guard.stats?.add(Stats.DRIVE_S, 60)
        step()
        main.postDelayed(::tick, 1000)
    }

    private fun step() {
        val before = Guard.guide.step
        val now = System.currentTimeMillis()
        Guard.guide.update(Guard.state, now)?.let(::say)
        if (Guard.guide.step == Step.IDLE) Guard.advisor.update(Guard.state, now)?.let(::advise)
        Guard.adviceKind?.takeUnless { Guard.advisor.stillValid(it, Guard.state) }?.let { Guard.advice = null; Guard.adviceKind = null }
        if (Guard.guide.step == Step.IDLE && idle.update(Guard.state, now)) say(Guard.copy.idling)
        if (before == Step.IDLE && Guard.guide.step == Step.CONFIRM) Guard.stats?.add(Stats.DETECT)
        if (before == Step.IDLE && Guard.guide.step != Step.IDLE) showGuide()
        if (before != Guard.guide.step) Log.i(TAG, "step $before -> ${Guard.guide.step}")
        if (before != Guard.guide.step) getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("step", Guard.guide.step.name).putLong("since", now).apply()
        Guard.onChange?.invoke()
    }

    private fun say(text: String) {
        if (ttsReady) tts?.speak(text) else Log.i(TAG, "say: $text")
    }

    // 백그라운드에서 화면을 바로 띄울 수 없는 경우를 대비해 전체 화면 알림으로 연다
    private fun showGuide() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, ALERT_CHANNEL).setSmallIcon(R.drawable.ic_hazard)
            .setContentTitle(Guard.copy.alertTitle).setContentText(Guard.copy.alertText)
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
        return Notification.Builder(this, STATUS_CHANNEL).setSmallIcon(R.drawable.ic_road)
            .setContentTitle("세이프스톱").setContentText(text).setOngoing(true).build()
    }

    companion object {
        const val TAG = "SafeStop"
        const val STATUS_CHANNEL = "status"
        const val ALERT_CHANNEL = "alert"
        const val PREFS = "guard"
        const val STATS = "stats"

        fun savedLang(ctx: Context) = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString("lang", null)
            ?.let { runCatching { Lang.valueOf(it) }.getOrNull() }
            ?: Lang.entries.firstOrNull { it.locale.language == Locale.getDefault().language } ?: Lang.KO
        const val NO_SPEED = "차속 권한이 없어 정차를 감지할 수 없습니다."
        val NAVI_STOP_TURNS = setOf(TurnType.TURN_TOLL, TurnType.TURN_RESTAREA, TurnType.TURN_ENTER_SERVICEAREA)

        // 에뮬레이터 실측: TBT distance는 앞 안내 지점부터의 구간 거리라 누적해서 본다
        fun stopZoneAhead(list: List<TBTInfo>, withinM: Double = 500.0): Boolean {
            var ahead = 0.0
            return list.any { ahead += it.distance; it.type in NAVI_STOP_TURNS && ahead <= withinM }
        }
        const val RESUME_MS = 10 * 60_000L
        // 에뮬레이터 v2.1.0 실측: 착좌 값이 Virtual Console과 반대로 온다. 실차 확인 필요
        const val SDK_OCCUPIED = false

        fun start(context: Context, from: Intent? = null) = context.startForegroundService(
            Intent(context, GuardService::class.java).apply { from?.extras?.let(::putExtras) })

        // 에뮬레이터 내비가 GPS 주입을 따르지 않아 디버그 빌드에서만 위치 주입 허용
        const val FAKE_LOCATION = "fake_location"
        const val ROUTE = "route"
        const val STT_WAV = "stt_wav"
        val LISTEN_STEPS = setOf(Step.CONFIRM, Step.HAZARD, Step.TRUNK, Step.EVACUATE)
        const val ACTION_ADD_WAYPOINT = "kr.safestop.ADD_WAYPOINT"
    }
}
