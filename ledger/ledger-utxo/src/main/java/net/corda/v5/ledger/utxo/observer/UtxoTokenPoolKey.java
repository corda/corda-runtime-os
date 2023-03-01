package net.corda.v5.ledger.utxo.observer;

import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Objects;

/**
 * Represents a key for a pool of similar tokens.
 * <p>
 * The full token pool key includes Holding ID, token type, issue, notary and symbol.
 * The platform provides the holding ID, notary and optional token type.
 */
public final class UtxoTokenPoolKey {

    /**
     * The type of token within a pool.
     */
    @Nullable
    private final String tokenType;

    /**
     * The SecureHash of the issuer of the tokens in a pool.
     */
    @NotNull
    private final SecureHash issuerHash;

    /**
     * The user defined symbol of the tokens in a pool.
     */
    @NotNull
    private final String symbol;

    /**
     * Creates a new instance of the {@link UtxoTokenPoolKey} class.
     *
     * @param tokenType The type of token within a pool. If nothing is specified the platform will default this to the
     * class name of the contact state this key was created from by an implementation of {@link UtxoLedgerTokenStateObserver}.
     * @param issuerHash The SecureHash of the issuer of the tokens in a pool.
     * @param symbol The user defined symbol of the tokens in a pool.
     */
    public UtxoTokenPoolKey(
            @Nullable final String tokenType,
            @NotNull final SecureHash issuerHash,
            @NotNull final String symbol) {
        this.tokenType = tokenType;
        this.issuerHash = issuerHash;
        this.symbol = symbol;
    }

    /**
     * Creates a new instance of the {@link UtxoTokenPoolKey} class.
     *
     * @param issuerHash The SecureHash of the issuer of the tokens in a pool.
     * @param symbol The user defined symbol of the tokens in a pool.
     */
    public UtxoTokenPoolKey(@NotNull final SecureHash issuerHash, @NotNull final String symbol) {
        this(null, issuerHash, symbol);
    }

    /**
     * Gets the type of token within a pool, or null if no type was specified.
     *
     * @return Returns the type of token within a pool, or null if no type was specified.
     */
    @Nullable
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Gets the SecureHash of the issuer of the tokens in a pool.
     *
     * @return Returns the {@link SecureHash} of the issuer of the tokens in a pool.
     */
    @NotNull
    public SecureHash getIssuerHash() {
        return issuerHash;
    }

    /**
     * Gets the user defined symbol of the tokens in a pool.
     *
     * @return Returns the user defined symbol of the tokens in a pool.
     */
    @NotNull
    public String getSymbol() {
        return symbol;
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param obj The object to compare with the current object.
     * @return Returns true if the specified object is equal to the current object; otherwise, false.
     */
    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof UtxoTokenPoolKey && equals((UtxoTokenPoolKey) obj);
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param other The Party to compare with the current object.
     * @return Returns true if the specified Party is equal to the current object; otherwise, false.
     */
    public boolean equals(@NotNull final UtxoTokenPoolKey other) {
        return Objects.equals(other.tokenType, tokenType)
                && Objects.equals(other.issuerHash, issuerHash)
                && Objects.equals(other.symbol, symbol);
    }

    /**
     * Serves as the default hash function.
     *
     * @return Returns a hash code for the current object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(tokenType, issuerHash, symbol);
    }

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    @Override
    public String toString() {
        return MessageFormat.format("UtxoTokenPoolKey(tokenType={0}, issuerHash={1}, symbol={1})", tokenType, issuerHash, symbol);
    }
}
