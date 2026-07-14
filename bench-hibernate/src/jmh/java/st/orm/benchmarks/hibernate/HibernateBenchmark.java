package st.orm.benchmarks.hibernate;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
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
import st.orm.benchmarks.hibernate.Entities.Owner;
import st.orm.benchmarks.hibernate.Entities.Pet;
import st.orm.benchmarks.hibernate.Entities.PetRow;
import st.orm.benchmarks.hibernate.Entities.Visit;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Hibernate ORM with its recommended high-performance settings: lazy
 * associations, explicit join fetch for graphs, sequence-backed ids with a
 * pooled optimizer, and JDBC statement batching for inserts.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
public class HibernateBenchmark {

    DataSource dataSource;
    SessionFactory sessionFactory;
    Params params;

    @Setup(Level.Trial)
    public void setUp() {
        dataSource = BenchDatabase.dataSource();
        var registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource)
                .applySetting(AvailableSettings.STATEMENT_BATCH_SIZE, 50)
                .applySetting(AvailableSettings.ORDER_INSERTS, true)
                .applySetting(AvailableSettings.SHOW_SQL, false)
                .build();
        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(Entities.City.class)
                .addAnnotatedClass(Entities.Owner.class)
                .addAnnotatedClass(Entities.PetType.class)
                .addAnnotatedClass(Entities.Pet.class)
                .addAnnotatedClass(Entities.Visit.class)
                .buildMetadata()
                .buildSessionFactory();
        params = new Params();
        Sanity.verify(singleRowById(), joinWithMapping10(), joinWithMapping100(), joinWithMapping1000(), projection(),
                batchInsert(), updateById(), objectGraph());
        BenchDatabase.resetInsertedVisits(dataSource);
    }

    @TearDown(Level.Iteration)
    public void resetInsertedRows() {
        BenchDatabase.resetInsertedVisits(dataSource);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        sessionFactory.close();
    }

    @Benchmark
    public Visit singleRowById() {
        long id = params.nextVisitId();
        return sessionFactory.fromSession(session -> session.find(Visit.class, id));
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
        return sessionFactory.fromSession(session -> session
                .createSelectionQuery(
                        "from Pet p join fetch p.owner o join fetch o.city where p.id > :base and p.id <= :top",
                        Pet.class)
                .setParameter("base", base)
                .setParameter("top", base + rows)
                .getResultList());
    }

    @Benchmark
    public Owner updateById() {
        long id = params.nextOwnerId();
        // Dirty checking: the flush at commit detects the change and issues the UPDATE.
        return sessionFactory.fromTransaction(session -> {
            Owner owner = session.find(Owner.class, id);
            owner.setTelephone(Params.toggleTelephone(owner.getTelephone()));
            return owner;
        });
    }

    @Benchmark
    public List<PetRow> projection() {
        long cityId = params.nextCityId();
        return sessionFactory.fromSession(session -> session
                .createSelectionQuery(
                        "select p.name, o.lastName, c.name from Pet p join p.owner o join o.city c where c.id = :cityId",
                        PetRow.class)
                .setParameter("cityId", cityId)
                .getResultList());
    }

    @Benchmark
    public List<Long> batchInsert() {
        int base = params.nextBatchBase();
        return sessionFactory.fromTransaction(session -> {
            List<Visit> visits = new ArrayList<>(Dataset.BATCH_SIZE);
            for (int i = 0; i < Dataset.BATCH_SIZE; i++) {
                Pet pet = session.getReference(Pet.class, Params.petIdForBatch(base, i));
                Visit visit = new Visit(pet, Dataset.visitDate(base + i), Dataset.visitDescription(base + i));
                session.persist(visit);
                visits.add(visit);
            }
            session.flush();
            List<Long> ids = new ArrayList<>(visits.size());
            for (Visit visit : visits) {
                ids.add(visit.getId());
            }
            return ids;
        });
    }

    @Benchmark
    public List<Owner> objectGraph() {
        long cityId = params.nextCityId();
        return sessionFactory.fromSession(session -> session
                .createSelectionQuery(
                        "select distinct o from Owner o join fetch o.pets join fetch o.city where o.city.id = :cityId",
                        Owner.class)
                .setParameter("cityId", cityId)
                .getResultList());
    }
}
