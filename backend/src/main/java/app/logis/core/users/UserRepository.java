package app.logis.core.users;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

    /** Locks all active admins for the duration of the transaction (invariant "at least one admin"). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = app.logis.core.users.Role.ADMIN and u.enabled = true")
    List<User> lockActiveAdmins();

    List<User> findAllByOrderByUsernameAsc();

    @Query("select u from User u where u.enabled = true and (lower(u.username) like :pattern escape '\\' "
            + "or lower(u.displayName) like :pattern escape '\\') order by u.username")
    List<User> searchActive(String pattern, Pageable pageable);
}
