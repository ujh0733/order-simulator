#!/usr/bin/env bash
set -euo pipefail

# 주문 생성기 병목 한계 측정 - 1단계(1분) 실행 스크립트
# 사용법: ./bottleneck_step.sh <1분당 생성 건수> [관리자ID] [비밀번호]
#   ./scripts/bottleneck_step.sh 180
#   ./scripts/bottleneck_step.sh 180 admin admin
#
# 로그인/내부 준비 과정은 화면에 찍지 않고, 실험 결과만 출력한다(블로그 캡처용).
# 60초 측정 구간 동안 10초마다 생성 건수와 함께 MySQL 컨테이너/앱(JVM) 프로세스의
# CPU·메모리를 같이 찍어서, 처리량과 자원 사용량을 한 표에서 바로 비교할 수 있게 한다.
# 문제가 생기면 표준에러(stderr)로만 출력하고 종료한다.

COUNT="${1:?사용법: $0 <1분당 생성 건수> [관리자ID] [비밀번호]}"
ADMIN_USER="${2:-admin}"
ADMIN_PASS="${3:-admin}"

BASE_URL="http://localhost:8080"
MYSQL_CONTAINER="order-simulator-mysql"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

mysql_exec() {
  docker exec -i -e MYSQL_PWD=app "$MYSQL_CONTAINER" mysql -uapp --default-character-set=utf8mb4 -N -B order_simulator
}

fail() {
  echo "오류: $1" >&2
  exit 1
}

# 앱은 Docker가 아니라 호스트에서 직접 뜬 JVM이라 docker stats에는 안 잡힌다. ps로 따로 본다.
APP_PID=$(lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null | head -1)

sample_mysql() {
  docker stats --no-stream --format "{{.CPUPerc}}\t{{.MemUsage}}" "$MYSQL_CONTAINER" 2>/dev/null || echo -e "N/A\tN/A"
}

sample_app() {
  if [[ -z "$APP_PID" ]]; then
    echo -e "N/A\tN/A"
    return
  fi
  ps -o %cpu=,rss= -p "$APP_PID" 2>/dev/null | awk '{printf "%s%%\t%dMB\n", $1, $2/1024}' || echo -e "N/A\tN/A"
}

