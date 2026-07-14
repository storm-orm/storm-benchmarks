package st.orm.benchmarks.jimmer;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.JoinColumn;
import org.babyfish.jimmer.sql.ManyToOne;
import org.babyfish.jimmer.sql.Table;

import java.time.LocalDate;

@Entity
@Table(name = "pet")
public interface Pet {

    @Id
    long id();

    String name();

    LocalDate birthDate();

    @ManyToOne
    @JoinColumn(name = "type_id")
    PetType type();

    @ManyToOne
    @JoinColumn(name = "owner_id")
    Owner owner();
}
