package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.ResolveStateRefs
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoOutputRecordFactory
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import java.nio.ByteBuffer

@Suppress("LongParameterList")
class UtxoResolveStateRefsRequestHandler(
    private val resolveStateRefs: ResolveStateRefs,
    private val serializationService: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val utxoOutputRecordFactory: UtxoOutputRecordFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        // Find the states
        val stateAndRefs = persistenceService.resolveStateRefs(
            resolveStateRefs.stateRefs.map {
                StateRef(
                    SecureHash(it.transactionHash.algorithm, it.transactionHash.serverHash.array()),
                    it.index
                )
            }
        )

        // Return output records
        return listOf(
            utxoOutputRecordFactory.getFindUnconsumedStatesByTypeSuccessRecord( // CORE-9012 use proper response format
                stateAndRefs.flatten().map(ByteBuffer::wrap),
                externalEventContext,
                serializationService
            )
        )
    }
}
