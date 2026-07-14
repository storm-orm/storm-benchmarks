#!/usr/bin/env bash
#
# Audits the SQL statements each implementation sends per workload, using PostgreSQL's
# statement log. For every module and workload a short quick-mode run executes against a
# log_statement=all container; the log segment is then summarized as statement-kind counts,
# making round-trip patterns (transaction wrapping, secondary queries) visible per library.
#
# Usage:
#   scripts/count-statements.sh                # audit all modules
#   scripts/count-statements.sh bench-storm    # audit one module
#
# The counts are per quick-mode run, not per operation; read them as ratios. A read workload
# with as many BEGINs as SELECTs wraps every query in a transaction; an objectGraph with
# twice as many secondary as primary SELECTs issues two extra queries per operation.

set -euo pipefail
cd "$(dirname "$0")/.."

MODULES=(bench-jdbc bench-storm bench-hibernate bench-jooq bench-exposed bench-exposed-dao bench-jimmer)
if [[ $# -gt 0 ]]; then
    MODULES=("$@")
fi
WORKLOADS=(singleRowById joinWithMapping100 projection batchInsert updateById objectGraph)

IMAGE="${BENCH_PG_IMAGE:-postgres:17-alpine}"
CONTAINER="bench-statement-audit"

cleanup() {
    docker rm -f "$CONTAINER" > /dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup

docker run -d --name "$CONTAINER" \
    -e POSTGRES_DB=bench -e POSTGRES_USER=bench -e POSTGRES_PASSWORD=bench \
    --tmpfs /var/lib/postgresql/data \
    -p 127.0.0.1::5432 \
    "$IMAGE" \
    postgres -c log_statement=all -c fsync=off -c synchronous_commit=off > /dev/null
PORT=$(docker port "$CONTAINER" 5432 | head -1 | cut -d: -f2)
until docker exec "$CONTAINER" pg_isready -U bench -q 2>/dev/null; do sleep 0.5; done
URL="jdbc:postgresql://127.0.0.1:${PORT}/bench"
echo "Audit database: $URL"

for module in "${MODULES[@]}"; do
    for workload in "${WORKLOADS[@]}"; do
        marker="AUDIT_MARK_${module}_${workload}"
        docker exec "$CONTAINER" psql -U bench -d bench -q -c "SELECT '$marker'" > /dev/null
        ./gradlew ":$module:jmh" -Pquick "-PbenchIncludes=${workload}\$" \
            -Dbench.jdbc.url="$URL" --console=plain --max-workers=1 -q > /dev/null 2>&1 || {
            echo "$module/$workload: RUN FAILED"; continue; }
        docker logs "$CONTAINER" 2>&1 | python3 - "$module" "$workload" "$marker" <<'PYEOF'
import re
import sys

module, workload, marker = sys.argv[1], sys.argv[2], sys.argv[3]
lines = sys.stdin.read().splitlines()
start = 0
for index, line in enumerate(lines):
    if marker in line and "SELECT" in line:
        start = index
statements = []
for line in lines[start + 1:]:
    match = re.search(r"(?:statement|execute [^:]*): (.*)", line)
    if match:
        statements.append(match.group(1).strip())

def normalize(statement):
    head = statement.split()[0].upper() if statement.split() else "?"
    if head == "SELECT":
        table = re.search(r"FROM\s+(\w+)", statement, re.IGNORECASE)
        return f"SELECT {table.group(1) if table else '?'}"
    if head in ("INSERT", "UPDATE", "DELETE"):
        table = re.search(r"(?:INTO|UPDATE|FROM)\s+(\w+)", statement, re.IGNORECASE)
        return f"{head} {table.group(1) if table else '?'}"
    return head

counts = {}
for statement in statements:
    if "AUDIT_MARK" in statement:
        continue
    key = normalize(statement)
    counts[key] = counts.get(key, 0) + 1
summary = ", ".join(f"{k}={v}" for k, v in sorted(counts.items(), key=lambda e: -e[1]))
print(f"{module}/{workload}: {summary}")
PYEOF
    done
done
