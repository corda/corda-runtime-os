package net.corda.membership.impl.p2p

import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.membership.p2p.helpers.TtlIdsFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory

internal class MembershipP2PMarkersProcessor(
    private val ttlIdsFactory: TtlIdsFactory = TtlIdsFactory()
) : DurableProcessor<String, AppMessageMarker> {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun onNext(events: List<Record<String, AppMessageMarker>>): List<Record<*, *>> {
        return events.flatMap { record ->
            val key = ttlIdsFactory.extractKey(record)
            if (key != null) {
                logger.warn("Got TTL for $key")
                listOf(
                    Record(
                        Schemas.Membership.REGISTRATION_COMMAND_TOPIC,
                        key,
                        RegistrationCommand(
                            DeclineRegistration("Could not send message to member.")
                        )
                    )
                )
            } else {
                emptyList()
            }
        }
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessageMarker::class.java
}
