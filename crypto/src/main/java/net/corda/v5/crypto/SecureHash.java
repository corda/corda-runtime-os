package net.corda.v5.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

/**
 * A cryptographically secure hash value, computed by a specified digest algorithm ({@link DigestAlgorithmName}).
 * A {@link SecureHash} can be computed and acquired through the {@link net.corda.v5.application.crypto.DigestService}.
 */
@DoNotImplement
@CordaSerializable
public interface SecureHash {
    /**
     * Hashing algorithm which was used to generate the hash.
     */
    @NotNull
    String getAlgorithm();

    /**
     * The result bytes of the hashing operation with the specified digest algorithm. The specified digest algorithm
     * can be acquired through {@link SecureHash#getAlgorithm()}
     */
    @NotNull
    byte[] getBytes();

    /**
     * Returns hexadecimal representation of the hash value.
     */
    @NotNull
    String toHexString();

    /**
     * The delimiter used in the string form of a secure hash to separate the algorithm name from the hexadecimal
     * string of the hash.
     * <p>
     * Please note that algorithm name may only match the regex [a-zA-Z_][a-zA-Z_0-9\-/]* so delimiter ':' is a safe separator.
     */
    char DELIMITER = ':';

    /**
     * Converts a {@link SecureHash} object to a string representation containing the <code>algorithm</code> and hexadecimal
     * representation of the <code>bytes</code> separated by the colon character ({@link net.corda.v5.crypto.SecureHash.DELIMITER}).
     * <p>
     * Example outcome of toString(): SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90
     */
    @NotNull
    String toString();
}