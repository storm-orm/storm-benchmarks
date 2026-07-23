package st.orm.benchmarks.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static st.orm.benchmarks.common.Dataset.CITIES;
import static st.orm.benchmarks.common.Dataset.OWNERS;
import static st.orm.benchmarks.common.Dataset.PETS;
import static st.orm.benchmarks.common.Dataset.PET_TYPES;
import static st.orm.benchmarks.common.Dataset.VISITS;

/**
 * Provides the PostgreSQL instance and connection pool shared by all benchmark
 * implementations in a JVM fork.
 *
 * <p>The database is resolved in this order:
 * <ol>
 *   <li>{@code -Dbench.jdbc.url} (with {@code -Dbench.jdbc.user} / {@code -Dbench.jdbc.password},
 *       both defaulting to {@code bench}) — used by the run scripts and CI, which start a single
 *       tuned container for the whole suite;</li>
 *   <li>otherwise a Testcontainers PostgreSQL instance is started on demand, so any single
 *       {@code :bench-*:jmh} task is self-contained.</li>
 * </ol>
 *
 * <p>Seeding is idempotent and deterministic; see {@link Dataset}.
 */
public final class BenchDatabase {

    private static final String IMAGE = System.getProperty("bench.postgres.image", "postgres:17-alpine");
    private static final int POOL_SIZE = Integer.getInteger("bench.pool.size", 10);

    private static HikariDataSource dataSource;

