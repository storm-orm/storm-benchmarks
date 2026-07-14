package st.orm.benchmarks.jimmer;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.Table;

@Entity
@Table(name = "city")
public interface City {

    @Id
    long id();

    String name();
}
