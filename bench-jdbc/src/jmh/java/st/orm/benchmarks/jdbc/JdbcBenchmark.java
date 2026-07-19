package st.orm.benchmarks.jdbc;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Hand-written JDBC baseline: prepared statements, manual mapping, explicit
 * transactions. This is the floor every mapping library adds overhead on top of.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@Threads(1)
@State(Scope.Benchmark)
public class JdbcBenchmark {

    DataSource dataSource;
    Params params;

    @Setup(Level.Trial)
    public void setUp() throws SQLException {
        dataSource = BenchDatabase.dataSource();
        params = new Params();
        Sanity.verify(singleRowById(), joinWithMapping10(), joinWithMapping100(), joinWithMapping1000(), projection(),
                batchInsert(), updateById(), objectGraph(), keyset(), dynamic(), multiStatement(), graphInsert());
        BenchDatabase.resetInsertedRows(dataSource);
    }

    @TearDown(Level.Iteration)
    public void resetInsertedRows() {
        BenchDatabase.resetInsertedRows(dataSource);
    }

    /**
     * Round-trip floor: a bare SELECT 1 with no mapping. Quantifies the wire and driver cost every other
     * benchmark includes, so library overhead can be read as score minus baseline.
     */
    @Benchmark
    public int baseline() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    @Benchmark
    public Visit singleRowById() throws SQLException {
        long id = params.nextVisitId();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, pet_id, visit_date, description FROM visit WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("visit " + id + " not found");
                }
                return new Visit(
                        resultSet.getLong(1),
                        resultSet.getLong(2),
                        resultSet.getObject(3, LocalDate.class),
                        resultSet.getString(4));
            }
        }
    }

    @Benchmark
    public List<Pet> joinWithMapping10() throws SQLException {
        return joinWithMapping(10);
    }

    @Benchmark
    public List<Pet> joinWithMapping100() throws SQLException {
        return joinWithMapping(100);
    }

    @Benchmark
    public List<Pet> joinWithMapping1000() throws SQLException {
        return joinWithMapping(1000);
    }

    private List<Pet> joinWithMapping(int rows) throws SQLException {
        long base = params.nextWindowBase(rows);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id, p.name, p.birth_date, p.type_id,
                            o.id, o.first_name, o.last_name, o.address, o.telephone,
                            c.id, c.name
                     FROM pet p
                     JOIN owner o ON p.owner_id = o.id
                     JOIN city c ON o.city_id = c.id
                     WHERE p.id > ? AND p.id <= ?""")) {
            statement.setLong(1, base);
            statement.setLong(2, base + rows);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Pet> pets = new ArrayList<>(rows);
                while (resultSet.next()) {
                    pets.add(mapPet(resultSet));
                }
                return pets;
            }
        }
    }

    @Benchmark
    public Owner updateById() throws SQLException {
        long id = params.nextOwnerId();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Owner owner;
                String telephone;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, first_name, last_name, address, telephone FROM owner WHERE id = ?")) {
                    statement.setLong(1, id);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalStateException("owner " + id + " not found");
                        }
                        telephone = Params.toggleTelephone(resultSet.getString(5));
                        owner = new Owner(
                                resultSet.getLong(1),
                                resultSet.getString(2),
                                resultSet.getString(3),
                                resultSet.getString(4),
                                telephone,
                                null);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE owner SET telephone = ? WHERE id = ?")) {
                    statement.setString(1, telephone);
                    statement.setLong(2, id);
                    statement.executeUpdate();
                }
                connection.commit();
                return owner;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Benchmark
    public List<PetRow> projection() throws SQLException {
        long cityId = params.nextCityId();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.name, o.last_name, c.name
                     FROM pet p
                     JOIN owner o ON p.owner_id = o.id
                     JOIN city c ON o.city_id = c.id
                     WHERE o.city_id = ?""")) {
            statement.setLong(1, cityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PetRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new PetRow(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3)));
                }
                return rows;
            }
        }
    }

    @Benchmark
    public List<Long> batchInsert() throws SQLException {
        int base = params.nextBatchBase();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO visit (pet_id, visit_date, description) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < Dataset.BATCH_SIZE; i++) {
                    statement.setLong(1, Params.petIdForBatch(base, i));
                    statement.setObject(2, Dataset.visitDate(base + i));
                    statement.setString(3, Dataset.visitDescription(base + i));
                    statement.addBatch();
                }
                statement.executeBatch();
                List<Long> ids = new ArrayList<>(Dataset.BATCH_SIZE);
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    while (keys.next()) {
                        ids.add(keys.getLong(1));
                    }
                }
                connection.commit();
                return ids;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Benchmark
    public List<OwnerWithPets> objectGraph() throws SQLException {
        long cityId = params.nextCityId();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT o.id, o.first_name, o.last_name, o.address, o.telephone,
                            c.id, c.name,
                            p.id, p.name, p.birth_date, p.type_id
                     FROM owner o
                     JOIN city c ON o.city_id = c.id
                     JOIN pet p ON p.owner_id = o.id
                     WHERE o.city_id = ?
                     ORDER BY o.id""")) {
            statement.setLong(1, cityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<Long, Owner> owners = new LinkedHashMap<>();
                Map<Long, List<Pet>> pets = new LinkedHashMap<>();
                while (resultSet.next()) {
                    long ownerId = resultSet.getLong(1);
                    Owner owner = owners.get(ownerId);
                    if (owner == null) {
                        owner = new Owner(
                                ownerId,
                                resultSet.getString(2),
                                resultSet.getString(3),
                                resultSet.getString(4),
                                resultSet.getString(5),
                                new City(resultSet.getLong(6), resultSet.getString(7)));
                        owners.put(ownerId, owner);
                        pets.put(ownerId, new ArrayList<>());
                    }
                    pets.get(ownerId).add(new Pet(
                            resultSet.getLong(8),
                            resultSet.getString(9),
                            resultSet.getObject(10, LocalDate.class),
                            resultSet.getLong(11),
                            owner));
                }
                List<OwnerWithPets> result = new ArrayList<>(owners.size());
                owners.forEach((id, owner) -> result.add(new OwnerWithPets(owner, pets.get(id))));
                return result;
            }
        }
    }

    @Benchmark
    public List<Pet> keyset() throws SQLException {
        long cursor = params.nextKeysetCursor();
        // Page size is inlined as a literal, not bound: a literal LIMIT lets the planner pick an early-terminating
        // plan for the small page, where a bound LIMIT forces a slower generic plan. PAGE_SIZE is a trusted constant.
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id, p.name, p.birth_date, p.type_id,
                            o.id, o.first_name, o.last_name, o.address, o.telephone,
                            c.id, c.name
                     FROM pet p
                     JOIN owner o ON p.owner_id = o.id
                     JOIN city c ON o.city_id = c.id
                     WHERE p.id > ?
                     ORDER BY p.id
                     LIMIT %d""".formatted(Dataset.PAGE_SIZE))) {
            statement.setLong(1, cursor);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Pet> pets = new ArrayList<>(Dataset.PAGE_SIZE);
                while (resultSet.next()) {
                    pets.add(mapPet(resultSet));
                }
                return pets;
            }
        }
    }

    @Benchmark
    public List<PetRow> dynamic() throws SQLException {
        Params.DynamicFilter filter = params.nextDynamicFilter();
        StringBuilder sql = new StringBuilder("""
                SELECT p.name, o.last_name, c.name
                FROM pet p
                JOIN owner o ON p.owner_id = o.id
                JOIN city c ON o.city_id = c.id
                WHERE o.city_id = ?""");
        if (filter.byDate()) {
            sql.append(" AND p.birth_date >= ?");
        }
        if (filter.byType()) {
            sql.append(" AND p.type_id = ?");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            statement.setLong(index++, filter.cityId());
            if (filter.byDate()) {
                statement.setObject(index++, filter.minBirthDate());
            }
            if (filter.byType()) {
                statement.setLong(index, filter.typeId());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PetRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new PetRow(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3)));
                }
                return rows;
            }
        }
    }

    @Benchmark
    public Long multiStatement() throws SQLException {
        long petId = params.nextMultiPetId();
        String description = Dataset.visitDescription((int) petId);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO visit (pet_id, visit_date, description) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, petId);
                    insert.setObject(2, Dataset.visitDate((int) petId));
                    insert.setString(3, description);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE visit SET description = ? WHERE id = ?")) {
                    update.setString(1, description + " (rechecked)");
                    update.setLong(2, id);
                    update.executeUpdate();
                }
                connection.commit();
                return id;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Benchmark
    public List<Visit> graphInsert() throws SQLException {
        List<Params.GraphInsert> graphs = new ArrayList<>(Dataset.GRAPH_SIZE);
        for (int i = 0; i < Dataset.GRAPH_SIZE; i++) {
            graphs.add(params.nextGraphInsert());
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<Long> ownerIds = batchInsertReturningKeys(connection,
                        "INSERT INTO owner (first_name, last_name, address, telephone, city_id) VALUES (?, ?, ?, ?, ?)",
                        graphs, (statement, index) -> {
                            Params.GraphInsert graph = graphs.get(index);
                            statement.setString(1, Dataset.firstName(graph.seed()));
                            statement.setString(2, Dataset.lastName(graph.seed()));
                            statement.setString(3, Dataset.address(graph.seed()));
                            statement.setString(4, Dataset.telephone(graph.seed()));
                            statement.setLong(5, graph.cityId());
                        });
                List<Long> petIds = batchInsertReturningKeys(connection,
                        "INSERT INTO pet (name, birth_date, type_id, owner_id) VALUES (?, ?, ?, ?)",
                        graphs, (statement, index) -> {
                            Params.GraphInsert graph = graphs.get(index);
                            statement.setString(1, Dataset.petName(graph.seed()));
                            statement.setObject(2, Dataset.petBirthDate(graph.seed()));
                            statement.setLong(3, graph.typeId());
                            statement.setLong(4, ownerIds.get(index));
                        });
                List<Long> visitIds = batchInsertReturningKeys(connection,
                        "INSERT INTO visit (pet_id, visit_date, description) VALUES (?, ?, ?)",
                        graphs, (statement, index) -> {
                            Params.GraphInsert graph = graphs.get(index);
                            statement.setLong(1, petIds.get(index));
                            statement.setObject(2, Dataset.visitDate(graph.seed()));
                            statement.setString(3, Dataset.visitDescription(graph.seed()));
                        });
                connection.commit();
                List<Visit> visits = new ArrayList<>(graphs.size());
                for (int i = 0; i < graphs.size(); i++) {
                    int seed = graphs.get(i).seed();
                    visits.add(new Visit(visitIds.get(i), petIds.get(i),
                            Dataset.visitDate(seed), Dataset.visitDescription(seed)));
                }
                return visits;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private interface RowBinder {
        void bind(PreparedStatement statement, int index) throws SQLException;
    }

    private static List<Long> batchInsertReturningKeys(Connection connection, String sql,
            List<?> items, RowBinder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < items.size(); i++) {
                binder.bind(statement, i);
                statement.addBatch();
            }
            statement.executeBatch();
            List<Long> ids = new ArrayList<>(items.size());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                while (keys.next()) {
                    ids.add(keys.getLong(1));
                }
            }
            return ids;
        }
    }

    private static Pet mapPet(ResultSet resultSet) throws SQLException {
        City city = new City(resultSet.getLong(10), resultSet.getString(11));
        Owner owner = new Owner(
                resultSet.getLong(5),
                resultSet.getString(6),
                resultSet.getString(7),
                resultSet.getString(8),
                resultSet.getString(9),
                city);
        return new Pet(
                resultSet.getLong(1),
                resultSet.getString(2),
                resultSet.getObject(3, LocalDate.class),
                resultSet.getLong(4),
                owner);
    }
}
