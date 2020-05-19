package ee.ria.eidas.proxy.specific.error;

import lombok.Getter;

public class AuthenticationRequestDeniedException extends RuntimeException {

    @Getter
    private final String inResponseTo;

    public AuthenticationRequestDeniedException(String message, String inResponseTo) {
        super(message);
        this.inResponseTo = inResponseTo;
    }
}
