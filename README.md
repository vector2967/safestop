<p align="center"><img src="art/logo.svg" alt="SafeStop" height="88"></p>

# 세이프스톱 (SafeStop)

**고속도로에서 차가 멈추면 차량이 먼저 알아채고, 대피 순서를 음성으로 하나씩 안내하는 Pleos Connect 앱**

고속도로 2차사고는 치사율이 일반 교통사고보다 훨씬 높습니다. 세이프스톱은 차속·기어·도어·착좌·에어백 신호로 비상정차를 감지합니다. 그리고 한국도로공사의 대피 요령 "비트박스"(비상등 → 트렁크 → 가드레일 밖 → 스마트폰 신고)를 운전자가 앱을 켜지 않아도 단계별로 안내합니다.

## APK
- `apk/SafeStop_SafeStop_release.apk` (release 서명, minSdk 28)

## 테스트 환경
- Pleos Connect Emulator **v2.1.0** (AAOS API 34, x86_64). AVD는 **Cold boot**로 시작합니다(Quick boot면 차량 데이터가 오지 않습니다).
- Windows 11, Android SDK Platform 35, JDK 17, Gradle 8.9(wrapper 포함)

## 실행 방법
1. 에뮬레이터를 Cold boot로 띄웁니다.
2. APK를 설치합니다. `-g`는 차속 권한(`android.car.permission.CAR_SPEED`)을 미리 허용합니다. 빼면 첫 실행 때 권한 창이 뜹니다.
   ```bash
   adb install -r -g apk/SafeStop_SafeStop_release.apk
   ```
3. 앱 목록(하단 그리드 아이콘)에서 **세이프스톱**을 한 번 실행합니다. 감시 서비스가 시작되고, 이후에는 앱 화면을 닫아도 감시가 계속됩니다(재부팅 시 자동 시작). 정차를 감지하면 전체 화면 알림으로 안내 화면이 올라옵니다.
4. 차량 상태는 Virtual Console로 바꿉니다.
   ```bash
   adb forward tcp:8088 tcp:8088   # 브라우저에서 http://127.0.0.1:8088
   ```

### 시연 시나리오 (수동)
| 순서 | Virtual Console 조작 | 앱 반응 |
|---|---|---|
| 1 | 기어 **D** → **ACCEL**을 눌러 80km/h 이상으로 가속 | "안전 운행 중" |
| 2 | **BRAKE**로 0km/h까지 감속 → 기어 **P** | "고속도로 정차 감지" 5초 확인 → ① 비상등 안내(화면+음성) → 8초 뒤 ② 트렁크 안내 |
| 3 | **TRUNK** 열기 | ③ 가드레일 밖 대피 안내 + 112 신고 문자 QR |
| 4 | **FL door** 열기(동승자가 남아 있는 상태) | "아직 차 안에 탑승자가 있습니다" 재안내 |
| 5 | Test Console → Seat Occupancy에서 모든 좌석을 **Empty**로 | ④ 신고 안내(112 · 1588-2504, "○○고속도로 ○○휴게소 ○km 전" 위치, QR) |

### 위치 테스트 (debug 빌드 전용)
에뮬레이터 내비는 GPS 주입을 따르지 않아 위치를 직접 넣을 수 있게 했습니다.
```bash
# 망향휴게소(경부선) 앞: 정차해도 안내하지 않음
adb shell am start -n kr.safestop/.MainActivity --es fake_location "36.855650,127.180929"
# 휴게소에서 3km 떨어진 본선: 정차하면 안내
adb shell am start -n kr.safestop/.MainActivity --es fake_location "36.882650,127.180929"
# 내비에 경로 걸기(대전 방향): TBT 톨게이트 판정 확인용
adb shell am start -n kr.safestop/.MainActivity --es route "36.3504,127.3845"
```

### 음성 명령 테스트 (debug 빌드 전용)
에뮬레이터 마이크 대신 16kHz·16bit·mono wav를 STT에 넣어 확인합니다. 실제 마이크로 시연하려면 에뮬레이터 확장 설정에서 "Virtual microphone uses host audio input"을 켭니다.
```bash
adb push yes.wav /data/local/tmp/yes.wav
adb shell am start -n kr.safestop/.MainActivity --es stt_wav /data/local/tmp/yes.wav
```

