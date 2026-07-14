package st.orm.benchmarks.storm

import st.orm.benchmarks.common.BenchDatabase
import st.orm.benchmarks.common.Dataset
import st.orm.template.ORMTemplate
import st.orm.template.eq

/** Splits the objectGraph cost: plain list, ordered list, and grouped terminal over the same query. */
fun main() {
    val orm = ORMTemplate.of(BenchDatabase.dataSource())
    val pets = orm.entity(Pet::class)

    fun measure(label: String, iterations: Int, block: (Int) -> Any?) {
        repeat(iterations) { block(it) } // warmup
        repeat(2) {
            val start = System.nanoTime()
            repeat(iterations) { block(it) }
            val nanos = (System.nanoTime() - start) / iterations
            println("%-46s %8.1f us/op".format(label, nanos / 1000.0))
        }
    }

    val iterations = 1500

    measure("where(city), resultList", iterations) { i ->
        pets.select().where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong()).resultList
    }
    measure("where(city), orderBy(owner), resultList", iterations) { i ->
        pets.select()
            .where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong())
            .orderBy(Pet_.owner)
            .resultList
    }
    measure("where(city), orderBy(owner), grouped", iterations) { i ->
        pets.select()
            .where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong())
            .orderBy(Pet_.owner)
            .resultGroupedBy(Pet_.owner)
    }
    measure("full objectGraph (grouped + map)", iterations) { i ->
        pets.select()
            .where(Pet_.owner.city.id eq (i % Dataset.CITIES + 1).toLong())
            .orderBy(Pet_.owner)
            .resultGroupedBy(Pet_.owner)
            .map { (owner, ownerPets) -> OwnerWithPets(owner, ownerPets) }
    }
}
