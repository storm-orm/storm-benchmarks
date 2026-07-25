#!/usr/bin/env python3
"""Detect when a fresh run is on different-speed hardware than the published run, so
its ABSOLUTE numbers must not be compared to the published table.

Usage: check_baseline_drift.py <this-run-dir> [<published-run-dir>] [--threshold PCT]

If <published-run-dir> is omitted, the newest results/<YYYY-MM-DD>/ is used. The check
is advisory: it always exits 0, printing a GitHub ::warning:: (or ::notice::) so the run
still completes. It flags two independent signals:

  1. env.json cpu_model differs   -> definitely a different instance.
  2. JDBC baseline drifts > threshold -> the box runs the fixed floor at a different
     speed, so cross-run absolute comparison is unsafe regardless of CPU labels.

Within-run comparisons (library vs library) and same-instance A/B runs are unaffected;
this only guards the one comparison the suite does not support: absolutes across runs.
"""
import argparse
import json
import re
import sys
from pathlib import Path


def baseline_us(run_dir: Path):
    data = json.loads((run_dir / "combined.json").read_text())
    for r in data:
        if r["library"] == "JDBC" and r["workload"] == "baseline":
            return r["score"]
    return None


def cpu(run_dir: Path) -> str:
    env = run_dir / "env.json"
    return json.loads(env.read_text()).get("cpu_model", "") if env.exists() else ""


def newest_published(this_dir: Path) -> Path | None:
    root = Path("results")
    dated = sorted(p for p in root.glob("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")
                   if p.is_dir() and p.resolve() != this_dir.resolve())
    return dated[-1] if dated else None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("this_dir")
    ap.add_argument("published_dir", nargs="?")
    ap.add_argument("--threshold", type=float, default=10.0)
    args = ap.parse_args()

    this = Path(args.this_dir)
    pub = Path(args.published_dir) if args.published_dir else newest_published(this)
    if pub is None:
        print("::notice::No published run to compare against; nothing to check.")
        return 0

    ct, cp = cpu(this), cpu(pub)
    bt, bp = baseline_us(this), baseline_us(pub)

    if ct and cp and ct != cp:
        print(f"::warning::Different CPU than the published run ({ct!r} vs {cp!r}). "
              f"This run's absolute µs/op are NOT comparable to {pub}/ — publish it as "
              f"its own within-run table; use an A/B run for any before/after claim.")
        return 0

    if bt and bp:
        drift = (bt - bp) / bp * 100.0
        if abs(drift) > args.threshold:
            print(f"::warning::JDBC baseline moved {drift:+.0f}% vs {pub}/ "
                  f"({bp:.1f} -> {bt:.1f} µs/op) — different-speed hardware. Do NOT "
                  f"compare this run's absolutes to the published table; compare "
                  f"within-run (relative to JDBC) or run a same-instance A/B.")
        else:
            print(f"::notice::JDBC baseline within {args.threshold:.0f}% of {pub}/ "
                  f"({drift:+.0f}%); hardware looks comparable.")
    else:
        print("::notice::Baseline unavailable in one run; skipping the drift check.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
