package net.corda.p2p.linkmanager.sessions.events

import net.corda.data.p2p.event.SessionDeleted
import net.corda.data.p2p.event.SessionEvent
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.common.CommonComponents
import net.corda.schema.Schemas

internal class StatefulSessionEventPublisher(
    private val commonComponents: CommonComponents,
): LifecycleWithDominoTile {
    private val coordinatorFactory
        get() = commonComponents.lifecycleCoordinatorFactory
    private val publisherFactory
        get() = commonComponents.publisherFactory
    private val messagingConfiguration
        get() = commonComponents.messagingConfiguration

    companion object {
        private const val CLIENT_ID = "StatefulSessionEventPublisher"
    }

    private val config = PublisherConfig(CLIENT_ID)
    private val publisher = PublisherWithDominoLogic(publisherFactory, coordinatorFactory, config, messagingConfiguration)

    fun sessionDeleted(key: String) {
        publisher.publish(listOf(Record(Schemas.P2P.SESSION_EVENTS, key, SessionEvent(SessionDeleted(key)))))
    }

    override val dominoTile = publisher.dominoTile
}