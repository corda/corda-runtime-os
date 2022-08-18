package net.corda.v5.ledger.consensual

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

/**
 * It provides access to the different Consensual Ledger Services
 */
@DoNotImplement
interface ConsensualLedgerService {
    /**
     * Returns an empty [ConsensualTransactionBuilder] instance
     */
    @Suspendable
    fun getTransactionBuilder(): ConsensualTransactionBuilder

    /* TODO
    fun fetchTransaction(id: SecureHash)
    fun finality(sessions)
    fun receiveFinality( ()-> ... )
    ... Vault ...
    */
}