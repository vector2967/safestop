#!/usr/bin/env bash
# Virtual Console 브라우저 탭은 닫아 둔다(열려 있으면 속도를 덮어쓸 수 있음)
VC=http://127.0.0.1:8088/vehicle
put() { for t in 1 2 3; do [ "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$VC/$1")" = 200 ] && return; sleep 0.5; done; echo "실패: $1"; }
seat() { put "seat?kind=occupied&value=$2&area=$1"; }
say() { echo "[$(date +%T)] $*"; }

# heartbeat가 없으면 콘솔 API가 503(emulator_inactive)
CLIENT=demo-$$
( while true; do curl -s -o /dev/null -X POST "http://127.0.0.1:8088/browser/session?client=$CLIENT"; sleep 1; done ) &
HB=$!
trap 'kill $HB 2>/dev/null' EXIT
sleep 3

say "초기화: 2명 탑승(운전석·조수석), 문·트렁크 닫힘, D"
for a in driver passenger; do seat $a true; done
for a in rear_left rear_right; do seat $a false; done
put "body?door_fl=0"; put "body?trunk=0"; put "gear?gear=Drive"; put "controls?speed=20&accelerator=0&brake=0"
sleep 3

say "100km/h까지 가속"
for s in $(seq 0 10 100); do put "controls?speed=$s&accelerator=60&brake=0"; sleep 0.5; done
sleep 6

say "고장 발생 → 감속 후 정차"
for s in $(seq 100 -10 0); do put "controls?speed=$s&accelerator=0&brake=60"; sleep 0.5; done
put "controls?speed=0&accelerator=0&brake=100"; put "gear?gear=Park"
sleep 28   # 콘솔 차속이 0이 되기까지 약 10초 → 확인 5초 → ① 비상등 → 8초 후 ② 트렁크

say "트렁크 열기"
put "body?trunk=1"
sleep 6

say "운전석 문 열고 운전자 하차 (조수석은 아직 탑승)"
put "body?door_fl=1"; seat driver false
sleep 8

say "조수석 탑승자도 대피"
seat passenger false
sleep 10
say "끝"
