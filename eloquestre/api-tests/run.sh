#!/usr/bin/env bash
# Testes da API de ponta a ponta (Node 22+). Sobe o back numa porta livre contra um
# MongoDB LOCAL (nunca o Atlas), roda os testes e derruba o servidor.
#
#   bash api-tests/run.sh                  # todos
#   bash api-tests/run.sh test_jobs.mjs    # um só
#
# Pré-requisitos: ./mvnw -DskipTests package  e  MongoDB em localhost:27017.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
PORT="${PORT:-8099}"
MONGO="${TEST_MONGO_URI:-mongodb://localhost:27017/eloquestre_teste}"
LOG="$DIR/backend_test.log"
JAR="$(ls "$ROOT"/target/*.jar 2>/dev/null | head -1)"
[ -z "$JAR" ] && { echo "Gere o jar antes: ./mvnw -DskipTests package"; exit 1; }

cd "$ROOT"
# teto de 15 min: se algo travar, o servidor de teste se encerra sozinho
JWT_SECRET="teste-local-segredo-com-mais-de-32-caracteres-ok" STORAGE_DIR="$DIR/.uploads" timeout 900 java -jar "$JAR" \
  --server.port="$PORT" --app.push.enabled=false --app.revenuecat.webhook-secret=segredo-de-teste \
  --spring.mail.host= --spring.mongodb.uri="$MONGO" > "$LOG" 2>&1 &
PID=$!
for _ in $(seq 1 90); do
  grep -q "Started Eloquestre" "$LOG" && break
  grep -q "APPLICATION FAILED" "$LOG" && { tail -30 "$LOG"; kill $PID; exit 1; }
  sleep 1
done

FAIL=0
TESTS="${*:-$(cd "$DIR" && ls test_*.mjs)}"
for t in $TESTS; do
  echo; echo "=== $t"
  (cd "$DIR" && node "$t" "http://localhost:$PORT") || FAIL=1
done

kill $PID 2>/dev/null
rm -rf "$DIR/.uploads"
echo; [ $FAIL -eq 0 ] && echo "TUDO OK" || echo "HÁ FALHAS (log do servidor: $LOG)"
exit $FAIL
