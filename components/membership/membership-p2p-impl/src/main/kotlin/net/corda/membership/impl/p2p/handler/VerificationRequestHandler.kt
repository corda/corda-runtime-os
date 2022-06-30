package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.p2p.VerificationRequest
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_VERIFICATION_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

internal class VerificationRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler()  {
    companion object {
        private val logger = contextLogger()
    }

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, VerificationRequest> {
        logger.info("Received verification request. Sending it to Verification Service to process.")
        return Record(
            MEMBERSHIP_VERIFICATION_TOPIC,
            header.destination.toCorda().id,
            avroSchemaRegistry.deserialize(payload)
        )
    }

}