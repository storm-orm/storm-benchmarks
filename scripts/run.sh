#!/usr/bin/env bash
#
# Runs the full benchmark suite against a single tuned PostgreSQL container
# and merges the per-library JMH results into results/.
#
# Usage:
#   scripts/run.sh                # full run (~45 minutes)
#   scripts/run.sh --quick       # quick smoke run (~5 minutes, numbers are indicative only)
#   scripts/run.sh --sample      # sample mode: p50/p99 latency percentiles instead of averages
#   scripts/run.sh --threads N   # run benchmarks with N concurrent threads
#
# Environment:
#   BENCH_JDBC_URL   use an existing PostgreSQL instead of starting a container
#   BENCH_PG_IMAGE   PostgreSQL image (default postgres:17-alpine)

set -euo pipefail
cd "$(dirname "$0")/.."

MODULES=(bench-jdbc bench-storm bench-hibernate bench-jooq bench-exposed bench-exposed-dao bench-ktorm bench-jimmer)
# Plain string, not an array: empty arrays trip `set -u` on macOS's bash 3.2.
GRADLE_FLAGS=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick) GRADLE_FLAGS="$GRADLE_FLAGS -Pquick"; shift ;;
        --sample) GRADLE_FLAGS="$GRADLE_FLAGS -Psample"; shift ;;
        --ab-gc) GRADLE_FLAGS="$GRADLE_FLAGS -PabGc"; shift ;;
        --threads) GRADLE_FLAGS="$GRADLE_FLAGS -PbenchThreads=$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

IMAGE="${BENCH_PG_IMAGE:-postgres:17-alpine}"
CONTAINER=""

cleanup() {
    if [[ -n "$CONTAINER" ]]; then
        docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

if [[ -z "${BENCH_JDBC_URL:-}" ]]; then
    echo "Starting PostgreSQL ($IMAGE)..."
    # Publish on all interfaces, like Testcontainers: loopback-only binds (-p 127.0.0.1::5432)
    # go through a slower Docker Desktop port-forwarder path on macOS (measured 380 vs 156 us
    # round-trip). The database is a throwaway with dummy credentials, up only for the run.
    #
    # Statistics discipline: every trial setup runs VACUUM ANALYZE (BenchDatabase.vacuumAnalyze),
    # settling the tables and refreshing planner statistics so no fork inherits cleanup debt or
    # stale statistics from the workload before it. The unreachable autovacuum_analyze_threshold
    # keeps autovacuum's automatic ANALYZE from flipping cached prepared plans mid-trial;
    # autovacuum's vacuum stays on as a backstop for within-trial churn.
    # auto_explain samples 0.1% of executions so the plans actually used (including the prepared
    # statement custom-vs-generic choice) land in the container log for post-run inspection.
    CONTAINER=$(docker run -d \
        -e POSTGRES_DB=bench -e POSTGRES_USER=bench -e POSTGRES_PASSWORD=bench \
        --tmpfs /var/lib/postgresql/data \
        -p 5432 \
        "$IMAGE" \
        postgres -c fsync=off -c synchronous_commit=off -c full_page_writes=off \
                 -c shared_buffers=256MB -c max_connections=50 \
                 -c autovacuum_analyze_threshold=2000000000 \
                 -c shared_preload_libraries=auto_explain \
                 -c auto_explain.log_min_duration=0 -c auto_explain.sample_rate=0.001)
    PORT=$(docker port "$CONTAINER" 5432 | grep '0.0.0.0' | head -1 | cut -d: -f2)
    BENCH_JDBC_URL="jdbc:postgresql://127.0.0.1:${PORT}/bench"
    for i in $(seq 1 60); do
        if docker exec "$CONTAINER" pg_isready -U bench -q 2>/dev/null; then
            break
        fi
        sleep 0.5
    done
fi
echo "Database: $BENCH_JDBC_URL"

# Sequential on purpose: benchmark runs must not compete for CPU or the database.
for module in "${MODULES[@]}"; do
    echo
    echo "=== $module ==="
    # shellcheck disable=SC2086
    ./gradlew ":$module:jmh" $GRADLE_FLAGS \
        -Dbench.jdbc.url="$BENCH_JDBC_URL" \
        --console=plain --max-workers=1
done

mkdir -p results
for module in "${MODULES[@]}"; do
    cp "$module/build/results/$module.json" results/
done

# The container log carries the sampled auto_explain plans; keep it with the results so plan
# regimes can be verified per run. Skipped when running against an externally managed database.
if [[ -n "$CONTAINER" ]]; then
    docker logs "$CONTAINER" > results/postgres-plans.log 2>&1
fi

python3 scripts/merge_results.py results
echo
echo "Results written to results/combined.json and results/summary.md"
