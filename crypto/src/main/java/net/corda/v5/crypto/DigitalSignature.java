package net.corda.v5.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.OpaqueBytes;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper around a digital signature.
 */
@CordaSerializable
public class DigitalSignature extends OpaqueBytes {
    public DigitalSignature(@NotNull byte[] bytes) {
        super(bytes);
    }

    /**
     * A digital signature that identifies who is the owner of the signing key used to create this signature.
     */
    public static class WithKeyId extends DigitalSignature {

        /**
         * Creates a new {@code WithKeyId} using the specified key ID and the signature ({@code bytes}).
         *
         * @param by      The ID of the public key (public key hash) whose corresponding private key used to sign the data
         *                (as if an instance of the {@link CompositeKey} is passed to the sign operation it may contain
         *                keys which are not actually owned by the member).
         * @param bytes   The signature.
         */
        public WithKeyId(@NotNull SecureHash by, @NotNull byte[] bytes) {
            super(bytes);
            this.by = by;
        }

        /**
         * Public key which corresponding private key was used to sign the data (as if an instance
         * of the {@link CompositeKey} is passed to the sign operation it may contain keys which are not actually owned by
         * the member).
         */
        private final SecureHash by;

        @NotNull
        public final SecureHash getBy() {
            return this.by;
        }
    }
}
