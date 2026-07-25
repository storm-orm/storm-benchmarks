#!/usr/bin/env bash
#
# Stamps the run's hardware/software fingerprint into <out-dir>/env.json.
#
# The fingerprint is what makes cross-run comparability *decidable*: two runs on a
# different cpu_model (or a different runner instance) must never have their absolute
# us/op compared, because the ephemeral runner pool does not guarantee the same silicon
# between runs. Comparisons live inside one run (library vs library) or inside one
# same-instance A/B (see compare_ab.py). See results/README.md.
#
# Usage: scripts/capture_env.sh <out-dir>
set -euo pipefail

OUT="${1:-results}"
mkdir -p "$OUT"

cpu_model=$(LC_ALL=C lscpu 2>/dev/null | sed -n 's/^Model name:[[:space:]]*//p' | head -1)
[ -n "$cpu_model" ] || cpu_model=$(sed -n 's/^model name[[:space:]]*:[[:space:]]*//p' /proc/cpuinfo 2>/dev/null | head -1)
[ -n "$cpu_model" ] || cpu_model=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || true)
cpu_cores=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || true)
cpu_max_mhz=$(LC_ALL=C lscpu 2>/dev/null | sed -n 's/^CPU max MHz:[[:space:]]*//p' | head -1)
kernel=$(uname -sr 2>/dev/null || true)
os=$( (. /etc/os-release 2>/dev/null && printf '%s' "$PRETTY_NAME") || true )
jdk=$(java -version 2>&1 | head -1 | tr -d '"' || true)

export EV_CPU="$cpu_model" EV_CORES="$cpu_cores" EV_MHZ="$cpu_max_mhz" \
       EV_KERNEL="$kernel" EV_OS="$os" EV_JDK="$jdk"
python3 - "$OUT/env.json" <<'PY'
import json, os, sys
json.dump({
    "cpu_model":     os.environ.get("EV_CPU", ""),
    "cpu_cores":     os.environ.get("EV_CORES", ""),
    "cpu_max_mhz":   os.environ.get("EV_MHZ", ""),
    "kernel":        os.environ.get("EV_KERNEL", ""),
    "os":            os.environ.get("EV_OS", ""),
    "jdk":           os.environ.get("EV_JDK", ""),
    # Populated by the workflow; empty on local runs.
    "runner_name":   os.environ.get("RUNNER_NAME", ""),
    "storm_ref":     os.environ.get("BENCH_STORM_REF", ""),
    "storm_sha":     os.environ.get("BENCH_STORM_SHA", ""),
    "storm_version": os.environ.get("BENCH_STORM_VERSION", ""),
    "suite_sha":     os.environ.get("BENCH_SUITE_SHA", ""),
    "timestamp":     os.environ.get("BENCH_TIMESTAMP", ""),
}, open(sys.argv[1], "w"), indent=2)
PY
echo "Wrote $OUT/env.json (cpu: ${cpu_model:-unknown})"
