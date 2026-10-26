package app.logis.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    @Query("select c from RecoveryCode c where c.userId = :userId and c.usedAt is null")
    List<RecoveryCode> findUnused(Long userId);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    void deleteAllForUser(Long userId);
}
