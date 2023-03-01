package net.corda.v5.ledger.utxo.token.selection;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * Represents a description of the selection criteria for a token selection query using the {@link TokenSelection} API.
 */
public final class TokenClaimCriteria {

    /**
     * The type of tokens to be selected.
     */
    @NotNull
    private final String tokenType;

    /**
     * The {@link SecureHash} of the issuer of tokens to be selected.
     */
    @NotNull
    private final SecureHash issuerHash;

    /**
     * The {@link MemberX500Name} of the notary of the tokens to be selected.
     */
    @NotNull
    private final MemberX500Name notaryX500Name;

    /**
     * The symbol of the notary of tokens to be selected.
     */
    @NotNull
    private final String symbol;

    /**
     * The minimum value for the sum of {@link ClaimedToken#getAmount()} for the selected tokens.
     */
    @NotNull
    private final BigDecimal targetAmount;

    /**
     * An optional regular expression to match against the {@link ClaimedToken#getTag()} field, or null to match all tags.
     */
    @Nullable
    private String tagRegex;

    /**
     * An optional owner {@link SecureHash} of the tokens to be selected, or null to match all owners.
     */
    @Nullable
    private SecureHash ownerHash;

    /**
     * Creates a new instance of the {@link TokenClaimCriteria} class.
     *
     * @param tokenType      The type of tokens to be selected.
     * @param issuerHash     The {@link SecureHash} of the issuer of tokens to be selected.
     * @param notaryX500Name The {@link MemberX500Name} of the notary of the tokens to be selected.
     * @param symbol         The symbol of the notary of tokens to be selected.
     * @param targetAmount   The minimum value for the sum of {@link ClaimedToken#getAmount()} for the selected tokens.
     */
    public TokenClaimCriteria(
            @NotNull final String tokenType,
            @NotNull final SecureHash issuerHash,
            @NotNull final MemberX500Name notaryX500Name,
            @NotNull final String symbol,
            @NotNull final BigDecimal targetAmount) {
        this.tokenType = tokenType;
        this.issuerHash = issuerHash;
        this.notaryX500Name = notaryX500Name;
        this.symbol = symbol;
        this.targetAmount = targetAmount;
    }

    /**
     * Gets the type of tokens to be selected.
     *
     * @return Returns the type of tokens to be selected.
     */
    @NotNull
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Gets the {@link SecureHash} of the issuer of tokens to be selected.
     *
     * @return Returns the {@link SecureHash} of the issuer of tokens to be selected.
     */
    @NotNull
    public SecureHash getIssuerHash() {
        return issuerHash;
    }

    /**
     * Gets the {@link MemberX500Name} of the notary of the tokens to be selected.
     *
     * @return Returns the {@link MemberX500Name} of the notary of the tokens to be selected.
     */
    @NotNull
    public MemberX500Name getNotaryX500Name() {
        return notaryX500Name;
    }

    /**
     * Gets the symbol of the notary of tokens to be selected.
     *
     * @return Returns the symbol of the notary of tokens to be selected.
     */
    @NotNull
    public String getSymbol() {
        return symbol;
    }

    /**
     * Gets the minimum value for the sum of {@link ClaimedToken#getAmount()} for the selected tokens.
     *
     * @return Returns the minimum value for the sum of {@link ClaimedToken#getAmount()} for the selected tokens.
     */
    @NotNull
    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    /**
     * Gets an optional regular expression to match against the {@link ClaimedToken#getTag()} field, or null to match all tags.
     *
     * @return Returns an optional regular expression to match against the ClaimedToken.tag field, or null to match all tags.
     */
    @Nullable
    public String getTagRegex() {
        return tagRegex;
    }

    /**
     * Sets an optional regular expression to match against the {@link ClaimedToken#getTag()} field, or null to match all tags.
     */
    public void setTagRegex(@Nullable final String tagRegex) {
        this.tagRegex = tagRegex;
    }

    /**
     * Gets an optional owner {@link SecureHash} of the tokens to be selected, or null to match all owners.
     *
     * @return Returns an optional owner {@link SecureHash} of the tokens to be selected, or null to match all owners.
     */
    @Nullable
    public SecureHash getOwnerHash() {
        return ownerHash;
    }

    /**
     * Sets an optional owner {@link SecureHash} of the tokens to be selected, or null to match all owners.
     */
    public void setOwnerHash(@Nullable final SecureHash ownerHash) {
        this.ownerHash = ownerHash;
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param obj The object to compare with the current object.
     * @return Returns true if the specified object is equal to the current object; otherwise, false.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        return this == obj || obj instanceof TokenClaimCriteria && equals((TokenClaimCriteria) obj);
    }

    /**
     * Determines whether the specified object is equal to the current object.
     *
     * @param other The Party to compare with the current object.
     * @return Returns true if the specified Party is equal to the current object; otherwise, false.
     */
    public boolean equals(@NotNull final TokenClaimCriteria other) {
        return Objects.equals(other.tokenType, tokenType)
                && Objects.equals(other.issuerHash, issuerHash)
                && Objects.equals(other.notaryX500Name, notaryX500Name)
                && Objects.equals(other.symbol, symbol)
                && Objects.compare(other.targetAmount, targetAmount, BigDecimal::compareTo) == 0
                && Objects.equals(other.tagRegex, tagRegex)
                && Objects.equals(other.ownerHash, ownerHash);
    }

    /**
     * Serves as the default hash function.
     *
     * @return Returns a hash code for the current object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(tokenType, issuerHash, notaryX500Name, symbol, targetAmount, tagRegex, ownerHash);
    }

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    @Override
    public String toString() {
        return MessageFormat.format(
                "TokenClaimCriteria(tokenType={0}, issuerHash={1}, notaryX500Name={2}, symbol={3}, targetAmount={4}, tagRegex={5}, ownerHash={6})",
                tokenType, issuerHash, notaryX500Name, symbol, targetAmount, tagRegex, ownerHash
        );
    }
}
