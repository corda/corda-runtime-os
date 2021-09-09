package net.corda.v5.ledger.transactions

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement

@DoNotImplement
interface TransactionBuilderFactory : CordaServiceInjectable, CordaFlowInjectable {

    /** Creates a new transaction builder. Properties are then set on the returned TransactionBuilder. */
    fun create() : TransactionBuilder

}

