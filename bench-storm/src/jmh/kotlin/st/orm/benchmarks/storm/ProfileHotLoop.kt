package st.orm.benchmarks.storm

import st.orm.benchmarks.common.BenchDatabase
import st.orm.benchmarks.common.Dataset
import st.orm.template.ORMTemplate
import st.orm.template.eq

/** Long monomorphic loops per build variant; each gets its own hot-path JIT profile. */
fun main() {
    val orm = ORMTemplate.of(BenchDatabase.dataSource())
    val visits = orm.entity(Visit::class)

    fun run(label: String, seconds: Long, block: (Int) -> Any?) {
        var i = 0
        val warmupEnd = System.nanoTime() + 3_000_000_000L
        while (System.nanoTime() < warmupEnd) { block(i++) }
        i = 0
        val start = System.nanoTime()
        val end = start + seconds * 1_000_000_000L
        while (System.nanoTime() < end) { block(i++) }
        val perOp = (System.nanoTime() - start) / i.toDouble() / 1000.0
        println("%-38s %8.2f us/op (%d ops)".format(label, perOp, i))
    }

    run("build: where(id), cycling", 8) { i -> visits.select().where((i % Dataset.VISITS + 1).toLong()).build() }
    run("build: where(id), constant", 8) { _ -> visits.select().where(1L).build() }
    run("build: eq predicate, cycling", 8) { i -> visits.select().where(Visit_.id eq (i % Dataset.VISITS + 1).toLong()).build() }
    run("build: bare select().build()", 8) { _ -> visits.select().build() }
}
