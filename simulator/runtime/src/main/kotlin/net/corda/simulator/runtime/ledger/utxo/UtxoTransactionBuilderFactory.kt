package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

fun interface UtxoTransactionBuilderFactory {
    fun createUtxoTransactionBuilder(
        signingService: SigningService,
        persistenceService: PersistenceService,
        configuration: SimulatorConfiguration
    ): UtxoTransactionBuilder
}

fun utxoTransactionBuilderFactoryBase(): UtxoTransactionBuilderFactory =
    UtxoTransactionBuilderFactory { ss, per, c ->
        UtxoTransactionBuilderBase(
            signingService = ss,
            persistenceService = per,
            configuration = c
        ) }