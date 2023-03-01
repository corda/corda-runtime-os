package net.corda.v5.ledger.utxo.observer;

import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Objects;

/**
 * Represents optional fields to allow CorDapps to filter for subsets of tokens within a token pool.
 */
public final class UtxoTokenFilterFields {

    /**
     * Optional user defined string that can be used for regular expression filters.
     */
    @Nullable
    private final String tag;

    /**
     * Optional token owner hash.
     */
    @Nullable
    private final SecureHash ownerHash;

    /**
     * Creates a new instance of the {@link UtxoTokenFilterFields} class.
     *
     * @param tag Optional user defined string that can be used for regular expression filters.
     * @param ownerHash Optional token owner hash.
     */
    public UtxoTokenFilterFields(@Nullable final String tag, @Nullable final SecureHash ownerHash) {
        this.tag = tag;
        this.ownerHash = ownerHash;
    }

    /**
     * Creates a new instance of the {@link UtxoTokenFilterFields} class.
     */
    public UtxoTokenFilterFields() {
        this(null, null);
    }

    /**
     * Gets an optional user defined string that can be used for regular expression filters.
     *
     * @return Returns an optional user defined string that can be used for regular expression filters.
     */
    @Nullable
    public String getTag() {
        return tag;
    }

    /**
     * Gets an optional token owner hash.
     *
     * @return Returns an optional token owner hash.
     */
    @Nullable
    public SecureHash getOwnerHash() {
        return ownerHash;
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param obj The object to compare with the current object.
     * @return Returns true if the specified object is equal to the current object; otherwise, false.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        return this == obj || obj instanceof UtxoTokenFilterFields && equals((UtxoTokenFilterFields) obj);
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param other The Party to compare with the current object.
     * @return Returns true if the specified Party is equal to the current object; otherwise, false.
     */
    public boolean equals(@NotNull final UtxoTokenFilterFields other) {
        return Objects.equals(other.tag, tag)
                && Objects.equals(other.ownerHash, ownerHash);
    }

    /**
     * Serves as the default hash function.
     *
     * @return Returns a hash code for the current object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(tag, ownerHash);
    }

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    @Override
    public String toString() {
        return MessageFormat.format("UtxoTokenFilterFields(tag={0}, ownerHash={1})", tag, ownerHash);
    }
}
