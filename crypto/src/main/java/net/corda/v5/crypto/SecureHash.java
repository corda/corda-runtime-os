package net.corda.v5.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.ByteArrays;
import net.corda.v5.base.types.OpaqueBytes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Container for a cryptographically secure hash value.
 * Provides utilities for generating a cryptographic hash using different algorithms (currently only SHA-256 supported).
 */
@CordaSerializable
public final class SecureHash extends OpaqueBytes {
    /**
     * The hash algorithm.
     */
    private final String algorithm;

   /**
     * Construct a secure hash.
     *
     * @param algorithm Hashing algorithm which was used to generate the hash.
     * @param bytes     Hash value.
     */
    public SecureHash(@NotNull String algorithm, @NotNull byte[] bytes) {
        super(bytes);
        this.algorithm = algorithm;
    }

    static final char DELIMITER = ':';

    /**
     * Creates a {@link SecureHash}.
     * <p>
     * This function does not validate the length of the created digest. Prefer using
     * <code>HashingService#parse</code> for a safer mechanism for creating {@link SecureHash}es.
     */
    @NotNull
    public static SecureHash parse(@NotNull String str) {
        int idx = str.indexOf(DELIMITER);
        if (idx == -1) {
            throw new IllegalArgumentException("Provided string: $str should be of format algorithm:hexadecimal");
        } else {
            String algorithm = str.substring(0, idx);
            String value = str.substring(idx + 1);
            byte[] data = ByteArrays.parseAsHex(value);
            return new SecureHash(algorithm, data);
        }
    }

    /**
     * Returns hexadecimal representation of the hash value.
     */
    @NotNull
    public String toHexString() {
        return ByteArrays.toHexString(this.getBytes());
    }

    /**
     * Returns the first specified number of hexadecimal digits of the {@link SecureHash} value.
     *
     * @param prefixLen The number of characters in the prefix.
     */
    @NotNull
    public String prefixChars(int prefixLen) {
        return this.toHexString().substring(0, prefixLen);
    }


    /**
     * Returns the first 6 hexadecimal digits of the {@link SecureHash} value.
     */
    @NotNull
    public String prefixChars() {
        return prefixChars(6);
    }

    /**
     * Compares the two given instances of the {@link SecureHash} based on the content.
     */
    public boolean equals(@Nullable Object other) {
        if (this == other) return true;
        return other instanceof SecureHash && this.algorithm.equals(((SecureHash) other).algorithm) && super.equals(other);
    }

    /**
     * Returns a hash code value for the object.
     */
    public int hashCode() {
        return ByteBuffer.wrap(this.getBytes()).getInt();
    }

    /**
     * Converts a {@link SecureHash} object to a string representation containing the <code>algorithm</code> and hexadecimal
     * representation of the <code>bytes</code> separated by the colon character.
     */
    @NotNull
    public String toString() {
        return this.algorithm + ':' + this.toHexString();
    }

    @NotNull
    public String getAlgorithm() {
        return this.algorithm;
    }
}