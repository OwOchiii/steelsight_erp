package orochi.steelsight.exception;

import org.springframework.dao.OptimisticLockingFailureException;

/** Someone else saved this record after the user opened it. The user should reload and retry. */
public class StaleEditException extends OptimisticLockingFailureException {

    public StaleEditException(String message) {
        super(message);
    }
}
