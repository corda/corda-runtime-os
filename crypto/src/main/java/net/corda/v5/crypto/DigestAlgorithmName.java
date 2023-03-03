package net.corda.v5.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The digest algorithm name. This class is to be used in Corda hashing API.
 */
@CordaSerializable
public final class DigestAlgorithmName {
    private final String name;

    /**
     * Construct a digest algorithm name.
     * <p>
     *
     * @param name The name of the digest algorithm to be used for the instance.
     */
    public DigestAlgorithmName(@NotNull String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Hash algorithm name unavailable or not specified");
        this.name = name;
    }

    /**
     * Instance of SHA-256
     */
    @NotNull
    public static final DigestAlgorithmName SHA2_256 = new DigestAlgorithmName("SHA-256");

    /**
     * Instance of Double SHA-256
     */
    @NotNull
    public static final DigestAlgorithmName SHA2_256D = new DigestAlgorithmName("SHA-256D");

    /**
     * Instance of SHA-384
     */
    @NotNull
    public static final DigestAlgorithmName SHA2_384 = new DigestAlgorithmName("SHA-384");

    /**
     * Instance of SHA-512
     */
    @NotNull
    public static final DigestAlgorithmName SHA2_512 = new DigestAlgorithmName("SHA-512");

    /**
     * Converts a {@link DigestAlgorithmName} object to a string representation.
     */
    @NotNull
    public String toString() {
        return this.name;
    }

    /**
     * Returns a hash code value for the object.
     */
    public int hashCode() {
        return this.name.toUpperCase().hashCode();
    }

    /**
     * Check if two specified instances of the {@link DigestAlgorithmName} are the same based on their content.
     *
     * @return true if they are equal.
     */
    public boolean equals(@Nullable Object other) {
        if (other == null) return false;
        if (this == other) return true;
        if (!(other instanceof DigestAlgorithmName)) return false;
        DigestAlgorithmName otherDigest = (DigestAlgorithmName) other;
        return this.name.equalsIgnoreCase(otherDigest.name);
    }

    @NotNull
    public String getName() {
        return this.name;
    }
}
