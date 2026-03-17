#!/usr/bin/env bash
set -euo pipefail

CONFIG="${1:?Usage: ./run-experiment.sh <config-file>}"

if [ ! -f "$CONFIG" ]; then
    echo "Error: config file not found: $CONFIG" >&2
    exit 1
fi

export ARC_MEM_EXPERIMENT_CONFIG="$(cd "$(dirname "$CONFIG")" && pwd)/$(basename "$CONFIG")"
exec ./mvnw spring-boot:run -pl arcmem-simulator
