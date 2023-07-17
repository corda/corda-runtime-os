package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.ConstructorForDeserialization
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

@CordaSerializable
data class FinalityPayload @ConstructorForDeserialization constructor(
    val map: Map<String, ByteArray>,
    private val serializationService: SerializationService
) {
    private companion object {
        const val INITIAL_TRANSACTION = "INITIAL_TRANSACTION"
        const val TRANSFER_ADDITIONAL_SIGNATURES = "TRANSFER_ADDITIONAL_SIGNATURES"
    }

    constructor(
        initialTransaction: UtxoSignedTransactionInternal,
        transferAdditionalSignatures: Boolean,
        serializationService: SerializationService
    ) : this(
        mapOf(
            INITIAL_TRANSACTION to serializationService.serialize(initialTransaction).bytes,
            TRANSFER_ADDITIONAL_SIGNATURES to serializationService.serialize(transferAdditionalSignatures).bytes
        ), serializationService
    )

    val initialTransaction by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            serializationService.deserialize<UtxoSignedTransactionInternal>(map[INITIAL_TRANSACTION]!!)
        } catch (e: Exception) {
            throw CordaRuntimeException(
                "Deserialization of ${map[INITIAL_TRANSACTION]} into UtxoSignedTransactionInternal failed.", e)
        }
    }
    val transferAdditionalSignatures by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            serializationService.deserialize<Boolean>(map[TRANSFER_ADDITIONAL_SIGNATURES]!!)
        } catch (e: Exception) {
            throw CordaRuntimeException(
                "Deserialization of ${map[INITIAL_TRANSACTION]} into UtxoSignedTransactionInternal failed.", e)
        }
    }
}