package net.corda.v5.ledger.crypto

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable

interface TransactionDigestAlgorithmNamesFactory : CordaServiceInjectable, CordaFlowInjectable {
    fun create() : TransactionDigestAlgorithmNames
}
