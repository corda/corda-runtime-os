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
     * Looks into a set of signing keys to find keys owned by the caller. In case of {@link CompositeKey} it looks into
     * the composite key leaves and returns the firstly found owned composite key leaf.
     *
     * @param keys The signing keys to look into.
     * @return A mapping that maps the requested signing key:
     * <ul>
     *     <li> to the same key if it is owned by the caller in case the requested signing key is a plain key </li>
     *     <li> to the firstly found composite key leaf to be owned by the caller, of the composite key's leaves (children)
     *     in case the requested signing key is a composite key </li>
     *     <li> to {@code null} if the requested key is not owned by the caller </li>
     * </ul>
     */
    @Suspendable
    @NotNull
    Map<PublicKey, PublicKey> findMySigningKeys(@NotNull Set<PublicKey> keys);
}
