package app.logis.core.documents;

/** A single document was deleted by a user; published within the deleting transaction. */
public record DocumentDeleted(String ownerType, Long ownerId, Long documentId) {
}
