#!/usr/bin/env python3
"""Counts the published lines-of-code figures for the benchmarks page.

Rule: non-blank, non-comment, non-import, non-package source lines. Queries = the benchmark
workload class; Models = the entity or table definition files. Purpose-built shapes defined to
speed a workload (fairness rule 3) move from the model count to the query count of the library
that declares them; the manifest below names them explicitly. Result types shared by every
implementation live in bench-common and appear in neither count. Generated code (Storm's
metamodel, jOOQ's table classes) is not in these files to begin with.

Usage: scripts/count_loc.py
"""
from pathlib import Path

QUERY_FILES = {
    "storm": "bench-storm/src/jmh/kotlin/st/orm/benchmarks/storm/StormBenchmark.kt",
    "ktorm": "bench-ktorm/src/jmh/kotlin/st/orm/benchmarks/ktorm/KtormBenchmark.kt",
    "hibernate": "bench-hibernate/src/jmh/java/st/orm/benchmarks/hibernate/HibernateBenchmark.java",
    "exposedDao": "bench-exposed-dao/src/jmh/kotlin/st/orm/benchmarks/exposeddao/ExposedDaoBenchmark.kt",
    "exposed": "bench-exposed/src/jmh/kotlin/st/orm/benchmarks/exposed/ExposedBenchmark.kt",
    "jooq": "bench-jooq/src/jmh/java/st/orm/benchmarks/jooq/JooqBenchmark.java",
    "jimmer": "bench-jimmer/src/jmh/java/st/orm/benchmarks/jimmer/JimmerBenchmark.java",
    "jdbc": "bench-jdbc/src/jmh/java/st/orm/benchmarks/jdbc/JdbcBenchmark.java",
}

MODEL_FILES = {
    "storm": ["bench-storm/src/main/kotlin/st/orm/benchmarks/storm/Entities.kt"],
    "jimmer": [
        "bench-jimmer/src/main/java/st/orm/benchmarks/jimmer/City.java",
        "bench-jimmer/src/main/java/st/orm/benchmarks/jimmer/Owner.java",
        "bench-jimmer/src/main/java/st/orm/benchmarks/jimmer/PetType.java",
        "bench-jimmer/src/main/java/st/orm/benchmarks/jimmer/Pet.java",
        "bench-jimmer/src/main/java/st/orm/benchmarks/jimmer/Visit.java",
    ],
    "exposed": ["bench-exposed/src/main/kotlin/st/orm/benchmarks/exposed/Tables.kt"],
    "ktorm": ["bench-ktorm/src/main/kotlin/st/orm/benchmarks/ktorm/Tables.kt"],
    "exposedDao": ["bench-exposed-dao/src/main/kotlin/st/orm/benchmarks/exposeddao/Entities.kt"],
    "hibernate": ["bench-hibernate/src/main/java/st/orm/benchmarks/hibernate/Entities.java"],
}

# Purpose-built workload shapes (fairness rule 3): (library, defining file, first line, last line
# matcher). Their countable lines move from the model count to the query count.
QUERY_SHAPES = [
    ("storm", "bench-storm/src/main/kotlin/st/orm/benchmarks/storm/Entities.kt",
     "@DbTable(\"owner\")", ") : Entity<Long>"),
]


def countable(path):
    lines, in_block = [], False
    for line in Path(path).read_text().splitlines():
        stripped = line.strip()
        if in_block:
            if "*/" in stripped:
                in_block = False
            continue
        if stripped.startswith("/*"):
            if "*/" not in stripped:
                in_block = True
            continue
        if not stripped or stripped.startswith("//") or stripped.startswith("*"):
            continue
        if stripped.startswith("import ") or stripped.startswith("package "):
            continue
        lines.append(line)
    return lines


def shape_lines(library, path):
    total = 0
    for lib, shape_path, first, last in QUERY_SHAPES:
        if lib != library or shape_path != path:
            continue
        lines = countable(path)
        start = next(i for i, l in enumerate(lines) if l.strip() == first)
        end = next(i for i in range(start, len(lines)) if lines[i].strip().endswith(last))
        total += end - start + 1
    return total


def main():
    print(f"{'library':11} {'queries':>7} {'model':>6}")
    for library, query_file in QUERY_FILES.items():
        queries = len(countable(query_file))
        model = 0
        for path in MODEL_FILES.get(library, []):
            moved = shape_lines(library, path)
            model += len(countable(path)) - moved
            queries += moved
        print(f"{library:11} {queries:7} {model if library in MODEL_FILES else '-':>6}")


if __name__ == "__main__":
    main()
