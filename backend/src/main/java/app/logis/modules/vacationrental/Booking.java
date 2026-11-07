package app.logis.modules.vacationrental;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "bookings", schema = "vacationrental")
class Booking {

    /** Owner type for documents. */
    static final String DOCUMENT_TYPE = "vacationrental.booking";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(nullable = false)
    private LocalDate arrival;

    @Column(nullable = false)
    private LocalDate departure;

    @Column(name = "external_ref", length = 64)
    private String externalRef;

    /** Agency the booking came from, e.g. "Fewo & Meer"; optional. */
    @Column(length = 64)
    private String agency;

    @Column(nullable = false)
    private short pets;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_state", nullable = false, length = 8)
    private ReviewState reviewState = ReviewState.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private BookingProposal proposal;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Channel channel;

    @Column(nullable = false)
    private short adults;

    @Column(nullable = false)
    private short children;

    @Column(name = "expected_rent", precision = 12, scale = 2)
    private BigDecimal expectedRent;

    @Column(nullable = false)
    private boolean cancelled;

    @Column(name = "guest_deleted", nullable = false)
    private boolean guestDeleted;

    @Embedded
    private Guest guest;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
    }

    Booking(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getUnitId() {
        return unitId;
    }

    LocalDate getArrival() {
        return arrival;
    }

    LocalDate getDeparture() {
        return departure;
    }

    /** Nights from arrival up to the night before departure. */
    long nights() {
        return ChronoUnit.DAYS.between(arrival, departure);
    }

    String getExternalRef() {
        return externalRef;
    }

    String getAgency() {
        return agency;
    }

    void setAgency(String agency) {
        this.agency = agency;
    }

    int getPets() {
        return pets;
    }

    void setPets(int pets) {
        this.pets = (short) pets;
    }

    ReviewState getReviewState() {
        return reviewState;
    }

    void setReviewState(ReviewState reviewState) {
        this.reviewState = reviewState;
    }

    BookingProposal getProposal() {
        return proposal;
    }

    /** Sets or clears the proposal; the review state follows unless the booking itself is still new. */
    void setProposal(BookingProposal proposal) {
        this.proposal = proposal;
        if (reviewState != ReviewState.NEW) {
            reviewState = proposal == null ? ReviewState.NONE : ReviewState.CHANGE;
        }
    }

    Channel getChannel() {
        return channel;
    }

    int getAdults() {
        return adults;
    }

    int getChildren() {
        return children;
    }

    BigDecimal getExpectedRent() {
        return expectedRent;
    }

    boolean isCancelled() {
        return cancelled;
    }

    void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    boolean isGuestDeleted() {
        return guestDeleted;
    }

    Guest getGuest() {
        return guest == null ? Guest.EMPTY : guest;
    }

    long getVersion() {
        return version;
    }

    void apply(Long unitId, LocalDate arrival, LocalDate departure, String externalRef, Channel channel, int adults,
            int children, BigDecimal expectedRent) {
        this.unitId = unitId;
        this.arrival = arrival;
        this.departure = departure;
        this.externalRef = externalRef;
        this.channel = channel;
        this.adults = (short) adults;
        this.children = (short) children;
        this.expectedRent = expectedRent;
    }

    void setGuest(Guest guest) {
        this.guest = guest;
        if (!guest.isEmpty()) {
            this.guestDeleted = false;
        }
    }

    /** Removes name and contact data for good; the booking itself stays. */
    void deleteGuest() {
        this.guest = Guest.EMPTY;
        this.guestDeleted = true;
    }
}
