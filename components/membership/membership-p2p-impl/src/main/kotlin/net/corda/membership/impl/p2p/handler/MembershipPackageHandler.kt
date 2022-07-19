package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.MembershipPackage
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.SYNCHRONISATION_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.toCorda
import java.nio.ByteBuffer

internal class MembershipPackageHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler() {
    companion object {
        private val logger = contextLogger()
    }

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, MembershipPackage> {
        logger.info("Received membership data package. Publishing to topic $SYNCHRONISATION_TOPIC.")
        return Record(
            SYNCHRONISATION_TOPIC,
            header.destination.toCorda().id,
            avroSchemaRegistry.deserialize(payload)
        )
    }
}
