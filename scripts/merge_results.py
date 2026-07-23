#!/usr/bin/env python3
"""Merges per-library JMH JSON results into combined.json and summary.md.

Usage: merge_results.py <results-dir>
"""
import json
import statistics
import sys
from pathlib import Path

LIBRARY_NAMES = {
    "bench-jdbc": "JDBC",
    "bench-storm": "Storm",
    "bench-hibernate": "Hibernate",
    "bench-jooq": "jOOQ",
    "bench-exposed": "Exposed",
    "bench-exposed-dao": "Exposed DAO",
    "bench-ktorm": "Ktorm",
    "bench-jimmer": "Jimmer",
}

# Reads light to heavy, then writes: the presentation order shared with the website's
# benchmarks page. Execution order is unrelated (JMH runs benchmarks alphabetically), and
# since every trial starts from a vacuumed, freshly analyzed table it does not need to be.
WORKLOAD_ORDER = [
    "baseline",
    "singleRowById",
    "projection",
    "keyset",
    "dynamic",
    "joinWithMapping10",
    "joinWithMapping100",
    "joinWithMapping1000",
    "objectGraph",
    "batchInsert",
    "updateById",
    "multiStatement",
    "graphInsert",
]


def main(results_dir: Path) -> None:
    rows = []
    for module, library in LIBRARY_NAMES.items():
        path = results_dir / f"{module}.json"
        if not path.exists():
            print(f"warning: {path} missing, skipping", file=sys.stderr)
            continue
        for run in json.loads(path.read_text()):
            metric = run["primaryMetric"]
            percentiles = metric.get("scorePercentiles") or {}
            # Score is the fastest fork (each fork scored as the mean of its measurement
            # iterations) and the spread is the full range up to the slowest fork. Benchmark noise
            # is one-sided: GC, scheduling and an unfavorable JIT compilation plan only ever add
            # time, so the fastest fork is the estimate of the framework's intrinsic cost least
            # contaminated by the harness, and the most reproducible across runs. The range keeps
            # the disagreement between forks visible instead of folding it into the score.
            fork_means = sorted(statistics.mean(fork) for fork in metric["rawData"])
            score = fork_means[0]
            spread = fork_means[-1] - fork_means[0]
            rows.append({
                "library": library,
                "workload": run["benchmark"].rsplit(".", 1)[-1],
                "mode": run["mode"],
                "score": score,
                "spread": spread,
                "mean": metric["score"],
                "error": metric["scoreError"],
                "p50": percentiles.get("50.0"),
                "p99": percentiles.get("99.0"),
                "unit": metric["scoreUnit"],
                "forks": run.get("forks"),
                "jdkVersion": run.get("jdkVersion"),
            })

    (results_dir / "combined.json").write_text(json.dumps(rows, indent=2) + "\n")

    libraries = [name for name in LIBRARY_NAMES.values() if any(r["library"] == name for r in rows)]
    by_key = {(r["library"], r["workload"]): r for r in rows}
    workloads = [w for w in WORKLOAD_ORDER if any(r["workload"] == w for r in rows)]
    workloads += sorted({r["workload"] for r in rows} - set(workloads))

    unit = rows[0]["unit"] if rows else "us/op"
    sample_mode = rows and rows[0]["mode"] == "sample"
    title = f"# Benchmark results ({unit}, fastest of 5 forks + range to the slowest fork, lower is better"
    title += ", p50 / p99)" if sample_mode else ")"
    lines = [
        title,
        "",
        "| Workload | " + " | ".join(libraries) + " |",
        "|---" * (len(libraries) + 1) + "|",
    ]
    for workload in workloads:
        cells = []
        for library in libraries:
            row = by_key.get((library, workload))
            if row is None:
                cells.append("n/a")
            elif sample_mode and row["p50"] is not None:
                cells.append(f"{row['p50']:.1f} / {row['p99']:.1f}")
            else:
                cells.append(f"{row['score']:.1f} +{row['spread']:.1f}")
        lines.append(f"| {workload} | " + " | ".join(cells) + " |")
    (results_dir / "summary.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else Path("results"))
