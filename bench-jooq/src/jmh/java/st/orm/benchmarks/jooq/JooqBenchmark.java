package st.orm.benchmarks.jooq;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import st.orm.benchmarks.common.BenchDatabase;
import st.orm.benchmarks.common.Dataset;
import st.orm.benchmarks.common.Params;
import st.orm.benchmarks.common.Sanity;
import st.orm.benchmarks.common.model.Models.City;
import st.orm.benchmarks.common.model.Models.Owner;
import st.orm.benchmarks.common.model.Models.OwnerWithPets;
import st.orm.benchmarks.common.model.Models.Pet;
import st.orm.benchmarks.common.model.Models.PetRow;
import st.orm.benchmarks.common.model.Models.Visit;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static st.orm.benchmarks.jooq.generated.Tables.CITY;
import static st.orm.benchmarks.jooq.generated.Tables.OWNER;
import static st.orm.benchmarks.jooq.generated.Tables.PET;
import static st.orm.benchmarks.jooq.generated.Tables.VISIT;

/**
 * jOOQ with generated classes and its modern mapping idioms: type-safe ad-hoc
 * converters for nested rows and MULTISET for the object graph (single query,
 * JSON aggregation under the hood).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(4)
@Threads(1)
@State(Scope.Benchmark)
public class JooqBenchmark {

    DataSource dataSource;
    DSLContext ctx;
    Params params;

    @Setup(Level.Trial)
    public void setUp() {
        dataSource = BenchDatabase.dataSource();
        ctx = DSL.using(dataSource, SQLDialect.POSTGRES);
        params = new Params();
        Sanity.verify(singleRowById(), joinWithMapping10(), joinWithMapping100(), joinWithMapping1000(), projection(),
                batchInsert(), updateById(), objectGraph(), keyset(), dynamic(), multiStatement(), graphInsert());
        BenchDatabase.resetInsertedRows(dataSource);
    }

    @TearDown(Level.Iteration)
    public void resetInsertedRows() {
        BenchDatabase.resetInsertedRows(dataSource);
    }

    @Benchmark
    public Visit singleRowById() {
        long id = params.nextVisitId();
        return ctx.select(VISIT.ID, VISIT.PET_ID, VISIT.VISIT_DATE, VISIT.DESCRIPTION)
                .from(VISIT)
                .where(VISIT.ID.eq(id))
                .fetchOne(Records.mapping(Visit::new));
    }

    @Benchmark
    public List<Pet> joinWithMapping10() {
        return joinWithMapping(10);
    }

    @Benchmark
    public List<Pet> joinWithMapping100() {
        return joinWithMapping(100);
    }

    @Benchmark
    public List<Pet> joinWithMapping1000() {
        return joinWithMapping(1000);
    }

    private List<Pet> joinWithMapping(int rows) {
        long base = params.nextWindowBase(rows);
        return ctx.select(
                        PET.ID, PET.NAME, PET.BIRTH_DATE, PET.TYPE_ID,
                        row(OWNER.ID, OWNER.FIRST_NAME, OWNER.LAST_NAME, OWNER.ADDRESS, OWNER.TELEPHONE,
                                row(CITY.ID, CITY.NAME).mapping(City::new)).mapping(Owner::new))
                .from(PET)
                .join(OWNER).on(PET.OWNER_ID.eq(OWNER.ID))
                .join(CITY).on(OWNER.CITY_ID.eq(CITY.ID))
                .where(PET.ID.gt(base).and(PET.ID.le(base + rows)))
                .fetch(Records.mapping(Pet::new));
    }

    @Benchmark
    public Long updateById() {
        long id = params.nextOwnerId();
        // UpdatableRecord.store() tracks changed fields and updates only those.
        return ctx.transactionResult(transaction -> {
            var record = DSL.using(transaction).fetchOne(OWNER, OWNER.ID.eq(id));
            record.setTelephone(Params.toggleTelephone(record.getTelephone()));
            record.store();
            return record.getId();
        });
    }

    @Benchmark
    public List<PetRow> projection() {
        long cityId = params.nextCityId();
        return ctx.select(PET.NAME, OWNER.LAST_NAME, CITY.NAME)
                .from(PET)
                .join(OWNER).on(PET.OWNER_ID.eq(OWNER.ID))
                .join(CITY).on(OWNER.CITY_ID.eq(CITY.ID))
                .where(OWNER.CITY_ID.eq(cityId))
                .fetch(Records.mapping(PetRow::new));
    }

    @Benchmark
    public List<Long> batchInsert() {
        int base = params.nextBatchBase();
        // The multi-row insert is atomic as a single statement; the transaction keeps the implementation
        // mechanism-robust, since larger batches would have to chunk into multiple statements.
        return ctx.transactionResult(transaction -> {
            var insert = DSL.using(transaction)
                    .insertInto(VISIT, VISIT.PET_ID, VISIT.VISIT_DATE, VISIT.DESCRIPTION);
            for (int i = 0; i < Dataset.BATCH_SIZE; i++) {
                insert = insert.values(Params.petIdForBatch(base, i), Dataset.visitDate(base + i),
                        Dataset.visitDescription(base + i));
            }
            return insert.returning(VISIT.ID).fetch().map(record -> record.get(VISIT.ID));
        });
    }

    @Benchmark
    public List<OwnerWithPets> objectGraph() {
        long cityId = params.nextCityId();
        return ctx.select(
                        OWNER.ID, OWNER.FIRST_NAME, OWNER.LAST_NAME, OWNER.ADDRESS, OWNER.TELEPHONE,
                        CITY.ID, CITY.NAME,
                        multiset(
                                select(PET.ID, PET.NAME, PET.BIRTH_DATE, PET.TYPE_ID)
                                        .from(PET)
                                        .where(PET.OWNER_ID.eq(OWNER.ID))))
                .from(OWNER)
                .join(CITY).on(OWNER.CITY_ID.eq(CITY.ID))
                .where(OWNER.CITY_ID.eq(cityId))
                .orderBy(OWNER.ID)
                .fetch(record -> {
                    Owner owner = new Owner(record.value1(), record.value2(), record.value3(), record.value4(),
                            record.value5(), new City(record.value6(), record.value7()));
                    List<Pet> pets = record.value8()
                            .map(pet -> new Pet(pet.value1(), pet.value2(), pet.value3(), pet.value4(), owner));
                    return new OwnerWithPets(owner, pets);
                });
    }

    @Benchmark
    public List<Pet> keyset() {
        long cursor = params.nextKeysetCursor();
        // jOOQ's native keyset pagination: ORDER BY .. SEEK(cursor) LIMIT.
        return ctx.select(
                        PET.ID, PET.NAME, PET.BIRTH_DATE, PET.TYPE_ID,
                        row(OWNER.ID, OWNER.FIRST_NAME, OWNER.LAST_NAME, OWNER.ADDRESS, OWNER.TELEPHONE,
                                row(CITY.ID, CITY.NAME).mapping(City::new)).mapping(Owner::new))
                .from(PET)
                .join(OWNER).on(PET.OWNER_ID.eq(OWNER.ID))
                .join(CITY).on(OWNER.CITY_ID.eq(CITY.ID))
                .orderBy(PET.ID)
                .seek(cursor)
                .limit(Dataset.PAGE_SIZE)
                .fetch(Records.mapping(Pet::new));
    }

    @Benchmark
    public List<PetRow> dynamic() {
        Params.DynamicFilter filter = params.nextDynamicFilter();
        Condition condition = OWNER.CITY_ID.eq(filter.cityId());
        if (filter.byDate()) {
            condition = condition.and(PET.BIRTH_DATE.ge(filter.minBirthDate()));
        }
        if (filter.byType()) {
            condition = condition.and(PET.TYPE_ID.eq(filter.typeId()));
        }
        return ctx.select(PET.NAME, OWNER.LAST_NAME, CITY.NAME)
                .from(PET)
                .join(OWNER).on(PET.OWNER_ID.eq(OWNER.ID))
                .join(CITY).on(OWNER.CITY_ID.eq(CITY.ID))
                .where(condition)
                .fetch(Records.mapping(PetRow::new));
    }

    @Benchmark
    public Long multiStatement() {
        long petId = params.nextMultiPetId();
        String description = Dataset.visitDescription((int) petId);
        return ctx.transactionResult(transaction -> {
            DSLContext c = DSL.using(transaction);
            Long id = c.insertInto(VISIT, VISIT.PET_ID, VISIT.VISIT_DATE, VISIT.DESCRIPTION)
                    .values(petId, Dataset.visitDate((int) petId), description)
                    .returning(VISIT.ID)
                    .fetchOne()
                    .getId();
            c.update(VISIT)
                    .set(VISIT.DESCRIPTION, description + " (rechecked)")
                    .where(VISIT.ID.eq(id))
                    .execute();
            return id;
        });
    }

    @Benchmark
    public List<Visit> graphInsert() {
        List<Params.GraphInsert> graphs = new ArrayList<>(Dataset.GRAPH_SIZE);
        for (int i = 0; i < Dataset.GRAPH_SIZE; i++) {
            graphs.add(params.nextGraphInsert());
        }
        return ctx.transactionResult(transaction -> {
            DSLContext c = DSL.using(transaction);
            var ownerInsert = c.insertInto(OWNER,
                    OWNER.FIRST_NAME, OWNER.LAST_NAME, OWNER.ADDRESS, OWNER.TELEPHONE, OWNER.CITY_ID);
            for (Params.GraphInsert graph : graphs) {
                ownerInsert = ownerInsert.values(Dataset.firstName(graph.seed()), Dataset.lastName(graph.seed()),
                        Dataset.address(graph.seed()), Dataset.telephone(graph.seed()), graph.cityId());
            }
            List<Long> ownerIds = ownerInsert.returning(OWNER.ID).fetch().map(record -> record.get(OWNER.ID));
            var petInsert = c.insertInto(PET, PET.NAME, PET.BIRTH_DATE, PET.TYPE_ID, PET.OWNER_ID);
            for (int i = 0; i < graphs.size(); i++) {
                Params.GraphInsert graph = graphs.get(i);
                petInsert = petInsert.values(Dataset.petName(graph.seed()), Dataset.petBirthDate(graph.seed()),
                        graph.typeId(), ownerIds.get(i));
            }
            List<Long> petIds = petInsert.returning(PET.ID).fetch().map(record -> record.get(PET.ID));
            var visitInsert = c.insertInto(VISIT, VISIT.PET_ID, VISIT.VISIT_DATE, VISIT.DESCRIPTION);
            for (int i = 0; i < graphs.size(); i++) {
                Params.GraphInsert graph = graphs.get(i);
                visitInsert = visitInsert.values(petIds.get(i), Dataset.visitDate(graph.seed()),
                        Dataset.visitDescription(graph.seed()));
            }
            List<Long> visitIds = visitInsert.returning(VISIT.ID).fetch().map(record -> record.get(VISIT.ID));
            List<Visit> visits = new ArrayList<>(graphs.size());
            for (int i = 0; i < graphs.size(); i++) {
                int seed = graphs.get(i).seed();
                visits.add(new Visit(visitIds.get(i), petIds.get(i),
                        Dataset.visitDate(seed), Dataset.visitDescription(seed)));
            }
            return visits;
        });
    }
}
