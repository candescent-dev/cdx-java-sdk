#!/usr/bin/env bash
# Run all Java examples in sorted order.
#
# From repo root:
#   bash scripts/run-all-java-examples.sh
#   bash scripts/run-all-java-examples.sh --log=/tmp/java-examples.log
#
# Default log: logs/all-examples.log
# Credentials: set in the environment or in repo-root `.env` (loaded automatically below).
#
# Prerequisite: Maven resolves com.candescent.forge:di-java-sdk (see pom.xml / README.md).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="${LOG_FILE:-$ROOT/logs/all-examples.log}"

for arg in "$@"; do
  case "$arg" in
    --log=*)
      LOG="${arg#--log=}"
      ;;
    -h|--help)
      sed -n '2,12p' "$0"
      exit 0
      ;;
  esac
done

mkdir -p "$(dirname "$LOG")"
LOCK="$(dirname "$LOG")/all-examples.lock"

if command -v flock >/dev/null 2>&1; then
  exec 9>"$LOCK"
  if ! flock -n 9; then
    echo "error: another run-all-java-examples.sh is already running (lock: $LOCK)" >&2
    exit 1
  fi
fi

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

EXAMPLES_DIR="$ROOT/examples/java"

: > "$LOG"
echo "Running Java examples — log: $LOG"
cd "$EXAMPLES_DIR"
mvn -q -DskipTests compile

for class in $(find src/main/java/com/candescent/examples -name '*Example.java' ! -name 'ExampleHelpers.java' -exec basename {} .java \; | sort); do
  main="com.candescent.examples.${class}"
  echo "===== $main =====" | tee -a "$LOG"
  # Pass mainClass explicitly — do not rely on a hardcoded <mainClass> in pom.xml.
  mvn -q org.codehaus.mojo:exec-maven-plugin:3.3.0:java \
    -Dexec.mainClass="$main" \
    -Dexec.classpathScope=compile \
    2>&1 | tee -a "$LOG" || true
  echo "" | tee -a "$LOG"
done

echo "Done. Full log: $LOG"
