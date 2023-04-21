package net.corda.v5.ledger.utxo.observer;

import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria;
import net.corda.v5.ledger.utxo.token.selection.TokenSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * Represents the structure of a token produced by an instance of {@link UtxoLedgerTokenStateObserver}.
 */
public final class UtxoToken {

    /**
     * The key of the token pool that this token belongs to.
     */
    @NotNull
    private final UtxoTokenPoolKey poolKey;

    /**
     * The amount represented by this token.
     */
    @NotNull
    private final BigDecimal amount;

    /**
     * Optional fields available to the {@link TokenSelection} API {@link TokenClaimCriteria}.
     */
    @Nullable
    private final UtxoTokenFilterFields filterFields;

    /**
     * Creates a new instance of the {@link UtxoToken} class.
     *
     * @param poolKey The key of the token pool that this token belongs to.
     * @param amount The amount represented by this token.
     * @param filterFields Optional fields available to the {@link TokenSelection} API {@link TokenClaimCriteria}.
     */
    public UtxoToken(
            @NotNull final UtxoTokenPoolKey poolKey,
            @NotNull final BigDecimal amount,
            @Nullable final UtxoTokenFilterFields filterFields) {
        this.poolKey = poolKey;
        this.amount = amount;
        this.filterFields = filterFields;
    }

    /**
     * Gets the key of the token pool that this token belongs to.
     *
     * @return Returns the key of the token pool that this token belongs to.
     */
    @NotNull
    public UtxoTokenPoolKey getPoolKey() {
        return poolKey;
    }

    /**
     * Gets the amount represented by this token.
     *
     * @return Returns the amount represented by this token.
     */
    @NotNull
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Gets the optional fields available to the {@link TokenSelection} API {@link TokenClaimCriteria}.
     *
     * @return Returns the optional fields available to the {@link TokenSelection} API {@link TokenClaimCriteria}.
     */
    @Nullable
    public UtxoTokenFilterFields getFilterFields() {
        return filterFields;
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param obj The object to compare with the current object.
     * @return Returns true if the specified object is equal to the current object; otherwise, false.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        return this == obj || obj instanceof UtxoToken && equals((UtxoToken) obj);
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param other The Party to compare with the current object.
     * @return Returns true if the specified Party is equal to the current object; otherwise, false.
     */
    public boolean equals(@NotNull final UtxoToken other) {
        return Objects.equals(other.poolKey, poolKey)
                && Objects.compare(other.amount, amount, BigDecimal::compareTo) == 0
                && Objects.equals(other.filterFields, filterFields);
    }

    /**
     * Serves as the default hash function.
     *
     * @return Returns a hash code for the current object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(poolKey, amount, filterFields);
    }

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    @Override
    public String toString() {
        return MessageFormat.format("UtxoToken(poolKey={0}, amount={1}, filterFields={2}", poolKey, amount, filterFields);
    }
}