# --- 준비 (화면에 표시하지 않음) ---
LOGIN_PAGE=$(curl -s -c "$COOKIE_JAR" "$BASE_URL/login")
CSRF_LOGIN=$(echo "$LOGIN_PAGE" | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//;s/"//')
[[ -n "$CSRF_LOGIN" ]] || fail "로그인 페이지에서 CSRF 토큰을 못 찾았습니다. 서버가 떠 있는지 확인하세요 ($BASE_URL/login)."

curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -o /dev/null \
  -X POST "$BASE_URL/login" \
  --data-urlencode "username=$ADMIN_USER" \
  --data-urlencode "password=$ADMIN_PASS" \
  --data-urlencode "_csrf=$CSRF_LOGIN"

DASHBOARD_HTML=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" "$BASE_URL/admin")
CSRF_TOKEN=$(echo "$DASHBOARD_HTML" | grep -o 'name="_csrf" content="[^"]*"' | sed 's/.*content="//;s/"//')
CSRF_HEADER=$(echo "$DASHBOARD_HTML" | grep -o 'name="_csrf_header" content="[^"]*"' | sed 's/.*content="//;s/"//')
[[ -n "$CSRF_TOKEN" && -n "$CSRF_HEADER" ]] || fail "로그인에 실패했습니다. ID/PW를 확인하세요."

LOAD_STATUS=$(curl -s -b "$COOKIE_JAR" "$BASE_URL/api/simulation/load")
RUNNING=$(echo "$LOAD_STATUS" | grep -o '"running":[a-z]*' | cut -d: -f2)
[[ "$RUNNING" != "true" ]] || fail "이미 진행 중인 부하 생성 작업이 있습니다. 끝난 뒤 다시 실행하세요: $LOAD_STATUS"

BASE_ID=$(echo "SELECT IFNULL(MAX(id),0) FROM orders;" | mysql_exec)
START_TS=$(date +%s)
START_LOCAL=$(date -r "$START_TS" +"%Y-%m-%d %H:%M:%S")

START_STATUS=$(curl -s -b "$COOKIE_JAR" -o /dev/null -w "%{http_code}" \
  -X POST "$BASE_URL/api/simulation/load" \
  -H "Content-Type: application/json" \
  -H "$CSRF_HEADER: $CSRF_TOKEN" \
  -d "{\"count\":${COUNT},\"durationMinutes\":1}")
[[ "$START_STATUS" == "200" ]] || fail "부하 생성 시작에 실패했습니다 (HTTP $START_STATUS)."

# --- 여기서부터 결과 출력 ---
echo "========================================================"
echo " 주문 생성기 병목 측정 - 목표 ${COUNT}건 / 1분"
echo "========================================================"
echo "시작 시각      : $START_LOCAL"
echo "부하 생성 시작  : OK (base_id=$BASE_ID)"
if [[ -z "$APP_PID" ]]; then
  echo "경고: 앱 프로세스(8080)를 찾지 못해 앱 CPU/메모리는 N/A로 표시됩니다." >&2
fi

echo "--------------------------------------------------------"
echo "[실시간] 10초 간격 CPU/메모리 (MySQL 컨테이너 / 앱 JVM)"
printf "  %-6s  %-9s %-10s  %-9s %-9s\n" "경과" "MySQL_CPU" "MySQL_MEM" "앱_CPU" "앱_RSS"

declare -a MYSQL_CPU MYSQL_MEM APP_CPU APP_RSS
for i in 1 2 3 4 5 6; do
  sleep 10
  ELAPSED=$((i * 10))

  IFS=$'\t' read -r m_cpu m_mem <<<"$(sample_mysql)"
  IFS=$'\t' read -r a_cpu a_rss <<<"$(sample_app)"
  MYSQL_CPU[i]="$m_cpu"; MYSQL_MEM[i]="$m_mem"; APP_CPU[i]="$a_cpu"; APP_RSS[i]="$a_rss"

  printf "  %-6s  %-9s %-10s  %-9s %-9s\n" "${ELAPSED}s" "$m_cpu" "$m_mem" "$a_cpu" "$a_rss"
done

CHECKPOINT_EPOCH=$((START_TS + 60))
CHECKPOINT_LOCAL=$(date -r "$CHECKPOINT_EPOCH" +"%Y-%m-%d %H:%M:%S")

ACHIEVED=$(echo "SELECT COUNT(*) FROM orders WHERE id > $BASE_ID AND ordered_at < '$CHECKPOINT_LOCAL';" | mysql_exec)
RATE=$(awk -v a="$ACHIEVED" -v c="$COUNT" 'BEGIN{printf "%.1f", (c>0 ? a/c*100 : 0)}')

echo "--------------------------------------------------------"
echo "[측정 시각 $CHECKPOINT_LOCAL] 60초 경과"
echo "  목표 건수     : ${COUNT}건"
echo "  실제 생성     : ${ACHIEVED}건"
echo "  달성률        : ${RATE}%"

echo "--------------------------------------------------------"
echo "[처리량+자원] 10초 구간별 생성 건수 / CPU / 메모리"
printf "  %-9s  %-8s  %-9s %-10s  %-9s %-9s\n" "구간(초)" "생성건수" "MySQL_CPU" "MySQL_MEM" "앱_CPU" "앱_RSS"

# 구간은 작업 시작 시각(START_LOCAL) 기준 상대 경과초로 잡는다(절대 시각 10초 그리드가 아님) -
# 그래야 위에서 10초마다 찍은 CPU/메모리 샘플과 정확히 같은 구간으로 짝지어진다.
echo "SELECT FLOOR(TIMESTAMPDIFF(SECOND, '$START_LOCAL', ordered_at)/10)*10 AS bucket, COUNT(*) AS cnt
      FROM orders WHERE id > $BASE_ID GROUP BY bucket ORDER BY bucket;" | mysql_exec > /tmp/.bottleneck_buckets.$$

declare -A BUCKET_CNT
while IFS=$'\t' read -r bucket cnt; do
  BUCKET_CNT[$bucket]="$cnt"
done < /tmp/.bottleneck_buckets.$$
rm -f /tmp/.bottleneck_buckets.$$

for i in 1 2 3 4 5 6; do
  BSTART=$(((i - 1) * 10))
  BEND=$((i * 10))
  CNT="${BUCKET_CNT[$BSTART]:-0}"
  printf "  %-9s  %-8s  %-9s %-10s  %-9s %-9s\n" \
    "${BSTART}-${BEND}" "${CNT}건" "${MYSQL_CPU[i]}" "${MYSQL_MEM[i]}" "${APP_CPU[i]}" "${APP_RSS[i]}"
done

echo "--------------------------------------------------------"
echo "[보조 지표]"
PENDING=$(curl -s -b "$COOKIE_JAR" "$BASE_URL/actuator/metrics/hikaricp.connections.pending" \
  | grep -o '"value":[0-9.]*' | head -1 | cut -d: -f2)
echo "  커넥션 풀 대기(pending) : ${PENDING:-N/A}"
echo "  진행 상태별 건수:"
echo "SELECT status, COUNT(*) FROM orders WHERE id > $BASE_ID GROUP BY status;" | mysql_exec \
  | awk -F'\t' '{printf "    %-12s %s건\n", $1, $2}'

FINAL_STATUS=$(curl -s -b "$COOKIE_JAR" "$BASE_URL/api/simulation/load")
FINAL_COMPLETED=$(echo "$FINAL_STATUS" | grep -o '"completed":[0-9]*' | cut -d: -f2)
FINAL_RUNNING=$(echo "$FINAL_STATUS" | grep -o '"running":[a-z]*' | cut -d: -f2)

echo "--------------------------------------------------------"
echo "[작업 종료] 60초 시점 기준 작업 상태"
if [[ "$FINAL_RUNNING" == "true" ]]; then
  echo "  상태          : 진행 중 (목표를 다 못 채워 계속 생성 중)"
else
  echo "  상태          : 완료"
fi
echo "  완료 건수      : ${FINAL_COMPLETED}/${COUNT}"
echo "========================================================"

if [[ "$FINAL_RUNNING" == "true" ]]; then
  echo "다음 단계를 실행하기 전에 완료를 기다리세요:" >&2
  echo "  watch -n 5 \"curl -s $BASE_URL/api/simulation/load\"" >&2
fi
