package net.corda.v5.application.crypto;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.crypto.SignatureSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.hash;

/**
 * Metadata attached to a signature.
 * <p>
 * The use of this metadata is decided by API layers above application. For example, the ledger implementation may
 * populate some properties when transaction signatures are requested.
 * <p>
 * Note that the metadata itself is not signed over.
 */
@CordaSerializable
public final class DigitalSignatureMetadata {
    private final Instant timestamp;
    private final SignatureSpec signatureSpec;
    private final Map<String, String> properties;

    private static void requireNotNull(@Nullable Object obj, @NotNull String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * @param timestamp The timestamp at which the signature was applied.
     * @param signatureSpec The signature spec.
     * @param properties A set of properties for this signature. Content depends on API layers above {@code application}.
     */
    public DigitalSignatureMetadata(
        @NotNull Instant timestamp,
        @NotNull SignatureSpec signatureSpec,
        @NotNull Map<String, String> properties
    ) {
        requireNotNull(timestamp, "timestamp should not be null");
        requireNotNull(signatureSpec, "signatureSpec should not be null");
        requireNotNull(properties, "properties should not be null");
        this.timestamp = timestamp;
        this.signatureSpec = signatureSpec;
        this.properties = unmodifiableMap(properties);
    }

    @NotNull
    public Instant getTimestamp() {
        return timestamp;
    }

    @NotNull
    public SignatureSpec getSignatureSpec() {
        return signatureSpec;
    }

    @NotNull
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        } else if  (!(obj instanceof DigitalSignatureMetadata)) {
            return false;
        }
        final DigitalSignatureMetadata other = (DigitalSignatureMetadata) obj;
        return timestamp.equals(other.timestamp)
                && signatureSpec.equals(other.signatureSpec)
                && properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        return hash(timestamp, signatureSpec, properties);
    }

    @Override
    @NotNull
    public String toString() {
        return "DigitalSignatureMetadata[timestamp=" + timestamp
            + ", signatureSpec=" + signatureSpec
            + ", properties=" + properties
            + ']';
    }
}
