package net.corda.v5.ledger.utxo.transaction

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.Party
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

/**
 * Defines UTXO ledger services.
 */
@DoNotImplement
interface UtxoLedgerService {

    /**
     * Gets a UTXO transaction builder
     *
     * @param notary The notary associated with the transaction builder.
     * @return Returns a new [UtxoTransactionBuilder] instance.
     */
    @Suspendable
    fun getTransactionBuilder(notary: Party): UtxoTransactionBuilder

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
     * Resolves the specified [StateRef] instances into [StateAndRef] instances of the specified [ContractState] type.
     *
     * @param T The underlying [ContractState] type.
     * @param stateRefs The [StateRef] instances to resolve.
     * @return Returns a [List] of [StateAndRef] of the specified [ContractState] type.
     */
    @Suspendable
    fun <T : ContractState> resolve(vararg stateRefs: StateRef): List<StateAndRef<T>>

    /**
     * Verifies the specified [StateAndRef] instances.
     *
     * @param stateAndRefs The [StateAndRef] instances to verify.
     */
    @Suspendable
    fun <T : ContractState> verify(stateAndRefs: Iterable<StateAndRef<T>>)

    /**
     * Verifies the specified [StateAndRef] instances.
     *
     * @param stateAndRefs The [StateAndRef] instances to verify.
     */
    @Suspendable
    fun <T : ContractState> verify(vararg stateAndRefs: StateAndRef<T>)
}
