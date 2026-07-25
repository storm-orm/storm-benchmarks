#!/usr/bin/env python3
"""Compare two SAME-INSTANCE benchmark runs (an A/B) and emit a Storm delta table.

Usage: compare_ab.py <baseline-dir> <candidate-dir> [--out FILE] [--force]

Both runs must have been produced on the same machine in the same job (their env.json
must report the same cpu_model). An absolute A/B delta is only meaningful within one
instance, so the script refuses mismatched hardware unless --force is given. This is the
supported way to answer "did this change make Storm faster?" -- run both refs back to
back on one runner (see .github/workflows/benchmark.yml, baseline_ref) rather than
comparing two dated runs whose hardware may differ.
"""
import argparse
import json
import sys
from pathlib import Path

WORKLOAD_ORDER = [
    "baseline", "singleRowById", "projection", "keyset", "dynamic",
    "joinWithMapping10", "joinWithMapping100", "joinWithMapping1000",
    "objectGraph", "batchInsert", "updateById", "multiStatement", "graphInsert",
]


def scores(run_dir: Path, library: str) -> dict:
    data = json.loads((run_dir / "combined.json").read_text())
    return {r["workload"]: r["score"] for r in data if r["library"] == library}


def cpu(run_dir: Path) -> str:
    env = run_dir / "env.json"
    if not env.exists():
        return ""
    return json.loads(env.read_text()).get("cpu_model", "")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("baseline")
    ap.add_argument("candidate")
    ap.add_argument("--out")
    ap.add_argument("--force", action="store_true",
                    help="compare even if the two runs report different CPUs")
    args = ap.parse_args()
    base, cand = Path(args.baseline), Path(args.candidate)

    cb, cc = cpu(base), cpu(cand)
    if cb and cc and cb != cc and not args.force:
        print(f"::error::Refusing A/B: different CPUs ({cb!r} vs {cc!r}). An absolute "
              f"delta is only valid on one instance. Re-run both refs in one job, or "
              f"pass --force to override.", file=sys.stderr)
        return 2

    sb, sc = scores(base, "Storm"), scores(cand, "Storm")
    lines = [
        f"# A/B: Storm delta (same instance{'' if cb else ', CPU unrecorded'})",
        "",
        f"- baseline: `{base}`" + (f" — {cb}" if cb else ""),
        f"- candidate: `{cand}`",
        "",
        "Absolute µs/op, lower is better. Both columns measured on the same box, so the",
        "delta is a real code effect, not hardware.",
        "",
        "| Workload | Storm baseline | Storm candidate | Δ | Δ% |",
        "|---|---|---|---|---|",
    ]
    workloads = [w for w in WORKLOAD_ORDER if w in sb or w in sc]
    for w in workloads:
        a, b = sb.get(w), sc.get(w)
        if a is None or b is None:
            continue
        d = b - a
        pct = d / a * 100.0
        arrow = "faster" if d < 0 else ("slower" if d > 0 else "flat")
        lines.append(f"| {w} | {a:.1f} | {b:.1f} | {d:+.1f} | {pct:+.1f}% ({arrow}) |")

    report = "\n".join(lines) + "\n"
    if args.out:
        Path(args.out).write_text(report)
    print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
