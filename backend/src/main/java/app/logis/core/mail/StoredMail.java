package app.logis.core.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** A mail fetched from a user's mailbox and what a handler did with it. */
@Entity
@Table(name = "mails", schema = "core")
class StoredMail {

    static final String NOT_RECOGNISED = "NOT_RECOGNISED";
    static final String IGNORED = "IGNORED";
    static final String FAILED = "FAILED";
    /** Content removed by the user; only the Message-ID is kept so the mail is not fetched again. */
    static final String DELETED = "DELETED";

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

    private String handler;

    @Column(nullable = false)
    private String result = NOT_RECOGNISED;

    @Column(name = "result_detail")
    private String resultDetail;

    private String kind;

    private String source;

    private String reference;

    @Column(name = "subject_name")
    private String subjectName;

    @Column(name = "link_type")
    private String linkType;

    @Column(name = "link_id")
    private Long linkId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String data;

    @Column(name = "parsed_by")
    private String parsedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoredMail() {
    }

    StoredMail(Long ownerUserId, String messageId, Instant receivedAt, byte[] raw, Instant createdAt) {
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

    String getHandler() {
        return handler;
    }

    String getResult() {
        return result;
    }

    String getResultDetail() {
        return resultDetail;
    }

    String getKind() {
        return kind;
    }

    String getSource() {
        return source;
    }

    String getReference() {
        return reference;
    }

    String getSubjectName() {
        return subjectName;
    }

    String getLinkType() {
        return linkType;
    }

    Long getLinkId() {
        return linkId;
    }

    String getParsedBy() {
        return parsedBy;
    }

    void read(MailContent content) {
        this.sender = truncate(content.sender(), 255);
        this.subject = truncate(content.subject(), 998);
        this.textPreview = content.text();
    }

    void handled(String handler, Handled h, String dataJson) {
        this.handler = handler;
        this.result = truncate(h.result(), 32);
        this.resultDetail = truncate(h.detail(), 255);
        this.kind = truncate(h.kind(), 32);
        this.source = truncate(h.source(), 64);
        this.reference = truncate(h.reference(), 128);
        this.subjectName = truncate(h.subjectName(), 255);
        this.linkType = truncate(h.linkType(), 64);
        this.linkId = h.linkId();
        this.data = dataJson;
        this.parsedBy = truncate(h.parsedBy(), 8);
    }

    void unhandled(String result, String detail) {
        this.handler = null;
        this.result = result;
        this.resultDetail = truncate(detail, 255);
        this.kind = null;
        this.source = null;
        this.reference = null;
        this.subjectName = null;
        this.linkType = null;
        this.linkId = null;
        this.data = null;
        this.parsedBy = null;
    }

    void setResult(String result) {
        this.result = result;
    }

    void ignore() {
        this.result = IGNORED;
        this.resultDetail = null;
    }

    /** Removes the mail content including guest data; the Message-ID stays known. */
    void forget() {
        this.raw = new byte[0];
        this.textPreview = null;
        this.sender = null;
        this.data = null;
        this.result = DELETED;
        this.resultDetail = null;
    }

    /** Results the user can process again: anything without a created or changed record. */
    boolean retryable() {
        return linkId == null && !DELETED.equals(result);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
