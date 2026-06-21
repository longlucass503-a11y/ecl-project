#!/usr/bin/env bash
set -e

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
SYSTEM_DIR="$(dirname "$SCRIPTS_DIR")"
LOGS_DIR="$SYSTEM_DIR/logs"

MODE="${1:-all}"

# ============================================================
# ECL 开发环境停止脚本
# 用法: ./stop-dev.sh [backend|frontend|all]
#
# 停止策略：
# 1. 用 pgrep 按进程名精确匹配并杀死
# 2. PID 文件作为辅助（可能因 npx/npm 包装而指向已退出的父进程）
# ============================================================

kill_by_pgrep() {
  local label="$1"
  local pattern="$2"
  local pids
  pids=$(pgrep -f "$pattern" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    # 过滤掉当前脚本自身的 shell
    local filtered=""
    for pid in $pids; do
      if [ "$pid" != "$$" ] && [ "$pid" != "$PPID" ]; then
        filtered="$filtered $pid"
      fi
    done
    if [ -n "$filtered" ]; then
      echo "[$label] Killing $label processes: $filtered"
      # shellcheck disable=SC2086
      kill $filtered 2>/dev/null || true
    fi
  fi

  # 清理 PID 文件
  rm -f "$LOGS_DIR/$label.pid"
}

case "$MODE" in
  backend)
    kill_by_pgrep "backend" "[s]pring-boot:run"
    ;;
  frontend)
    kill_by_pgrep "frontend" "[n]ode.*vite"
    ;;
  all)
    kill_by_pgrep "frontend" "[n]ode.*vite"
    kill_by_pgrep "backend" "[s]pring-boot:run"
    ;;
  *)
    echo "用法: $0 [backend|frontend|all]"
    exit 1
    ;;
esac

echo "=== 停止完成 ==="
