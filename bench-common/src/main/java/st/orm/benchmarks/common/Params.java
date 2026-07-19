package st.orm.benchmarks.common;

import java.time.LocalDate;

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
    private long keysetCounter;
    private long dynamicCounter;
    private long multiCounter;
    private long graphCounter;

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
     * Cursor for the next keyset (scroll) page: a pet id such that the {@code PAGE_SIZE} ids strictly greater than
     * it all exist, so a query for {@code id > cursor ORDER BY id LIMIT PAGE_SIZE} returns exactly a full page.
     */
    public long nextKeysetCursor() {
        return (keysetCounter++ * Dataset.PAGE_SIZE) % (Dataset.PETS - Dataset.PAGE_SIZE);
    }

    /**
     * Deterministic filter set for the next dynamic-query invocation. The city predicate is always present
     * (bounding the result to one city's pets); the date and type predicates cycle on and off. Combination 0
     * (city only) is what the sanity check sees, so its row count is stable at {@link Dataset#PETS_PER_CITY}.
     */
    public DynamicFilter nextDynamicFilter() {
        long n = dynamicCounter++;
        long cityId = (n % Dataset.CITIES) + 1;
        int combo = (int) (n % 4);
        boolean byDate = combo == 1 || combo == 3;
        boolean byType = combo == 2 || combo == 3;
        long typeId = (n % Dataset.PET_TYPES) + 1;
        return new DynamicFilter(cityId, byDate, byType, Dataset.DYNAMIC_MIN_BIRTH_DATE, typeId);
    }

    /** Pet the next multi-statement unit of work attaches its new visit to. */
    public long nextMultiPetId() {
        return (multiCounter++ % Dataset.PETS) + 1;
    }

    /**
     * Parameters for the next graph-insert invocation: an existing city and pet type to attach the new owner and
     * pet to, plus a data seed for the generated field values.
     */
    public GraphInsert nextGraphInsert() {
        long n = graphCounter++;
        long cityId = (n % Dataset.CITIES) + 1;
        long typeId = (n % Dataset.PET_TYPES) + 1;
        int seed = (int) (n % Dataset.OWNERS) + 1;
        return new GraphInsert(cityId, typeId, seed);
    }

    /**
     * Optional predicate set for the dynamic-query workload. The city equality is always applied; {@code byDate}
     * toggles a {@code birth_date >= minBirthDate} range and {@code byType} a {@code type_id = typeId} equality.
     */
    public record DynamicFilter(long cityId, boolean byDate, boolean byType, LocalDate minBirthDate, long typeId) {
    }

    /**
     * Parameters for the graph-insert workload: the existing {@code cityId} the new owner belongs to, the existing
     * {@code typeId} the new pet is, and a {@code seed} that feeds the deterministic field-value generators.
     */
    public record GraphInsert(long cityId, long typeId, int seed) {
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
