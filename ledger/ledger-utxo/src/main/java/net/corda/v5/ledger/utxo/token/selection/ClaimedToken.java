package net.corda.v5.ledger.utxo.token.selection;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.StateRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Defines a claimed token from a {@link TokenSelection}.
 */
@DoNotImplement
public interface ClaimedToken {

    /**
     * Gets the state ref of the token.
     *
     * @return Returns the state ref of the token.
     */
    @NotNull
    StateRef getStateRef();

    /**
     * Gets the type of the token.
     *
     * @return Returns the type of the token.
     */
    @NotNull
    String getTokenType();

    /**
     * Gets the issuer of the token.
     *
     * @return Returns the issuer of the token.
     */
    @NotNull
    SecureHash getIssuerHash();

    /**
     * Gets the notary for the token.
     *
     * @return Returns the notary for the token.
     */
    @NotNull
    MemberX500Name getNotaryX500Name();

    /**
     * gets the symbol for the token.
     *
     * @return Returns the symbol for the token.
     */
    @NotNull
    String getSymbol();

    /**
     * Gets the user-defined tag for the token.
     *
     * @return Returns the user-defined tag for the token.
     */
    @Nullable
    String getTag();

    /**
     * Gets the owner of the token.
     *
     * @return Returns the owner of the token.
     */
    @Nullable
    SecureHash getOwnerHash();

    /**
     * Gets the amount of the token.
     *
     * @return Returns the amount of the token.
     */
    @NotNull
    BigDecimal getAmount();
}
