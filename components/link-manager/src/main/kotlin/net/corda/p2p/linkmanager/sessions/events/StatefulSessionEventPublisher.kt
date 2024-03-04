package net.corda.p2p.linkmanager.sessions.events

import net.corda.data.p2p.event.SessionCreated
import net.corda.data.p2p.event.SessionDeleted
import net.corda.data.p2p.event.SessionDirection
import net.corda.data.p2p.event.SessionEvent
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas

class StatefulSessionEventPublisher(
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherFactory: PublisherFactory,
    messagingConfiguration: SmartConfig,
): LifecycleWithDominoTile {

    companion object {
        private const val CLIENT_ID = "StatefulSessionEventPublisher"
    }

    private val config = PublisherConfig(CLIENT_ID)
    private val publisher = PublisherWithDominoLogic(publisherFactory, coordinatorFactory, config, messagingConfiguration)

    fun sessionCreated(key: String, direction: SessionDirection) {
        publisher.publish(listOf(Record(Schemas.P2P.SESSION_EVENTS, key, SessionEvent(SessionCreated(direction, key)))))
    }

    fun sessionDeleted(key: String) {
        publisher.publish(listOf(Record(Schemas.P2P.SESSION_EVENTS, key, SessionEvent(SessionDeleted(key)))))
    }

    override val dominoTile = publisher.dominoTile
}