package app.logis.core.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface MailboxRepository extends JpaRepository<Mailbox, Long> {

    List<Mailbox> findByEnabledTrue();
}
