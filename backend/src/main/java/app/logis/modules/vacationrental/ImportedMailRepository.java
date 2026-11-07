package app.logis.modules.vacationrental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface ImportedMailRepository extends JpaRepository<ImportedMail, Long> {

    boolean existsByOwnerUserIdAndMessageId(Long ownerUserId, String messageId);

    @Query("select m from ImportedMail m where m.ownerUserId = :ownerUserId "
            + "and m.result <> app.logis.modules.vacationrental.ImportedMail.Result.DELETED "
            + "order by m.receivedAt desc, m.id desc")
    List<ImportedMail> findForOwner(Long ownerUserId);

    @Query("select m from ImportedMail m where m.id = :id and m.ownerUserId = :ownerUserId "
            + "and m.result <> app.logis.modules.vacationrental.ImportedMail.Result.DELETED")
    Optional<ImportedMail> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    @Query("select m from ImportedMail m where m.ownerUserId = :ownerUserId and m.result = :result "
            + "and lower(m.agency) = lower(:agency) order by m.receivedAt, m.id")
    List<ImportedMail> findWaiting(Long ownerUserId, ImportedMail.Result result, String agency);

    @Modifying
    @Query("update ImportedMail m set m.result = app.logis.modules.vacationrental.ImportedMail.Result.DISCARDED "
            + "where m.bookingId = :bookingId")
    int markDiscarded(Long bookingId);
}
