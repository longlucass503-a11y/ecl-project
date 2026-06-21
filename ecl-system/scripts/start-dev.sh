#!/usr/bin/env bash
set -e

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
SYSTEM_DIR="$(dirname "$SCRIPTS_DIR")"
LOGS_DIR="$SYSTEM_DIR/logs"
mkdir -p "$LOGS_DIR"

MODE="${1:-all}"

# ============================================================
# ECL 开发环境启动脚本
# 用法: ./start-dev.sh [backend|frontend|all]
#
# 安全说明：
# - 所有服务通过 nohup 在后台启动，命令立即返回
# - PID 文件仅用于参考，停止服务以进程名匹配为准
# - 不包含 sleep / tail 等阻塞操作
# ============================================================

start_backend() {
  echo "[backend] Starting Spring Boot on port 8080..."
  cd "$SYSTEM_DIR/ecl-bootstrap"
  # setsid 创建一个新会话，完全脱离当前进程组，
  # 避免框架在 bash 命令结束后杀死整个进程组
  setsid bash -c "
    mvn spring-boot:run >> '$LOGS_DIR/backend.log' 2>&1 &
    echo \\\$! > '$LOGS_DIR/backend.pid'
    wait
  " > /dev/null 2>&1 &
  local pid=$!
  echo "[backend] PID $pid (setid wrapper) — log: $LOGS_DIR/backend.log"
}

start_frontend() {
  echo "[frontend] Starting Vite on port 3000..."
  cd "$SYSTEM_DIR/ecl-frontend"
  setsid bash -c "
    PORT=3000 npx vite --host 0.0.0.0 >> '$LOGS_DIR/frontend.log' 2>&1 &
    echo \\\$! > '$LOGS_DIR/frontend.pid'
    wait
  " > /dev/null 2>&1 &
  local pid=$!
  echo "[frontend] PID $pid (setid wrapper) — log: $LOGS_DIR/frontend.log"
}

case "$MODE" in
  backend)   start_backend ;;
  frontend)  start_frontend ;;
  all)
    start_backend
    start_frontend
    ;;
  *)
    echo "用法: $0 [backend|frontend|all]"
    exit 1
    ;;
esac

echo "=== 服务已在后台启动 ==="
echo "运行 ./stop-dev.sh 停止服务"
echo "运行 ./status.sh    查看状态"
