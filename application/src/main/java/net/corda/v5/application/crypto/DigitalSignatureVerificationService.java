package net.corda.v5.application.crypto;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SignatureSpec;
import net.corda.v5.crypto.exceptions.CryptoSignatureException;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;

/**
 * Allows flows to verify digital signatures.
 * <p>
 * Corda provides an instance of {@link DigitalSignatureVerificationService} to flows via property injection.
 */
@DoNotImplement
public interface DigitalSignatureVerificationService {
    // TODO The following `verify` overload should be aligned with the other one as per: https://r3-cev.atlassian.net/browse/CORE-9332
    /**
     * Verifies a digital signature by using {@code signatureSpec}.
     * Always throws an exception if verification fails.
     *
     * @param publicKey The signer's {@link PublicKey}.
     * @param signatureSpec The signature spec.
     * @param signatureData The signatureData on a message.
     * @param clearData The clear data/message that was signed (usually the Merkle root).
     *
     * @throws CryptoSignatureException If verification of the digital signature fails.
     * @throws IllegalArgumentException If the signature scheme is not supported or if any of the clear or signature
     * data is empty.
     */
    void verify(@NotNull PublicKey publicKey, @NotNull SignatureSpec signatureSpec, @NotNull byte[] signatureData, @NotNull byte[] clearData);

    /**
     * Verifies a digital signature against data. Throws {@link CryptoSignatureException} if verification fails.
     *
     * @param originalData The original data on which the signature was applied (usually the Merkle root).
     * @param signature The digital signature.
     * @param publicKey The signer's {@link PublicKey}.
     * @param signatureSpec The signature spec.
     *
     * @throws CryptoSignatureException If verification of the digital signature fails.
     * @throws IllegalArgumentException If the signature scheme is not supported or if any of the clear or signature
     * data is empty.
     */
    void verify(@NotNull byte[] originalData, @NotNull DigitalSignature signature, @NotNull PublicKey publicKey, @NotNull SignatureSpec signatureSpec);
}
