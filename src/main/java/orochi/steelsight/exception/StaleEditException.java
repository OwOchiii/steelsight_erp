package orochi.steelsight.exception;

import org.springframework.dao.OptimisticLockingFailureException;

/** Someone else saved this record after the user opened it. The user should reload and retry. */
public class StaleEditException extends OptimisticLockingFailureException {

    public StaleEditException(String what, Object id) {
        super(what + " " + id + " was changed by someone else");
    }

    /** Compares the version the user's form was loaded with against the current one. */
    public static void check(String what, Object id, Long current, long expected) {
        if (current == null || current != expected) {
            throw new StaleEditException(what, id);
        }
    }
}
