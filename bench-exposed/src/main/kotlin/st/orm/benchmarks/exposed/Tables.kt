package st.orm.benchmarks.exposed

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate

object Cities : Table("city") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    override val primaryKey = PrimaryKey(id)
}

object Owners : Table("owner") {
    val id = long("id").autoIncrement()
    val firstName = varchar("first_name", 50)
    val lastName = varchar("last_name", 50)
    val address = varchar("address", 120)
    val telephone = varchar("telephone", 20)
    val cityId = long("city_id").references(Cities.id)
    override val primaryKey = PrimaryKey(id)
}

object PetTypes : Table("pet_type") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 50)
    override val primaryKey = PrimaryKey(id)
}

object Pets : Table("pet") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 50)
    val birthDate = date("birth_date")
    val typeId = long("type_id").references(PetTypes.id)
    val ownerId = long("owner_id").references(Owners.id)
    override val primaryKey = PrimaryKey(id)
}

object Visits : Table("visit") {
    val id = long("id").autoIncrement()
    val petId = long("pet_id").references(Pets.id)
    val visitDate = date("visit_date")
    val description = varchar("description", 200)
    override val primaryKey = PrimaryKey(id)
}

data class City(val id: Long, val name: String)

data class Owner(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val address: String,
    val telephone: String,
    val city: City,
)

data class Pet(
    val id: Long,
    val name: String,
    val birthDate: LocalDate,
    val typeId: Long,
    val owner: Owner,
)

data class Visit(
    val id: Long,
    val petId: Long,
    val visitDate: LocalDate,
    val description: String,
)

data class PetRow(val petName: String, val ownerLastName: String, val cityName: String)

data class OwnerWithPets(val owner: Owner, val pets: List<Pet>)
