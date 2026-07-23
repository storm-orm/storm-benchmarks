package st.orm.benchmarks.exposed

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup
import st.orm.benchmarks.common.BenchDatabase
import st.orm.benchmarks.common.Dataset
import st.orm.benchmarks.common.Params
import st.orm.benchmarks.common.Sanity
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Exposed (JetBrains) using the SQL DSL: explicit joins mapped to data
 * classes. Every operation runs inside `transaction { }`, as the API requires.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(5)
@Threads(1)
@State(Scope.Benchmark)
open class ExposedBenchmark {

    lateinit var dataSource: DataSource
    lateinit var database: Database
    lateinit var params: Params

    @Setup(Level.Trial)
    fun setUp() {
        dataSource = BenchDatabase.dataSource()
        database = Database.connect(dataSource)
        params = Params()
        Sanity.verify(
            singleRowById(), joinWithMapping10(), joinWithMapping100(), joinWithMapping1000(),
            projection(), batchInsert(), updateById(), objectGraph(),
            keyset(), dynamic(), multiStatement(), graphInsert(),
        )
        BenchDatabase.resetInsertedRows(dataSource)
        BenchDatabase.analyze(dataSource)
    }

    @TearDown(Level.Iteration)
    fun resetInsertedRows() {
        BenchDatabase.resetInsertedRows(dataSource)
    }

    @Benchmark
    fun singleRowById(): Visit {
        val id = params.nextVisitId()
        return transaction(database) {
            Visits.selectAll().where { Visits.id eq id }.single().toVisit()
        }
    }

    @Benchmark
    fun joinWithMapping10(): List<Pet> = joinWithMapping(10)

    @Benchmark
    fun joinWithMapping100(): List<Pet> = joinWithMapping(100)

    @Benchmark
    fun joinWithMapping1000(): List<Pet> = joinWithMapping(1000)

    private fun joinWithMapping(rows: Int): List<Pet> {
        val base = params.nextWindowBase(rows)
        return transaction(database) {
            (Pets innerJoin Owners innerJoin Cities)
                .selectAll()
                .where { (Pets.id greater base) and (Pets.id lessEq base + rows) }
                .map { it.toPet() }
        }
    }

    @Benchmark
    fun updateById(): String {
        val id = params.nextOwnerId()
        return transaction(database) {
            val row = Owners.selectAll().where { Owners.id eq id }.single()
            val telephone = Params.toggleTelephone(row[Owners.telephone])
            Owners.update({ Owners.id eq id }) { it[Owners.telephone] = telephone }
            telephone
        }
    }

    @Benchmark
    fun projection(): List<PetRow> {
        val cityId = params.nextCityId()
        return transaction(database) {
            (Pets innerJoin Owners innerJoin Cities)
                .select(Pets.name, Owners.lastName, Cities.name)
                .where { Owners.cityId eq cityId }
                .map { PetRow(it[Pets.name], it[Owners.lastName], it[Cities.name]) }
        }
    }

    @Benchmark
    fun batchInsert(): List<Long> {
        val base = params.nextBatchBase()
        return transaction(database) {
            Visits.batchInsert(0 until Dataset.BATCH_SIZE, shouldReturnGeneratedValues = true) { i ->
                this[Visits.petId] = Params.petIdForBatch(base, i)
                this[Visits.visitDate] = Dataset.visitDate(base + i)
                this[Visits.description] = Dataset.visitDescription(base + i)
            }.map { it[Visits.id] }
        }
    }

    @Benchmark
    fun objectGraph(): List<OwnerWithPets> {
        val cityId = params.nextCityId()
        return transaction(database) {
            (Owners innerJoin Cities innerJoin Pets)
                .selectAll()
                .where { Owners.cityId eq cityId }
                .orderBy(Owners.id)
                .groupIntoOwners()
        }
    }

    @Benchmark
    fun keyset(): List<Pet> {
        val cursor = params.nextKeysetCursor()
        return transaction(database) {
            (Pets innerJoin Owners innerJoin Cities)
                .selectAll()
                .where { Pets.id greater cursor }
                .orderBy(Pets.id)
                .limit(Dataset.PAGE_SIZE)
                .map { it.toPet() }
        }
    }

