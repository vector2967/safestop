# 세이프스톱 (SafeStop)

**고속도로에서 차가 멈추면 차량이 먼저 알아채고, 대피 순서를 음성으로 하나씩 안내하는 Pleos Connect 앱**

고속도로 2차사고는 치사율이 일반 교통사고보다 훨씬 높습니다. 세이프스톱은 차속·기어·도어·착좌·에어백 신호로 비상정차를 감지합니다. 그리고 한국도로공사의 대피 요령 "비트밖스"(비상등 → 트렁크 → 가드레일 밖 → 스마트폰 신고)를 운전자가 앱을 켜지 않아도 단계별로 안내합니다.

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
3. 앱 목록(하단 그리드 아이콘)에서 **세이프스톱**을 실행합니다.
4. 차량 상태는 Virtual Console로 바꿉니다.
   ```bash
   adb forward tcp:8088 tcp:8088   # 브라우저에서 http://127.0.0.1:8088
   ```

### 시연 시나리오 (수동)
| 순서 | Virtual Console 조작 | 앱 반응 |
|---|---|---|
| 1 | 기어 **D** → **ACCEL**을 눌러 80km/h 이상으로 가속 | "안전 운행 중" |
| 2 | **BRAKE**로 0km/h까지 감속 → 기어 **P** | ① 비상등 안내(화면+음성), 8초 뒤 ② 트렁크 안내 |
| 3 | **TRUNK** 열기 | ③ 가드레일 밖 대피 안내 |
| 4 | **FL door** 열기(동승자가 남아 있는 상태) | "아직 차 안에 탑승자가 있습니다" 재안내 |
| 5 | Test Console → Seat Occupancy에서 모든 좌석을 **Empty**로 | ④ 신고 안내(112 · 1588-2504, 현재 위치) |

### 시연 시나리오 (자동)
`demo/scenario.sh`가 위 과정을 Virtual Console REST API로 재생합니다. Git Bash에서 실행하며 curl이 필요합니다.
```bash
bash demo/scenario.sh
```

## 감지 규칙
- **진입 조건**: 아래 중 하나
  - 에어백 전개
  - 최근 2분 안에 80km/h 이상으로 달렸거나 제한속도 100 이상인 도로를 지났고, 지금 **차속 0에 P/N 또는 운전석 문 열림**
- **해제**: 다시 10km/h 이상으로 출발하거나 "괜찮아요" 버튼
- 순수 로직은 `app/src/main/java/kr/safestop/StopGuide.kt`에 있고, 단위 테스트는 `./gradlew testDebugUnitTest`로 실행합니다.

## 사용한 Pleos SDK · API
| SDK | 용도 |
|---|---|
| Vehicle 2.3.2 | 기어, 도어·트렁크, 좌석 착좌, 에어백 전개 |
| NaviHelper 3.0.5 | 현재 위치·주소(신고 안내), 도로 제한속도 |
| TextToSpeech 3.3.1 (ON_DEVICE) | 단계별 한국어 음성 안내 |
| AAOS Car API | 차속(`PERF_VEHICLE_SPEED`), 주행 제한 대응(`distractionOptimized`) |

## 빌드
```bash
./gradlew assembleDebug
./gradlew assembleRelease   # 서명하려면 keystore.properties 필요(저장소에 포함하지 않음)
```

## 알려진 제약
- 비상등·경고등·도로 종류는 서드파티 앱에 공개된 API가 없습니다. 그래서 비상등은 감지하지 않고 안내만 하며, 고속도로 여부는 차속과 제한속도로 추정합니다.
- 에뮬레이터 v2.1.0에서는 Vehicle SDK의 착좌 값이 Virtual Console 표시와 반대로 전달됩니다. `MainActivity.SDK_OCCUPIED`로 맞춰 두었습니다.

## 오픈소스 · 라이선스
- Pleos Connect SDK(Vehicle, NaviHelper, TextToSpeech): 42dot SDK License Agreement
- JUnit 4.13.2(테스트 전용): Eclipse Public License 1.0
