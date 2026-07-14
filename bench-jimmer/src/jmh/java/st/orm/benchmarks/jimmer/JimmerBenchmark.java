package st.orm.benchmarks.jimmer;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.BatchSaveResult;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.ast.tuple.Tuple3;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Jimmer with its object-fetcher model: associations load through secondary
 * batched queries (its idiomatic strategy), predicates join through the DSL.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
public class JimmerBenchmark {

    DataSource dataSource;
    JSqlClient sqlClient;
    Params params;

    @Setup(Level.Trial)
    public void setUp() throws SQLException {
        dataSource = BenchDatabase.dataSource();
        sqlClient = JSqlClient.newBuilder()
                .setConnectionManager(org.babyfish.jimmer.sql.runtime.ConnectionManager.simpleConnectionManager(dataSource))
                .setDialect(new PostgresDialect())
                .build();
        params = new Params();
        Sanity.verify(singleRowById(), joinWithMapping10(), joinWithMapping100(), joinWithMapping1000(), projection(),
                batchInsert(), updateById(), objectGraph());
        BenchDatabase.resetInsertedVisits(dataSource);
    }

    @TearDown(Level.Iteration)
    public void resetInsertedRows() {
        BenchDatabase.resetInsertedVisits(dataSource);
    }

    @Benchmark
    public Visit singleRowById() {
        long id = params.nextVisitId();
        return sqlClient.getEntities().findById(Visit.class, id);
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
        PetTable table = PetTable.$;
        return sqlClient.createQuery(table)
                .where(table.id().gt(base))
                .where(table.id().le(base + rows))
                .select(table.fetch(
                        PetFetcher.$
                                .allScalarFields()
                                .owner(OwnerFetcher.$
                                        .allScalarFields()
                                        .city(CityFetcher.$.allScalarFields()))))
                .execute();
    }

    @Benchmark
    public Owner updateById() throws SQLException {
        long id = params.nextOwnerId();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                OwnerTable table = OwnerTable.$;
                Owner owner = sqlClient.createQuery(table)
                        .where(table.id().eq(id))
                        .select(table)
                        .execute(connection)
                        .getFirst();
                Owner updated = OwnerDraft.$.produce(owner,
                        draft -> draft.setTelephone(Params.toggleTelephone(owner.telephone())));
                sqlClient.getEntities()
                        .saveCommand(updated)
                        .setMode(SaveMode.UPDATE_ONLY)
                        .execute(connection);
                connection.commit();
                return updated;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Benchmark
    public List<Tuple3<String, String, String>> projection() {
        long cityId = params.nextCityId();
        PetTable table = PetTable.$;
        return sqlClient.createQuery(table)
                .where(table.owner().city().id().eq(cityId))
                .select(table.name(), table.owner().lastName(), table.owner().city().name())
                .execute();
    }

    @Benchmark
    public List<Long> batchInsert() throws SQLException {
        int base = params.nextBatchBase();
        List<Visit> visits = new ArrayList<>(Dataset.BATCH_SIZE);
        for (int i = 0; i < Dataset.BATCH_SIZE; i++) {
            int index = base + i;
            long petId = Params.petIdForBatch(base, i);
            visits.add(VisitDraft.$.produce(draft -> {
                draft.setPet(ImmutableObjects.makeIdOnly(Pet.class, petId));
                draft.setVisitDate(Dataset.visitDate(index));
                draft.setDescription(Dataset.visitDescription(index));
            }));
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BatchSaveResult<Visit> result = sqlClient.getEntities()
                        .saveEntitiesCommand(visits)
                        .setMode(SaveMode.INSERT_ONLY)
                        .execute(connection);
                connection.commit();
                List<Long> ids = new ArrayList<>(Dataset.BATCH_SIZE);
                for (var item : result.getItems()) {
                    ids.add(item.getModifiedEntity().id());
                }
                return ids;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Benchmark
    public List<Owner> objectGraph() {
        long cityId = params.nextCityId();
        OwnerTable table = OwnerTable.$;
        return sqlClient.createQuery(table)
                .where(table.city().id().eq(cityId))
                .orderBy(table.id().asc())
                .select(table.fetch(
                        OwnerFetcher.$
                                .allScalarFields()
                                .city(CityFetcher.$.allScalarFields())
                                .pets(PetFetcher.$.allScalarFields())))
                .execute();
    }
}