    @Benchmark
    fun dynamic(): List<PetRow> {
        val filter = params.nextDynamicFilter()
        return transaction(database) {
            (Pets innerJoin Owners innerJoin Cities)
                .select(Pets.name, Owners.lastName, Cities.name)
                .where {
                    var condition: Op<Boolean> = Owners.cityId eq filter.cityId
                    if (filter.byDate) condition = condition and (Pets.birthDate greaterEq filter.minBirthDate)
                    if (filter.byType) condition = condition and (Pets.typeId eq filter.typeId)
                    condition
                }
                .map { PetRow(it[Pets.name], it[Owners.lastName], it[Cities.name]) }
        }
    }

    @Benchmark
    fun multiStatement(): Long {
        val petId = params.nextMultiPetId()
        return transaction(database) {
            // The insert result carries the generated id and the inserted row, so no separate read-back.
            val inserted = Visits.insert {
                it[Visits.petId] = petId
                it[Visits.visitDate] = Dataset.visitDate(petId.toInt())
                it[Visits.description] = Dataset.visitDescription(petId.toInt())
            }
            val id = inserted[Visits.id]
            val description = inserted[Visits.description]
            Visits.update({ Visits.id eq id }) { it[Visits.description] = "$description (rechecked)" }
            id
        }
    }

    @Benchmark
    fun graphInsert(): List<Long> {
        val graphs = (0 until Dataset.GRAPH_SIZE).map { params.nextGraphInsert() }
        return transaction(database) {
            val ownerIds = Owners.batchInsert(graphs, shouldReturnGeneratedValues = true) { graph ->
                this[Owners.firstName] = Dataset.firstName(graph.seed)
                this[Owners.lastName] = Dataset.lastName(graph.seed)
                this[Owners.address] = Dataset.address(graph.seed)
                this[Owners.telephone] = Dataset.telephone(graph.seed)
                this[Owners.cityId] = graph.cityId
            }.map { it[Owners.id] }
            val petIds = Pets.batchInsert(graphs.indices, shouldReturnGeneratedValues = true) { i ->
                val graph = graphs[i]
                this[Pets.name] = Dataset.petName(graph.seed)
                this[Pets.birthDate] = Dataset.petBirthDate(graph.seed)
                this[Pets.typeId] = graph.typeId
                this[Pets.ownerId] = ownerIds[i]
            }.map { it[Pets.id] }
            Visits.batchInsert(graphs.indices, shouldReturnGeneratedValues = true) { i ->
                val graph = graphs[i]
                this[Visits.petId] = petIds[i]
                this[Visits.visitDate] = Dataset.visitDate(graph.seed)
                this[Visits.description] = Dataset.visitDescription(graph.seed)
            }.map { it[Visits.id] }
        }
    }

    private fun ResultRow.toVisit() = Visit(
        id = this[Visits.id],
        petId = this[Visits.petId],
        visitDate = this[Visits.visitDate],
        description = this[Visits.description],
    )

    private fun ResultRow.toOwner() = Owner(
        id = this[Owners.id],
        firstName = this[Owners.firstName],
        lastName = this[Owners.lastName],
        address = this[Owners.address],
        telephone = this[Owners.telephone],
        city = City(this[Cities.id], this[Cities.name]),
    )

    private fun ResultRow.toPet(owner: Owner = toOwner()) = Pet(
        id = this[Pets.id],
        name = this[Pets.name],
        birthDate = this[Pets.birthDate],
        typeId = this[Pets.typeId],
        owner = owner,
    )

    private fun Iterable<ResultRow>.groupIntoOwners(): List<OwnerWithPets> {
        val owners = LinkedHashMap<Long, Owner>()
        val pets = LinkedHashMap<Long, MutableList<Pet>>()
        for (row in this) {
            val owner = owners.getOrPut(row[Owners.id]) { row.toOwner() }
            pets.getOrPut(owner.id) { mutableListOf() }.add(row.toPet(owner))
        }
        return owners.values.map { OwnerWithPets(it, pets.getValue(it.id)) }
    }
}
