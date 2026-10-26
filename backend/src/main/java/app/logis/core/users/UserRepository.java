package app.logis.core.users;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(String username);

    /** Sperrt alle aktiven Admins fuer die Dauer der Transaktion (Invariante "mindestens ein Admin"). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = app.logis.core.users.Role.ADMIN and u.enabled = true")
    List<User> lockActiveAdmins();

    List<User> findAllByOrderByUsernameAsc();
}
