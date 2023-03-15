package net.corda.v5.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.OpaqueBytes;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;

/**
 * A wrapper around a digital signature.
 */
@CordaSerializable
public class DigitalSignature extends OpaqueBytes {
    public DigitalSignature(@NotNull byte[] bytes) {
        super(bytes);
    }

    /**
     * A digital signature that identifies who the public key is owned by.
     */
    public static class WithKey extends DigitalSignature {

        /**
         * Construct WithKey
         *
         * @param by      The public key of the corresponding private key used to sign the data (as if an instance
         *                of the {@link CompositeKey} is passed to the sign operation it may contain keys which are not actually owned by
         *                the member).
         * @param bytes   The signature.
         */
        public WithKey(@NotNull PublicKey by, @NotNull byte[] bytes) {
            super(bytes);
            this.by = by;
        }

        /**
         * Public key which corresponding private key was used to sign the data (as if an instance
         * of the {@link CompositeKey} is passed to the sign operation it may contain keys which are not actually owned by
         * the member).
         */
        private final PublicKey by;

        @NotNull
        public final PublicKey getBy() {
            return this.by;
        }
    }
}
