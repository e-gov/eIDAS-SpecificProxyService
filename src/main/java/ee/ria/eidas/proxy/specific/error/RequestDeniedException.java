package ee.ria.eidas.proxy.specific.error;

import lombok.Getter;

public class RequestDeniedException extends RuntimeException {

    @Getter
    private final String inResponseTo;

    public RequestDeniedException(String message, String inResponseTo) {
        super(message);
        this.inResponseTo = inResponseTo;
    }
}
