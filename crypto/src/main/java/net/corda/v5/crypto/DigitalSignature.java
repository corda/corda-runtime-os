package net.corda.v5.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.OpaqueBytes;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Map;

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

        @NotNull
        public final Map<String, String> getContext() {
            return this.context;
        }

        /**
         * Construct WithKey
         *
         * @param by      The public key of the corresponding private key used to sign the data (as if an instance
         *                of the {@link CompositeKey} is passed to the sign operation it may contain keys which are not actually owned by
         *                the member).
         * @param bytes   The signature.
         * @param context The context which was passed to the signing operation, note that this context is not signed over.
         */
        public WithKey(@NotNull PublicKey by, @NotNull byte[] bytes, @NotNull Map<String, String> context) {
            super(bytes);
            this.by = by;
            this.context = context;
        }

        /**
         * Public key which corresponding private key was used to sign the data (as if an instance
         * of the {@link CompositeKey} is passed to the sign operation it may contain keys which are not actually owned by
         * the member).
         */
        private final PublicKey by;

        private final Map<String, String> context;

        @NotNull
        public final PublicKey getBy() {
            return this.by;
        }
    }
}
