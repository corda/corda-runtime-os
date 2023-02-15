package net.corda.v5.application.crypto;

import java.security.PublicKey;
import java.util.Objects;

import net.corda.v5.base.annotations.ConstructorForDeserialization;
import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.crypto.DigitalSignature;
import net.corda.v5.crypto.merkle.MerkleProof;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapper over the signature output accompanied by signer's public key and signature metadata.
 */
@CordaSerializable
public final class DigitalSignatureAndMetadata {
    private final DigitalSignature.WithKey signature;
    private final DigitalSignatureMetadata metadata;
    private final MerkleProof proof;

    private static void requireNotNull(@Nullable Object obj, @NotNull String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * @param signature The signature that was applied.
     * @param metadata Attached {@link DigitalSignatureMetadata} for this signature.
     * @param proof Attached {@link MerkleProof} if this is a batch signature.
     */
    @ConstructorForDeserialization
    public DigitalSignatureAndMetadata(
        @NotNull DigitalSignature.WithKey signature,
        @NotNull DigitalSignatureMetadata metadata,
        @Nullable MerkleProof proof
    ) {
        requireNotNull(signature, "signature must not be null");
        requireNotNull(metadata, "metadata must not be null");
        this.signature = signature;
        this.metadata = metadata;
        this.proof = proof;
    }

    public DigitalSignatureAndMetadata(
        @NotNull DigitalSignature.WithKey signature,
        @NotNull DigitalSignatureMetadata metadata
    ) {
        this(signature, metadata, null);
    }

    @NotNull
    public DigitalSignature.WithKey getSignature() {
        return signature;
    }

    @NotNull
    public DigitalSignatureMetadata getMetadata() {
        return metadata;
    }

    @Nullable
    public MerkleProof getProof() {
        return proof;
    }

    /**
     * @return The {@link PublicKey} that created the signature.
     */
    @NotNull
    public PublicKey getBy() {
        return signature.getBy();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof DigitalSignatureAndMetadata)) {
            return false;
        }
        final DigitalSignatureAndMetadata other = (DigitalSignatureAndMetadata) obj;
        return signature.equals(other.signature) && metadata.equals(other.metadata) && Objects.equals(proof, other.proof);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature, metadata, proof);
    }

    @Override
    @NotNull
    public String toString() {
        return "DigitalSignatureAndMetadata[signature=" + signature
            + ", metadata=" + metadata
            + ", proof=" + proof
            + ']';
    }
}
