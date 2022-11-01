package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

/**
 * Defines UTXO ledger services.
 */
@DoNotImplement
interface UtxoLedgerService {

    /**
     * Gets a UTXO transaction builder
     *
     * @return Returns a new [UtxoTransactionBuilder] instance.
     */
    @Suspendable
    fun getTransactionBuilder(): UtxoTransactionBuilder

    /**
     * Resolves the specified [StateRef] instances into [StateAndRef] instances of the specified [ContractState] type.
     *
     * @param T The underlying [ContractState] type.
     * @param stateRefs The [StateRef] instances to resolve.
     * @return Returns a [List] of [StateAndRef] of the specified [ContractState] type.
     */
    @Suspendable
    fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>>

    /**
     * Resolves the specified [StateRef] instance into a [StateAndRef] instance of the specified [ContractState] type.
     *
     * @param T The underlying [ContractState] type.
     * @param stateRef The [StateRef] instances to resolve.
     * @return Returns a [StateAndRef] of the specified [ContractState] type.
     */
    @Suspendable
    fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T>

    // TODO CORE-7327 Add verify(signedTx) verify(ledgerTx)
}
