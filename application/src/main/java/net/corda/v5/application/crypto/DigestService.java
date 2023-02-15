package net.corda.v5.application.crypto;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;

/**
 * Provides hashing capabilities to be used in all sandbox types.
 */
@DoNotImplement
public interface DigestService {
    /**
     * Computes the digest of the {@code byte[]}.
     *
     * @param bytes The {@code byte[]} to hash.
     * @param digestName The digest algorithm to be used for hashing.
     */
    @Suspendable
    @NotNull
    SecureHash hash(@NotNull byte[] bytes, @NotNull DigestAlgorithmName digestName);

    /**
     * Computes the digest of the {@link InputStream}.
     *
     * @param inputStream The {@link InputStream} to hash.
     * @param digestName The digest algorithm to be used for hashing.
     */
    @Suspendable
    @NotNull
    SecureHash hash(@NotNull InputStream inputStream, @NotNull DigestAlgorithmName digestName);

    /**
     * Returns the {@link DigestAlgorithmName} digest length in bytes.
     *
     * @param digestName The digest algorithm to get the digest length for.
     */
    @Suspendable
    int digestLength(@NotNull DigestAlgorithmName digestName);
}
