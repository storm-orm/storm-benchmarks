package st.orm.benchmarks.storm

import st.orm.benchmarks.common.BenchDatabase
import st.orm.benchmarks.common.Dataset
import st.orm.benchmarks.common.Params
import st.orm.Ref
import st.orm.Scrollable
import st.orm.core.template.SqlInterceptor
import st.orm.template.ORMTemplate
import st.orm.template.PredicateBuilder
import st.orm.template.eq
import st.orm.template.greater
import st.orm.template.greaterEq
import st.orm.template.lessEq
import st.orm.template.refById
import st.orm.template.selectFrom
import st.orm.template.transactionBlocking

/**
 * Runs every Storm workload once and prints the SQL it generates, so the
 * statements behind the benchmarks can be inspected without a profiler:
 *
 *   ./gradlew :bench-storm:printSql
 */
fun main() {
    val dataSource = BenchDatabase.dataSource()
    val orm = ORMTemplate.of(dataSource)
    val visits = orm.entity(Visit::class)
    val pets = orm.entity(Pet::class)

    fun show(label: String, block: () -> Any?) {
        println("=== $label ===")
        SqlInterceptor.observe({ sql -> println(sql.statement().trim() + "\n") }) {
            block()
        }
    }

    show("singleRowById") {
        visits.getById(1L)
    }

    show("joinWithMapping") {
        pets.select()
            .where((Pet_.id greater 0L) and (Pet_.id lessEq 100L))
            .resultList
    }

    show("projection") {
        orm.selectFrom<Pet, PetRow> { "${Pet_.name}, ${Pet_.owner.lastName}, ${Pet_.owner.city.name}" }
            .where(Pet_.owner.city.id eq 1L)
            .resultList
    }

    show("batchInsert") {
        val newVisits = (0 until Dataset.BATCH_SIZE).map { i ->
            Visit(
                pet = refById<Pet>(Params.petIdForBatch(0, i)),
                visitDate = Dataset.visitDate(i),
                description = Dataset.visitDescription(i),
            )
        }
        transactionBlocking {
            visits.insertAndFetchIds(newVisits)
        }
    }

    show("updateById") {
        val owners = orm.entity(OwnerCityRef::class)
        transactionBlocking {
            val owner = owners.getById(1L)
            owners.update(owner.copy(telephone = "5550000000"))
        }
    }

    show("objectGraph") {
        pets.select()
            .where(Pet_.owner.city.id eq 1L)
            .orderBy(Pet_.owner)
            .resultGroupedBy(Pet_.owner)
            .map { (owner, ownerPets) -> OwnerWithPets(owner, ownerPets) }
    }

    show("keyset") {
        pets.scroll(Scrollable.of(Pet_.id, 100L, Dataset.PAGE_SIZE)).content
    }

    show("dynamic (city + date + type)") {
        var predicate: PredicateBuilder<Pet, *, *> = Pet_.owner.city.id eq 1L
        predicate = predicate and (Pet_.birthDate greaterEq Dataset.DYNAMIC_MIN_BIRTH_DATE)
        predicate = predicate and (Pet_.type eq refById<PetType>(1L))
        orm.selectFrom<Pet, PetRow> { "${Pet_.name}, ${Pet_.owner.lastName}, ${Pet_.owner.city.name}" }
            .where(predicate)
            .resultList
    }

    show("multiStatement") {
        transactionBlocking {
            val visit = visits.insertAndFetch(
                Visit(pet = refById<Pet>(1L), visitDate = Dataset.visitDate(1), description = Dataset.visitDescription(1)),
            )
            visits.update(visit.copy(description = "${visit.description} (rechecked)"))
            visit.id
        }
    }

    show("graphInsert") {
        val graphVisits = (0 until Dataset.GRAPH_SIZE).map { seed ->
            val owner = Owner(
                firstName = Dataset.firstName(seed),
                lastName = Dataset.lastName(seed),
                address = Dataset.address(seed),
                telephone = Dataset.telephone(seed),
                city = City(id = 1L, name = ""),
            )
            val pet = Pet(
                name = Dataset.petName(seed),
                birthDate = Dataset.petBirthDate(seed),
                type = refById<PetType>(1L),
                owner = owner,
            )
            Visit(
                pet = Ref.of(pet),
                visitDate = Dataset.visitDate(seed),
                description = Dataset.visitDescription(seed),
            )
        }
        transactionBlocking {
            orm.writeSet().insertAndFetchIds(graphVisits)
        }
    }

    BenchDatabase.resetInsertedRows(dataSource)
}
