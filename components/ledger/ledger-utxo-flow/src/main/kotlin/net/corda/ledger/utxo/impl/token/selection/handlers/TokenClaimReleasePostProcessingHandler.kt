package net.corda.ledger.utxo.impl.token.selection.handlers

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.ledger.utxo.token.selection.data.TokenForceClaimRelease
import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.handlers.FlowPostProcessingHandler
import net.corda.ledger.utxo.impl.token.selection.services.TokenClaimCheckpointService
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [FlowPostProcessingHandler::class])
class TokenClaimReleasePostProcessingHandler @Activate constructor(
    @Reference(service = TokenClaimCheckpointService::class)
    private val tokenClaimCheckpointService: TokenClaimCheckpointService,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : FlowPostProcessingHandler {

    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any> {}

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun postProcess(context: FlowEventContext<Any>): List<Record<*, *>> {
        return if (context.checkpoint.isCompleted || context.sendToDlq) {
            tokenClaimCheckpointService.getTokenClaims(context.checkpoint).map {
                logger.info("Generating a claim release request for claim id ${it.claimId}")
                val avroPoolKey = it.poolKey.toTokenPoolCacheKey()
                val claimRelease = TokenForceClaimRelease.newBuilder()
                    .setPoolKey(avroPoolKey)
                    .setClaimId(it.claimId)
                    .build()

                Record(
                    Schemas.Services.TOKEN_CACHE_EVENT,
                    checkNotNull(serializer.serialize(avroPoolKey)),
                    serializer.serialize(TokenPoolCacheEvent(avroPoolKey, claimRelease))
                )
            }
        } else {
            return listOf()
        }
    }
}
