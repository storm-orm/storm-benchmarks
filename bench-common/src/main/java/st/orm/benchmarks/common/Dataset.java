package st.orm.benchmarks.common;

import java.time.LocalDate;

/**
 * Deterministic dataset layout shared by every benchmark implementation.
 *
 * <p>The data is generated from the row index alone, so every library runs
 * against byte-identical data regardless of seeding order:
 * <ul>
 *   <li>{@code owner.city_id = (ownerIndex % CITIES) + 1} — 50 owners per city</li>
 *   <li>{@code pet.owner_id = (petIndex % OWNERS) + 1} — 2 pets per owner, 100 pets per city</li>
 *   <li>{@code pet.type_id = (petIndex % PET_TYPES) + 1}</li>
 *   <li>{@code visit.pet_id = (visitIndex % PETS) + 1} — 3 visits per pet</li>
 * </ul>
 */
public final class Dataset {

    public static final int CITIES = 100;
    public static final int PET_TYPES = 6;
    public static final int OWNERS = 5_000;
    public static final int PETS = 10_000;
    public static final int VISITS = 30_000;

    public static final int OWNERS_PER_CITY = OWNERS / CITIES;
    public static final int PETS_PER_OWNER = PETS / OWNERS;
    public static final int PETS_PER_CITY = PETS / CITIES;

    /** Rows written per invocation of the batch-insert workload. */
    public static final int BATCH_SIZE = 100;

    /** visit_seq starts here; every benchmark-inserted visit has an id at or above this floor. */
    public static final long INSERTED_ID_FLOOR = 1_000_000L;

    public static final LocalDate PET_EPOCH = LocalDate.of(2015, 1, 1);
    public static final LocalDate VISIT_EPOCH = LocalDate.of(2020, 1, 1);

    public static String cityName(int index) {
        return "City %03d".formatted(index);
    }

    public static String petTypeName(int index) {
        return switch (index) {
            case 1 -> "cat";
            case 2 -> "dog";
            case 3 -> "lizard";
            case 4 -> "snake";
            case 5 -> "bird";
            default -> "hamster";
        };
    }

    public static String firstName(int index) {
        return "First%05d".formatted(index);
    }

    public static String lastName(int index) {
        return "Last%05d".formatted(index);
    }

    public static String address(int index) {
        return "%d Main Street".formatted(index);
    }

    public static String telephone(int index) {
        return "555%07d".formatted(index);
    }

    public static String petName(int index) {
        return "Pet%05d".formatted(index);
    }

    public static LocalDate petBirthDate(int index) {
        return PET_EPOCH.plusDays(index % 3650);
    }

    public static LocalDate visitDate(int index) {
        return VISIT_EPOCH.plusDays(index % 2000);
    }

    public static String visitDescription(int index) {
        return "Checkup %d".formatted(index);
    }

    private Dataset() {
    }
}
