package net.corda.membership.impl.p2p.handler

import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.data.membership.command.synchronisation.SynchronisationMetaData
import net.corda.data.membership.command.synchronisation.member.ProcessMembershipUpdates
import net.corda.data.membership.p2p.MembershipPackage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.SYNCHRONIZATION_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory

internal class MembershipPackageHandler(
    avroSchemaRegistry: AvroSchemaRegistry
) : AuthenticatedMessageHandler<MembershipPackage>(avroSchemaRegistry) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val payloadType = MembershipPackage::class.java

    override fun invokeAuthenticatedMessage(
        header: AuthenticatedMessageHeader,
        payload: MembershipPackage
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
                    payload
                )
            )
        )
    }
}
