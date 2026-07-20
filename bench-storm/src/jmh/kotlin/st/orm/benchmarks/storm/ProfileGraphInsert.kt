package st.orm.benchmarks.storm

import st.orm.Ref
import st.orm.benchmarks.common.BenchDatabase
import st.orm.benchmarks.common.Dataset
import st.orm.benchmarks.common.Params
import st.orm.template.ORMTemplate
import st.orm.template.refById
import st.orm.template.transactionBlocking

/**
 * Splits the graphInsert cost into graph construction, write-set discovery, and the underlying
 * batched inserts, by running the same three-level owner -> pet -> visit write once through
 * `writeSet` (the benchmark shape) and once hand-ordered with three `insertAndFetchIds` calls
 * (the structure jOOQ uses, but on Storm's own insert path):
 *
 *   ./gradlew :bench-storm:profileGraphInsert
 *
 * The delta between the two full runs is the price of the write set's graph discovery and
 * key propagation. Coarse local numbers; verify absolute values with JMH.
 */
fun main() {
    val dataSource = BenchDatabase.dataSource()
    val orm = ORMTemplate.of(dataSource)
    val ownerRepo = orm.entity(Owner::class)
    val petRepo = orm.entity(Pet::class)
    val visitRepo = orm.entity(Visit::class)
    val params = Params()

    fun buildGraphs(): List<Triple<Owner, Pet, Visit>> = (0 until Dataset.GRAPH_SIZE).map {
        val graph = params.nextGraphInsert()
        val seed = graph.seed
        val owner = Owner(
            firstName = Dataset.firstName(seed),
            lastName = Dataset.lastName(seed),
            address = Dataset.address(seed),
            telephone = Dataset.telephone(seed),
            city = City(id = graph.cityId, name = ""),
        )
        val pet = Pet(
            name = Dataset.petName(seed),
            birthDate = Dataset.petBirthDate(seed),
            type = refById<PetType>(graph.typeId),
            owner = owner,
        )
        val visit = Visit(
            pet = Ref.of(pet),
            visitDate = Dataset.visitDate(seed),
            description = Dataset.visitDescription(seed),
        )
        Triple(owner, pet, visit)
    }

    // Optional label filter (-Ponly=<substring>) to focus a JFR recording on specific variants.
    val only = System.getProperty("profile.only")

    fun measure(label: String, iterations: Int, block: () -> Any?) {
        if (only != null && !label.contains(only)) {
            return
        }
        repeat(iterations / 4) { block() } // warmup
        BenchDatabase.resetInsertedRows(dataSource)
        val start = System.nanoTime()
        repeat(iterations) { block() }
        val nanos = (System.nanoTime() - start) / iterations
        println("%-46s %8.1f us/op".format(label, nanos / 1000.0))
        BenchDatabase.resetInsertedRows(dataSource)
    }

    val iterations = Integer.getInteger("profile.iterations", 400)

    measure("build graphs only (no database)", iterations) {
        buildGraphs()
    }

    measure("writeSet: insert only (no result fetch)", iterations) {
        val visits = buildGraphs().map { it.third }
        transactionBlocking {
            orm.writeSet().insert(visits)
        }
    }

    measure("writeSet: insertAndFetch (with re-select)", iterations) {
        val visits = buildGraphs().map { it.third }
        transactionBlocking {
            orm.writeSet().insertAndFetch(visits).map { it as Visit }
        }
    }

    measure("manual: 3 x insertAndFetchIds, hand-ordered", iterations) {
        val graphs = buildGraphs()
        transactionBlocking {
            val ownerIds = ownerRepo.insertAndFetchIds(graphs.map { it.first })
            val pets = graphs.mapIndexed { i, (owner, pet, _) ->
                pet.copy(owner = owner.copy(id = ownerIds[i]))
            }
            val petIds = petRepo.insertAndFetchIds(pets)
            val visits = graphs.mapIndexed { i, (_, _, visit) ->
                visit.copy(pet = refById<Pet>(petIds[i]))
            }
            val visitIds = visitRepo.insertAndFetchIds(visits)
            visits.mapIndexed { i, visit -> visit.copy(id = visitIds[i]) }
        }
    }

    // Same database work as "writeSet: insert only": visits written through insert(List), which still
    // executes a JDBC batch rather than a multi-row statement. The writeSet delta over this variant is
    // pure discovery/rebuild CPU.
    measure("manual: matched (visits via insert batch)", iterations) {
        val graphs = buildGraphs()
        transactionBlocking {
            val ownerIds = ownerRepo.insertAndFetchIds(graphs.map { it.first })
            val pets = graphs.mapIndexed { i, (owner, pet, _) ->
                pet.copy(owner = owner.copy(id = ownerIds[i]))
            }
            val petIds = petRepo.insertAndFetchIds(pets)
            val visits = graphs.mapIndexed { i, (_, _, visit) ->
                visit.copy(pet = refById<Pet>(petIds[i]))
            }
            visitRepo.insert(visits)
        }
    }

    // Sizes the insertAndFetch re-select alone: 20 visits by id. Timed inline so the reset in measure()
    // does not delete the rows being fetched.
    val warmIds = transactionBlocking {
        val graphs = buildGraphs()
        val ownerIds = ownerRepo.insertAndFetchIds(graphs.map { it.first })
        val pets = graphs.mapIndexed { i, (owner, pet, _) -> pet.copy(owner = owner.copy(id = ownerIds[i])) }
        val petIds = petRepo.insertAndFetchIds(pets)
        visitRepo.insertAndFetchIds(graphs.mapIndexed { i, (_, _, visit) -> visit.copy(pet = refById<Pet>(petIds[i])) })
    }
    repeat(iterations / 4) { visitRepo.findAllById(warmIds) }
    val reselectStart = System.nanoTime()
    repeat(iterations) { visitRepo.findAllById(warmIds) }
    val reselectNanos = (System.nanoTime() - reselectStart) / iterations
    println("%-46s %8.1f us/op".format("re-select only: findAllById(20 visits)", reselectNanos / 1000.0))
    BenchDatabase.resetInsertedRows(dataSource)
}
