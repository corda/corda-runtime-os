package net.corda.v5.application.crypto;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.crypto.CompositeKey;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.SignatureSpec;
import org.jetbrains.annotations.NotNull;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.Set;

/**
 * Responsible for storing and using private keys to sign things. An implementation of this may, for example, call out
 * to a hardware security module that enforces various auditing and frequency-of-use requirements.
 * <p>
 * Corda provides an instance of {@link DigitalSignatureVerificationService} to flows via property injection.
 */
@DoNotImplement
public interface SigningService {

    /**
     * Using the provided signing {@link PublicKey}, internally looks up the matching {@link PrivateKey} and signs the data.
     *
     * @param bytes The data to sign over using the chosen key.
     * @param publicKey The {@link PublicKey} partner to an internally held {@link PrivateKey}, either derived from the node's
     * primary identity, or previously generated via the freshKey method. If the {@link PublicKey} is actually
     * a {@link CompositeKey}, the first leaf signing key hosted by the node is used.
     * @param signatureSpec The {@link SignatureSpec} to use when producing this signature.
     *
     * @return A {@link DigitalSignature.WithKey} representing the signed data and the {@link PublicKey} that belongs to the
     * same {@link KeyPair} as the {@link PrivateKey} that signed the data.
     *
     * @throws CordaRuntimeException If the input key is not a member of {@code keys}.
     */
    @Suspendable
    @NotNull
    DigitalSignature.WithKey sign(@NotNull byte[] bytes, @NotNull PublicKey publicKey, @NotNull SignatureSpec signatureSpec);

    /**
     * Gets a set of signing keys to look into and returns a mapping of the requested signing keys to signing keys
     * found to be owned by the caller. In case of {@link CompositeKey} it maps the composite key with the firstly found
     * composite key leaf.
     *
     * @param keys The signing keys to look into.
     * @return A mapping of requested signing keys to found signing keys to be owned by the caller or {@code null} if not found to be owned.
     */
    @Suspendable
    @NotNull
    Map<PublicKey, PublicKey> findMySigningKeys(@NotNull Set<PublicKey> keys);
}
