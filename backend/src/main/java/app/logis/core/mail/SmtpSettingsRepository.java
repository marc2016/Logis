package app.logis.core.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SmtpSettingsRepository extends JpaRepository<SmtpSettings, Short> {

    default Optional<SmtpSettings> current() {
        return findById(SmtpSettings.ID);
    }
}
