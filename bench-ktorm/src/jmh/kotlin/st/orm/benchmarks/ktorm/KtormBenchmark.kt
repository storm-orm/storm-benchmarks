package st.orm.benchmarks.ktorm

import org.ktorm.database.Database
import org.ktorm.dsl.and
import org.ktorm.dsl.asc
import org.ktorm.dsl.batchInsert
import org.ktorm.dsl.eq
import org.ktorm.dsl.from
import org.ktorm.dsl.greater
import org.ktorm.dsl.greaterEq
import org.ktorm.dsl.innerJoin
import org.ktorm.dsl.insertAndGenerateKey
import org.ktorm.dsl.lessEq
import org.ktorm.dsl.map
import org.ktorm.dsl.orderBy
import org.ktorm.dsl.select
import org.ktorm.dsl.where
import org.ktorm.entity.add
import org.ktorm.entity.filter
import org.ktorm.entity.find
import org.ktorm.entity.sequenceOf
import org.ktorm.entity.sortedBy
import org.ktorm.entity.take
import org.ktorm.entity.toList
import org.ktorm.schema.ColumnDeclaring
import org.ktorm.support.postgresql.PostgreSqlDialect
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
 * Ktorm using its entity sequence API for the entity-shaped workloads and the
 * SQL DSL for the flat projection and the grouped object graph. Reference
 * bindings drive the join and nested materialization; reads run in autocommit,
 * as idiomatic Ktorm does. Writes run inside `useTransaction { }`.
 *
 * The batch-insert workload uses Ktorm's native `batchInsert`, a single JDBC batch
 * inside one transaction. Ktorm has no batch path that returns generated keys, so,
 * unlike every other library, this workload inserts the rows without retrieving their
 * ids; see METHODOLOGY.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
open class KtormBenchmark {

    lateinit var dataSource: DataSource
    lateinit var database: Database
    lateinit var params: Params

    @Setup(Level.Trial)
    fun setUp() {
        dataSource = BenchDatabase.dataSource()
        database = Database.connect(dataSource, dialect = PostgreSqlDialect())
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
        return database.sequenceOf(Visits).find { it.id eq id }!!
    }

    @Benchmark
    fun joinWithMapping10(): List<Pet> = joinWithMapping(10)

    @Benchmark
    fun joinWithMapping100(): List<Pet> = joinWithMapping(100)

    @Benchmark
    fun joinWithMapping1000(): List<Pet> = joinWithMapping(1000)

    private fun joinWithMapping(rows: Int): List<Pet> {
        val base = params.nextWindowBase(rows)
        return database.sequenceOf(Pets)
            .filter { (Pets.id greater base) and (Pets.id lessEq base + rows) }
            .toList()
    }

    @Benchmark
    fun updateById(): String {
        val id = params.nextOwnerId()
        return database.useTransaction {
            // withReferences = false keeps the read lazy: owner columns only, no city join.
            val owner = database.sequenceOf(Owners, withReferences = false).find { it.id eq id }!!
            val telephone = Params.toggleTelephone(owner.telephone)
            owner.telephone = telephone
            // flushChanges writes only the changed column, Ktorm's dirty tracking in action.
            owner.flushChanges()
            telephone
        }
    }

    @Benchmark
    fun projection(): List<PetRow> {
        val cityId = params.nextCityId()
        return database.from(Pets)
            .innerJoin(Owners, on = Pets.ownerId eq Owners.id)
            .innerJoin(Cities, on = Owners.cityId eq Cities.id)
            .select(Pets.name, Owners.lastName, Cities.name)
            .where { Owners.cityId eq cityId }
            .map { PetRow(it[Pets.name]!!, it[Owners.lastName]!!, it[Cities.name]!!) }
    }

    @Benchmark
    fun batchInsert(): List<Int> {
        val base = params.nextBatchBase()
        return database.useTransaction {
            // Ktorm's native batch: one JDBC batch, returns affected-row counts, not keys.
            database.batchInsert(Visits) {
                for (i in 0 until Dataset.BATCH_SIZE) {
                    item {
                        set(it.petId, Params.petIdForBatch(base, i))
                        set(it.visitDate, Dataset.visitDate(base + i))
                        set(it.description, Dataset.visitDescription(base + i))
                    }
                }
            }.toList()
        }
    }

