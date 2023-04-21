package net.corda.v5.application.crypto;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.SignatureSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;
import java.util.List;

@DoNotImplement
public interface SignatureSpecService {
    /**
     * Works out a default signature spec for specified public key, given current security policies.
     *
     * @param publicKey The public key to be used for signing.
     *
     * @return An appropriate {@link SignatureSpec}, or {@code null} if nothing is available for the key type.
     */
    @Suspendable
    @Nullable
    SignatureSpec defaultSignatureSpec(@NotNull PublicKey publicKey);

    /**
     * Works out a default signature spec for specified public key and digest algorithm given current security policies.
     *
     * @param publicKey The public key to be used for signing.
     * @param digestAlgorithmName The digest algorithm to use, for example, {@link DigestAlgorithmName#SHA2_256}.
     *
     * @return An appropriate {@link SignatureSpec}, or null if nothing is available for the key type.
     */
    @Suspendable
    @Nullable
    SignatureSpec defaultSignatureSpec(@NotNull PublicKey publicKey, @NotNull DigestAlgorithmName digestAlgorithmName);

    /**
     * Returns compatible signature specs for specified public key, given current security policies.
     *
     * @param publicKey The public key to be used for signing.
     */
    @Suspendable
    @NotNull
    List<SignatureSpec> compatibleSignatureSpecs(@NotNull PublicKey publicKey);

    /**
     * Returns compatible signature specs for specified public key and digest algorithm, given current security policies.
     *
     * @param publicKey The public key to be used for signing.
     * @param digestAlgorithmName The digest algorithm to use, for example, [DigestAlgorithmName.SHA2_256].
     */
    @Suspendable
    @NotNull
    List<SignatureSpec> compatibleSignatureSpecs(@NotNull PublicKey publicKey, @NotNull DigestAlgorithmName digestAlgorithmName);
}
