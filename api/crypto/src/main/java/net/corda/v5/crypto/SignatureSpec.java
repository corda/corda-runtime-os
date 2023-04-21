package net.corda.v5.crypto;


import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A digital signature scheme.
 */
@CordaSerializable
@DoNotImplement
public interface SignatureSpec {

    /**
     * Gets the signature-scheme name as required to create {@link java.security.Signature} objects
     * (for example, <code>SHA256withECDSA</code>).
     *
     * @return A string containing the signature name.
     */
    @NotNull
    String getSignatureName();
}
