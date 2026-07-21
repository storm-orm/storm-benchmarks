package st.orm.benchmarks.exposeddao

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
 * Exposed (JetBrains) using the DAO API: entity classes with lazy references,
 * eager loading via `with(...)`, and flush-time write batching. Projections
 * drop to the SQL DSL, as DAO applications do. Associations load through
 * batched secondary queries, like Jimmer's fetcher model.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(5)
@Threads(1)
@State(Scope.Benchmark)
open class ExposedDaoBenchmark {

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
    }

    @TearDown(Level.Iteration)
    fun resetInsertedRows() {
        BenchDatabase.resetInsertedRows(dataSource)
    }

    @Benchmark
    fun singleRowById(): Visit {
        val id = params.nextVisitId()
        return transaction(database) {
            VisitDao.findById(id)!!.toVisit()
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
            PetDao.wrapRows(
                Pets.selectAll()
                    .where { (Pets.id greater base) and (Pets.id lessEq base + rows) },
            )
                .with(PetDao::owner, OwnerDao::city)
                .map { it.toPet() }
        }
    }

    @Benchmark
    fun updateById(): OwnerDao {
        val id = params.nextOwnerId()
        // DAO dirty tracking: the change is flushed as an UPDATE at transaction commit.
        return transaction(database) {
            val dao = OwnerDao.findById(id) ?: error("owner not found")
            dao.telephone = Params.toggleTelephone(dao.telephone)
            dao
        }
    }

    @Benchmark
    fun projection(): List<PetRow> {
        val cityId = params.nextCityId()
        return transaction(database) {
            (Pets innerJoin Owners innerJoin Cities)
                .select(Pets.name, Owners.lastName, Cities.name)
                .where { Owners.cityId eq EntityID(cityId, Cities) }
                .map { PetRow(it[Pets.name], it[Owners.lastName], it[Cities.name]) }
        }
    }

    @Benchmark
    fun batchInsert(): List<Long> {
        val base = params.nextBatchBase()
        return transaction(database) {
            val daos = (0 until Dataset.BATCH_SIZE).map { i ->
                VisitDao.new {
                    petId = EntityID(Params.petIdForBatch(base, i), Pets)
                    visitDate = Dataset.visitDate(base + i)
                    description = Dataset.visitDescription(base + i)
                }
            }
            // Reading the ids forces the entity cache to flush the pending inserts as a batch.
            daos.map { it.id.value }
        }
    }

    @Benchmark
    fun objectGraph(): List<OwnerWithPets> {
        val cityId = params.nextCityId()
        return transaction(database) {
            OwnerDao.find { Owners.cityId eq EntityID(cityId, Cities) }
                .orderBy(Owners.id to SortOrder.ASC)
                .with(OwnerDao::city, OwnerDao::pets)
                .map { dao ->
                    val owner = dao.toOwner()
                    OwnerWithPets(owner, dao.pets.map { it.toPet(owner) })
                }
        }
    }

    @Benchmark
    fun keyset(): List<Pet> {
        val cursor = params.nextKeysetCursor()
        return transaction(database) {
            PetDao.wrapRows(
                Pets.selectAll()
                    .where { Pets.id greater cursor }
                    .orderBy(Pets.id to SortOrder.ASC)
                    .limit(Dataset.PAGE_SIZE),
            )
                .with(PetDao::owner, OwnerDao::city)
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
                    var condition: Op<Boolean> = Owners.cityId eq EntityID(filter.cityId, Cities)
                    if (filter.byDate) condition = condition and (Pets.birthDate greaterEq filter.minBirthDate)
                    if (filter.byType) condition = condition and (Pets.typeId eq EntityID(filter.typeId, PetTypes))
                    condition
                }
                .map { PetRow(it[Pets.name], it[Owners.lastName], it[Cities.name]) }
        }
    }

    @Benchmark
    fun multiStatement(): Long {
        val pid = params.nextMultiPetId()
        return transaction(database) {
            val dao = VisitDao.new {
                this.petId = EntityID(pid, Pets)
                visitDate = Dataset.visitDate(pid.toInt())
                description = Dataset.visitDescription(pid.toInt())
            }
            // Reading the generated id forces the pending insert to flush; the DAO is the entity in hand.
            val id = dao.id.value
            dao.description = dao.description + " (rechecked)"
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
                this[Owners.cityId] = EntityID(graph.cityId, Cities)
            }.map { it[Owners.id].value }
            val petIds = Pets.batchInsert(graphs.indices, shouldReturnGeneratedValues = true) { i ->
                val graph = graphs[i]
                this[Pets.name] = Dataset.petName(graph.seed)
                this[Pets.birthDate] = Dataset.petBirthDate(graph.seed)
                this[Pets.typeId] = EntityID(graph.typeId, PetTypes)
                this[Pets.ownerId] = EntityID(ownerIds[i], Owners)
            }.map { it[Pets.id].value }
            Visits.batchInsert(graphs.indices, shouldReturnGeneratedValues = true) { i ->
                val graph = graphs[i]
                this[Visits.petId] = EntityID(petIds[i], Pets)
                this[Visits.visitDate] = Dataset.visitDate(graph.seed)
                this[Visits.description] = Dataset.visitDescription(graph.seed)
            }.map { it[Visits.id].value }
        }
    }

    private fun VisitDao.toVisit() = Visit(
        id = id.value,
        petId = petId.value,
        visitDate = visitDate,
        description = description,
    )

    private fun CityDao.toCity() = City(id.value, name)

    private fun OwnerDao.toOwner() = Owner(
        id = id.value,
        firstName = firstName,
        lastName = lastName,
        address = address,
        telephone = telephone,
        city = city.toCity(),
    )

    private fun PetDao.toPet(owner: Owner = this.owner.toOwner()) = Pet(
        id = id.value,
        name = name,
        birthDate = birthDate,
        typeId = typeId.value,
        owner = owner,
    )
}
