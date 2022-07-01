package net.corda.membership.verification.service.impl

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.p2p.VerificationRequest
import net.corda.data.membership.p2p.VerificationResponse
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

class MembershipVerificationProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : DurableProcessor<String, VerificationRequest> {
    private companion object {
        val logger = contextLogger()
        val clock = UTCClock()
        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
        const val TTL = 1000L
    }

    override val keyClass = String::class.java
    override val valueClass = VerificationRequest::class.java

    private val responseSerializer = cordaAvroSerializationFactory.createAvroSerializer<VerificationResponse> {  }

    override fun onNext(events: List<Record<String, VerificationRequest>>): List<Record<*, *>> {
        logger.info("Handling request")
        return events.map {
            val authenticatedMessageHeader = AuthenticatedMessageHeader(
                // we need to switch here the source and destination
                // MGM
                it.value?.source,
                // member
                it.value?.destination,
                it.value?.requestTimestamp?.plusMillis(TTL)?.toEpochMilli(),
                it.value?.requestId,
                it.value?.requestId,
                MEMBERSHIP_P2P_SUBSYSTEM
            )
            val authenticatedMessage = AuthenticatedMessage(
                authenticatedMessageHeader,
                ByteBuffer.wrap(
                    responseSerializer.serialize(
                        VerificationResponse(
                            it.value?.requestId,
                            it.value?.requestTimestamp,
                            clock.instant(),
                            KeyValuePairList(emptyList<KeyValuePair>())
                        )
                    )
                )
            )
            Record(
                P2P_OUT_TOPIC,
                it.value?.source?.toCorda()?.id ?: "",
                AppMessage(authenticatedMessage)
            )
        }
    }
}