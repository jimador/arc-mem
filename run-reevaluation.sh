#!/usr/bin/env bash
set -euo pipefail

EXPERIMENT_ID="${1:?Usage: ./run-reevaluation.sh <experiment-report-id> [output-dir]}"
OUTPUT_DIR="${2:-reevaluation-output}"

export ARC_MEM_REEVALUATE_EXPERIMENT_ID="$EXPERIMENT_ID"
export ARC_MEM_REEVALUATE_OUTPUT_DIR="$OUTPUT_DIR"
exec ./mvnw spring-boot:run -pl arcmem-simulator