### 야간 안내 확인
Virtual Console에서 전조등을 켜면(또는 19시~6시) 대피 단계에 야간 주의 문장이 붙습니다.
```bash
curl -X PUT "http://127.0.0.1:8088/vehicle/lights?feature=headlamp_mode&value=LowBeam"
```

### 시연 시나리오 (자동)
`demo/scenario.sh`가 위 과정을 Virtual Console REST API로 재생합니다. Git Bash에서 실행하며 curl이 필요합니다.
```bash
bash demo/scenario.sh
```

## 감지 규칙
- **진입 조건**: 아래 중 하나
  - 에어백 전개
  - 최근 2분 안에 80km/h 이상으로 달렸거나 제한속도 100 이상인 도로를 지났고, 지금 **차속 0에 P/N 또는 운전석 문 열림**
  - 같은 고속 주행 뒤 **D단 그대로 멈췄더라도** 다음 중 하나면 갓길 정차로 봅니다(정체와 구분)
    - 60km/h 이상에서 우측 방향지시등을 켜거나 핸들을 오른쪽으로 10도 넘게 돌린 뒤 30초 안에 정차
    - 배터리 3%·연료 2% 이하로 정차(연료 소진)
- **제외**: 휴게소·졸음쉼터 반경 400m 안의 정차, 내비 경로 안내 중 500m 안에 톨게이트·휴게소 진입이 있는 정차(정상 정차). 내비 TBT의 `distance`는 앞 안내 지점부터의 구간 거리라서 누적해 계산합니다
- **확인 단계**: 5초 동안 "고속도로 정차 감지"를 알리고 취소할 수 있게 합니다. 문이나 트렁크를 열면 바로 안내를 시작합니다.
- **해제**: 기어를 넣고 다시 10km/h 이상으로 출발하거나 "정상 정차예요" 버튼
- **재시작**: 앱 프로세스가 죽었다 살아나도 10분 안이면 진행하던 단계에서 이어 갑니다.
- **점검 표시**: 차속 권한이 없거나 차량 신호·음성·내비 연결에 실패하면 상단에 "점검 필요"와 이유를 표시합니다.
- **권한 안내**: 권한을 바로 요청하지 않고, 왜 필요한지(차속은 시스템 창에 "위치 정보"로 표시됨, 주행 중에는 시스템이 창을 막음)를 먼저 보여 준 뒤 "권한 허용하기" 버튼으로 요청합니다.
- **터널**: 터널 위치는 앱이 알 수 없어서(내비 주의 구간에 터널 유형이 없음) 대피 안내에 "터널 안이면 비상구로" 요령을 함께 넣었습니다.
- **신고 위치**: 번들한 휴게소·졸음쉼터 좌표(468곳)와 내비의 진행 방향으로 "경부고속도로 ○○휴게소 2.1km 전, ○○졸음쉼터에서 3.4km 지난 곳" 같은 문구를 만들어 화면과 QR(112 문자신고)에 넣습니다.
- **예방 안내**: 60km/h 이상 주행 중 배터리 15%·연료 10% 이하이거나 2시간 넘게 쉬지 않고 운전하면 "갓길에 서지 말고 ○km 앞 ○○휴게소에서 쉬어 가세요"라고 말하고, 알림의 **경유지로 추가** 버튼으로 내비에 경유지를 넣습니다(같은 안내는 30분에 한 번).
- **음성으로 답하기**: 안내 음성이 끝나면 마이크를 열어 "했어요·열었어요·나왔어요"(다음 단계), "다시"(다시 듣기), 확인 단계의 "정상이에요"(취소)를 알아듣습니다. "아직·못 했어요"는 넘기지 않습니다. 안내 음성이 끝난 뒤에만 들어서 자기 목소리를 인식하지 않습니다.
- **야간**: 전조등이 켜져 있거나(자동 전조등은 밤·터널에서 켜짐) 19시~6시면 대피 단계에 "밤에는 뒤차가 멈춘 차와 사람을 잘 보지 못합니다. 차 옆에 서 있지 말고 바로 가드레일 밖으로 피하세요."를 덧붙입니다.
- **가족에게 위치 보내기**: 신고 단계에서 112 문자 QR 옆에 가족 공유 QR을 함께 보여 줍니다. 찍으면 받는 사람이 빈 문자 창에 위치 문장과 카카오맵 링크가 들어갑니다.
- **외국어**: 상단 언어 버튼으로 한국어·English·中文을 바꿉니다(외국인 렌터카). 바꾸면 지금 단계를 그 언어로 다시 읽습니다. 112 문자와 위치 문장은 받는 사람이 한국 경찰·가족이라 한국어로 둡니다. 에뮬레이터 온디바이스 TTS·STT에는 중국어가 없어서(`unsupported locale: zh-CN`) 중국어는 화면만 중국어, 음성은 영어로 자동 대체합니다. 112는 2024-03-18부터 영어·중국어 통역을 24시간 제공합니다.
- **공회전 환기 안내**: 연료 잔량이 있는 차(내연기관·하이브리드)가 P단에 사람을 태운 채 시동을 30분 넘게 켜 두면 일산화탄소 위험을 알리고 환기를 권합니다(1시간에 한 번). 시동은 AAOS `IGNITION_STATE`로 읽습니다.
- **이번 달 기록**: 대기 화면에 이번 달 주행 시간, 쉬어 가기 안내 횟수, 정차 감지 횟수를 보여 줍니다(기기 안에만 저장).
- 판단 로직은 `StopGuide.kt`·`Advisor.kt`·`Voice.kt`, 언어별 문구는 `Texts.kt`, 위치 계산은 `Places.kt`에 있고, 단위 테스트는 `./gradlew testDebugUnitTest`로 실행합니다.

