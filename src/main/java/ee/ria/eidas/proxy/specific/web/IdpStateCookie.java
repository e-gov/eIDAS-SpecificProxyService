package ee.ria.eidas.proxy.specific.web;

import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseCookie;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

@UtilityClass
public class IdpStateCookie {

    public static final String NAME = "__Host-IDP_STATE";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generateValue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static boolean matches(String value, String expectedHash) {
        if (value == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(value).getBytes(UTF_8), expectedHash.getBytes(UTF_8));
    }

    public static String create(String value) {
        return ResponseCookie.from(NAME, value)
                .path("/")
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
                .build()
                .toString();
    }

    public static String expire() {
        return ResponseCookie.from(NAME, "")
                .path("/")
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
                .maxAge(0)
                .build()
                .toString();
    }
}
