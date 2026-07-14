package st.orm.benchmarks.exposeddao

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate

object Cities : LongIdTable("city") {
    val name = varchar("name", 100)
}

object Owners : LongIdTable("owner") {
    val firstName = varchar("first_name", 50)
    val lastName = varchar("last_name", 50)
    val address = varchar("address", 120)
    val telephone = varchar("telephone", 20)
    val cityId = reference("city_id", Cities)
}

object PetTypes : LongIdTable("pet_type") {
    val name = varchar("name", 50)
}

object Pets : LongIdTable("pet") {
    val name = varchar("name", 50)
    val birthDate = date("birth_date")
    val typeId = reference("type_id", PetTypes)
    val ownerId = reference("owner_id", Owners)
}

object Visits : LongIdTable("visit") {
    val petId = reference("pet_id", Pets)
    val visitDate = date("visit_date")
    val description = varchar("description", 200)
}

class CityDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CityDao>(Cities)

    var name by Cities.name
}

class OwnerDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<OwnerDao>(Owners)

    var firstName by Owners.firstName
    var lastName by Owners.lastName
    var address by Owners.address
    var telephone by Owners.telephone
    var city by CityDao referencedOn Owners.cityId
    val pets by PetDao referrersOn Pets.ownerId
}

class PetDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PetDao>(Pets)

    var name by Pets.name
    var birthDate by Pets.birthDate
    var typeId by Pets.typeId
    var owner by OwnerDao referencedOn Pets.ownerId
}

class VisitDao(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<VisitDao>(Visits)

    var petId by Visits.petId
    var visitDate by Visits.visitDate
    var description by Visits.description
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
