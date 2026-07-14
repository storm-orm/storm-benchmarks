package st.orm.benchmarks.common.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Canonical result shapes for implementations without their own mapping layer
 * (plain JDBC, jOOQ). The mapped libraries (Storm, Hibernate, Exposed, Jimmer)
 * return their own entity types with the same materialized fields.
 */
public final class Models {

    public record City(long id, String name) {
    }

    public record Owner(long id, String firstName, String lastName, String address, String telephone, City city) {
    }

    public record Pet(long id, String name, LocalDate birthDate, long typeId, Owner owner) {
    }

    public record Visit(long id, long petId, LocalDate visitDate, String description) {
    }

    /** Row shape of the projection workload. */
    public record PetRow(String petName, String ownerLastName, String cityName) {
    }

    /** Result shape of the object-graph workload. */
    public record OwnerWithPets(Owner owner, List<Pet> pets) {
    }

    private Models() {
    }
}
