package st.orm.benchmarks.storm

import st.orm.benchmarks.common.BenchDatabase
import st.orm.benchmarks.common.Dataset
import st.orm.template.ORMTemplate
import st.orm.template.eq
import st.orm.template.selectFrom
import java.time.LocalDate

/**
 * Splits Storm's per-query cost into build (SQL generation) versus execution plus hydration,
 * with a raw JDBC floor on the same pool:
 *
 *   ./gradlew :bench-storm:profileSplit
 *
 * Caveat: the short interleaved blocks pollute JIT call-site profiles, which can overstate the
 * cheap phases severalfold (build measured 20us here versus 2.4us in a dedicated hot loop; see
 * ProfileHotLoop). Use this for coarse splits only; verify absolute numbers with ProfileHotLoop or JMH.
 */
fun main() {
    val dataSource = BenchDatabase.dataSource()
    val orm = ORMTemplate.of(dataSource)
    val visits = orm.entity(Visit::class)
    val pets = orm.entity(Pet::class)

    fun measure(label: String, iterations: Int, block: (Int) -> Any?) {
        repeat(iterations / 2) { block(it) } // warmup
        val start = System.nanoTime()
        repeat(iterations) { block(it) }
        val nanos = (System.nanoTime() - start) / iterations
        println("%-42s %8.1f us/op".format(label, nanos / 1000.0))
    }

    val iterations = 4000

    println("== getById (single row) ==")
    measure("storm: builder chain only, where(id)", iterations) { i ->
        visits.select().where((i % Dataset.VISITS + 1).toLong())
    }
    measure("storm: build only, where(id)", iterations) { i ->
        visits.select().where((i % Dataset.VISITS + 1).toLong()).build()
    }
    measure("storm: builder chain only, eq predicate", iterations) { i ->
        visits.select().where(Visit_.id eq (i % Dataset.VISITS + 1).toLong())
    }
    measure("storm: build only, eq predicate", iterations) { i ->
        visits.select().where(Visit_.id eq (i % Dataset.VISITS + 1).toLong()).build()
    }
    measure("storm: build, eq CONSTANT id", iterations) { _ ->
        visits.select().where(Visit_.id eq 1L).build()
    }
    measure("storm: build, where(CONSTANT id)", iterations) { _ ->
        visits.select().where(1L).build()
    }
    measure("storm: bare build, Visit (no where)", iterations) { _ ->
        visits.select().build()
    }
    measure("storm: bare build, Pet (no where)", iterations) { _ ->
        pets.select().build()
    }
    measure("storm: build only, Pet eq id", iterations) { i ->
        pets.select().where(Pet_.id eq (i % Dataset.PETS + 1).toLong()).build()
    }
    measure("storm: full getById", iterations) { i ->
        visits.getById((i % Dataset.VISITS + 1).toLong())
    }
    measure("jdbc: full (prepare+execute+map)", iterations) { i ->
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

    println("== joinWithMapping (100 rows, 3-table join) ==")
    measure("storm: build only (SQL generation)", iterations) { i ->
        pets.select().where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong()).build()
    }
    measure("storm: full resultList", iterations) { i ->
        pets.select().where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong()).resultList
    }

    println("== projection (100 rows, selectFrom template) ==")
    measure("storm: build only (SQL generation)", iterations) { i ->
        orm.selectFrom<Pet, PetRow> { "${Pet_.name}, ${Pet_.owner.lastName}, ${Pet_.owner.city.name}" }.where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong()).build()
    }
    measure("storm: full resultList", iterations) { i ->
        orm.selectFrom<Pet, PetRow> { "${Pet_.name}, ${Pet_.owner.lastName}, ${Pet_.owner.city.name}" }.where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong()).resultList
    }
}
