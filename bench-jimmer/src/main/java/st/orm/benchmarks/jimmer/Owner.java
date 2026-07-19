package st.orm.benchmarks.jimmer;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.JoinColumn;
import org.babyfish.jimmer.sql.ManyToOne;
import org.babyfish.jimmer.sql.OneToMany;
import org.babyfish.jimmer.sql.Table;

import java.util.List;

@Entity
@Table(name = "owner")
public interface Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    String firstName();

    String lastName();

    String address();

    String telephone();

    @ManyToOne
    @JoinColumn(name = "city_id")
    City city();

    @OneToMany(mappedBy = "owner")
    List<Pet> pets();
}
