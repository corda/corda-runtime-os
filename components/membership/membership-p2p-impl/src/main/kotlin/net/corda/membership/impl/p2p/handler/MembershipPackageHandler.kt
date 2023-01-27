package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.SYNCHRONIZATION_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

internal class MembershipPackageHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler() {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, SynchronisationCommand> {
        logger.info("Received membership data package. Publishing to topic $SYNCHRONIZATION_TOPIC.")
        return Record(
            SYNCHRONIZATION_TOPIC,
            header.destination.toCorda().shortHash.value,
            SynchronisationCommand(
                ProcessMembershipUpdates(
                    SynchronisationMetaData(
                        header.source,
                        header.destination
                    ),
                    avroSchemaRegistry.deserialize(payload)
                )
            )
        )
    }
}
