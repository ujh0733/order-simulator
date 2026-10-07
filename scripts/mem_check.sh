#!/usr/bin/env bash
set -euo pipefail

# 앱 메모리를 "실행 전 / 실행 후"로 비교한다. 측정할 때마다 GC를 1번 수행해서(앱이 잠깐 멈춤)
# 쓰레기를 치운 뒤의 값을 읽으므로, 두 값의 차이가 실제로 늘어난 메모리다.
#
# 사용법:
#   ./scripts/mem_check.sh before   실행 전 값을 저장
#   (부하 실행)
#   ./scripts/mem_check.sh after    실행 후 값을 읽고 전/후/차이를 출력
#   ./scripts/mem_check.sh          지금 값만 출력

SNAPSHOT="${TMPDIR:-/tmp}/order-simulator-mem-before"
PID=$(lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null | head -1 || true)
[[ -n "$PID" ]] || { echo "오류: 8080에서 실행 중인 앱이 없습니다." >&2; exit 1; }

human() {
  awk -v b="$1" 'BEGIN { split("B KiB MiB GiB", u, " "); i = 1; while (b >= 1024 && i < 4) { b /= 1024; i++ } printf "%.1f %s", b, u[i] }'
}

signed_human() {
  awk -v d="$1" 'BEGIN { s = (d < 0) ? "-" : "+"; a = (d < 0) ? -d : d; split("B KiB MiB GiB", u, " "); i = 1; while (a >= 1024 && i < 4) { a /= 1024; i++ } printf "%s%.1f %s", s, a, u[i] }'
}

# GC 후 힙 사용량(B)과 long[] 합계(B)를 "힙 long" 형태로 출력한다.
measure() {
  local longbytes heap
  # GC.class_histogram은 먼저 GC를 수행하고 살아있는 객체만 센다.
  longbytes=$(jcmd "$PID" GC.class_histogram 2>/dev/null | grep "\[J " | awk '{print $3}')
  heap=$(curl -s "http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap" \
    | sed -n 's/.*"statistic":"VALUE","value":\([^}]*\)}.*/\1/p' | head -1 | awk '{printf "%.0f", $1}')
  echo "$heap $longbytes"
}

case "${1:-}" in
  before)
    measure > "$SNAPSHOT"
    read -r H L < "$SNAPSHOT"
    echo "실행 전 저장: 힙(GC 후) $(human "$H") | long[] ${L} B"
    ;;
  after)
    [[ -f "$SNAPSHOT" ]] || { echo "오류: 먼저 './scripts/mem_check.sh before'를 실행하세요." >&2; exit 1; }
    read -r H0 L0 < "$SNAPSHOT"
    read -r H1 L1 < <(measure)
    printf "실행 전   힙(GC 후) %-10s | long[] %s B\n" "$(human "$H0")" "$L0"
    printf "실행 후   힙(GC 후) %-10s | long[] %s B\n" "$(human "$H1")" "$L1"
    printf "차이      힙(GC 후) %-10s | long[] %+d B\n" "$(signed_human "$((H1 - H0))")" "$((L1 - L0))"
    ;;
  "")
    read -r H L < <(measure)
    echo "현재: 힙(GC 후) $(human "$H") | long[] ${L} B"
    ;;
  *)
    echo "사용법: $0 [before|after]" >&2
    exit 1
    ;;
esac
