package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder

fun interface UtxoTransactionBuilderFactory {
    fun createUtxoTransactionBuilder(
        signingService: SigningService,
        serializationService: SerializationService,
        persistenceService: PersistenceService,
        configuration: SimulatorConfiguration
    ): UtxoTransactionBuilder
}

fun utxoTransactionBuilderFactoryBase(): UtxoTransactionBuilderFactory =
    UtxoTransactionBuilderFactory { ss, ser, per, c ->
        UtxoTransactionBuilderBase(
            signingService = ss,
            serializer = ser,
            persistenceService = per,
            configuration = c
        ) }