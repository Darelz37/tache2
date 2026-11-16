#!/usr/bin/env bash
# Démarre l'API Spring Boot (profil dev par défaut) ou le worker d'import.
# Usage : ./scripts/start-api.sh          -> API sur http://localhost:8080
#         ./scripts/start-api.sh worker   -> worker d'import CSV
set -euo pipefail
API_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../api" && pwd)"
cd "$API_DIR"
JAR="target/proclama-api.jar"
[ -f "$JAR" ] || { echo "[api] jar absent : compilation..."; mvn -B -q -DskipTests package; }
if [ "${1:-}" = "worker" ]; then
  exec java -jar "$JAR" --app.mode=worker --spring.main.web-application-type=none
fi
exec java -jar "$JAR"
