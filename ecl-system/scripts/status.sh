#!/usr/bin/env bash
set -e

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
SYSTEM_DIR="$(dirname "$SCRIPTS_DIR")"
LOGS_DIR="$SYSTEM_DIR/logs"

echo "========== ECL 服务状态 =========="

check_backend() {
  local pids
  pids=$(pgrep -f "[s]pring-boot:run" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "Backend:  🟢 RUNNING (PIDs: $(echo "$pids" | tr '\n' ' '))"
  elif ss -tlnp 2>/dev/null | grep -q ":8080 "; then
    echo "Backend:  🟡 PORT 8080 IN USE (not via spring-boot)"
  else
    echo "Backend:  🔴 STOPPED"
  fi
}

check_frontend() {
  local pids
  pids=$(pgrep -f "[n]ode.*vite" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "Frontend: 🟢 RUNNING (PIDs: $(echo "$pids" | tr '\n' ' '))"
  elif ss -tlnp 2>/dev/null | grep -q ":3000 "; then
    echo "Frontend: 🟡 PORT 3000 IN USE (not via vite)"
  else
    echo "Frontend: 🔴 STOPPED"
  fi
}

check_backend
check_frontend

echo ""
echo "端口检查:"
ports=$(ss -tlnp 2>/dev/null | awk '/:8080|:3000/{print $4}' | sed 's/.*://' | sort -u || true)
for port in $ports; do
  case "$port" in
    8080) echo "  Port 8080 (Backend) ✅" ;;
    3000) echo "  Port 3000 (Frontend) ✅" ;;
  esac
done
[ -z "$ports" ] && echo "  无服务端口在监听" || true

echo ""
echo "日志:"
echo "  backend:  $LOGS_DIR/backend.log"
echo "  frontend: $LOGS_DIR/frontend.log"
