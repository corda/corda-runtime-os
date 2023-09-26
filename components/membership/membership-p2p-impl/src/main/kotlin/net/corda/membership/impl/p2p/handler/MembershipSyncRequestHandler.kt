package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.mgm.ProcessSyncRequest
import net.corda.data.membership.p2p.MembershipSyncRequest
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.SYNCHRONIZATION_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.virtualnode.toCorda
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class MembershipSyncRequestHandler(
    avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler<MembershipSyncRequest>(avroSchemaRegistry) {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val payloadType = MembershipSyncRequest::class.java

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: MembershipSyncRequest
    ): Record<String, SynchronisationCommand> {
        val syncId = payload.distributionMetaData.syncId
        logger.info("Synchronisation request from ${header.source.x500Name} is received with synchronization ID ${syncId}.")
        return Record(
            SYNCHRONIZATION_TOPIC,
            "${syncId}-${header.source.toCorda().shortHash}",
            SynchronisationCommand(
                ProcessSyncRequest(
                    SynchronisationMetaData(
                        header.destination,
                        header.source
                    ),
                    payload
                )
            )
        )
    }
}