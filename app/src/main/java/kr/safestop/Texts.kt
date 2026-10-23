package kr.safestop

import java.util.Locale

enum class Lang(val locale: Locale, val label: String) {
    KO(Locale.KOREA, "한국어"), EN(Locale.US, "English"), ZH(Locale.CHINA, "中文");

    val copy get() = when (this) { KO -> Texts.KO; EN -> Texts.EN; ZH -> Texts.ZH }
    fun next() = entries[(ordinal + 1) % entries.size]
}

class Copy(
    val speech: Map<Step, String>,
    val passengersInside: String,
    val night: String,
    val screen: Map<Step, Pair<String, String>>,
    val stepNames: List<String>,
    val bandWatching: String, val bandCheck: String, val bandDetected: String, val bandGuiding: String, val bandDone: String,
    val allow: String, val start: String, val startNow: String, val call: String, val allOut: String, val next: String,
    val normalStop: String, val replay: String, val endHold: String,
    val peopleLeft: String,
    val noteTrunk: String, val label112: String, val labelFamily: String,
    val permTitle: String, val permWhy: String,
    val sayHint: Map<Step, String>,
    val here: String, val passed: String, val ahead: String,
    val lowBattery: String, val lowFuel: String, val longDrive: String, val waypointTitle: String, val addWaypoint: String,
    val alertTitle: String, val alertText: String, val watching: String,
    val month: String, val idling: String,
)

// 112 문자와 위치 문장은 받는 쪽이 한국 경찰·가족이라 언어 설정과 상관없이 한국어로 둔다
object Texts {
    val KO = Copy(
        speech = mapOf(
            Step.CONFIRM to "고속도로 정차가 감지되었습니다. 5초 뒤 대피 안내를 시작합니다.",
            Step.HAZARD to "지금 바로 비상등을 켜세요.",
            Step.TRUNK to "트렁크를 열어 뒤차에 정차 사실을 알리세요.",
            Step.EVACUATE to "휴대폰을 챙겨 차 뒤쪽이 아닌, 가드레일 밖 안전한 곳으로 모두 대피하세요. 터널 안이면 비상구로 대피하세요.",
            Step.REPORT to "대피 후 112나 한국도로공사 1588-2504로 신고하세요. 무료 긴급견인을 받을 수 있습니다.",
        ),
        passengersInside = "아직 차 안에 탑승자가 있습니다. 모두 함께 가드레일 밖으로 대피하세요.",
        night = "밤에는 뒤차가 멈춘 차와 사람을 잘 보지 못합니다. 차 옆에 서 있지 말고 바로 가드레일 밖으로 피하세요.",
        screen = mapOf(
            Step.IDLE to ("고속도로 정차 감시 중" to "차가 고속도로에 멈추면 대피 순서를 음성으로 안내합니다."),
            Step.CONFIRM to ("고속도로 정차를 감지했어요" to "잠시 뒤 대피 안내를 시작합니다."),
            Step.HAZARD to ("비상등을 켜세요" to "대시보드 가운데에 있는 빨간 삼각형 버튼을 누르세요."),
            Step.TRUNK to ("트렁크를 여세요" to "트렁크를 열어 두면 뒤차가 고장 차량인 것을 멀리서 알아봅니다."),
            Step.EVACUATE to ("가드레일 밖으로 나가세요" to "차 앞뒤에 서 있지 말고 가드레일을 넘어 도로 바깥에서 기다리세요. 터널 안이면 초록 유도등을 따라 비상구로 나가세요."),
            Step.REPORT to ("112에 신고하세요" to "한국도로공사 1588-2504에 전화하면 가까운 안전지대까지 무료로 견인해 줍니다."),
        ),
        stepNames = listOf("비상등", "트렁크", "대피", "신고"),
        bandWatching = "●  감시 중", bandCheck = "!  점검 필요", bandDetected = "정차 감지", bandGuiding = "대피 안내 중", bandDone = "대피 완료",
        allow = "권한 허용하기", start = "대피 안내 시작", startNow = "지금 시작", call = "112 전화", allOut = "모두 나왔어요", next = "다음 단계",
        normalStop = "정상 정차예요", replay = "다시 듣기", endHold = "길게 눌러 종료",
        peopleLeft = "차 안에 아직 %d명 있어요",
        noteTrunk = "내리기 전에 휴대폰 카메라로 찍어 두세요", label112 = "112 문자 신고", labelFamily = "가족에게 위치 보내기",
        permTitle = "권한을 허용해 주세요",
        permWhy = "정차를 감지하려면 차량 속도를, 화면을 띄우려면 알림을 써야 합니다. 차량 속도는 시스템 창에 '위치 정보'로 표시됩니다. 주행 중에는 창이 열리지 않으니 정차한 뒤 눌러 주세요.",
        sayHint = mapOf(Step.CONFIRM to "“정상이에요”라고 말하면 취소", Step.HAZARD to "켰으면 “했어요”라고 말하세요",
            Step.TRUNK to "열었으면 “열었어요”라고 말하세요", Step.EVACUATE to "모두 나왔으면 “나왔어요”라고 말하세요"),
        here = "현재 위치", passed = " 지남", ahead = " 앞",
        lowBattery = "배터리가 부족합니다. 갓길에 서지 말고 %.0f킬로미터 앞 %s에서 충전하세요.",
        lowFuel = "연료가 부족합니다. 갓길에 서지 말고 %.0f킬로미터 앞 %s에서 주유하세요.",
        longDrive = "두 시간 넘게 운전하셨습니다. 갓길이 아닌 %.0f킬로미터 앞 %s에서 쉬어 가세요.",
        waypointTitle = "%s 경유 추천", addWaypoint = "경유지로 추가",
        alertTitle = "고속도로 정차 감지", alertText = "대피 안내를 시작합니다", watching = "고속도로 정차를 감시하고 있습니다",
        month = "이번 달  주행 %d시간 %d분 · 쉬어 가기 안내 %d회 · 정차 감지 %d회",
        idling = "시동을 켠 채 30분 넘게 머물고 있습니다. 시동을 켠 채 잠들지 마세요. 배기구가 눈이나 벽에 막히면 일산화탄소가 차 안으로 들어올 수 있습니다. 히터를 켤 때는 창문을 조금 열어 두세요.",
    )

