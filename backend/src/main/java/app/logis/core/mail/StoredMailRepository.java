package app.logis.core.mail;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

interface StoredMailRepository extends JpaRepository<StoredMail, Long> {

    boolean existsByOwnerUserIdAndMessageId(Long ownerUserId, String messageId);

    @Query("select m from StoredMail m where m.ownerUserId = :ownerUserId and m.result <> 'DELETED' "
            + "order by m.receivedAt desc, m.id desc")
    List<StoredMail> findForOwner(Long ownerUserId);

    @Query("select m from StoredMail m where m.id = :id and m.ownerUserId = :ownerUserId and m.result <> 'DELETED'")
    Optional<StoredMail> findOwn(Long id, Long ownerUserId);

    @Query("select m from StoredMail m where m.ownerUserId = :ownerUserId and m.handler = :handler "
            + "and m.result = :result and lower(m.source) = lower(:source) "
            + "and lower(m.subjectName) = lower(:subjectName) order by m.receivedAt, m.id")
    List<StoredMail> findWaiting(Long ownerUserId, String handler, String result, String source, String subjectName);

    @Modifying
    @Query("update StoredMail m set m.result = :result where m.linkType = :linkType and m.linkId = :linkId")
    int markByLink(String linkType, Long linkId, String result);
}
