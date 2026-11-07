package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A mail fetched from a user's mailbox and what the import did with it. */
@Entity
@Table(name = "imported_mails", schema = "vacationrental")
class ImportedMail {

    enum Result {
        BOOKING_CREATED, CHANGE_PROPOSED, ATTACHED, DUPLICATE, UNASSIGNED_UNIT, NOT_MATCHED, PERIOD_TAKEN,
        NOT_RECOGNISED, IGNORED, DISCARDED, FAILED,
        /** Content removed by the user; only the Message-ID is kept so the mail is not fetched again. */
        DELETED;

        /** Results the user can process again. */
        boolean retryable() {
            return this == UNASSIGNED_UNIT || this == NOT_MATCHED || this == PERIOD_TAKEN || this == NOT_RECOGNISED
                    || this == FAILED || this == IGNORED;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(name = "message_id", nullable = false, updatable = false)
    private String messageId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    private String sender;

    private String subject;

    @Column(nullable = false)
    private byte[] raw;

    @Column(name = "text_preview")
    private String textPreview;

    private String agency;

    @Enumerated(EnumType.STRING)
    @Column(name = "mail_type")
    private ParsedMail.MailType mailType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ParsedMail parsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "parsed_by", nullable = false)
    private MailRecognizer.Source parsedBy = MailRecognizer.Source.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Result result = Result.NOT_RECOGNISED;

    @Column(name = "result_detail")
    private String resultDetail;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportedMail() {
    }

    ImportedMail(Long ownerUserId, String messageId, Instant receivedAt, byte[] raw, Instant createdAt) {
        this.ownerUserId = ownerUserId;
        this.messageId = messageId;
        this.receivedAt = receivedAt;
        this.raw = raw;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getOwnerUserId() {
        return ownerUserId;
    }

    Instant getReceivedAt() {
        return receivedAt;
    }

    String getSender() {
        return sender;
    }

    String getSubject() {
        return subject;
    }

    byte[] getRaw() {
        return raw;
    }

    String getTextPreview() {
        return textPreview;
    }

    String getAgency() {
        return agency;
    }

    ParsedMail.MailType getMailType() {
        return mailType;
    }

    ParsedMail getParsed() {
        return parsed;
    }

    MailRecognizer.Source getParsedBy() {
        return parsedBy;
    }

    Result getResult() {
        return result;
    }

    String getResultDetail() {
        return resultDetail;
    }

    Long getBookingId() {
        return bookingId;
    }

    void read(MailContent content, MailRecognizer.Recognition recognition) {
        this.sender = truncate(content.sender(), 255);
        this.subject = truncate(content.subject(), 998);
        this.textPreview = content.text();
        this.parsed = recognition.parsed();
        this.parsedBy = recognition.source();
        this.agency = recognition.parsed() == null ? null : truncate(recognition.parsed().agency(), 64);
        this.mailType = recognition.parsed() == null ? null : recognition.parsed().type();
    }

    /** Removes the mail content including guest data; the Message-ID stays known. */
    void forget() {
        this.raw = new byte[0];
        this.textPreview = null;
        this.parsed = null;
        this.sender = null;
        this.result = Result.DELETED;
        this.resultDetail = null;
    }

    void result(Result result, Long bookingId, String detail) {
        this.result = result;
        this.bookingId = bookingId;
        this.resultDetail = truncate(detail, 255);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
