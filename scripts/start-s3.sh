#!/usr/bin/env bash
# Démarre le stockage objet S3 (SeaweedFS) en local, sans Docker.
# Cherche "weed" dans le PATH, sinon dans tools/weed.
# Ports : S3 8333 (utilisé par l'API) ; volume 8081 (8080 est réservé à l'API).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEED="$(command -v weed || true)"
[ -z "$WEED" ] && [ -x "$ROOT/tools/weed" ] && WEED="$ROOT/tools/weed"
if [ -z "$WEED" ]; then
  echo "[erreur] 'weed' introuvable : voir README.md (étape 2)." >&2
  exit 1
fi
DATA="$ROOT/.local/s3-data"
mkdir -p "$DATA"
echo "[s3] SeaweedFS sur http://localhost:8333 (données : $DATA)"
exec "$WEED" server -dir="$DATA" -s3 -s3.config="$ROOT/scripts/s3-dev.json" \
  -ip=127.0.0.1 -volume.port=8081 -master.volumeSizeLimitMB=256 -volume.max=0
