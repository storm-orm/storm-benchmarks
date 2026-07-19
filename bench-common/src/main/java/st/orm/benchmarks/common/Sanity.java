package st.orm.benchmarks.common;

import java.util.List;

/**
 * Result-shape assertions run once per trial before measurement starts. A
 * workload that returns the wrong number of rows fails the benchmark instead
 * of silently producing numbers for different work.
 */
public final class Sanity {

    public static void verify(Object singleRow, List<?> joined10, List<?> joined100, List<?> joined1000,
                              List<?> projected, List<?> inserted, Object updated, List<?> graph,
                              List<?> keyset, List<?> dynamic, Object multiStatement, List<?> graphInsert) {
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
        require(keyset.size() == Dataset.PAGE_SIZE,
                "keyset returned " + keyset.size() + " rows, expected " + Dataset.PAGE_SIZE);
        // The sanity call always sees dynamic-filter combination 0 (city only), so its count is deterministic.
        require(dynamic.size() == Dataset.PETS_PER_CITY,
                "dynamic returned " + dynamic.size() + " rows, expected " + Dataset.PETS_PER_CITY);
        require(multiStatement != null, "multiStatement returned no result");
        // One returned visit id per graph; the foreign keys make each visit unwritable unless its new pet, and
        // that pet's new owner, were inserted first, so a full-size list proves every graph committed.
        require(graphInsert.size() == Dataset.GRAPH_SIZE,
                "graphInsert returned " + graphInsert.size() + " visit ids, expected " + Dataset.GRAPH_SIZE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Sanity check failed: " + message);
        }
    }

    private Sanity() {
    }
}
