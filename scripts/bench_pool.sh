#!/usr/bin/env bash
set -euo pipefail

THREADS=${THREADS:-32}
LOOPS=${LOOPS:-2000}
WARMUP=${WARMUP:-200}

mvn -q -Dtest=PoolBenchmarkTest -Dvostok.bench=true \
  -Dbench.threads="$THREADS" -Dbench.loops="$LOOPS" -Dbench.warmup="$WARMUP" test
