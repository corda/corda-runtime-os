package net.corda.v5.ledger.utxo.transaction.filtered;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.ledger.utxo.Command;
import net.corda.v5.ledger.utxo.ContractState;
import net.corda.v5.ledger.utxo.StateRef;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.function.Predicate;

/**
 * Defines which transaction component groups to include in a {@link UtxoFilteredTransaction}. Any component groups
 * not specified by the builder are filtered out, so that their content or proof of existence is not included in the
 * resulting {@link UtxoFilteredTransaction}.
 */
// TODO : Consider alignment with other transaction builders. Should we use "add" instead of "with"?
@DoNotImplement
@SuppressWarnings("TooManyFunctions")
public interface UtxoFilteredTransactionBuilder {

    /**
     * Includes an audit proof of the notary component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the notary component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withNotary();

    /**
     * Includes an audit proof of the time window component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the time window component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withTimeWindow();

    /**
     * Includes a size proof of the signatories component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the signatories component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withSignatoriesSize();

    /**
     * Includes an audit proof of the signatories component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the signatories component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withSignatories();

    /**
     * Includes an audit proof of the signatories component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @param predicate Implements a filtering function that is applied to each deserialized component within the
     *                  signatories component group. A component is included when the predicate returns true; otherwise
     *                  the component is filtered.
     * @return Returns the current {@link UtxoFilteredTransaction} including the filtered signatories component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withSignatories(@NotNull Predicate<PublicKey> predicate);

    /**
     * Includes a size proof of the input state refs component group from a {@link UtxoSignedTransaction} in the
     * current {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the input state refs component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withInputStatesSize();

    /**
     * Includes an audit proof of the input state refs component group from a {@link UtxoSignedTransaction} in the
     * current {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the input state refs component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withInputStates();

    /**
     * Includes an audit proof of the input state refs component group from a {@link UtxoSignedTransaction} in the
     * current {@link UtxoFilteredTransaction}.
     *
     * @param predicate Implements a filtering function that is applied to each deserialized component within the
     *                  input state refs component group. A component is included when the predicate returns true;
     *                  otherwise the component is filtered.
     * @return Returns the current {@link UtxoFilteredTransaction} including the filtered input state refs component
     * group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withInputStates(@NotNull Predicate<StateRef> predicate);

    /**
     * Includes a size proof of the reference input state refs component group from a {@link UtxoSignedTransaction} in
     * the current {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the reference input state refs component
     * group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withReferenceStatesSize();

    /**
     * Includes an audit proof of the reference input state refs component group from a {@link UtxoSignedTransaction} in
     * the current {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the reference input state refs component
     * group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withReferenceStates();

    /**
     * Includes an audit proof of the reference input state refs component group from a {@link UtxoSignedTransaction} in the
     * current {@link UtxoFilteredTransaction}.
     *
     * @param predicate Implements a filtering function that is applied to each deserialized component within the
     *                  reference input state refs component group. A component is included when the predicate returns
     *                  true; otherwise the component is filtered.
     * @return Returns the current {@link UtxoFilteredTransaction} including the filtered reference input state refs
     * component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withReferenceStates(@NotNull Predicate<StateRef> predicate);

    /**
     * Includes a size proof of the output state refs component group from a {@link UtxoSignedTransaction} in the
     * current {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the output state refs component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withOutputStatesSize();

    /**
     * Includes an audit proof of the output state refs component group from a {@link UtxoSignedTransaction} in the
     * current {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the output state refs component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withOutputStates();

    /**
     * Includes an audit proof of the output state refs component group from a {@link UtxoSignedTransaction} in the
     * current {@link UtxoFilteredTransaction}.
     *
     * @param predicate Implements a filtering function that is applied to each deserialized component within the
     *                  output state refs component group. A component is included when the predicate returns true;
     *                  otherwise the component is filtered.
     * @return Returns the current {@link UtxoFilteredTransaction} including the filtered output state refs component
     * group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withOutputStates(@NotNull Predicate<ContractState> predicate);

    /**
     * Includes a size proof of the commands component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the commands component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withCommandsSize();

    /**
     * Includes an audit proof of the commands component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @return Returns the current {@link UtxoFilteredTransaction} including the commands component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withCommands();

    /**
     * Includes an audit proof of the commands component group from a {@link UtxoSignedTransaction} in the current
     * {@link UtxoFilteredTransaction}.
     *
     * @param predicate Implements a filtering function that is applied to each deserialized component within the
     *                  commands component group. A component is included when the predicate returns true;
     *                  otherwise the component is filtered.
     * @return Returns the current {@link UtxoFilteredTransaction} including the filtered commands component group.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransactionBuilder withCommands(@NotNull Predicate<Command> predicate);

    /**
     * Builds a {@link UtxoFilteredTransaction} that contains the component groups and components specified by the
     * current {@link UtxoFilteredTransactionBuilder}.
     *
     * @return Returns a {@link UtxoFilteredTransaction} that contains the component groups and components specified
     * by the current {@link UtxoFilteredTransactionBuilder}.
     */
    @NotNull
    @Suspendable
    UtxoFilteredTransaction build();
}
