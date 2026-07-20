package st.orm.benchmarks.storm

import com.sun.management.ThreadMXBean
import st.orm.benchmarks.common.BenchDatabase
import st.orm.benchmarks.common.Dataset
import st.orm.template.ORMTemplate
import st.orm.template.eq
import java.lang.management.ManagementFactory
import java.time.LocalDate

/**
 * Splits the per-operation allocation of the point-read path into phases using precise per-thread
 * allocation counters (no sampling):
 *
 *   ./gradlew :bench-storm:profileAlloc
 *
 * Bytes are exact for the measuring thread; use alongside ProfileSplit's wall-clock split to see which
 * phase produces the garbage that separates Storm from the leanest implementations.
 */
fun main() {
    val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    val threadId = Thread.currentThread().threadId()
    val dataSource = BenchDatabase.dataSource()
    val orm = ORMTemplate.of(dataSource)
    val visits = orm.entity(Visit::class)

    fun measure(label: String, iterations: Int, block: (Int) -> Any?) {
        repeat(iterations / 2) { block(it) } // warmup
        val start = threadBean.getThreadAllocatedBytes(threadId)
        repeat(iterations) { block(it) }
        val bytes = (threadBean.getThreadAllocatedBytes(threadId) - start) / iterations
        println("%-46s %8d B/op".format(label, bytes))
    }

    val iterations = 6000

    println("== getById (single row) allocation split ==")
    measure("builder chain only, eq predicate", iterations) { i ->
        visits.select().where(Visit_.id eq (i % Dataset.VISITS + 1).toLong())
    }
    measure("build only (SQL generation)", iterations) { i ->
        visits.select().where(Visit_.id eq (i % Dataset.VISITS + 1).toLong()).build()
    }
    measure("full getById", iterations) { i ->
        visits.getById((i % Dataset.VISITS + 1).toLong())
    }
    measure("jdbc floor (prepare+execute+map)", iterations) { i ->
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id, pet_id, visit_date, description FROM visit WHERE id = ?").use { statement ->
                statement.setLong(1, (i % Dataset.VISITS + 1).toLong())
                statement.executeQuery().use { rs ->
                    rs.next()
                    listOf(rs.getLong(1), rs.getLong(2), rs.getObject(3, LocalDate::class.java), rs.getString(4))
                }
            }
        }
    }
}
