package st.orm.benchmarks.hibernate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entities following Hibernate's own recommendations: lazy associations
 * everywhere and sequence generators with pooled optimizers on every table the
 * write workloads insert into, so JDBC batching stays enabled. Identity
 * columns would force each insert to execute immediately to obtain its key.
 */
public final class Entities {

    @Entity(name = "City")
    @Table(name = "city")
    public static class City {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "name")
        private String name;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @Entity(name = "Owner")
    @Table(name = "owner")
    @DynamicUpdate
    public static class Owner {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "owner_seq_gen")
        @SequenceGenerator(name = "owner_seq_gen", sequenceName = "owner_seq", allocationSize = 50)
        private Long id;

        @Column(name = "first_name")
        private String firstName;

        @Column(name = "last_name")
        private String lastName;

        @Column(name = "address")
        private String address;

        @Column(name = "telephone")
        private String telephone;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "city_id")
        private City city;

        @OneToMany(mappedBy = "owner", cascade = CascadeType.PERSIST)
        private List<Pet> pets = new ArrayList<>();

        protected Owner() {
        }

        public Owner(String firstName, String lastName, String address, String telephone, City city) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.address = address;
            this.telephone = telephone;
            this.city = city;
        }

        public Long getId() {
            return id;
        }

        public List<Pet> getPets() {
            return pets;
        }

        public String getTelephone() {
            return telephone;
        }

        public void setTelephone(String telephone) {
            this.telephone = telephone;
        }
    }

    @Entity(name = "PetType")
    @Table(name = "pet_type")
    public static class PetType {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "name")
        private String name;

        public Long getId() {
            return id;
        }
    }

    @Entity(name = "Pet")
    @Table(name = "pet")
    public static class Pet {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pet_seq_gen")
        @SequenceGenerator(name = "pet_seq_gen", sequenceName = "pet_seq", allocationSize = 50)
        private Long id;

        @Column(name = "name")
        private String name;

        @Column(name = "birth_date")
        private LocalDate birthDate;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "type_id")
        private PetType type;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "owner_id")
        private Owner owner;

        @OneToMany(mappedBy = "pet", cascade = CascadeType.PERSIST)
        private List<Visit> visits = new ArrayList<>();

        protected Pet() {
        }

        public Pet(String name, LocalDate birthDate, PetType type, Owner owner) {
            this.name = name;
            this.birthDate = birthDate;
            this.type = type;
            this.owner = owner;
        }

        public Long getId() {
            return id;
        }

        public List<Visit> getVisits() {
            return visits;
        }
    }

    @Entity(name = "Visit")
    @Table(name = "visit")
    public static class Visit {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "visit_seq_gen")
        @SequenceGenerator(name = "visit_seq_gen", sequenceName = "visit_seq", allocationSize = 50)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "pet_id")
        private Pet pet;

        @Column(name = "visit_date")
        private LocalDate visitDate;

        @Column(name = "description")
        private String description;

        protected Visit() {
        }

        public Visit(Pet pet, LocalDate visitDate, String description) {
            this.pet = pet;
            this.visitDate = visitDate;
            this.description = description;
        }

        public Long getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /** Row shape of the projection workload; instantiated by Hibernate from the query select list. */
    public record PetRow(String petName, String ownerLastName, String cityName) {
    }

    private Entities() {
    }
}
