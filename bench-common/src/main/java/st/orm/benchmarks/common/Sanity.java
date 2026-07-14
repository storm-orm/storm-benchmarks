package st.orm.benchmarks.common;

import java.util.List;

/**
 * Result-shape assertions run once per trial before measurement starts. A
 * workload that returns the wrong number of rows fails the benchmark instead
 * of silently producing numbers for different work.
 */
public final class Sanity {

    public static void verify(Object singleRow, List<?> joined10, List<?> joined100, List<?> joined1000,
                              List<?> projected, List<?> inserted, Object updated, List<?> graph) {
        require(singleRow != null, "singleRowById returned no row");
        require(joined10.size() == 10, "joinWithMapping10 returned " + joined10.size() + " rows, expected 10");
        require(joined100.size() == 100, "joinWithMapping100 returned " + joined100.size() + " rows, expected 100");
        require(joined1000.size() == 1000, "joinWithMapping1000 returned " + joined1000.size() + " rows, expected 1000");
        require(projected.size() == Dataset.PETS_PER_CITY,
                "projection returned " + projected.size() + " rows, expected " + Dataset.PETS_PER_CITY);
        require(inserted.size() == Dataset.BATCH_SIZE,
                "batchInsert returned " + inserted.size() + " ids, expected " + Dataset.BATCH_SIZE);
        require(updated != null, "updateById returned no result");
        require(graph.size() == Dataset.OWNERS_PER_CITY,
                "objectGraph returned " + graph.size() + " owners, expected " + Dataset.OWNERS_PER_CITY);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Sanity check failed: " + message);
        }
    }

    private Sanity() {
    }
}
