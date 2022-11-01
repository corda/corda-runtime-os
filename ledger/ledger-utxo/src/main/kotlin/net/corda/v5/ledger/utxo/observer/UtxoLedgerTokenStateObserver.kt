package net.corda.v5.ledger.utxo.observer

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.token.selection.TokenSelection

/**
 * The [UtxoLedgerTokenStateObserver] observes produced contract states of type [T] when they are committed to the
 * ledger. Users should implement this interface for any states that need to be selectable via the [TokenSelection] API.
 *
 * The Corda platform will discover and invoke implementations of this interface for all produced states that match
 * the type specified by [UtxoLedgerTokenStateObserver.stateType].
 *
 * Example of use in Java.
 * ```Java
 * public class ExampleStateJ implements ContractState {
 *     public List<PublicKey> participants;
 *     public SecureHash issuer;
 *     public String currency;
 *     public BigDecimal amount;
 *
 *     @NotNull
 *     @Override
 *     public List<PublicKey> getParticipants() {
 *         return participants;
 *     }
 * }
 *
 * public class UtxoLedgerTokenStateObserverJavaExample implements UtxoLedgerTokenStateObserver<ExampleStateJ> {
 *
 *     @NotNull
 *     @Override
 *     public Class<ExampleStateJ> getStateType() {
 *         return ExampleStateJ.class;
 *     }
 *
 *     @NotNull
 *     @Override
 *     public UtxoToken onProduced(@NotNull StateAndRef<? extends ExampleStateJ> stateAndRef) {
 *         ExampleStateJ state = stateAndRef.getState().getContractState();
 *
 *         return new UtxoToken(
 *                 new UtxoTokenPoolKey(ExampleStateK.class.getName(), state.issuer, state.currency),
 *                 state.amount,
 *                 new UtxoTokenFilterFields()
 *         );
 *     }
 * }
 * ```
 * Example of use in Kotlin.
 * ```Kotlin
 * data class ExampleStateK(
 *     override val participants: List<PublicKey>,
 *     val issuer: SecureHash,
 *     val currency: String,
 *     val amount: BigDecimal
 * ) : ContractState
 *
 *  * class UtxoLedgerTokenStateObserverKotlinExample : UtxoLedgerTokenStateObserver<ExampleStateK> {
 *
 *     override val stateType = ExampleStateK::class.java
 *
 *     override fun onProduced(stateAndRef: StateAndRef<ExampleStateK>): UtxoToken {
 *         val state = stateAndRef.state.contractState
 *         return UtxoToken(
 *             UtxoTokenPoolKey(ExampleStateK::class.java.name, state.issuer, state.currency),
 *             state.amount,
 *             UtxoTokenFilterFields()
 *         )
 *     }
 * }
 * ```
 *
 * @property stateType Type of contract the observer is for.
 */
interface UtxoLedgerTokenStateObserver<T : ContractState> {

    val stateType: Class<T>

    /***
     * Called for each new state[T] committed to the ledger
     *
     * @param stateAndRef Instance of the committed state and its reference.
     */
    fun onProduced(stateAndRef: StateAndRef<T>): UtxoToken
}