## 사용한 Pleos SDK · API
| SDK | 용도 |
|---|---|
| Vehicle 2.3.2 | 도어·트렁크, 좌석 착좌, 에어백 전개, 배터리 SOC·연료 잔량 |
| NaviHelper 3.0.5 | 현재 위치·진행 방향(신고 위치 문구, 휴게소 판정), 도로 제한속도, 쉼터 경유지 추가 |
| Vehicle 2.3.2 (Light) | 하향등 상태(야간·터널 판단) |
| TextToSpeech 3.3.1 (ON_DEVICE) | 단계별 음성 안내(한국어·영어), 안내가 끝난 시점(`onDone`)에 음성 인식 시작 |
| SpeechToText 3.3.1 (ON_DEVICE) | 음성으로 단계 넘기기·다시 듣기·취소 |
| AAOS Car API | 차속(`PERF_VEHICLE_SPEED`), 기어(`GEAR_SELECTION`, Pleos 기어 API는 deprecated), 시동(`IGNITION_STATE`), 주행 제한 대응(`distractionOptimized`) |

## 빌드
```bash
./gradlew assembleDebug
./gradlew assembleRelease   # 서명하려면 keystore.properties 필요(저장소에 포함하지 않음)
```

## 알려진 제약
- 비상등·경고등·도로 종류는 서드파티 앱에 공개된 API가 없습니다. 그래서 비상등은 감지하지 않고 안내만 하며, 고속도로 여부는 차속과 제한속도로 추정합니다.
- 에뮬레이터 v2.1.0에서는 Vehicle SDK의 착좌 값이 Virtual Console 표시와 반대로 전달됩니다. `GuardService.SDK_OCCUPIED`로 맞춰 두었습니다.
- 백그라운드에서 화면을 직접 띄우는 것은 플랫폼이 막아서 전체 화면 알림으로 엽니다. 음성 안내는 화면과 무관하게 동작합니다.
- 톨게이트 좌표와 민자고속도로 시설은 로그인 없이 받을 수 있는 공개 데이터가 없어 아직 포함하지 않았습니다.
- 에뮬레이터 온디바이스 STT는 합성 음성으로 시험했을 때 "켰어요"를 자주 틀렸습니다("기억이 있어요"). "했어요·다음·나왔어요·다시"는 잘 인식해서 화면 안내도 그 말로 했습니다.

## 오픈소스 · 데이터 · 라이선스
- Pleos Connect SDK(Vehicle, NaviHelper, TextToSpeech, SpeechToText): 42dot SDK License Agreement
- ZXing core 3.5.3(QR 생성): Apache License 2.0
- JUnit 4.13.2(테스트 전용): Eclipse Public License 1.0
- `app/src/main/assets/highway_places.csv`: 공공데이터포털(한국도로공사) 「전국휴게소정보표준데이터」, 「한국도로공사_졸음쉼터」 가공. 이용허락범위 제한 없음(2026-10-22 수집)