    @SuppressWarnings("resource")
    public static synchronized DataSource dataSource() {
        if (dataSource != null) {
            return dataSource;
        }
        String url = System.getProperty("bench.jdbc.url", System.getenv("BENCH_JDBC_URL"));
        String user = System.getProperty("bench.jdbc.user", "bench");
        String password = System.getProperty("bench.jdbc.password", "bench");
        if (url == null) {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("bench")
                    .withUsername("bench")
                    .withPassword("bench")
                    .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                    .withCommand(
                            "postgres",
                            "-c", "fsync=off",
                            "-c", "synchronous_commit=off",
                            "-c", "full_page_writes=off",
                            "-c", "shared_buffers=256MB",
                            "-c", "max_connections=50",
                            // Statistics discipline (mirrors scripts/run.sh): vacuumAnalyze() settles the
                            // tables and refreshes planner statistics at trial setup, and the unreachable
                            // threshold keeps autovacuum's automatic ANALYZE from flipping cached plans
                            // mid-trial. Autovacuum's vacuum stays on as a backstop for within-trial churn.
                            "-c", "autovacuum_analyze_threshold=2000000000",
                            // Sampled plan logging, so the plans actually used (including the prepared
                            // statement custom-vs-generic choice) can be read from the container log.
                            "-c", "shared_preload_libraries=auto_explain",
                            "-c", "auto_explain.log_min_duration=0",
                            "-c", "auto_explain.sample_rate=0.001");
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(POOL_SIZE);
        config.setMinimumIdle(POOL_SIZE);
        dataSource = new HikariDataSource(config);
        ensureSeeded(dataSource);
        return dataSource;
    }

    /** Creates the schema and seeds the deterministic dataset if not present yet. */
    public static void ensureSeeded(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            // Serialize concurrent seeding attempts (e.g., parallel Gradle tasks against one container).
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(902214)");
            }
            if (!tableExists(connection, "city")) {
                executeScript(connection);
            }
            if (isEmpty(connection, "city")) {
                seed(connection);
            }
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to seed benchmark database", e);
        }
    }

    /**
     * Removes every row written by the write workloads (batch-insert, multi-statement, and graph-insert),
     * restoring the seeded dataset; runs untimed between iterations. Inserted visits carry an id at or above the
     * insert floor, while graph-inserted owners and pets carry ids above the seeded maxima. Deletion runs
     * child-before-parent (visit, then pet, then owner) to respect the foreign keys.
     */
    public static void resetInsertedRows(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM visit WHERE id >= " + Dataset.INSERTED_ID_FLOOR);
            statement.executeUpdate("DELETE FROM pet WHERE id > " + Dataset.PETS);
            statement.executeUpdate("DELETE FROM owner WHERE id > " + Dataset.OWNERS);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reset inserted rows", e);
        }
    }

    /**
     * Settles the physical tables and refreshes planner statistics; runs untimed at trial setup so every
     * fork measures against the same starting state regardless of which workload ran before it. The vacuum
     * pays the cleanup debt left by a preceding write-heavy workload (dead tuples that would otherwise slow
     * the earliest forks of the next benchmark while autovacuum catches up), and the statistics refresh is
     * needed because automatic statistics collection is disabled on the benchmark container: the write
     * workloads churn enough rows that an autovacuum ANALYZE landing mid-trial can flip cached prepared
     * plans between the custom and generic regime, moving join-heavy workloads by 2x.
     */
    public static void vacuumAnalyze(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM ANALYZE");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to vacuum and analyze benchmark database", e);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT to_regclass(?)")) {
            statement.setString(1, "public." + table);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getObject(1) != null;
            }
        }
    }

    private static boolean isEmpty(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getLong(1) == 0;
        }
    }

    private static void executeScript(Connection connection) throws SQLException {
        String script;
        try (InputStream inputStream = BenchDatabase.class.getResourceAsStream("/schema.sql")) {
            if (inputStream == null) {
                throw new IllegalStateException("schema.sql not found on classpath");
            }
            script = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema.sql", e);
        }
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    private static void seed(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO city (id, name) VALUES (?, ?)")) {
            for (int i = 1; i <= CITIES; i++) {
                statement.setLong(1, i);
                statement.setString(2, Dataset.cityName(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pet_type (id, name) VALUES (?, ?)")) {
            for (int i = 1; i <= PET_TYPES; i++) {
                statement.setLong(1, i);
                statement.setString(2, Dataset.petTypeName(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO owner (id, first_name, last_name, address, telephone, city_id) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= OWNERS; i++) {
                statement.setLong(1, i);
                statement.setString(2, Dataset.firstName(i));
                statement.setString(3, Dataset.lastName(i));
                statement.setString(4, Dataset.address(i));
                statement.setString(5, Dataset.telephone(i));
                statement.setLong(6, (i % CITIES) + 1);
                statement.addBatch();
                if (i % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pet (id, name, birth_date, type_id, owner_id) VALUES (?, ?, ?, ?, ?)")) {
            for (int i = 1; i <= PETS; i++) {
                statement.setLong(1, i);
                statement.setString(2, Dataset.petName(i));
                statement.setObject(3, Dataset.petBirthDate(i));
                statement.setLong(4, (i % PET_TYPES) + 1);
                statement.setLong(5, (i % OWNERS) + 1);
                statement.addBatch();
                if (i % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO visit (id, pet_id, visit_date, description) VALUES (?, ?, ?, ?)")) {
            for (int i = 1; i <= VISITS; i++) {
                statement.setLong(1, i);
                statement.setLong(2, (i % PETS) + 1);
                statement.setObject(3, Dataset.visitDate(i));
                statement.setString(4, Dataset.visitDescription(i));
                statement.addBatch();
                if (i % 1000 == 0) {
                    statement.executeBatch();
                }
            }
            statement.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT setval(pg_get_serial_sequence('city', 'id'), " + CITIES + ")");
            statement.execute("SELECT setval(pg_get_serial_sequence('pet_type', 'id'), " + PET_TYPES + ")");
            statement.execute("SELECT setval(pg_get_serial_sequence('owner', 'id'), " + OWNERS + ")");
            statement.execute("SELECT setval(pg_get_serial_sequence('pet', 'id'), " + PETS + ")");
            statement.execute("ANALYZE");
        }
    }

    private BenchDatabase() {
    }
}
