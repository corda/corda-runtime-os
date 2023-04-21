package net.corda.v5.ledger.utxo.observer;

import net.corda.v5.application.crypto.DigestService;
import net.corda.v5.ledger.utxo.ContractState;
import net.corda.v5.ledger.utxo.token.selection.TokenSelection;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a mechanism to observe produced contract states of type {@link T} when they are committed to the ledger.
 * <p>
 * Users should implement this interface for any states that need to be selectable via the {@link TokenSelection} API.
 * <p>
 * The Corda platform will discover and invoke implementations of this interface for all produced states that match
 * the type specified by {@link UtxoLedgerTokenStateObserver#getStateType()}.
 * <p>
 * Example usage:
 * <ul>
 * <li>Java:<pre>{@code
 * public class ExampleStateJ implements ContractState {
 * public List<PublicKey> participants;
 * public SecureHash issuer;
 * public String currency;
 * public BigDecimal amount;
 *
 * @NotNull
 * @Override public List<PublicKey> getParticipants() {
 * return participants;
 * }
 * }
 *
 * public class UtxoLedgerTokenStateObserverJavaExample implements UtxoLedgerTokenStateObserver<ExampleStateJ> {
 * @NotNull
 * @Override public Class<ExampleStateJ> getStateType() {
 * return ExampleStateJ.class;
 * }
 * @NotNull
 * @Override public UtxoToken onCommit(@NotNull ExampleStateJ state) {
 * return new UtxoToken(
 * new UtxoTokenPoolKey(ExampleStateK.class.getName(), state.issuer, state.currency),
 * state.amount,
 * new UtxoTokenFilterFields()
 * );
 * }
 * }
 * }</pre></li>
 * <li>Kotlin:<pre>{@code
 * data class ExampleStateK(
 * override val participants: List<PublicKey>,
 * val issuer: SecureHash,
 * val currency: String,
 * val amount: BigDecimal
 * ) : ContractState
 *
 * class UtxoLedgerTokenStateObserverKotlinExample : UtxoLedgerTokenStateObserver<ExampleStateK> {
 *
 * override val stateType = ExampleStateK::class.java
 *
 * override fun onCommit(state: ExampleStateK): UtxoToken {
 * return UtxoToken(
 * UtxoTokenPoolKey(ExampleStateK::class.java.name, state.issuer, state.currency),
 * state.amount,
 * UtxoTokenFilterFields()
 * )
 * }
 * }
 * }</pre></li></ul>
 */
public interface UtxoLedgerTokenStateObserver<T extends ContractState> {

    /**
     * Gets the {@link ContractState} type that the current observer is intended for.
     *
     * @return Returns the {@link ContractState} type that the current observer is intended for.
     */
    // TODO : Consider providing default implementation
    @NotNull
    Class<T> getStateType();

    /**
     * The action to be performed when a {@link ContractState} of type {@link T} is committed to the ledger.
     *
     * @param state The {@link ContractState} that was committed to the ledger.
     * @return Returns a {@link UtxoToken}.
     */
    @NotNull
    UtxoToken onCommit(@NotNull T state, @NotNull DigestService digestService);
}
