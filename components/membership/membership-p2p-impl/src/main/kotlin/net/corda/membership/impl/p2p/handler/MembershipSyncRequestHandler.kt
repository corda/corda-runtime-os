package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.Companion.SYNCHRONIZATION_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

internal class MembershipSyncRequestHandler(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler() {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: ByteBuffer
    ): Record<String, SynchronisationCommand> {
        val request = avroSchemaRegistry.deserialize<MembershipSyncRequest>(payload)
        val metadata = request.distributionMetaData
        logger.info("Synchronisation request from ${header.source.x500Name} is received with synchronization ID ${metadata.syncId}.")
        return Record(
            SYNCHRONIZATION_TOPIC,
            "${metadata.syncId}-${header.source.toCorda().shortHash}",
            SynchronisationCommand(
                ProcessSyncRequest(
                    SynchronisationMetaData(
                        header.destination,
                        header.source
                    ),
                    request
                )
            )
        )
    }
}