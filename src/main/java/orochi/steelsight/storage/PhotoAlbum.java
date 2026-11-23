package orochi.steelsight.storage;

/** Kinds of photo, kept apart because face photos are biometric data and tickets are not. */
public enum PhotoAlbum {
    TICKET,
    FACE,
    /** Violation evidence photos the detection app uploads; the ERP only reads them (SRS CM-07). */
    EVIDENCE
}
