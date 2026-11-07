package app.logis.core.documents;

/**
 * An object type that documents can be attached to. One bean per type answers the access questions, so the generic
 * document endpoints never need to know the owner.
 */
public interface DocumentOwnerType {

    /** Stable type key, e.g. {@code realestate.cost}. */
    String type();

    /** Whether the user may see the owner's documents. {@code false} is answered with HTTP 404. */
    boolean canRead(Long ownerId, Long userId);

    /** Whether the user may add and delete documents. Only asked when {@link #canRead} is {@code true}. */
    boolean canWrite(Long ownerId, Long userId);
}
