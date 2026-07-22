package st.orm.benchmarks.ktorm

import org.ktorm.entity.Entity
import org.ktorm.schema.Table
import org.ktorm.schema.date
import org.ktorm.schema.long
import org.ktorm.schema.varchar
import java.time.LocalDate

/**
 * Ktorm entities are interfaces bound to tables via column bindings. Foreign
 * keys declared with `references(...)` are followed automatically when a table
 * is queried through the entity sequence API, producing the join and the nested
 * object graph in one statement. The `visit` foreign key to `pet` stays a plain
 * id column, matching the reference-only shape the single-row workload measures.
 */

interface City : Entity<City> {
    companion object : Entity.Factory<City>()
    val id: Long
    var name: String
}

interface Owner : Entity<Owner> {
    companion object : Entity.Factory<Owner>()
    val id: Long
    var firstName: String
    var lastName: String
    var address: String
    var telephone: String
    var city: City
}

interface Pet : Entity<Pet> {
    companion object : Entity.Factory<Pet>()
    val id: Long
    var name: String
    var birthDate: LocalDate
    var typeId: Long
    var owner: Owner
}

interface Visit : Entity<Visit> {
    companion object : Entity.Factory<Visit>()
    val id: Long
    var petId: Long
    var visitDate: LocalDate
    var description: String
}

object Cities : Table<City>("city") {
    val id = long("id").primaryKey().bindTo { it.id }
    val name = varchar("name").bindTo { it.name }
}

object Owners : Table<Owner>("owner") {
    val id = long("id").primaryKey().bindTo { it.id }
    val firstName = varchar("first_name").bindTo { it.firstName }
    val lastName = varchar("last_name").bindTo { it.lastName }
    val address = varchar("address").bindTo { it.address }
    val telephone = varchar("telephone").bindTo { it.telephone }
    val cityId = long("city_id").references(Cities) { it.city }
}

object PetTypes : Table<Nothing>("pet_type") {
    val id = long("id").primaryKey()
    val name = varchar("name")
}

object Pets : Table<Pet>("pet") {
    val id = long("id").primaryKey().bindTo { it.id }
    val name = varchar("name").bindTo { it.name }
    val birthDate = date("birth_date").bindTo { it.birthDate }
    val typeId = long("type_id").bindTo { it.typeId }
    val ownerId = long("owner_id").references(Owners) { it.owner }
}

object Visits : Table<Visit>("visit") {
    val id = long("id").primaryKey().bindTo { it.id }
    val petId = long("pet_id").bindTo { it.petId }
    val visitDate = date("visit_date").bindTo { it.visitDate }
    val description = varchar("description").bindTo { it.description }
}

data class PetRow(val petName: String, val ownerLastName: String, val cityName: String)

data class OwnerWithPets(val owner: Owner, val pets: List<Pet>)