    val EN = Copy(
        speech = mapOf(
            Step.CONFIRM to "Highway stop detected. Evacuation guidance starts in 5 seconds.",
            Step.HAZARD to "Turn on your hazard lights now.",
            Step.TRUNK to "Open the trunk to warn vehicles behind you.",
            Step.EVACUATE to "Take your phone and move behind the guardrail, not behind the car. In a tunnel, go to the emergency exit.",
            Step.REPORT to "After evacuating, call 112, or the Korea Expressway Corporation at 1588-2504 for free emergency towing.",
        ),
        passengersInside = "There are still passengers in the car. Everyone must move behind the guardrail.",
        night = "At night, drivers behind cannot see a stopped car or people well. Do not stand next to the car. Get behind the guardrail right away.",
        screen = mapOf(
            Step.IDLE to ("Watching for highway stops" to "If your car stops on a highway, you will hear what to do, step by step."),
            Step.CONFIRM to ("Highway stop detected" to "Evacuation guidance starts shortly."),
            Step.HAZARD to ("Turn on hazard lights" to "Press the red triangle button in the middle of the dashboard."),
            Step.TRUNK to ("Open the trunk" to "An open trunk tells drivers far behind that your car has broken down."),
            Step.EVACUATE to ("Get behind the guardrail" to "Do not stand in front of or behind the car. Climb over the guardrail and wait off the road. In a tunnel, follow the green lights to the emergency exit."),
            Step.REPORT to ("Call 112" to "112 has English interpreters 24 hours a day. Call 1588-2504 (Korea Expressway Corporation) for a free tow to the nearest safe area."),
        ),
        stepNames = listOf("Hazards", "Trunk", "Evacuate", "Report"),
        bandWatching = "●  Watching", bandCheck = "!  Check needed", bandDetected = "Stop detected", bandGuiding = "Evacuating", bandDone = "Evacuated",
        allow = "Allow access", start = "Start guidance", startNow = "Start now", call = "Call 112", allOut = "Everyone is out", next = "Next step",
        normalStop = "Normal stop", replay = "Repeat", endHold = "Hold to end",
        peopleLeft = "%d still in the car",
        noteTrunk = "Scan this with your phone before you get out", label112 = "Text 112", labelFamily = "Send location to family",
        permTitle = "Please allow access",
        permWhy = "Vehicle speed is needed to detect stops, and notifications to show this screen. The system shows vehicle speed as 'Location'. The dialog does not open while driving, so tap after stopping.",
        sayHint = mapOf(Step.CONFIRM to "Say “normal” to cancel", Step.HAZARD to "Say “done” when they are on",
            Step.TRUNK to "Say “done” when it is open", Step.EVACUATE to "Say “done” when everyone is out"),
        here = "You", passed = " behind", ahead = " ahead",
        lowBattery = "Battery is low. Do not stop on the shoulder. Charge at %2\$s, %1\$.0f kilometers ahead.",
        lowFuel = "Fuel is low. Do not stop on the shoulder. Refuel at %2\$s, %1\$.0f kilometers ahead.",
        longDrive = "You have been driving for over two hours. Take a break at %2\$s, %1\$.0f kilometers ahead, not on the shoulder.",
        waypointTitle = "Stop at %s", addWaypoint = "Add as waypoint",
        alertTitle = "Highway stop detected", alertText = "Starting evacuation guidance", watching = "Watching for highway stops",
        month = "This month  Driving %dh %dm · Break reminders %d · Stops detected %d",
        idling = "The engine has been running for over 30 minutes while parked. Do not sleep with the engine on. If the exhaust is blocked by snow or a wall, carbon monoxide can enter the car. Open a window a little when using the heater.",
    )

