package app.logis.core.mail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** The current or last fetch of a mailbox, kept in memory for the progress display (design.md D1). */
final class FetchRun {

    enum Kind { POLL, OLDER }

    enum State { RUNNING, DONE, FAILED }

    private final Long ownerUserId;
    private final Kind kind;
    private final LocalDate from;
    private final Instant startedAt;
    private final AtomicInteger checked = new AtomicInteger();
    private final AtomicInteger created = new AtomicInteger();
    private volatile Integer total;
    private volatile State state = State.RUNNING;
    private volatile String error;
    private volatile Instant finishedAt;
    /** Started by the scheduler, not by the user. */
    private volatile boolean automatic;
    /** Mails of this run that need attention, as "sender: subject"; for the notification at its end. */
    private final java.util.List<String> attention = new java.util.concurrent.CopyOnWriteArrayList<>();

    FetchRun(Long ownerUserId, Kind kind, LocalDate from, Instant startedAt) {
        this.ownerUserId = ownerUserId;
        this.kind = kind;
        this.from = from;
        this.startedAt = startedAt;
    }

    void total(int total) {
        this.total = total;
    }

    void checked(boolean isNew) {
        checked.incrementAndGet();
        if (isNew) {
            created.incrementAndGet();
        }
    }

    void finish(Instant at, String error) {
        this.error = error;
        this.finishedAt = at;
        this.state = error == null ? State.DONE : State.FAILED;
    }

    FetchRun automatic() {
        this.automatic = true;
        return this;
    }

    boolean isAutomatic() {
        return automatic;
    }

    void needsAttention(String line) {
        attention.add(line);
    }

    java.util.List<String> attention() {
        return java.util.List.copyOf(attention);
    }

    boolean running() {
        return state == State.RUNNING;
    }

    Long ownerUserId() {
        return ownerUserId;
    }

    Kind kind() {
        return kind;
    }

    LocalDate from() {
        return from;
    }

    Instant startedAt() {
        return startedAt;
    }

    Integer total() {
        return total;
    }

    int checked() {
        return checked.get();
    }

    int created() {
        return created.get();
    }

    State state() {
        return state;
    }

    String error() {
        return error;
    }

    Instant finishedAt() {
        return finishedAt;
    }
}
