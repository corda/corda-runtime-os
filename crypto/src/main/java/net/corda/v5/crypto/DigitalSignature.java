package net.corda.v5.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper around a digital signature.
 */
@DoNotImplement
@CordaSerializable
public interface DigitalSignature {

    @NotNull
    byte[] getBytes();

    /**
     * A digital signature that identifies who is the owner of the signing key used to create this signature.
     */
    @DoNotImplement
    interface WithKeyId extends DigitalSignature {

        /**
         * Gets the key ID of the public key (public key hash) whose private key pair was used to sign the data. If the
         * original key passed in to the sign operation is a {@link CompositeKey} then the key ID, is the ID of the
         * composite key leaf used to sign.
         */
        @NotNull
        SecureHash getBy();
    }
}
