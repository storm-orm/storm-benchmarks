package st.orm.benchmarks.storm

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
import st.orm.repository.EntityRepository
import st.orm.template.ORMTemplate
import st.orm.template.eq
import st.orm.template.greater
import st.orm.template.lessEq
import st.orm.template.refById
import st.orm.template.selectFrom
import st.orm.template.transactionBlocking
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * Storm using its idiomatic Kotlin API: repositories, the metamodel select
 * DSL, SQL templates for the projection, and the ambient transaction API.
 * Entity graphs (pet with owner and city) load in a single auto-joined query.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
open class StormBenchmark {

    lateinit var dataSource: DataSource
    lateinit var orm: ORMTemplate
    lateinit var visits: EntityRepository<Visit, Long>
    lateinit var pets: EntityRepository<Pet, Long>
    lateinit var owners: EntityRepository<OwnerCityRef, Long>
    lateinit var params: Params

    @Setup(Level.Trial)
    fun setUp() {
        dataSource = BenchDatabase.dataSource()
        orm = ORMTemplate.of(dataSource)
        visits = orm.entity(Visit::class)
        pets = orm.entity(Pet::class)
        owners = orm.entity(OwnerCityRef::class)
        params = Params()
        Sanity.verify(singleRowById(), joinWithMapping10(), joinWithMapping100(), joinWithMapping1000(),
            projection(), batchInsert(), updateById(), objectGraph())
        BenchDatabase.resetInsertedVisits(dataSource)
    }

    @TearDown(Level.Iteration)
    fun resetInsertedRows() {
        BenchDatabase.resetInsertedVisits(dataSource)
    }

    @Benchmark
    fun singleRowById(): Visit {
        val id = params.nextVisitId()
        return visits.getById(id)
    }

    @Benchmark
    fun joinWithMapping10(): List<Pet> = joinWithMapping(10)

    @Benchmark
    fun joinWithMapping100(): List<Pet> = joinWithMapping(100)

    @Benchmark
    fun joinWithMapping1000(): List<Pet> = joinWithMapping(1000)

    private fun joinWithMapping(rows: Int): List<Pet> {
        val base = params.nextWindowBase(rows)
        return pets.select()
            .where((Pet_.id greater base) and (Pet_.id lessEq base + rows))
            .resultList
    }

    @Benchmark
    fun updateById(): OwnerCityRef {
        val id = params.nextOwnerId()
        return transactionBlocking {
            val owner = owners.getById(id)
            val updated = owner.copy(telephone = Params.toggleTelephone(owner.telephone))
            owners.update(updated)
            updated
        }
    }

    @Benchmark
    fun projection(): List<PetRow> {
        val cityId = params.nextCityId()
        return orm.selectFrom<Pet, PetRow> { "${Pet_.name}, ${Pet_.owner.lastName}, ${Pet_.owner.city.name}" }
            .where(Pet_.owner.city.id eq cityId)
            .resultList
    }

    @Benchmark
    fun batchInsert(): List<Long> {
        val base = params.nextBatchBase()
        val newVisits = (0 until Dataset.BATCH_SIZE).map { i ->
            Visit(
                pet = refById<Pet>(Params.petIdForBatch(base, i)),
                visitDate = Dataset.visitDate(base + i),
                description = Dataset.visitDescription(base + i),
            )
        }
        // The batch runs on a single connection either way; the transaction makes it atomic, matching the
        // insert semantics of the other implementations.
        return transactionBlocking {
            visits.insertAndFetchIds(newVisits)
        }
    }

    @Benchmark
    fun objectGraph(): List<OwnerWithPets> {
        val cityId = params.nextCityId()
        return pets.select()
            .where(Pet_.owner.city.id eq cityId)
            .orderBy(Pet_.owner)
            .resultGroupedBy(Pet_.owner)
            .map { (owner, ownerPets) -> OwnerWithPets(owner, ownerPets) }
    }


}
