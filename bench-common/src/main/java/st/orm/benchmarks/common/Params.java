package st.orm.benchmarks.common;

/**
 * Deterministic parameter cycling for benchmark invocations. Every library
 * walks the same id sequences, so all implementations touch identical rows.
 * Benchmarks run single-threaded; no synchronization needed.
 */
public final class Params {

    private long visitCounter;
    private long cityCounter;
    private long batchCounter;
    private long windowCounter;
    private long ownerCounter;

    public long nextVisitId() {
        return (visitCounter++ % Dataset.VISITS) + 1;
    }

    public long nextCityId() {
        return (cityCounter++ % Dataset.CITIES) + 1;
    }

    /** Base index for the next batch of {@link Dataset#BATCH_SIZE} visit rows. */
    public int nextBatchBase() {
        return (int) ((batchCounter++ * Dataset.BATCH_SIZE) % Dataset.PETS);
    }

    public static long petIdForBatch(int batchBase, int offset) {
        return ((batchBase + offset) % Dataset.PETS) + 1;
    }

    /**
     * Base pet id for the next window of {@code rows} consecutive pets; the window always fits within the pet
     * table, so a query for {@code id > base AND id <= base + rows} returns exactly {@code rows} pets.
     */
    public long nextWindowBase(int rows) {
        return (windowCounter++ * rows) % (Dataset.PETS - rows + 1);
    }

    public long nextOwnerId() {
        return (ownerCounter++ % Dataset.OWNERS) + 1;
    }

    /**
     * Derives a telephone value from the current one by toggling the prefix, guaranteeing that every update is a
     * real change. Deriving from the read value keeps this correct across JMH forks; a generated sequence would
     * repeat in the next fork and turn updates into no-ops for libraries with change detection.
     */
    public static String toggleTelephone(String current) {
        return (current.startsWith("555") ? "666" : "555") + current.substring(3);
    }
}
