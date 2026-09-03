# Repeat: 2026-09-03, Storm 1.14.0

A second execution of the whole suite, unchanged, on an equivalent instance. It is not a
published dataset and no figure is quoted from it. It exists to answer one question the suite
could not otherwise answer: how much of the difference between two libraries is the libraries,
and how much is the run.

- **Date:** measured 2026-09-03, a few hours after [`../2026-09-03`](../2026-09-03/)
- **Storm:** `v1.14.0` at commit `c128ac47`, resolved as `1.14.0` (identical to the published run)
- **Suite:** commit `4a58d9c`
- **Runner:** GitHub-hosted dedicated runner, 4 vCPU / 16 GB, Ubuntu 24.04.4, AMD EPYC 7763
- **Workflow run:** https://github.com/storm-orm/storm-benchmarks/actions/runs/33760052284
- **JDK:** OpenJDK 64-Bit Server VM 21 (21.0.12.1)
- **JMH:** identical configuration to the published run

The hardware is equivalent, which is what makes the comparison meaningful: the JDBC baseline
measures 138.6 µs/op here against 140.7 µs/op on the published run, 1.5% apart, on the same CPU
model. Library versions are identical.

## What it shows

**Individual scores move by about 1%.** Median absolute change per library across the twelve
workloads is 0.6% to 1.5%, with a worst case of 6.5%.

**Rankings inside a close group do not survive.** Counting outright wins, Storm is fastest on
eight of twelve workloads in the published run and six of twelve here. The winner changes on the
projection, the update and the create-then-amend. Nothing changed between the runs but the run:
those three workloads are decided inside 1.3%, so which library comes first is which way the
noise fell.

**The shape of the field does survive, exactly.** Sorting each workload into three groups by a
3% band around the fastest framework gives the identical partition in both runs, the same
workloads in each group:

| | Workloads | Published | Repeat |
|---|---|---|---|
| Storm alone at the front, nothing within 3% | `singleRowById`, `keyset`, all three joins | 5 | 5 |
| Level | `projection`, `dynamic`, `updateById`, `multiStatement`, `batchInsert`, `graphInsert` | 6 | 6 |
| Behind | `objectGraph` (jOOQ) | 1 | 1 |

Every workload is either inside 1.3% or clear by more than 4.0%; nothing lands between. The band
therefore sits in an empty stretch of the distribution, and 2%, 3% or 4% partition the field
identically.

## Consequence for how results are read

A difference smaller than the band is not a result. Published claims state the leading group
rather than a ranking within it, and any figure quoted elsewhere should hold in both runs, not
just the one that shipped.

See [`../../METHODOLOGY.md`](../../METHODOLOGY.md#measured-precision).
