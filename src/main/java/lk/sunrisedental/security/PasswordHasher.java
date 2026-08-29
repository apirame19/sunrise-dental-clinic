package lk.sunrisedental.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Salted, iterated password hashing for staff credentials.
 *
 * <p>Uses {@code PBKDF2WithHmacSHA256} from the JDK, so no third-party cryptography library is
 * introduced. Each password gets its own 16-byte {@link SecureRandom} salt, which means two staff
 * members who happen to choose the same password still end up with different stored hashes, and a
 * precomputed rainbow table is useless against the {@code users} table.</p>
 *
 * <p>The iteration count is stored alongside the hash rather than being hard-coded into the
 * verification path. That is what allows the work factor to be raised in future without
 * invalidating credentials that were hashed under the old cost.</p>
 *
 * <p>Verification uses {@link MessageDigest#isEqual(byte[], byte[])}, which compares in constant
 * time. A naive {@code String.equals} would return as soon as two bytes differed, leaking how much
 * of a guessed hash was correct through response timing.</p>
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** Cost factor applied to newly hashed passwords. */
    public static final int DEFAULT_ITERATIONS = 120_000;

    /** Any stored credential below this cost is refused rather than silently accepted. */
    public static final int MINIMUM_ITERATIONS = 100_000;

    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
        // Utility class.
    }

    /**
     * The three values that must be persisted together for a credential to be verifiable.
     *
     * @param hash       Base64 of the derived key
     * @param salt       Base64 of the random salt
     * @param iterations the cost factor used
     */
    public record HashedPassword(String hash, String salt, int iterations) {
    }

    /**
     * Hashes a new password with a freshly generated salt and the default cost factor.
     *
     * <p>The supplied array is zeroed before this method returns, so the caller cannot leave a
     * plain-text password sitting in the heap. Pass a copy if the value is still needed.</p>
     *
     * @param password the plain-text password; wiped on return
     * @return the hash, salt and iteration count to persist
     * @throws IllegalArgumentException if the password is null or empty
     */
    public static HashedPassword hash(char[] password) {
        requireUsablePassword(password);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        try {
            String hash = derive(password, salt, DEFAULT_ITERATIONS);
            return new HashedPassword(hash, saltBase64, DEFAULT_ITERATIONS);
        } finally {
            wipe(password);
        }
    }

    /**
     * Recomputes a hash against a known salt and cost factor.
     *
     * <p>Used when verifying a stored credential, and by the seed-data generator.</p>
     *
     * @param password   the plain-text password; wiped on return
     * @param saltBase64 the Base64 salt the credential was created with
     * @param iterations the cost factor the credential was created with
     * @return Base64 of the derived key
     * @throws IllegalArgumentException if the password is unusable or the cost factor is too low
     */
    public static String deriveHash(char[] password, String saltBase64, int iterations) {
        requireUsablePassword(password);
        if (iterations < MINIMUM_ITERATIONS) {
            wipe(password);
            throw new IllegalArgumentException(
                    "Iteration count " + iterations + " is below the minimum of " + MINIMUM_ITERATIONS);
        }
        try {
            return derive(password, Base64.getDecoder().decode(saltBase64), iterations);
        } finally {
            wipe(password);
        }
    }

    /**
     * Verifies a candidate password against a stored credential.
     *
     * <p>Never throws for bad stored data. A corrupt or missing hash is a failed authentication,
     * not a server error, because an exception escaping here would turn a damaged user row into a
     * 500 response that distinguishes it from an ordinary wrong password.</p>
     *
     * @param password       the candidate password; wiped on return
     * @param expectedHash   the stored Base64 hash
     * @param saltBase64     the stored Base64 salt
     * @param iterations     the stored cost factor
     * @return {@code true} only if the candidate reproduces the stored hash exactly
     */
    public static boolean matches(char[] password, String expectedHash, String saltBase64, int iterations) {
        if (password == null || password.length == 0 || expectedHash == null || saltBase64 == null) {
            wipe(password);
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] expected = Base64.getDecoder().decode(expectedHash);
            byte[] actual = Base64.getDecoder().decode(derive(password, salt, iterations));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Malformed Base64 or an unusable cost factor in the stored row.
            return false;
        } finally {
            wipe(password);
        }
    }

    // ------------------------------------------------------------------ internals

    private static String derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] key = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (NoSuchAlgorithmException e) {
            // PBKDF2WithHmacSHA256 is mandated by the Java platform; absence means a broken JRE.
            throw new IllegalStateException("PBKDF2 algorithm unavailable in this JRE", e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive password hash", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static void requireUsablePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
