package orochi.steelsight.exception;

/** The request is allowed but breaks a rule, such as editing a completed shift. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