    val ZH = Copy(
        speech = mapOf(
            Step.CONFIRM to "检测到车辆在高速公路上停车。5秒后开始避险引导。",
            Step.HAZARD to "请立即打开危险报警闪光灯。",
            Step.TRUNK to "请打开后备箱，提醒后方车辆。",
            Step.EVACUATE to "请带上手机，不要站在车后，立即转移到护栏外的安全地带。在隧道内请前往紧急出口。",
            Step.REPORT to "撤离后请拨打112，或拨打韩国道路公社1588-2504，可享受免费紧急拖车服务。",
        ),
        passengersInside = "车内还有乘客。请所有人一起转移到护栏外。",
        night = "夜间后方车辆很难看清停下的车和人。不要站在车旁，请立即转移到护栏外。",
        screen = mapOf(
            Step.IDLE to ("正在监测高速停车" to "车辆在高速公路上停下时，会用语音逐步引导避险。"),
            Step.CONFIRM to ("检测到高速停车" to "即将开始避险引导。"),
            Step.HAZARD to ("打开双闪灯" to "按下仪表台中间的红色三角按钮。"),
            Step.TRUNK to ("打开后备箱" to "打开后备箱，后方车辆能从远处看出这是故障车。"),
            Step.EVACUATE to ("转移到护栏外" to "不要站在车前或车后，翻过护栏在路外等候。在隧道内请沿绿色指示灯走到紧急出口。"),
            Step.REPORT to ("拨打112报警" to "112全天24小时提供中文翻译。拨打韩国道路公社1588-2504，可免费拖车到最近的安全地带。"),
        ),
        stepNames = listOf("双闪灯", "后备箱", "撤离", "报警"),
        bandWatching = "●  监测中", bandCheck = "!  需要检查", bandDetected = "检测到停车", bandGuiding = "正在避险", bandDone = "已撤离",
        allow = "允许权限", start = "开始引导", startNow = "立即开始", call = "拨打112", allOut = "都已下车", next = "下一步",
        normalStop = "正常停车", replay = "再听一遍", endHold = "长按结束",
        peopleLeft = "车内还有%d人",
        noteTrunk = "下车前请用手机扫描", label112 = "短信报警112", labelFamily = "发送位置给家人",
        permTitle = "请允许权限",
        permWhy = "检测停车需要车速，显示画面需要通知权限。系统会把车速显示为“位置信息”。行驶中无法打开授权窗口，请停车后再点击。",
        sayHint = mapOf(Step.CONFIRM to "说“正常”可取消", Step.HAZARD to "打开后请说“好了”",
            Step.TRUNK to "打开后请说“好了”", Step.EVACUATE to "全部下车后请说“好了”"),
        here = "当前位置", passed = " 已过", ahead = " 前方",
        lowBattery = "电量不足。请不要停在路肩，在前方%1\$.0f公里的%2\$s充电。",
        lowFuel = "油量不足。请不要停在路肩，在前方%1\$.0f公里的%2\$s加油。",
        longDrive = "您已连续驾驶两小时以上。请不要停在路肩，在前方%1\$.0f公里的%2\$s休息。",
        waypointTitle = "建议途经%s", addWaypoint = "添加途经点",
        alertTitle = "检测到高速停车", alertText = "开始避险引导", watching = "正在监测高速停车",
        month = "本月  行驶%d小时%d分 · 休息提醒%d次 · 检测到停车%d次",
        idling = "车辆已怠速停留30分钟以上。请不要开着发动机睡觉。排气管被雪或墙堵住时，一氧化碳可能进入车内。开暖风时请把车窗打开一点。",
    )
}