    @Benchmark
    fun objectGraph(): List<OwnerWithPets> {
        val cityId = params.nextCityId()
        return database.from(Owners)
            .innerJoin(Cities, on = Owners.cityId eq Cities.id)
            .innerJoin(Pets, on = Pets.ownerId eq Owners.id)
            .select()
            .where { Owners.cityId eq cityId }
            .orderBy(Owners.id.asc())
            .map { Pets.createEntity(it) }
            .groupIntoOwners()
    }

    @Benchmark
    fun keyset(): List<Pet> {
        val cursor = params.nextKeysetCursor()
        return database.sequenceOf(Pets)
            .filter { Pets.id greater cursor }
            .sortedBy { Pets.id }
            .take(Dataset.PAGE_SIZE)
            .toList()
    }

    @Benchmark
    fun dynamic(): List<PetRow> {
        val filter = params.nextDynamicFilter()
        val conditions = ArrayList<ColumnDeclaring<Boolean>>()
        conditions += Owners.cityId eq filter.cityId
        if (filter.byDate) conditions += Pets.birthDate greaterEq filter.minBirthDate
        if (filter.byType) conditions += Pets.typeId eq filter.typeId
        return database.from(Pets)
            .innerJoin(Owners, on = Pets.ownerId eq Owners.id)
            .innerJoin(Cities, on = Owners.cityId eq Cities.id)
            .select(Pets.name, Owners.lastName, Cities.name)
            .where { conditions.reduce { a, b -> a and b } }
            .map { PetRow(it[Pets.name]!!, it[Owners.lastName]!!, it[Cities.name]!!) }
    }

    @Benchmark
    fun multiStatement(): Long {
        val pid = params.nextMultiPetId()
        return database.useTransaction {
            // add() inserts and populates the entity's generated id; flushChanges() writes the amend.
            val visit = Visit {
                petId = pid
                visitDate = Dataset.visitDate(pid.toInt())
                description = Dataset.visitDescription(pid.toInt())
            }
            database.sequenceOf(Visits).add(visit)
            visit.description = "${visit.description} (rechecked)"
            visit.flushChanges()
            visit.id
        }
    }

    @Benchmark
    fun graphInsert(): List<Visit> {
        val graphs = (0 until Dataset.GRAPH_SIZE).map { params.nextGraphInsert() }
        return database.useTransaction {
            // Ktorm has no batch insert that returns keys, so owners and pets go in row by row to thread the ids.
            val ownerIds = graphs.map { graph ->
                database.insertAndGenerateKey(Owners) {
                    set(it.firstName, Dataset.firstName(graph.seed))
                    set(it.lastName, Dataset.lastName(graph.seed))
                    set(it.address, Dataset.address(graph.seed))
                    set(it.telephone, Dataset.telephone(graph.seed))
                    set(it.cityId, graph.cityId)
                } as Long
            }
            val petIds = graphs.mapIndexed { i, graph ->
                database.insertAndGenerateKey(Pets) {
                    set(it.name, Dataset.petName(graph.seed))
                    set(it.birthDate, Dataset.petBirthDate(graph.seed))
                    set(it.typeId, graph.typeId)
                    set(it.ownerId, ownerIds[i])
                } as Long
            }
            graphs.mapIndexed { i, graph ->
                val visit = Visit {
                    petId = petIds[i]
                    visitDate = Dataset.visitDate(graph.seed)
                    description = Dataset.visitDescription(graph.seed)
                }
                database.sequenceOf(Visits).add(visit)
                visit
            }
        }
    }

    private fun List<Pet>.groupIntoOwners(): List<OwnerWithPets> {
        val owners = LinkedHashMap<Long, Owner>()
        val pets = LinkedHashMap<Long, MutableList<Pet>>()
        for (pet in this) {
            val owner = owners.getOrPut(pet.owner.id) { pet.owner }
            pets.getOrPut(owner.id) { mutableListOf() }.add(pet)
        }
        return owners.values.map { OwnerWithPets(it, pets.getValue(it.id)) }
    }
}
