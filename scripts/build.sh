#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

CYAN='\033[0;36m'; GREEN='\033[0;32m'; NC='\033[0m'
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }

# 인수 없으면 전체 빌드, 있으면 해당 모듈만
MODULES=("$@")
if [[ ${#MODULES[@]} -eq 0 ]]; then
  MODULES=(eureka-server api-gateway user-service connection-service message-service fanout-delivery-service)
fi

cd "$ROOT"

TASKS=()
for module in "${MODULES[@]}"; do
  TASKS+=(":$module:bootJar")
done

info "Building: ${MODULES[*]}"
./gradlew "${TASKS[@]}" -x test --parallel

echo ""
success "Build complete"
echo ""
for module in "${MODULES[@]}"; do
  jar=$(find "$ROOT/services/$module/build/libs" -name "*.jar" ! -name "*plain*" 2>/dev/null | head -1 || true)
  if [[ -n "$jar" ]]; then
    size=$(du -sh "$jar" | cut -f1)
    echo "  $module → $(basename "$jar") ($size)"
  fi
done
echo ""
