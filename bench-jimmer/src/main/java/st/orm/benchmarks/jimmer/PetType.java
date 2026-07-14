package st.orm.benchmarks.jimmer;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.Table;

@Entity
@Table(name = "pet_type")
public interface PetType {

    @Id
    long id();

    String name();
}
