package st.orm.benchmarks.storm

import st.orm.DbTable
import st.orm.DynamicUpdate
import st.orm.UpdateMode
import st.orm.Entity
import st.orm.FK
import st.orm.PK
import st.orm.Ref
import java.time.LocalDate

data class City(
    @PK val id: Long = 0,
    val name: String,
) : Entity<Long>

data class Owner(
    @PK val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val address: String,
    val telephone: String,
    @FK val city: City,
) : Entity<Long>

/**
 * Owner shape for the update workload: the city is a lazy [Ref], mirroring the lazy associations the other
 * libraries declare on their entities. The eager [Owner] aggregate is exercised by the read workloads.
 */
@DbTable("owner")
@DynamicUpdate(UpdateMode.FIELD)
data class OwnerCityRef(
    @PK val id: Long = 0,
    val firstName: String,
    val lastName: String,
    val address: String,
    val telephone: String,
    @FK val city: Ref<City>,
) : Entity<Long>

data class PetType(
    @PK val id: Long = 0,
    val name: String,
) : Entity<Long>

data class Pet(
    @PK val id: Long = 0,
    val name: String,
    val birthDate: LocalDate,
    @FK val type: Ref<PetType>,
    @FK val owner: Owner,
) : Entity<Long>

data class Visit(
    @PK val id: Long = 0,
    @FK val pet: Ref<Pet>,
    val visitDate: LocalDate,
    val description: String,
) : Entity<Long>

data class PetRow(val petName: String, val ownerLastName: String, val cityName: String)

data class OwnerWithPets(val owner: Owner, val pets: List<Pet>)
