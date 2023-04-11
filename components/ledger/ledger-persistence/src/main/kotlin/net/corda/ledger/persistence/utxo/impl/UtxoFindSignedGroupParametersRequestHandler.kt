package net.corda.ledger.persistence.utxo.impl

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.persistence.FindSignedGroupParameters
import net.corda.data.persistence.EntityResponse
import net.corda.ledger.common.data.transaction.SignedGroupParametersContainer
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.messaging.api.records.Record
import net.corda.persistence.common.ResponseFactory
import net.corda.v5.application.serialization.SerializationService
import java.nio.ByteBuffer

class UtxoFindSignedGroupParametersRequestHandler(
    private val findSignedGroupParameters: FindSignedGroupParameters,
    private val serializationService: SerializationService,
    private val externalEventContext: ExternalEventContext,
    private val persistenceService: UtxoPersistenceService,
    private val responseFactory: ResponseFactory
) : RequestHandler {

    override fun execute(): List<Record<*, *>> {
        val signedGroupParameters = persistenceService.findSignedGroupParameters(
            findSignedGroupParameters.hash,
        )
        val signedGroupParametersContainer = if(signedGroupParameters != null) {
            with(signedGroupParameters) {
                SignedGroupParametersContainer(hash, bytes, signature, signatureSpec)
            }
        } else {
            null
        }
        return listOf(responseFactory.successResponse(
            externalEventContext,
            EntityResponse(
                listOfNotNull(signedGroupParametersContainer)
                    .map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
            )
        ))
    }
}
